// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.rocket.util

import chisel3._
import chisel3.util._

object PopCountAtLeast {
  private def two(x: UInt): (Bool, Bool) = x.getWidth match {
    case 1 => (x.asBool, false.B)
    case n =>
      val half = x.getWidth / 2
      val (leftOne, leftTwo) = two(x(half - 1, 0))
      val (rightOne, rightTwo) = two(x(x.getWidth - 1, half))
      (leftOne || rightOne, leftTwo || rightTwo || (leftOne && rightOne))
  }
  def apply(x: UInt, n: Int): Bool = n match {
    case 0 => true.B
    case 1 => x.orR
    case 2 => two(x)._2
    case 3 => PopCount(x) >= n.U
  }
}
