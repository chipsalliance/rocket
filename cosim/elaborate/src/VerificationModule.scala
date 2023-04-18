package cosim.elaborate

import chisel3._
import chisel3.experimental.ExtModule
import chisel3.experimental.hierarchy.{Definition, instantiable, public}
import chisel3.util.{Decoupled, HasExtModuleInline}
import cosim.elaborate.TapModule
import freechips.rocketchip.tile.NMI
import org.chipsalliance.tilelink.bundle.{TLChannelA, TLChannelB, TLChannelC, TLChannelD, TLChannelE, TileLinkChannelAParameter, TileLinkChannelBParameter, TileLinkChannelCParameter, TileLinkChannelDParameter, TileLinkChannelEParameter}

class VerificationModule(dut:DUT) extends TapModule {
  val clockRate = 5
  val latPeekCommit = 2
  val latPokeTL = 1
  val latPeekTL = 2

  val clock = IO(Output(Clock()))
  val reset = IO(Output(Bool()))
  val resetVector = IO(Output(UInt(32.W)))
  val nmi = IO(Output(new NMI(32)))
  val intIn = IO(Output(Bool()))

  val tlAParam = TileLinkChannelAParameter(32, 2, 64, 3)
  val tlBParam = TileLinkChannelBParameter(32, 2, 64, 3)
  val tlCParam = TileLinkChannelCParameter(32, 2, 64, 3)
  val tlDParam = TileLinkChannelDParameter(32, 2, 64, 3)
  val tlEParam = TileLinkChannelEParameter(2)

  val tlbundle_a = Flipped(Decoupled(new TLChannelA(tlAParam)))

  val tlportA = IO(Flipped(Decoupled(new TLChannelA(tlAParam))))
  val tlportB = IO(Decoupled(new TLChannelB(tlBParam)))
  val tlportC = IO(Flipped(Decoupled(new TLChannelC(tlCParam))))
  val tlportD = IO(Decoupled(new TLChannelD(tlDParam)))
  val tlportE = IO(Flipped(Decoupled(new TLChannelE(tlEParam))))

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
         |  import "DPI-C" function void $desiredName(output bit[31:0] resetVector);
         |
         |  always @ (posedge clock) $desiredName(resetVector);
         |endmodule
         |""".stripMargin
    )
  })
  dpiBasePoke.clock := clock
  resetVector := dpiBasePoke.resetVector

  val dpiRefillQueue = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiRefillQueue"
    val clock = IO(Input(Clock()))
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(
         |  input clock
         |);
         |  import "DPI-C" function void $desiredName();
         |
         |  initial $desiredName();
         |
         |  always @ (negedge clock) $desiredName();
         |endmodule
         |""".stripMargin
    )
  })
  dpiRefillQueue.clock := clock;

  val dpiCommitPeek = Module(new ExtModule with HasExtModuleInline {
    override val desiredName = "dpiCommitPeek"
    val clock       = IO(Input(Clock()))
    val rf_wen      = IO(Input(Bool()))
    val rf_waddr    = IO(Input(UInt(32.W)))
    val rf_wdata    = IO(Input(UInt(64.W)))
    val wb_reg_pc   = IO(Input(UInt(32.W)))
    val wb_reg_inst = IO(Input(UInt(32.W)))
    val wb_valid    = IO(Input(Bool()))
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(
         |  input clock,
         |  input rf_wen,
         |  input wb_valid,
         |  input [31:0] rf_waddr,
         |  input [63:0] rf_wdata,
         |  input [31:0] wb_reg_pc,
         |  input [31:0] wb_reg_inst
         |);
         |
         |  bit[31:0] rf_wdata_high, rf_wdata_low;
         |  assign rf_wdata_high = rf_wdata[63:32];
         |  assign rf_wdata_low  = rf_wdata[31:0];
         |
         |  import "DPI-C" function void $desiredName(
         |  input bit rf_wen,
         |  input bit wb_valid,
         |  input bit[31:0] rf_waddr,
         |  input bit[31:0] rf_wdata_high,
         |  input bit[31:0] rf_wdata_low,
         |  input bit[31:0] wb_reg_pc,
         |  input bit[31:0] wb_reg_inst
         |  );
         |  always @ (posedge clock) #($latPeekCommit) $desiredName(
         |  rf_wen,
         |  wb_valid,
         |  rf_waddr,
         |  rf_wdata_high,
         |  rf_wdata_low,
         |  wb_reg_pc,
         |  wb_reg_inst
         |  );
         |
         |endmodule
         |""".stripMargin
    )
  })
  //todo:use rf_ext_w0_en
  dpiCommitPeek.rf_wen      := tap(dut.ldut.rocketTile.module.core.rocketImpl.rf_wen)
  dpiCommitPeek.rf_waddr    := tap(dut.ldut.rocketTile.module.core.rocketImpl.rf_waddr)
  dpiCommitPeek.rf_wdata    := tap(dut.ldut.rocketTile.module.core.rocketImpl.rf_wdata)
  dpiCommitPeek.wb_reg_pc   := tap(dut.ldut.rocketTile.module.core.rocketImpl.wb_reg_pc)
  dpiCommitPeek.wb_reg_inst := tap(dut.ldut.rocketTile.module.core.rocketImpl.wb_reg_inst)
  dpiCommitPeek.wb_valid    := tap(dut.ldut.rocketTile.module.core.rocketImpl.wb_valid)
  dpiCommitPeek.clock       := clock


  @instantiable
  class PeekTL(param_a: TileLinkChannelAParameter, param_c: TileLinkChannelCParameter) extends ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPeekTL"
    @public val clock = IO(Input(Clock()))
    @public val aBits: TLChannelA = IO(Input(new TLChannelA(param_a)))
    @public val cBits: TLChannelC = IO(Input(new TLChannelC(param_c)))
    @public val aValid: Bool = IO(Input(Bool()))
    @public val cValid: Bool = IO(Input(Bool()))
    @public val dReady: Bool = IO(Input(Bool()))
    @public val miss: Bool = IO(Input(Bool()))
    @public val pc :UInt = IO(Input(UInt(32.W)))
    setInline(
      "dpiPeekTL.sv",
      s"""module $desiredName(
         |  input clock,
         |  input bit[31:0] pc,
         |  input bit[${aBits.opcode.getWidth - 1}:0]  aBits_opcode,
         |  input bit[${aBits.param.getWidth - 1}:0]   aBits_param,
         |  input bit[${aBits.size.getWidth - 1}:0]    aBits_size,
         |  input bit[${aBits.source.getWidth - 1}:0]  aBits_source,
         |  input bit[${aBits.address.getWidth - 1}:0] aBits_address,
         |  input bit[${aBits.mask.getWidth - 1}:0]    aBits_mask,
         |  input bit[${aBits.data.getWidth - 1}:0]    aBits_data,
         |  input bit[${cBits.opcode.getWidth - 1}:0]  cBits_opcode,
         |  input bit[${cBits.param.getWidth - 1}:0]   cBits_param,
         |  input bit[${cBits.size.getWidth - 1}:0]    cBits_size,
         |  input bit[${cBits.source.getWidth - 1}:0]  cBits_source,
         |  input bit[${cBits.address.getWidth - 1}:0] cBits_address,
         |  input bit[${cBits.data.getWidth - 1}:0]    cBits_data,
         |  input bit aBits_corrupt,
         |  input bit cBits_corrupt,
         |  input bit aValid,
         |  input bit cValid,
         |  input bit dReady,
         |  input bit miss
         |);
         |import "DPI-C" function void dpiPeekChannelA(
         |  input bit[31:0] pc,
         |  input bit[${aBits.opcode.getWidth - 1}:0]  a_opcode,
         |  input bit[${aBits.param.getWidth - 1}:0]   a_param,
         |  input bit[${aBits.size.getWidth - 1}:0]    a_size,
         |  input bit[${aBits.source.getWidth - 1}:0]  a_source,
         |  input bit[${aBits.address.getWidth - 1}:0] a_address,
         |  input bit[${aBits.mask.getWidth - 1}:0]    a_mask,
         |  input bit[${aBits.data.getWidth - 1}:0]    a_data,
         |  input bit a_corrupt,
         |  input bit a_valid,
         |  input bit d_ready,
         |  input miss
         |);
         |always @ (posedge clock) #($latPeekTL) dpiPeekChannelA(
         |  pc,
         |  aBits_opcode,
         |  aBits_param,
         |  aBits_size,
         |  aBits_source,
         |  aBits_address,
         |  aBits_mask,
         |  aBits_data,
         |  aBits_corrupt,
         |  aValid,
         |  dReady,
         |  miss
         |);
         |endmodule
         |""".stripMargin
    )
  }
  @instantiable
  class PokeTL(param_d: TileLinkChannelDParameter) extends ExtModule with HasExtModuleInline {
    override val desiredName = "dpiPokeTL"
    @public val clock = IO(Input(Clock()))
    @public val dBits: TLChannelD = IO(Output(new TLChannelD(param_d)))
    @public val dValid = IO(Output(Bool()))
    @public val dReady = IO(Input(Bool()))
    setInline(
      s"$desiredName.sv",
      s"""module $desiredName(
         |  input clock,
         |  output bit[${dBits.opcode.getWidth - 1}:0] dBits_opcode,
         |  output bit[${dBits.param.getWidth - 1}:0] dBits_param,
         |  output bit[${dBits.size.getWidth - 1}:0] dBits_size,
         |  output bit[${dBits.source.getWidth - 1}:0] dBits_source,
         |  output bit[${dBits.sink.getWidth - 1}:0] dBits_sink,
         |  output bit[${dBits.denied.getWidth - 1}:0] dBits_denied,
         |  output bit[${dBits.data.getWidth - 1}:0] dBits_data,
         |  output bit dBits_corrupt,
         |  output bit dValid,
         |  input bit dReady
         |);
         |  bit[31:0] data_high,data_low;
         |  assign dBits_data[63:32] = data_high;
         |  assign dBits_data[31:0] = data_low;
         |
         |import "DPI-C" function void $desiredName(
         |  output bit[31:0] data_high,
         |  output bit[31:0] data_low,
         |  output bit[${dBits.opcode.getWidth - 1}:0] d_opcode,
         |  output bit[${dBits.param.getWidth - 1}:0] d_param,
         |  output bit[${dBits.size.getWidth - 1}:0] d_size,
         |  output bit[${dBits.source.getWidth - 1}:0] d_source,
         |  output bit[${dBits.sink.getWidth - 1}:0] d_sink,
         |  output bit[${dBits.denied.getWidth - 1}:0] d_denied,
         |  output bit d_corrupt,
         |  output bit d_valid,
         |  input bit d_ready
         |);
         |always @ (posedge clock) #($latPokeTL) $desiredName(
         |  data_high,
         |  data_low,
         |  dBits_opcode,
         |  dBits_param,
         |  dBits_size,
         |  dBits_source,
         |  dBits_sink,
         |  dBits_denied,
         |  dBits_corrupt,
         |  dValid,
         |  dReady
         |);
         |endmodule
         |""".stripMargin
    )
  }

  val dpiPeekTL = Module(new PeekTL(tlAParam,tlCParam))
  dpiPeekTL.clock := clock
  dpiPeekTL.aBits := tlportA.bits
  dpiPeekTL.cBits := tlportC.bits
  dpiPeekTL.aValid := tlportA.valid
  dpiPeekTL.cValid := tlportC.valid
  dpiPeekTL.dReady := tlportD.ready
  dpiPeekTL.pc := tap(dut.ldut.rocketTile.module.core.rocketImpl.ex_reg_pc)
  dpiPeekTL.miss := tap(dut.ldut.rocketTile.frontend.icache.module.s2_miss)

  val dpiPokeTL = Module(new PokeTL(tlDParam))
  dpiPokeTL.clock := clock
  tlportD.bits := dpiPokeTL.dBits
  tlportD.valid := dpiPokeTL.dValid
  dpiPokeTL.dReady := tlportD.ready




  tlportA.ready := true.B
  tlportC.ready := true.B
  tlportE.ready := true.B

  tlportB.valid := false.B
  tlportB.bits.opcode := 0.U
  tlportB.bits.param := 0.U
  tlportB.bits.size := 0.U
  tlportB.bits.source := 0.U
  tlportB.bits.address := 0.U
  tlportB.bits.mask := 0.U
  tlportB.bits.data := 0.U
  tlportB.bits.corrupt := 0.U

  done()

}
