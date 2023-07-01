// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.rocket

import chisel3._
import chisel3.util.{isPow2,log2Ceil,log2Up,Decoupled,Valid}
import org.chipsalliance.rocket.util._
import org.chipsalliance.rocket.MemoryOpConstants._

case class DCacheParams(
  xLen: Int,
  paddrBits: Int,
  vaddrBitsExtended: Int,
  coreDataBits: Int,
  coreMaxAddrBits: Int,
  cacheBlockBytes: Int,
  pgIdxBits: Int,
  addressBits: Int,
  dataBits: Int,
  lrscCycles: Int, // ISA requires 16-insn LRSC sequences to succeed
  dcacheReqTagBits: Int,
  dcacheArbPorts: Int,
  usingVM: Boolean,
  nSets: Int = 64,
  nWays: Int = 4,
  rowBits: Int = 64,
  subWordBitsOption: Option[Int] = None,
  replacementPolicy: String = "random",
  nTLBSets: Int = 1,
  nTLBWays: Int = 32,
  nTLBBasePageSectors: Int = 4,
  nTLBSuperpages: Int = 4,
  tagECC: Option[String] = None,
  dataECC: Option[String] = None,
  dataECCBytes: Int = 1,
  nMSHRs: Int = 1,
  nSDQ: Int = 17,
  nRPQ: Int = 16,
  nMMIOs: Int = 1,
  blockBytes: Int = 64,
  separateUncachedResp: Boolean = false,
  acquireBeforeRelease: Boolean = false,
  pipelineWayMux: Boolean = false,
  clockGate: Boolean = false,
  scratch: Option[BigInt] = None) {

  def coreDataBytes: Int = coreDataBits / 8
  def xBytes: Int = xLen / 8

  def tagCode: Code = Code.fromString(tagECC)
  def dataCode: Code = Code.fromString(dataECC)

  def dataScratchpadBytes: Int = scratch.map(_ => nSets*blockBytes).getOrElse(0)
  def usingDataScratchpad: Boolean = scratch.nonEmpty

  def replacement = new RandomReplacement(nWays)

  def silentDrop: Boolean = !acquireBeforeRelease

  require((!scratch.isDefined || nWays == 1),
    "Scratchpad only allowed in direct-mapped cache.")
  require((!scratch.isDefined || nMSHRs == 0),
    "Scratchpad only allowed in blocking cache.")
  if (scratch.isEmpty)
    require(isPow2(nSets), s"nSets($nSets) must be pow2")

  def blockOffBits = log2Up(cacheBlockBytes)
  def idxBits = log2Up(nSets)
  def untagBits = blockOffBits + idxBits
  def pgUntagBits = if (usingVM) untagBits min pgIdxBits else untagBits
  def tagBits = addressBits - pgUntagBits
  def wayBits = log2Up(nWays)
  def isDM = nWays == 1
  def rowBytes = rowBits/8
  def rowOffBits = log2Up(rowBytes)

  def lgCacheBlockBytes = log2Ceil(cacheBlockBytes)
  def cacheDataBits = dataBits
  def cacheDataBytes = cacheDataBits / 8
  def cacheDataBeats = (cacheBlockBytes * 8) / cacheDataBits
  def refillCycles = cacheDataBeats

  def wordBits = coreDataBits
  def wordBytes = coreDataBits / 8
  def subWordBits = subWordBitsOption.getOrElse(wordBits)
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
  if (!scratch.isDefined)
    require(rowBits == cacheDataBits, s"rowBits($rowBits) != cacheDataBits($cacheDataBits)")
  // would need offset addr for puts if data width < xlen
  require(xLen <= cacheDataBits, s"xLen($xLen) > cacheDataBits($cacheDataBits)")
}

class HellaCacheReq(params: DCacheParams) extends Bundle {
  val phys = Bool()
  val no_alloc = Bool()
  val no_xcpt = Bool()

  val addr = UInt(params.coreMaxAddrBits.W)
  val idx  = Option.when(params.usingVM && params.untagBits > params.pgIdxBits)(UInt(params.coreMaxAddrBits.W))
  val tag  = UInt((params.dcacheReqTagBits + log2Ceil(params.dcacheArbPorts)).W)
  val cmd  = UInt(M_SZ.W)
  val size = UInt(log2Ceil(params.coreDataBytes.log2 + 1).W)
  val signed = Bool()
  val dprv = UInt(PRV.SZ.W)
  val dv = Bool()
}

class HellaCacheWriteData(params: DCacheParams) extends Bundle {
  val data = UInt(params.coreDataBits.W)
  val mask = UInt(params.coreDataBytes.W)
}

class HellaCacheResp(params: DCacheParams) extends Bundle {
  val replay = Bool()
  val has_data = Bool()
  val data_word_bypass = UInt(params.coreDataBits.W)
  val data_raw = UInt(params.coreDataBits.W)
  val store_data = UInt(params.coreDataBits.W)
  val data = UInt(params.coreDataBits.W)
  val mask = UInt(params.coreDataBytes.W)
  val tag  = UInt((params.dcacheReqTagBits + log2Ceil(params.dcacheArbPorts)).W)
  val size = UInt(log2Ceil(params.coreDataBytes.log2 + 1).W)
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
class HellaCacheIO(params: DCacheParams) extends Bundle {
  val req = Decoupled(new HellaCacheReq(params))
  val s1_kill = Output(Bool()) // kill previous cycle's req
  val s1_data = Output(new HellaCacheWriteData(params)) // data for previous cycle's req
  val s2_nack = Input(Bool()) // req from two cycles ago is rejected
  val s2_nack_cause_raw = Input(Bool()) // reason for nack is store-load RAW hazard (performance hint)
  val s2_kill = Output(Bool()) // kill req from two cycles ago
  val s2_uncached = Input(Bool()) // advisory signal that the access is MMIO
  val s2_paddr = Input(UInt(params.paddrBits.W)) // translated address

  val resp = Flipped(Valid(new HellaCacheResp(params)))
  val replay_next = Input(Bool())
  val s2_xcpt = Input(new HellaCacheExceptions)
  val s2_gpa = Input(UInt(params.vaddrBitsExtended.W))
  val s2_gpa_is_pte = Input(Bool())
  val uncached_resp = Option.when(params.separateUncachedResp)(Flipped(Decoupled(new HellaCacheResp(params))))
  val ordered = Input(Bool())
  val perf = Input(new HellaCachePerfEvents())

  val keep_clock_enabled = Output(Bool()) // should D$ avoid clock-gating itself?
  val clock_enabled = Input(Bool()) // is D$ currently being clocked?
}

/** Metadata array used for all HellaCaches */

class L1Metadata(tagBits: Int) extends Bundle {
  val coh = new ClientMetadata
  val tag = UInt(tagBits.W)
}

object L1Metadata {
  def apply(tagBits: Int, tag: Bits, coh: ClientMetadata) = {
    val meta = Wire(new L1Metadata(tagBits))
    meta.tag := tag
    meta.coh := coh
    meta
  }
}

class L1MetaReadReq(idxBits: Int, nWays: Int, tagBits: Int) extends Bundle {
  val idx    = UInt(idxBits.W)
  val way_en = UInt(nWays.W)
  val tag    = UInt(tagBits.W)
}

class L1MetaWriteReq(idxBits: Int, nWays: Int, tagBits: Int) extends L1MetaReadReq(idxBits, nWays, tagBits)  {
  val data = new L1Metadata(tagBits)
}

class L1MetadataArray[T <: L1Metadata](onReset: () => T, idxBits: Int, tagBits: Int, nWays: Int, nSets: Int) extends Module {
  val rstVal = onReset()
  val io = IO(new Bundle {
    val read = Flipped(Decoupled(new L1MetaReadReq(idxBits, nWays, tagBits)))
    val write = Flipped(Decoupled(new L1MetaWriteReq(idxBits, nWays, tagBits)))
    val resp = Output(Vec(nWays, rstVal.cloneType))
  })

  val rst_cnt = RegInit(0.U(log2Up(nSets+1).W))
  val rst = rst_cnt < nSets.U
  val waddr = Mux(rst, rst_cnt, io.write.bits.idx)
  val wdata = Mux(rst, rstVal, io.write.bits.data).asUInt
  val wmask = Mux(rst || (nWays == 1).B, (-1).S, io.write.bits.way_en.asSInt).asBools
  val rmask = Mux(rst || (nWays == 1).B, (-1).S, io.read.bits.way_en.asSInt).asBools
  when (rst) { rst_cnt := rst_cnt+1.U }

  val metabits = rstVal.getWidth
  val tag_array = SyncReadMem(nSets, Vec(nWays, UInt(metabits.W)))
  val wen = rst || io.write.valid
  when (wen) {
    tag_array.write(waddr, VecInit.fill(nWays)(wdata), wmask)
  }
  io.resp := tag_array.read(io.read.bits.idx, io.read.fire()).map(_.asTypeOf(chiselTypeOf(rstVal)))

  io.read.ready := !wen // so really this could be a 6T RAM
  io.write.ready := !rst
}


/** Base classes for Diplomatic TL2 HellaCaches */

abstract class HellaCache(staticIdForMetadataUseOnly: Int, protected val cfg: DCacheParams) extends Module {
  protected def cacheClientParameters = cfg.scratch.map(x => Seq()).getOrElse(Seq(TLMasterParameters.v1(
    name          = s"Core ${staticIdForMetadataUseOnly} DCache",
    sourceId      = IdRange(0, 1 max cfg.nMSHRs),
    supportsProbe = TransferSizes(cfg.blockBytes, cfg.blockBytes))))

  protected def mmioClientParameters = Seq(TLMasterParameters.v1(
    name          = s"Core ${staticIdForMetadataUseOnly} DCache MMIO",
    sourceId      = IdRange(firstMMIO, firstMMIO + cfg.nMMIOs),
    requestFifo   = true))

  def firstMMIO = (cacheClientParameters.map(_.sourceId.end) :+ 0).max

  val node = TLClientNode(Seq(TLMasterPortParameters.v1(
    clients = cacheClientParameters ++ mmioClientParameters,
    minLatency = 1,
    requestFields = tileParams.core.useVM.option(Seq()).getOrElse(Seq(AMBAProtField())))))

  val hartIdSinkNodeOpt = cfg.scratch.map(_ => BundleBridgeSink[UInt]())
  val mmioAddressPrefixSinkNodeOpt = cfg.scratch.map(_ => BundleBridgeSink[UInt]())

  val module: HellaCacheModule

  def flushOnFenceI = cfg.scratch.isEmpty && !node.edges.out(0).manager.managers.forall(m => !m.supportsAcquireB || !m.executable || m.regionType >= RegionType.TRACKED || m.regionType <= RegionType.IDEMPOTENT)

  def canSupportCFlushLine = !usingVM || cfg.blockBytes * cfg.nSets <= (1 << pgIdxBits)

  require(!tileParams.core.haveCFlush || cfg.scratch.isEmpty, "CFLUSH_D_L1 instruction requires a D$")
}