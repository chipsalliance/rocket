// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket.util

import chisel3._
import chisel3.util.{HasBlackBoxResource, HasBlackBoxPath}

import java.nio.file.{Files, Paths}

abstract class ClockGate extends BlackBox
  with HasBlackBoxResource with HasBlackBoxPath {
  val io = IO(new Bundle{
    val in = Input(Clock())
    val test_en = Input(Bool())
    val en = Input(Bool())
    val out = Output(Clock())
  })

  def addVerilogResource(vsrc: String): Unit = {
    if (Files.exists(Paths.get(vsrc)))
      addPath(vsrc)
    else
      addResource(vsrc)
  }
}

object ClockGate {
  def apply[T <: ClockGate](
      in: Clock,
      en: Bool,
      modelFile: Option[String],
      name: Option[String] = None): Clock = {
    val cg = Module(new EICG_wrapper)
    name.foreach(cg.suggestName(_))
    modelFile.map(cg.addVerilogResource(_))

    cg.io.in := in
    cg.io.test_en := false.B
    cg.io.en := en
    cg.io.out
  }

  def apply[T <: ClockGate](
      in: Clock,
      en: Bool,
      name: String): Clock =
    apply(in, en, Some(name))
}

// behavioral model of Integrated Clock Gating cell
class EICG_wrapper extends ClockGate