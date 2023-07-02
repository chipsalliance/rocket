// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket.tilelink

import chisel3._
import chisel3.util.Decoupled

class TLCreditedBuffer(delay: TLCreditedDelay) extends Module
{
  val node = TLCreditedAdapterNode(
    clientFn  = p => p.copy(delay = delay + p.delay),
    managerFn = p => p.copy(delay = delay + p.delay))

  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      out.a :<> in.a.pipeline(delay.a)
      in.b :<> out.b.pipeline(delay.b)
      out.c :<> in.c.pipeline(delay.c)
      in.d :<> out.d.pipeline(delay.d)
      out.e :<> in.e.pipeline(delay.e)
    }
  }
}

object TLCreditedBuffer {
  def apply(delay: TLCreditedDelay): TLCreditedAdapterNode = {
    val buffer = Module(new TLCreditedBuffer(delay))
    buffer.node
  }
  def apply(delay: CreditedDelay): TLCreditedAdapterNode = apply(TLCreditedDelay(delay))
  def apply(): TLCreditedAdapterNode = apply(CreditedDelay(1, 1))
}

class TLCreditedSource(delay: TLCreditedDelay) extends Module
{
  val node = TLCreditedSourceNode(delay)
  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val tld = edgeOut.delay
      out.a :<> CreditedIO.fromSender(in.a, tld.a.total).pipeline(delay.a)
      in.b :<> Decoupled(out.b.pipeline(delay.b).toReceiver(tld.b.total))
      out.c :<> CreditedIO.fromSender(in.c, tld.c.total).pipeline(delay.c)
      in.d :<> Decoupled(out.d.pipeline(delay.d).toReceiver(tld.d.total))
      out.e :<> CreditedIO.fromSender(in.e, tld.e.total).pipeline(delay.e)
    }
  }
}

object TLCreditedSource {
  def apply(delay: TLCreditedDelay): TLCreditedSourceNode = {
    val source = Module(new TLCreditedSource(delay))
    source.node
  }
  def apply(delay: CreditedDelay): TLCreditedSourceNode = apply(TLCreditedDelay(delay))
  def apply(): TLCreditedSourceNode = apply(CreditedDelay(1, 1))
}

class TLCreditedSink(delay: TLCreditedDelay) extends Module
{
  val node = TLCreditedSinkNode(delay)
  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val tld = edgeIn.delay
      out.a :<> Decoupled(in.a.pipeline(delay.a).toReceiver(tld.a.total))
      in.b :<> CreditedIO.fromSender(out.b, tld.b.total).pipeline(delay.b)
      out.c :<> Decoupled(in.c.pipeline(delay.c).toReceiver(tld.c.total))
      in.d :<> CreditedIO.fromSender(out.d, tld.d.total).pipeline(delay.d)
      out.e :<> Decoupled(in.e.pipeline(delay.e).toReceiver(tld.e.total))
    }
  }
}

object TLCreditedSink {
  def apply(delay: TLCreditedDelay): TLCreditedSinkNode = {
    val sink = Module(new TLCreditedSink(delay))
    sink.node
  }
  def apply(delay: CreditedDelay): TLCreditedSinkNode = apply(TLCreditedDelay(delay))
  def apply(): TLCreditedSinkNode = apply(CreditedDelay(1, 1))
}

// Synthesizable unit tests

class TLRAMCreditedCrossing(txns: Int, params: CreditedCrossing) extends Module {
  val model = Module(new TLRAMModel("CreditedCrossing"))
  val fuzz = Module(new TLFuzzer(txns))
  val island = Module(new CrossingWrapper(params))
  val ram  = island { Module(new TLRAM(AddressSet(0x0, 0x3ff))) }

  island.crossTLIn(ram.node) := TLFragmenter(4, 256) := TLDelayer(0.1) := model.node := fuzz.node

  lazy val module = new Impl
  class Impl extends ModuleImp(this) with UnitTestModule {
    io.finished := fuzz.module.io.finished
  }
}

class TLRAMCreditedCrossingTest(txns: Int = 5000, timeout: Int = 500000) extends UnitTest(timeout) {
  val dut_1000 = Module(Module(new TLRAMCreditedCrossing(txns, CreditedCrossing(CreditedDelay(1, 0), CreditedDelay(0, 0)))).module)
  val dut_0100 = Module(Module(new TLRAMCreditedCrossing(txns, CreditedCrossing(CreditedDelay(0, 1), CreditedDelay(0, 0)))).module)
  val dut_0010 = Module(Module(new TLRAMCreditedCrossing(txns, CreditedCrossing(CreditedDelay(0, 0), CreditedDelay(1, 0)))).module)
  val dut_0001 = Module(Module(new TLRAMCreditedCrossing(txns, CreditedCrossing(CreditedDelay(0, 0), CreditedDelay(0, 1)))).module)
  val dut_1111 = Module(Module(new TLRAMCreditedCrossing(txns, CreditedCrossing(CreditedDelay(1, 1), CreditedDelay(1, 1)))).module)

  val duts = Seq(dut_1000, dut_0100, dut_0010, dut_0001, dut_1111)
  duts.foreach { _.io.start := true.B }
  io.finished := duts.map(_.io.finished).reduce(_ && _)
}
