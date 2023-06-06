// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.rocket

import chisel3._
import chisel3.util._
import org.chipsalliance.rocket.util._
import org.chipsalliance.rocket.ScalarOpConstants._
import org.chipsalliance.rocket.MemoryOpConstants._
import Instructions._
import CustomInstructions._

abstract trait DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])]
}

class IntCtrlSigs(aluFn: ALUFN = ALUFN()) extends Bundle {

  val legal = Bool()
  val fp = Bool()
  val rocc = Bool()
  val branch = Bool()
  val jal = Bool()
  val jalr = Bool()
  val rxs2 = Bool()
  val rxs1 = Bool()
  val scie = Bool()
  val zbk = Bool()
  val zkn = Bool()
  val zks = Bool()
  val sel_alu2 = Bits(A2_X.getWidth.W)
  val sel_alu1 = Bits(A1_X.getWidth.W)
  val sel_imm = Bits(IMM_X.getWidth.W)
  val alu_dw = Bool()
  val alu_fn = Bits(aluFn.FN_X.getWidth.W)
  val mem = Bool()
  val mem_cmd = Bits(M_SZ.W)
  val rfs1 = Bool()
  val rfs2 = Bool()
  val rfs3 = Bool()
  val wfd = Bool()
  val mul = Bool()
  val div = Bool()
  val wxd = Bool()
  val csr = Bits(CSR.SZ.W)
  val fence_i = Bool()
  val fence = Bool()
  val amo = Bool()
  val dp = Bool()

  def default: List[BitPat] =
                //           jal                                                                         renf1               fence.i
                //   val     | jalr                                                                      | renf2             |
                //   | fp_val| | renx2                                                                   | | renf3           |
                //   | | rocc| | | renx1               s_alu1                              mem_val       | | | wfd           |
                //   | | | br| | | | scie      s_alu2  |       imm    dw     alu           | mem_cmd     | | | | mul         |
                //   | | | | | | | | | zbk     |       |       |      |      |             | |           | | | | | div       | fence
                //   | | | | | | | | | | zkn   |       |       |      |      |             | |           | | | | | | wxd     | | amo
                //   | | | | | | | | | | | zks |       |       |      |      |             | |           | | | | | | |       | | | dp
                List(N,X,X,X,X,X,X,X,X,X,X,X,  A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,   N,M_X,        X,X,X,X,X,X,X,CSR.X,X,X,X,X)

  def decode(inst: UInt, table: Iterable[(BitPat, List[BitPat])]) = {
    val decoder = DecodeLogic(inst, default, table)
    val sigs = Seq(legal, fp, rocc, branch, jal, jalr, rxs2, rxs1, scie, zbk, zkn, zks, sel_alu2,
                   sel_alu1, sel_imm, alu_dw, alu_fn, mem, mem_cmd,
                   rfs1, rfs2, rfs3, wfd, mul, div, wxd, csr, fence_i, fence, amo, dp)
    sigs zip decoder map {case(s,d) => s := d}
    this
  }
}

class IDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    BNE->       List(Y,N,N,Y,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SNE,   N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BEQ->       List(Y,N,N,Y,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SEQ,   N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BLT->       List(Y,N,N,Y,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SLT,   N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BLTU->      List(Y,N,N,Y,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SLTU,  N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BGE->       List(Y,N,N,Y,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SGE,   N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    BGEU->      List(Y,N,N,Y,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_SB,DW_XPR,aluFn.FN_SGEU,  N,M_X,        N,N,N,N,N,N,N,CSR.N,N,N,N,N),

    JAL->       List(Y,N,N,N,Y,N,N,N,N,N,N,N,A2_SIZE,A1_PC,  IMM_UJ,DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    JALR->      List(Y,N,N,N,N,Y,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AUIPC->     List(Y,N,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_PC,  IMM_U, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),

    LB->        List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    LH->        List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    LW->        List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    LBU->       List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    LHU->       List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SB->        List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    SH->        List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    SW->        List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,N,N,N,N,N,N,CSR.N,N,N,N,N),

    LUI->       List(Y,N,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_U, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ADDI->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLTI ->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SLT,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLTIU->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SLTU,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ANDI->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_AND,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ORI->       List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_OR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    XORI->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_XOR,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ADD->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SUB->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SUB,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLT->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SLT,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLTU->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SLTU,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AND->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_AND,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    OR->        List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_OR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    XOR->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_XOR,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLL->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SL,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRL->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRA->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_SRA,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),

    FENCE->     List(Y,N,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.N,N,Y,N,N),

    ECALL->     List(Y,N,N,N,N,N,N,X,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    EBREAK->    List(Y,N,N,N,N,N,N,X,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    MRET->      List(Y,N,N,N,N,N,N,X,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    WFI->       List(Y,N,N,N,N,N,N,X,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    CEASE->     List(Y,N,N,N,N,N,N,X,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    CSRRW->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.W,N,N,N,N),
    CSRRS->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.S,N,N,N,N),
    CSRRC->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.C,N,N,N,N),
    CSRRWI->    List(Y,N,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_Z, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.W,N,N,N,N),
    CSRRSI->    List(Y,N,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_Z, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.S,N,N,N,N),
    CSRRCI->    List(Y,N,N,N,N,N,N,N,N,N,N,N,A2_IMM, A1_ZERO,IMM_Z, DW_XPR,aluFn.FN_ADD,   N,M_X,        N,N,N,N,N,N,Y,CSR.C,N,N,N,N))
}

class FenceIDecode(flushDCache: Boolean, aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  private val (v, cmd) = if (flushDCache) (Y, BitPat(M_FLUSH_ALL)) else (N, M_X)

  val table: Array[(BitPat, List[BitPat])] = Array(
    FENCE_I->   List(Y,N,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     v,cmd,        N,N,N,N,N,N,N,CSR.N,Y,Y,N,N))
}

class CFlushDecode(supportsFlushLine: Boolean, aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  private def zapRs1(x: BitPat) = if (supportsFlushLine) x else BitPat(x.value.U)

  val table: Array[(BitPat, List[BitPat])] = Array(
    zapRs1(CFLUSH_D_L1)->
                List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_FLUSH_ALL,N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    zapRs1(CDISCARD_D_L1)->
                List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_FLUSH_ALL,N,N,N,N,N,N,N,CSR.I,N,N,N,N))
}

class SVMDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SFENCE_VMA->List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_SFENCE,   N,N,N,N,N,N,N,CSR.I,N,N,N,N))
}

class SDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SRET->      List(Y,N,N,N,N,N,N,X,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N))
}

class HypervisorDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(

    HFENCE_VVMA->List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_HFENCEV,  N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    HFENCE_GVMA->List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_HFENCEG,  N,N,N,N,N,N,N,CSR.I,N,N,N,N),

    HLV_B ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_XRD,      N,N,N,N,N,N,Y,CSR.I,N,N,N,N),
    HLV_BU->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_XRD,      N,N,N,N,N,N,Y,CSR.I,N,N,N,N),
    HLV_H ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_XRD,      N,N,N,N,N,N,Y,CSR.I,N,N,N,N),
    HLV_HU->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_XRD,      N,N,N,N,N,N,Y,CSR.I,N,N,N,N),
    HLVX_HU->   List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_HLVX,     N,N,N,N,N,N,Y,CSR.I,N,N,N,N),
    HLV_W->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_XRD,      N,N,N,N,N,N,Y,CSR.I,N,N,N,N),
    HLVX_WU->   List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_HLVX,     N,N,N,N,N,N,Y,CSR.I,N,N,N,N),

    HSV_B->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_I, DW_XPR, aluFn.FN_ADD, Y,M_XWR,      N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    HSV_H->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_I, DW_XPR, aluFn.FN_ADD, Y,M_XWR,      N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    HSV_W->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_I, DW_XPR, aluFn.FN_ADD, Y,M_XWR,      N,N,N,N,N,N,N,CSR.I,N,N,N,N))
}

class DebugDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    DRET->      List(Y,N,N,N,N,N,N,X,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N))
}

class NMIDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    MNRET->     List(Y,N,N,N,N,N,N,X,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,N,CSR.I,N,N,N,N))
}

class I32Decode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  import Instructions32._
  val table: Array[(BitPat, List[BitPat])] = Array(
    SLLI->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SL,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRLI->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRAI->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SRA,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class I64Decode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    LD->        List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    LWU->       List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SD->        List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,N,N,N,N,N,N,CSR.N,N,N,N,N),

    SLLI->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SL,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRLI->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRAI->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_SRA,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),

    ADDIW->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_32,aluFn.FN_ADD,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLLIW->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_32,aluFn.FN_SL,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRLIW->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_32,aluFn.FN_SR,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRAIW->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_32,aluFn.FN_SRA,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ADDW->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32,aluFn.FN_ADD,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SUBW->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32,aluFn.FN_SUB,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLLW->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32,aluFn.FN_SL,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRLW->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32,aluFn.FN_SR,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SRAW->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32,aluFn.FN_SRA,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class Hypervisor64Decode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    HLV_D->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_XRD,      N,N,N,N,N,N,Y,CSR.I,N,N,N,N),
    HSV_D->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_I, DW_XPR, aluFn.FN_ADD, Y,M_XWR,      N,N,N,N,N,N,N,CSR.I,N,N,N,N),
    HLV_WU->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_ZERO, A1_RS1, IMM_X, DW_XPR, aluFn.FN_ADD, Y,M_XRD,      N,N,N,N,N,N,Y,CSR.I,N,N,N,N))
}

class MDecode(pipelinedMul: Boolean, aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val M = if (pipelinedMul) Y else N
  val D = if (pipelinedMul) N else Y
  val table: Array[(BitPat, List[BitPat])] = Array(
    MUL->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_MUL,   N,M_X,        N,N,N,N,M,D,Y,CSR.N,N,N,N,N),
    MULH->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_MULH,  N,M_X,        N,N,N,N,M,D,Y,CSR.N,N,N,N,N),
    MULHU->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_MULHU, N,M_X,        N,N,N,N,M,D,Y,CSR.N,N,N,N,N),
    MULHSU->    List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_MULHSU,N,M_X,        N,N,N,N,M,D,Y,CSR.N,N,N,N,N),

    DIV->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_DIV,   N,M_X,        N,N,N,N,N,Y,Y,CSR.N,N,N,N,N),
    DIVU->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_DIVU,  N,M_X,        N,N,N,N,N,Y,Y,CSR.N,N,N,N,N),
    REM->       List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_REM,   N,M_X,        N,N,N,N,N,Y,Y,CSR.N,N,N,N,N),
    REMU->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_XPR,aluFn.FN_REMU,  N,M_X,        N,N,N,N,N,Y,Y,CSR.N,N,N,N,N))
}

class M64Decode(pipelinedMul: Boolean, aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  import Instructions64._
  val M = if (pipelinedMul) Y else N
  val D = if (pipelinedMul) N else Y
  val table: Array[(BitPat, List[BitPat])] = Array(
    MULW->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32, aluFn.FN_MUL,   N,M_X,        N,N,N,N,M,D,Y,CSR.N,N,N,N,N),

    DIVW->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32, aluFn.FN_DIV,   N,M_X,        N,N,N,N,N,Y,Y,CSR.N,N,N,N,N),
    DIVUW->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32, aluFn.FN_DIVU,  N,M_X,        N,N,N,N,N,Y,Y,CSR.N,N,N,N,N),
    REMW->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32, aluFn.FN_REM,   N,M_X,        N,N,N,N,N,Y,Y,CSR.N,N,N,N,N),
    REMUW->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2, A1_RS1, IMM_X, DW_32, aluFn.FN_REMU,  N,M_X,        N,N,N,N,N,Y,Y,CSR.N,N,N,N,N))
}

class ADecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    AMOADD_W->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_ADD,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOXOR_W->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_XOR,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOSWAP_W-> List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_SWAP,  N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOAND_W->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_AND,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOOR_W->   List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_OR,    N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOMIN_W->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_MIN,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOMINU_W-> List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_MINU,  N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOMAX_W->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_MAX,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOMAXU_W-> List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_MAXU,  N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),

    LR_W->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XLR,      N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    SC_W->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XSC,      N,N,N,N,N,N,Y,CSR.N,N,N,Y,N))
}

class A64Decode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    AMOADD_D->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_ADD,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOSWAP_D-> List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_SWAP,  N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOXOR_D->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_XOR,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOAND_D->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_AND,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOOR_D->   List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_OR,    N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOMIN_D->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_MIN,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOMINU_D-> List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_MINU,  N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOMAX_D->  List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_MAX,   N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    AMOMAXU_D-> List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XA_MAXU,  N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),

    LR_D->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XLR,      N,N,N,N,N,N,Y,CSR.N,N,N,Y,N),
    SC_D->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   Y,M_XSC,      N,N,N,N,N,N,Y,CSR.N,N,N,Y,N))
}

class HDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FCVT_S_H->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FCVT_H_S->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSGNJ_H->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSGNJX_H->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSGNJN_H->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FMIN_H->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FMAX_H->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FADD_H->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSUB_H->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FMUL_H->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FMADD_H->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N),
    FMSUB_H->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N),
    FNMADD_H->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N),
    FNMSUB_H->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N),
    FCLASS_H->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FMV_X_H->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FCVT_W_H->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FCVT_WU_H-> List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FEQ_H->     List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,N,N,N,Y,CSR.N,N,N,N,N),
    FLT_H->     List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,N,N,N,Y,CSR.N,N,N,N,N),
    FLE_H->     List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,N,N,N,Y,CSR.N,N,N,N,N),
    FMV_H_X->   List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FCVT_H_W->  List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FCVT_H_WU-> List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FLH->       List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSH->       List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,Y,N,N,N,N,N,CSR.N,N,N,N,N),
    FDIV_H->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSQRT_H->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N))
}

class FDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FSGNJ_S->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSGNJX_S->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSGNJN_S->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FMIN_S->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FMAX_S->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FADD_S->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSUB_S->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FMUL_S->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FMADD_S->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N),
    FMSUB_S->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N),
    FNMADD_S->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N),
    FNMSUB_S->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,N),
    FCLASS_S->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FMV_X_W->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FCVT_W_S->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FCVT_WU_S-> List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FEQ_S->     List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,N,N,N,Y,CSR.N,N,N,N,N),
    FLT_S->     List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,N,N,N,Y,CSR.N,N,N,N,N),
    FLE_S->     List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,N,N,N,Y,CSR.N,N,N,N,N),
    FMV_W_X->   List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FCVT_S_W->  List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FCVT_S_WU-> List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FLW->       List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSW->       List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,Y,N,N,N,N,N,CSR.N,N,N,N,N),
    FDIV_S->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N),
    FSQRT_S->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,N))
}

class DDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FCVT_S_D->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FCVT_D_S->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FSGNJ_D->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FSGNJX_D->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FSGNJN_D->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FMIN_D->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FMAX_D->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FADD_D->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FSUB_D->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FMUL_D->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FMADD_D->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,Y),
    FMSUB_D->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,Y),
    FNMADD_D->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,Y),
    FNMSUB_D->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,Y,Y,N,N,N,CSR.N,N,N,N,Y),
    FCLASS_D->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y),
    FCVT_W_D->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y),
    FCVT_WU_D-> List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y),
    FEQ_D->     List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,N,N,N,Y,CSR.N,N,N,N,Y),
    FLT_D->     List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,N,N,N,Y,CSR.N,N,N,N,Y),
    FLE_D->     List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,N,N,N,Y,CSR.N,N,N,N,Y),
    FCVT_D_W->  List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FCVT_D_WU-> List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FLD->       List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_I, DW_XPR,aluFn.FN_ADD,   Y,M_XRD,      N,N,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FSD->       List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_IMM, A1_RS1, IMM_S, DW_XPR,aluFn.FN_ADD,   Y,M_XWR,      N,Y,N,N,N,N,N,CSR.N,N,N,N,Y),
    FDIV_D->    List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FSQRT_D->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,Y,N,Y,N,N,N,CSR.N,N,N,N,Y))
}

class HDDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    FCVT_D_H->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FCVT_H_D->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,Y,N,N,N,CSR.N,N,N,N,Y))
}

class H64Decode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    FCVT_L_H->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FCVT_LU_H-> List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FCVT_H_L->  List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FCVT_H_LU-> List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N))
}

class F64Decode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    FCVT_L_S->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FCVT_LU_S-> List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    FCVT_S_L->  List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N),
    FCVT_S_LU-> List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,N))
}

class D64Decode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    FMV_X_D->   List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y),
    FCVT_L_D->  List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y),
    FCVT_LU_D-> List(Y,Y,N,N,N,N,N,N,N,N,N,N,A2_X,   A1_X,   IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        Y,N,N,N,N,N,Y,CSR.N,N,N,N,Y),
    FMV_D_X->   List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FCVT_D_L->  List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,Y),
    FCVT_D_LU-> List(Y,Y,N,N,N,N,N,Y,N,N,N,N,A2_X,   A1_RS1, IMM_X, DW_X,  aluFn.FN_X,     N,M_X,        N,N,N,Y,N,N,N,CSR.N,N,N,N,Y))
}

class SCIEDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SCIE.opcode->
                List(Y,N,N,N,N,N,Y,Y,Y,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_X,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

trait UsesABLUFN {
  val aluFn = ABLUFN()
}

class ZBADecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SH1ADD ->   List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR, aluFn.FN_SH1ADD,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SH2ADD ->   List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR, aluFn.FN_SH2ADD,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SH3ADD ->   List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR, aluFn.FN_SH3ADD,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBA64Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    ADD_UW ->   List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_64,aluFn.FN_ADDUW   ,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SLLI_UW ->  List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_64,aluFn.FN_SLLIUW  ,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SH1ADD_UW-> List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_64,aluFn.FN_SH1ADDUW,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SH2ADD_UW-> List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_64,aluFn.FN_SH2ADDUW,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SH3ADD_UW-> List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_64,aluFn.FN_SH3ADDUW,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

// In both Zbb and Zbkb
class ZBBNDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    ANDN ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ANDN,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ORN  ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ORN ,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    XNOR ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_XNOR,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

// In both Zbb and Zbkb
class ZBBRDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    ROR ->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ROR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ROL ->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ROL,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBBR32Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions32._
  val table: Array[(BitPat, List[BitPat])] = Array(
    RORI ->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_ROR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBBR64Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    RORI ->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_ROR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    RORW ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_32, aluFn.FN_ROR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    ROLW ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_32, aluFn.FN_ROL,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    RORIW ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_32, aluFn.FN_ROR,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

// Only in Zbb
class ZBBCDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    CLZ ->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_CLZ ,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CTZ ->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_CTZ ,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CPOP ->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_CPOP,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBBC64Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    CLZW ->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_32,aluFn.FN_CLZ ,      N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CTZW ->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_32,aluFn.FN_CTZ ,      N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CPOPW ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_32,aluFn.FN_CPOP,      N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

// Only in Zbb
class ZBBMDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    MAX ->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_MAX ,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    MAXU ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_MAXU,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    MIN ->      List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_MIN ,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    MINU ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_MINU,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

// Only in Zbb
class ZBBSEDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SEXT_H ->   List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_SEXTH,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SEXT_B ->   List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_SEXTB,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBBZE64Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    ZEXT_H ->   List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ZEXTH,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBBZE32Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions32._
  val table: Array[(BitPat, List[BitPat])] = Array(
    ZEXT_H ->   List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ZEXTH,    N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBBORCBDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    ORC_B ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ORCB,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

// In both Zbb and Zbkb
class ZBBREV864Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    REV8 ->     List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_REV8,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

// In both Zbb and Zbkb
class ZBBREV832Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions32._
  val table: Array[(BitPat, List[BitPat])] = Array(
    REV8->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X,A1_RS1, IMM_X, DW_XPR,aluFn.FN_REV8,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

// Only in Zbkb
class ZBKBDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    PACK ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_PACK,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    PACKH ->    List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_PACKH,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BREV8 ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X  ,A1_RS1, IMM_X, DW_XPR,aluFn.FN_BREV8,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBKB64Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    PACKW ->    List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_32, aluFn.FN_PACK,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBKB32Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions32._
  val table: Array[(BitPat, List[BitPat])] = Array(
    ZIP ->      List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X  ,A1_RS1, IMM_X, DW_X, aluFn.FN_ZIP,     N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    UNZIP ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_X  ,A1_RS1, IMM_X, DW_X, aluFn.FN_UNZIP,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

// also in Zbkc but Zbkc does not have CLMULR
class ZBCDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    CLMUL ->    List(Y,N,N,N,N,N,Y,Y,N,Y,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_CLMUL,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CLMULH ->   List(Y,N,N,N,N,N,Y,Y,N,Y,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_CLMULH, N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBCRDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    CLMULR ->   List(Y,N,N,N,N,N,Y,Y,N,Y,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_CLMULR, N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBKXDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    XPERM8 ->   List(Y,N,N,N,N,N,Y,Y,N,Y,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_XPERM8, N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    XPERM4 ->   List(Y,N,N,N,N,N,Y,Y,N,Y,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_XPERM4, N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBSDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    BCLR ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_BCLR,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BEXT ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_BEXT,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BINV ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_BINV,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BSET ->     List(Y,N,N,N,N,N,Y,Y,N,N,N,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_BSET,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBS32Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions32._
  val table: Array[(BitPat, List[BitPat])] = Array(
    BCLRI ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_BCLR,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BEXTI ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_BEXT,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BINVI ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_BINV,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BSETI ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_BSET,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZBS64Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    BCLRI ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_BCLR,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BEXTI ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_BEXT,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BINVI ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_BINV,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    BSETI ->    List(Y,N,N,N,N,N,N,Y,N,N,N,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_BSET,   N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZKND32Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions32._
  val table: Array[(BitPat, List[BitPat])] = Array(
    AES32DSI ->
                List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_DS, N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AES32DSMI->
                List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_DSM,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}
class ZKND64Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    AES64DS ->  List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_DS, N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AES64DSM -> List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_DSM,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AES64IM ->  List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_IM, N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AES64KS1I ->List(Y,N,N,N,N,N,N,Y,N,N,Y,N,A2_IMM,A1_RS1, IMM_I, DW_XPR,aluFn.FN_AES_KS1,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AES64KS2 -> List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_KS2,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}
class ZKNE32Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions32._
  val table: Array[(BitPat, List[BitPat])] = Array(
    AES32ESI -> List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_ES, N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AES32ESMI ->List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_ESM,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}
class ZKNE64Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    AES64ES ->  List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_ES, N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    AES64ESM -> List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_XPR,aluFn.FN_AES_ESM,N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZKNHDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SHA256SIG0->List(Y,N,N,N,N,N,N,Y,N,N,Y,N,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA256_SIG0,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA256SIG1->List(Y,N,N,N,N,N,N,Y,N,N,Y,N,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA256_SIG1,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA256SUM0->List(Y,N,N,N,N,N,N,Y,N,N,Y,N,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA256_SUM0,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA256SUM1->List(Y,N,N,N,N,N,N,Y,N,N,Y,N,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA256_SUM1,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}
class ZKNH32Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions32._
  val table: Array[(BitPat, List[BitPat])] = Array(
    SHA512SIG0L ->
                List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SIG0,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA512SIG1L ->
                List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SIG1,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA512SIG0H ->
                List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SIG0,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA512SIG1H ->
                List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SIG1,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA512SUM0R ->
                List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SUM0,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA512SUM1R ->
                List(Y,N,N,N,N,N,Y,Y,N,N,Y,N,A2_RS2,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SUM1,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}
class ZKNH64Decode extends DecodeConstants with UsesABLUFN
{
  import Instructions64._
  val table: Array[(BitPat, List[BitPat])] = Array(
    SHA512SIG0->List(Y,N,N,N,N,N,N,Y,N,N,Y,N,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SIG0,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA512SIG1->List(Y,N,N,N,N,N,N,Y,N,N,Y,N,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SIG1,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA512SUM0->List(Y,N,N,N,N,N,N,Y,N,N,Y,N,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SUM0,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SHA512SUM1->List(Y,N,N,N,N,N,N,Y,N,N,Y,N,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SHA512_SUM1,N,M_X,    N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class ZKSDecode extends DecodeConstants with UsesABLUFN
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    SM4ED ->    List(Y,N,N,N,N,N,Y,Y,N,N,N,Y,A2_RS2,A1_RS1, IMM_X, DW_X,  aluFn.FN_SM4ED,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SM4KS ->    List(Y,N,N,N,N,N,Y,Y,N,N,N,Y,A2_RS2,A1_RS1, IMM_X, DW_X,  aluFn.FN_SM4KS,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SM3P0 ->    List(Y,N,N,N,N,N,N,Y,N,N,N,Y,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SM3P0,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    SM3P1 ->    List(Y,N,N,N,N,N,N,Y,N,N,N,Y,A2_X  ,A1_RS1, IMM_X, DW_X,  aluFn.FN_SM3P1,  N,M_X,        N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}

class RoCCDecode(aluFn: ALUFN = ALUFN()) extends DecodeConstants
{
  val table: Array[(BitPat, List[BitPat])] = Array(
    CUSTOM0->           List(Y,N,Y,N,N,N,N,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM0_RS1->       List(Y,N,Y,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM0_RS1_RS2->   List(Y,N,Y,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM0_RD->        List(Y,N,Y,N,N,N,N,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM0_RD_RS1->    List(Y,N,Y,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM0_RD_RS1_RS2->List(Y,N,Y,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM1->           List(Y,N,Y,N,N,N,N,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM1_RS1->       List(Y,N,Y,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM1_RS1_RS2->   List(Y,N,Y,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM1_RD->        List(Y,N,Y,N,N,N,N,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM1_RD_RS1->    List(Y,N,Y,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM1_RD_RS1_RS2->List(Y,N,Y,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM2->           List(Y,N,Y,N,N,N,N,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM2_RS1->       List(Y,N,Y,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM2_RS1_RS2->   List(Y,N,Y,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM2_RD->        List(Y,N,Y,N,N,N,N,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM2_RD_RS1->    List(Y,N,Y,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM2_RD_RS1_RS2->List(Y,N,Y,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM3->           List(Y,N,Y,N,N,N,N,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM3_RS1->       List(Y,N,Y,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM3_RS1_RS2->   List(Y,N,Y,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,N,CSR.N,N,N,N,N),
    CUSTOM3_RD->        List(Y,N,Y,N,N,N,N,N,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM3_RD_RS1->    List(Y,N,Y,N,N,N,N,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N),
    CUSTOM3_RD_RS1_RS2->List(Y,N,Y,N,N,N,Y,Y,N,N,N,N,A2_ZERO,A1_RS1, IMM_X, DW_XPR,aluFn.FN_ADD,   N,M_X,N,N,N,N,N,N,Y,CSR.N,N,N,N,N))
}
