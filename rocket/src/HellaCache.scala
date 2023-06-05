// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.rocket

import chisel3._
import chisel3.util.{isPow2,log2Ceil,log2Up,Decoupled,Valid}
import chisel3.dontTouch
import scala.collection.mutable.ListBuffer
import org.chipsalliance.rocket.util._
import org.chipsalliance.rocket.MemoryOpConstants._

class HellaCacheReq(
  xLen: Int,
  coreDataBits: Int,
  coreDataBytes: Int,
  val subWordBits: Int,
  cacheBlockBytes: Int,
  cacheDataBeats: Int,
  cacheDataBits: Int,
  dcacheReqTagBits: Int,
  dcacheArbPorts: Int,
  untagBits: Int,
  blockOffBits: Int,
  rowBits: Int,
  coreMaxAddrBits: Int,
  pgIdxBits: Int,
  val lrscCycles: Int, // ISA requires 16-insn LRSC sequences to succeed
  nWays: Int,
  nMMIOs: Int,
  dataScratchpadBytes: Int,
  dataECCBytes: Int,
  dataCode: Code,
  usingDataScratchpad: Boolean,
  usingVM: Boolean
) extends Bundle {
  def wordBits = coreDataBits
  def wordBytes = coreDataBytes
  def subWordBytes = subWordBits / 8
  def wordOffBits = log2Up(wordBytes)
  def beatBytes = cacheBlockBytes / cacheDataBeats
  def beatWords = beatBytes / wordBytes
  def beatOffBits = log2Up(beatBytes)
  def idxMSB = untagBits-1
  def idxLSB = blockOffBits
  def offsetmsb = idxLSB-1
  def offsetlsb = wordOffBits
  def rowWords = rowBits/wordBits
  def doNarrowRead = coreDataBits * nWays % rowBits == 0
  def eccBytes = dataECCBytes
  val eccBits = dataECCBytes * 8
  val encBits = dataCode.width(eccBits)
  val encWordBits = encBits * (wordBits / eccBits)
  def encDataBits = dataCode.width(coreDataBits) // NBDCache only
  def encRowBits = encDataBits*rowWords
  def lrscBackoff = 3 // disallow LRSC reacquisition briefly
  def blockProbeAfterGrantCycles = 8 // give the processor some time to issue a request after a grant
  def nIOMSHRs = nMMIOs
  def maxUncachedInFlight = nMMIOs
  def dataScratchpadSize = dataScratchpadBytes

  require(rowBits >= coreDataBits, s"rowBits($rowBits) < coreDataBits($coreDataBits)")
  if (!usingDataScratchpad)
    require(rowBits == cacheDataBits, s"rowBits($rowBits) != cacheDataBits($cacheDataBits)")
  // would need offset addr for puts if data width < xlen
  require(xLen <= cacheDataBits, s"xLen($xLen) > cacheDataBits($cacheDataBits)")

  val phys = Bool()
  val no_alloc = Bool()
  val no_xcpt = Bool()

  val addr = UInt(coreMaxAddrBits.W)
  val idx  = Option.when(usingVM && untagBits > pgIdxBits)(UInt(coreMaxAddrBits.W))
  val tag  = UInt((dcacheReqTagBits + log2Ceil(dcacheArbPorts)).W)
  val cmd  = UInt(M_SZ.W)
  val size = UInt(log2Ceil(coreDataBytes.log2 + 1).W)
  val signed = Bool()
  val dprv = UInt(PRV.SZ.W)
  val dv = Bool()
}

class HellaCacheWriteData(coreDataBits: Int, coreDataBytes: Int) extends Bundle {
  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
}

class HellaCacheResp(coreDataBits: Int, coreDataBytes: Int) extends Bundle {
  val replay = Bool()
  val has_data = Bool()
  val data_word_bypass = UInt(coreDataBits.W)
  val data_raw = UInt(coreDataBits.W)
  val store_data = UInt(coreDataBits.W)
  val data = UInt(coreDataBits.W)
  val mask = UInt(coreDataBytes.W)
}

class AlignmentExceptions extends Bundle {
  val ld = Bool()
  val st = Bool()
}

class HellaCacheExceptions extends Bundle {
  val ma = new AlignmentExceptions
  val pf = new AlignmentExceptions
  val gf = new AlignmentExceptions
  val ae = new AlignmentExceptions
}

class HellaCachePerfEvents extends Bundle {
  val acquire = Bool()
  val release = Bool()
  val grant = Bool()
  val tlbMiss = Bool()
  val blocked = Bool()
  val canAcceptStoreThenLoad = Bool()
  val canAcceptStoreThenRMW = Bool()
  val canAcceptLoadThenLoad = Bool()
  val storeBufferEmptyAfterLoad = Bool()
  val storeBufferEmptyAfterStore = Bool()
}

// interface between D$ and processor/DTLB
class HellaCacheIO(
  paddrBits: Int,
  vaddrBitsExtended: Int,
  separateUncachedResp: Boolean,
  xLen: Int,
  coreDataBits: Int,
  coreDataBytes: Int,
  subWordBits: Int,
  cacheBlockBytes: Int,
  cacheDataBeats: Int,
  cacheDataBits: Int,
  dcacheReqTagBits: Int,
  dcacheArbPorts: Int,
  untagBits: Int,
  blockOffBits: Int,
  rowBits: Int,
  coreMaxAddrBits: Int,
  pgIdxBits: Int,
  lrscCycles: Int,
  nWays: Int,
  nMMIOs: Int,
  dataScratchpadBytes: Int,
  dataECCBytes: Int,
  dataCode: Code,
  usingDataScratchpad: Boolean,
  usingVM: Boolean
) extends Bundle {
  val req = Decoupled(new HellaCacheReq(
    xLen, coreDataBits, coreDataBytes, subWordBits, cacheBlockBytes, cacheDataBeats,
    cacheDataBits, dcacheReqTagBits, dcacheArbPorts, untagBits, blockOffBits,
    rowBits, coreMaxAddrBits, pgIdxBits, lrscCycles, nWays, nMMIOs, dataScratchpadBytes,
    dataECCBytes, dataCode, usingDataScratchpad, usingVM
  ))
  val s1_kill = Output(Bool()) // kill previous cycle's req
  val s1_data = Output(new HellaCacheWriteData(coreDataBits, coreDataBytes)) // data for previous cycle's req
  val s2_nack = Input(Bool()) // req from two cycles ago is rejected
  val s2_nack_cause_raw = Input(Bool()) // reason for nack is store-load RAW hazard (performance hint)
  val s2_kill = Output(Bool()) // kill req from two cycles ago
  val s2_uncached = Input(Bool()) // advisory signal that the access is MMIO
  val s2_paddr = Input(UInt(paddrBits.W)) // translated address

  val resp = Flipped(Valid(new HellaCacheResp(coreDataBits, coreDataBytes)))
  val replay_next = Input(Bool())
  val s2_xcpt = Input(new HellaCacheExceptions)
  val s2_gpa = Input(UInt(vaddrBitsExtended.W))
  val s2_gpa_is_pte = Input(Bool())
  val uncached_resp = Option.when(separateUncachedResp)(Flipped(Decoupled(new HellaCacheResp(coreDataBits, coreDataBytes))))
  val ordered = Input(Bool())
  val perf = Input(new HellaCachePerfEvents())

  val keep_clock_enabled = Output(Bool()) // should D$ avoid clock-gating itself?
  val clock_enabled = Input(Bool()) // is D$ currently being clocked?
}