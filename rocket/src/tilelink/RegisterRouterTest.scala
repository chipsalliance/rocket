// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket.tilelink

import Chisel._

class TLRRTest0(address: BigInt)
  extends RRTest0(address)
  with HasTLControlRegMap

class TLRRTest1(address: BigInt)
  extends RRTest1(address, concurrency = 6, undefZero = false)
  with HasTLControlRegMap

class FuzzRRTest0(txns: Int) extends Module {
  val fuzz = Module(new TLFuzzer(txns))
  val rrtr = Module(new TLRRTest0(0x400))

  rrtr.node := TLFragmenter(4, 32) := TLDelayer(0.1) := fuzz.node

  lazy val module = new Impl
  class Impl extends ModuleImp(this) with UnitTestModule {
    io.finished := fuzz.module.io.finished
  }
}

class TLRR0Test(txns: Int = 5000, timeout: Int = 500000) extends UnitTest(timeout) {
  val dut = Module(Module(new FuzzRRTest0(txns)).module)
  io.finished := dut.io.finished
}

class FuzzRRTest1(txns: Int) extends Module {
  val fuzz = Module(new TLFuzzer(txns))
  val rrtr = Module(new TLRRTest1(0x400))

  rrtr.node := TLFragmenter(4, 32) := TLDelayer(0.1) := fuzz.node

  lazy val module = new Impl
  class Impl extends ModuleImp(this) with UnitTestModule {
    io.finished := fuzz.module.io.finished
  }
}

class TLRR1Test(txns: Int = 5000, timeout: Int = 500000) extends UnitTest(timeout) {
  val dut = Module(Module(new FuzzRRTest1(txns)).module)
  io.finished := dut.io.finished
}

