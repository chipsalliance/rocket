package cosim.elaborate

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.util.{Decoupled, HasExtModuleInline}
import cosim.elaborate.TapModule
import freechips.rocketchip.tile.NMI
import org.chipsalliance.tilelink.bundle.{TLChannelA, TLChannelB, TLChannelC, TLChannelD, TLChannelE, TileLinkChannelAParameter, TileLinkChannelBParameter, TileLinkChannelCParameter, TileLinkChannelDParameter, TileLinkChannelEParameter}

class VerificationModule extends TapModule {
  val clockRate = 5

  val clock = IO(Output(Clock()))
  val reset = IO(Output(Bool()))
  val resetVector = IO(Output(UInt(32.W)))
  val nmi = IO(Output(new NMI(32)))
  val intIn = IO(Output(Bool()))

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

  nmi.rnmi := true.B
  nmi.rnmi_exception_vector := 0.U
  nmi.rnmi_interrupt_vector := 0.U

  intIn := false.B


  val dpiBasePoke = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiBasePoke"
    val resetVector = IO(Output(UInt(32.W)))
    val clock = IO(Input(Clock()))
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(
         |  input clock,
         |  output [31:0] resetVector
         |);
         |  import "DPI-C" function void dpiBasePoke(output bit[31:0] resetVector);
         |
         |  always @ (posedge clock) $desiredName(resetVector);
         |endmodule
         |""".stripMargin
    )
  })
  dpiBasePoke.clock := clock
  resetVector := dpiBasePoke.resetVector

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
  dontTouch(tlportA.bits.address)
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
