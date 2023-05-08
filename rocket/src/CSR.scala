// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.rocket

import chisel3._
import chisel3.util.{BitPat, Cat, Fill, Mux1H, PopCount, PriorityMux, RegEnable, UIntToOH, Valid, log2Ceil, log2Up}

import scala.collection.mutable.LinkedHashMap
// import Instructions._
// import CustomInstructions._

object PRV
{
  val SZ = 2
  val U = 0
  val S = 1
  val H = 2
  val M = 3
}
