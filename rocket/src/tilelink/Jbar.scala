// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket.tilelink

import Chisel._

class TLJbar(policy: TLArbiter.Policy = TLArbiter.roundRobin) extends Module
{
  val node: TLJunctionNode = new TLJunctionNode(
    clientFn  = { seq =>
      Seq.fill(node.dRatio)(seq(0).v1copy(
        minLatency = seq.map(_.minLatency).min,
        clients = (TLXbar.mapInputIds(seq) zip seq) flatMap { case (range, port) =>
          port.clients map { client => client.v1copy(
            sourceId = client.sourceId.shift(range.start)
          )}
        }
      ))
    },
    managerFn = { seq =>
      val fifoIdFactory = TLXbar.relabeler()
      Seq.fill(node.uRatio)(seq(0).v1copy(
        minLatency = seq.map(_.minLatency).min,
        endSinkId = TLXbar.mapOutputIds(seq).map(_.end).max,
        managers = seq.flatMap { port =>
          require (port.beatBytes == seq(0).beatBytes,
            s"Xbar data widths don't match: ${port.managers.map(_.name)} has ${port.beatBytes}B vs ${seq(0).managers.map(_.name)} has ${seq(0).beatBytes}B")
          val fifoIdMapper = fifoIdFactory()
          port.managers map { manager => manager.v1copy(
            fifoId = manager.fifoId.map(fifoIdMapper(_))
          )}
        }
      ))
    }) {
      override def circuitIdentity = uRatio == 1 && dRatio == 1
    }

  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    node.inoutGrouped.foreach { case (in, out) => TLXbar.circuit(policy, in, out) }
  }
}

object TLJbar
{
  def apply(policy: TLArbiter.Policy = TLArbiter.roundRobin) = {
    val jbar = Module(new TLJbar(policy))
    jbar.node
  }
}

// Synthesizable unit tests

class TLJbarTestImp(nClients: Int, nManagers: Int, txns: Int) extends Module {
  val jbar = Module(new TLJbar)

  val fuzzers = Seq.fill(nClients) {
    val fuzzer = Module(new TLFuzzer(txns))
    jbar.node :*= TLXbar() := TLDelayer(0.1) := fuzzer.node
    fuzzer
  }

  for (n <- 0 until nManagers) {
    TLRAM(AddressSet(0x0+0x400*n, 0x3ff)) := TLFragmenter(4, 256) := TLDelayer(0.1) := jbar.node
  }

  lazy val module = new Impl
  class Impl extends ModuleImp(this) with UnitTestModule {
    io.finished := fuzzers.map(_.module.io.finished).reduce(_ && _)
  }
}

class TLJbarTest(nClients: Int, nManagers: Int, txns: Int = 5000, timeout: Int = 500000) extends UnitTest(timeout) {
  val dut = Module(Module(new TLJbarTestImp(nClients, nManagers, txns)).module)
  io.finished := dut.io.finished
}
