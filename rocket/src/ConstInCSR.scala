// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.rocket

import chisel3._
import chisel3.util._

// wait for CSR
object PRV
{
  val SZ = 2
  val U = 0
  val S = 1
  val H = 2
  val M = 3
}

object CSR
{
  // commands
  val SZ = 3
  def X = BitPat.dontCare(SZ)
  def N = 0.U(SZ.W)
  def R = 2.U(SZ.W)
  def I = 4.U(SZ.W)
  def W = 5.U(SZ.W)
  def S = 6.U(SZ.W)
  def C = 7.U(SZ.W)

  // mask a CSR cmd with a valid bit
  def maskCmd(valid: Bool, cmd: UInt): UInt = {
    // all commands less than CSR.I are treated by CSRFile as NOPs
    cmd & ~Mux(valid, 0.U, CSR.I)
  }

  val ADDRSZ = 12

  def modeLSB: Int = 8
  def mode(addr: Int): Int = (addr >> modeLSB) % (1 << PRV.SZ)
  def mode(addr: UInt): UInt = addr(modeLSB + PRV.SZ - 1, modeLSB)

  def busErrorIntCause = 128
  def debugIntCause = 14 // keep in sync with MIP.debug
  def debugTriggerCause = {
    val res = debugIntCause
    require(!(Causes.all contains res))
    res
  }
  def rnmiIntCause = 13  // NMI: Higher numbers = higher priority, must not reuse debugIntCause
  def rnmiBEUCause = 12

  val firstCtr = CSRs.cycle
  val firstCtrH = CSRs.cycleh
  val firstHPC = CSRs.hpmcounter3
  val firstHPCH = CSRs.hpmcounter3h
  val firstHPE = CSRs.mhpmevent3
  val firstMHPC = CSRs.mhpmcounter3
  val firstMHPCH = CSRs.mhpmcounter3h
  val firstHPM = 3
  val nCtr = 32
  val nHPM = nCtr - firstHPM
  val hpmWidth = 40

  val maxPMPs = 16
}
