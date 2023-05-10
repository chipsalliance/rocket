// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.rocket

import chisel3._
import chisel3.util._
import scala.math.min
import scala.collection.{immutable, mutable}

package object util {
  def OptimizationBarrier[T <: Data](in: T): T = {
    val barrier = Module(new Module {
      val io = IO(new Bundle {
        val x = Input(chiselTypeOf(in))
        val y = Output(chiselTypeOf(in))
      })
      io.y := io.x
      override def desiredName = "OptimizationBarrier"
    })
    barrier.io.x := in
    barrier.io.y
  }
}
