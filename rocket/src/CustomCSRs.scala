// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket

import chisel3._

case class CustomCSR(id: Int, mask: BigInt, init: Option[BigInt])

object CustomCSRs {
  val mnscratch = 0x350
  val mnepc = 0x351
  val mncause = 0x352
  val mnstatus = 0x353
  val all = {
    val res = collection.mutable.ArrayBuffer[Int]()
    res += mnscratch
    res += mnepc
    res += mncause
    res += mnstatus
    res.toArray
  }
  val all32 = {
    val res = collection.mutable.ArrayBuffer(all:_*)
    res.toArray
  }
}

class CustomCSRIO(xLen: Int) extends Bundle {
  val wen = Bool()
  val wdata = UInt(xLen.W)
  val value = UInt(xLen.W)
}

class CustomCSRs(xLen: Int) extends Bundle {
  // Not all cores have these CSRs, but those that do should follow the same
  // numbering conventions.  So we list them here but default them to None.
  protected def bpmCSRId = 0x7c0
  protected def bpmCSR: Option[CustomCSR] = None

  protected def chickenCSRId = 0x7c1
  protected def chickenCSR: Option[CustomCSR] = None

  // If you override this, you'll want to concatenate super.decls
  def decls: Seq[CustomCSR] = bpmCSR.toSeq ++ chickenCSR

  val csrs = Vec(decls.size, new CustomCSRIO(xLen))

  def flushBTB = getOrElse(bpmCSR, _.wen, false.B)
  def bpmStatic = getOrElse(bpmCSR, _.value(0), false.B)
  def disableDCacheClockGate = getOrElse(chickenCSR, _.value(0), false.B)
  def disableICacheClockGate = getOrElse(chickenCSR, _.value(1), false.B)
  def disableCoreClockGate = getOrElse(chickenCSR, _.value(2), false.B)
  def disableSpeculativeICacheRefill = getOrElse(chickenCSR, _.value(3), false.B)
  def suppressCorruptOnGrantData = getOrElse(chickenCSR, _.value(9), false.B)

  protected def getByIdOrElse[T](id: Int, f: CustomCSRIO => T, alt: T): T = {
    val idx = decls.indexWhere(_.id == id)
    if (idx < 0) alt else f(csrs(idx))
  }

  protected def getOrElse[T](csr: Option[CustomCSR], f: CustomCSRIO => T, alt: T): T =
    csr.map(c => getByIdOrElse(c.id, f, alt)).getOrElse(alt)
}
