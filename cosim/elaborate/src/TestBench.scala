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

class TestBench extends RawModule {
  val clock = Wire(Clock())
  val reset = Wire(Bool())
  val verificationModule = Module(new VerificationModule)
  val dut = withClockAndReset(clock, reset) {
    Module(
      new DUT(new cosimConfig)
    )
  }
  clock := verificationModule.clock
  reset := verificationModule.reset

  dut.nmi := verificationModule.nmi
  dut.intIn <> verificationModule.intIn
  dut.resetVector <> verificationModule.resetVector
  dut.memory_0_a <> verificationModule.tlportA
  dut.memory_0_b <> verificationModule.tlportB
  dut.memory_0_c <> verificationModule.tlportC
  dut.memory_0_d <> verificationModule.tlportD
  dut.memory_0_e <> verificationModule.tlportE

}

class VerificationModule extends TapModule {
  val clockRate = 5

  val clock = IO(Output(Clock()))
  val reset = IO(Output(Bool()))

  val verbatim = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "Verbatim"
    val clock = IO(Output(Clock()))
    val reset = IO(Output(Bool()))
    setInline(
      "verbatim.sv",
      s"""module Verbatim(
         |  output clock,
         |  output reset
         |);
         |  reg _clock = 1'b0;
         |  always #($clockRate) _clock = ~_clock;
         |  reg _reset = 1'b1;
         |  initial #(${2 * clockRate + 1}) _reset = 0;
         |
         |  assign clock = _clock;
         |  assign reset = _reset;
         |
         |  import "DPI-C" function void dpiInitCosim();
         |  initial dpiInitCosim();
         |
         |  import "DPI-C" function void dpiTimeoutCheck();
         |  always #(${2 * clockRate + 1}) dpiTimeoutCheck();
         |
         |  export "DPI-C" function dpiDumpWave;
         |  function dpiDumpWave(input string file);
         |   $$dumpfile(file);
         |   $$dumpvars(0);
         |  endfunction;
         |
         |  export "DPI-C" function dpiFinish;
         |  function dpiFinish();
         |   $$finish;
         |  endfunction;
         |
         |  export "DPI-C" function dpiError;
         |  function dpiError(input string what);
         |   $$error(what);
         |  endfunction;
         |
         |endmodule
         |""".stripMargin
    )
  })
  clock := verbatim.clock
  reset := verbatim.reset
  
  val nmi = IO(Output(new NMI(32)))
  nmi.rnmi := true.B
  nmi.rnmi_exception_vector := 0.U
  nmi.rnmi_interrupt_vector := 0.U

  val intIn = IO(Output(Bool()))
  val resetVector = IO(Output(UInt(32.W)))
  intIn := false.B
  resetVector := 0.U


  val tlAParam = TileLinkChannelAParameter(32, 2, 64, 3)
  val tlBParam = TileLinkChannelBParameter(32, 2, 64, 3)
  val tlCParam = TileLinkChannelCParameter(32, 2, 64, 3)
  val tlDParam = TileLinkChannelDParameter(32, 2, 64, 2)
  val tlEParam = TileLinkChannelEParameter(2)

  val tlportA = IO(Flipped(Decoupled(new TLChannelA(tlAParam))))
  val tlportB = IO(Decoupled(new TLChannelB(tlBParam)))
  val tlportC = IO(Flipped(Decoupled(new TLChannelC(tlCParam))))
  val tlportD = IO(Decoupled(new TLChannelD(tlDParam)))
  val tlportE = IO(Flipped(Decoupled(new TLChannelE(tlEParam))))

  tlportA.ready := false.B
  tlportC.ready := false.B
  tlportE.ready := false.B

  tlportB.valid := false.B
  tlportB.bits.opcode := 0.U
  tlportB.bits.param := 0.U
  tlportB.bits.size := 0.U
  tlportB.bits.source := 0.U
  tlportB.bits.address := 0.U
  tlportB.bits.mask := 0.U
  tlportB.bits.data := 0.U
  tlportB.bits.corrupt := 0.U

  tlportD.valid := false.B
  tlportD.bits.opcode := 0.U
  tlportD.bits.param := 0.U
  tlportD.bits.size := 0.U
  tlportD.bits.source := 0.U
  tlportD.bits.data := 0.U
  tlportD.bits.corrupt := 0.U
  tlportD.bits.denied := 0.U
  tlportD.bits.sink := 0.U
}


