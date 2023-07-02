// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket.tilelink

import chisel3._
import org.chipsalliance.rocket.util._

class TLAsyncCrossingSource(sync: Option[Int]) extends Module
{
  def this(x: Int) = this(Some(x))
  def this() = this(None)

  val node = TLAsyncSourceNode(sync)

  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val bce = edgeIn.manager.anySupportAcquireB && edgeIn.client.anySupportProbe
      val psync = sync.getOrElse(edgeOut.manager.async.sync)
      val params = edgeOut.manager.async.copy(sync = psync)

      out.a <> ToAsyncBundle(in.a, params)
      in.d <> FromAsyncBundle(out.d, psync)
      property.cover(in.a, "TL_ASYNC_CROSSING_SOURCE_A", "MemorySystem;;TLAsyncCrossingSource Channel A")
      property.cover(in.d, "TL_ASYNC_CROSSING_SOURCE_D", "MemorySystem;;TLAsyncCrossingSource Channel D")

      if (bce) {
        in.b <> FromAsyncBundle(out.b, psync)
        out.c <> ToAsyncBundle(in.c, params)
        out.e <> ToAsyncBundle(in.e, params)
        property.cover(in.b, "TL_ASYNC_CROSSING_SOURCE_B", "MemorySystem;;TLAsyncCrossingSource Channel B")
        property.cover(in.c, "TL_ASYNC_CROSSING_SOURCE_C", "MemorySystem;;TLAsyncCrossingSource Channel C")
        property.cover(in.e, "TL_ASYNC_CROSSING_SOURCE_E", "MemorySystem;;TLAsyncCrossingSource Channel E")
      } else {
        in.b.valid := false.B
        in.c.ready := true.B
        in.e.ready := true.B
        out.b.ridx := 0.U
        out.c.widx := 0.U
        out.e.widx := 0.U
      }
    }
  }
}

class TLAsyncCrossingSink(params: AsyncQueueParams = AsyncQueueParams()) extends Module
{
  val node = TLAsyncSinkNode(params)

  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      val bce = edgeOut.manager.anySupportAcquireB && edgeOut.client.anySupportProbe

      out.a <> FromAsyncBundle(in.a, params.sync)
      in.d <> ToAsyncBundle(out.d, params)
      property.cover(out.a, "TL_ASYNC_CROSSING_SINK_A", "MemorySystem;;TLAsyncCrossingSink Channel A")
      property.cover(out.d, "TL_ASYNC_CROSSING_SINK_D", "MemorySystem;;TLAsyncCrossingSink Channel D")

      if (bce) {
        in.b <> ToAsyncBundle(out.b, params)
        out.c <> FromAsyncBundle(in.c, params.sync)
        out.e <> FromAsyncBundle(in.e, params.sync)
        property.cover(out.b, "TL_ASYNC_CROSSING_SINK_B", "MemorySystem;;TLAsyncCrossingSinkChannel B")
        property.cover(out.c, "TL_ASYNC_CROSSING_SINK_C", "MemorySystem;;TLAsyncCrossingSink Channel C")
        property.cover(out.e, "TL_ASYNC_CROSSING_SINK_E", "MemorySystem;;TLAsyncCrossingSink Channel E")
      } else {
        in.b.widx := 0.U
        in.c.ridx := 0.U
        in.e.ridx := 0.U
        out.b.ready := true.B
        out.c.valid := false.B
        out.e.valid := false.B
      }
    }
  }
}

object TLAsyncCrossingSource
{
  def apply(): TLAsyncSourceNode = apply(None)
  def apply(sync: Int): TLAsyncSourceNode = apply(Some(sync))
  def apply(sync: Option[Int]): TLAsyncSourceNode =
  {
    val asource = Module(new TLAsyncCrossingSource(sync))
    asource.node
  }
}

object TLAsyncCrossingSink
{
  def apply(params: AsyncQueueParams = AsyncQueueParams()) =
  {
    val asink = Module(new TLAsyncCrossingSink(params))
    asink.node
  }
}

@deprecated("TLAsyncCrossing is fragile. Use TLAsyncCrossingSource and TLAsyncCrossingSink", "rocket-chip 1.2")
class TLAsyncCrossing(params: AsyncQueueParams = AsyncQueueParams()) extends Module
{
  val source = Module(new TLAsyncCrossingSource())
  val sink = Module(new TLAsyncCrossingSink(params))
  val node = NodeHandle(source.node, sink.node)

  sink.node := source.node

  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    val io = IO(new Bundle {
      val in_clock  = Input(Clock())
      val in_reset  = Input(Bool())
      val out_clock = Input(Clock())
      val out_reset = Input(Bool())
    })

    source.module.clock := io.in_clock
    source.module.reset := io.in_reset
    sink.module.clock := io.out_clock
    sink.module.reset := io.out_reset
  }
}

// Synthesizable unit tests

class TLRAMAsyncCrossing(txns: Int, params: AsynchronousCrossing = AsynchronousCrossing()) extends Module {
  val model = Module(new TLRAMModel("AsyncCrossing"))
  val fuzz = Module(new TLFuzzer(txns))
  val island = Module(new CrossingWrapper(params))
  val ram  = island { Module(new TLRAM(AddressSet(0x0, 0x3ff))) }

  island.crossTLIn(ram.node) := TLFragmenter(4, 256) := TLDelayer(0.1) := model.node := fuzz.node

  lazy val module = new Impl
  class Impl extends ModuleImp(this) with UnitTestModule {
    io.finished := fuzz.module.io.finished

    // Shove the RAM into another clock domain
    val clocks = Module(new Pow2ClockDivider(2))
    island.module.clock := clocks.io.clock_out
  }
}

class TLRAMAsyncCrossingTest(txns: Int = 5000, timeout: Int = 500000) extends UnitTest(timeout) {
  val dut_wide   = Module(Module(new TLRAMAsyncCrossing(txns)).module)
  val dut_narrow = Module(Module(new TLRAMAsyncCrossing(txns, AsynchronousCrossing(safe = false, narrow = true))).module)
  io.finished := dut_wide.io.finished && dut_narrow.io.finished
}
