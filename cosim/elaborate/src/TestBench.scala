package cosim.elaborate

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util.{Decoupled, HasExtModuleInline}
import freechips.rocketchip.diplomacy.{AddressSet, BundleBridgeSource, InModuleBody, LazyModule, RegionType, SimpleLazyModule, TransferSizes}
import freechips.rocketchip.interrupts.{IntSinkNode, IntSinkPortSimple, IntSourceNode, IntSourcePortSimple}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink.{TLManagerNode, TLSlaveParameters, TLSlavePortParameters}
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.{NMI, PriorityMuxHartIdFromSeq, RocketTile}
import org.chipsalliance.cde.config.{Config, Field}
import freechips.rocketchip.diplomacy._
import org.chipsalliance.tilelink.bundle._

class TestBench(xLen: Int) extends RawModule {
  val clock = Wire(Clock())
  val reset = Wire(Bool())
  val dut = withClockAndReset(clock, reset) {
    Module(
      new DUT(cosimConfig(xLen))
    )
  }
  val verificationModule = Module(new VerificationModule(dut))
  clock := verificationModule.clock
  reset := verificationModule.reset

  dut.nmi := verificationModule.nmi
  dut.intIn := verificationModule.intIn
  dut.resetVector := verificationModule.resetVector
  dut.memory_0_a <> verificationModule.tlportA
  dut.memory_0_b <> verificationModule.tlportB
  dut.memory_0_c <> verificationModule.tlportC
  dut.memory_0_d <> verificationModule.tlportD
  dut.memory_0_e <> verificationModule.tlportE

}


