// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket.util

import Chisel._
import chisel3.util.random.LFSR
import scala.math._

object Random
{
  def apply(mod: Int, random: UInt): UInt = {
    if (isPow2(mod)) random.extract(log2Ceil(mod)-1,0)
    else PriorityEncoder(partition(apply(1 << log2Up(mod*8), random), mod))
  }
  def apply(mod: Int): UInt = apply(mod, randomizer)
  def oneHot(mod: Int, random: UInt): UInt = {
    if (isPow2(mod)) UIntToOH(random(log2Up(mod)-1,0))
    else PriorityEncoderOH(partition(apply(1 << log2Up(mod*8), random), mod)).asUInt
  }
  def oneHot(mod: Int): UInt = oneHot(mod, randomizer)

  private def randomizer = LFSR(16)
  private def partition(value: UInt, slices: Int) =
    Seq.tabulate(slices)(i => value < UInt(((i + 1) << value.getWidth) / slices))
}

object PopCountAtLeast {
  private def two(x: UInt): (Bool, Bool) = x.getWidth match {
    case 1 => (x.asBool, Bool(false))
    case n =>
      val half = x.getWidth / 2
      val (leftOne, leftTwo) = two(x(half - 1, 0))
      val (rightOne, rightTwo) = two(x(x.getWidth - 1, half))
      (leftOne || rightOne, leftTwo || rightTwo || (leftOne && rightOne))
  }
  def apply(x: UInt, n: Int): Bool = n match {
    case 0 => Bool(true)
    case 1 => x.orR
    case 2 => two(x)._2
    case 3 => PopCount(x) >= UInt(n)
  }
}


