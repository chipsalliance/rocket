package cosim.myelaborate

import chisel3._
import chisel3.util.Decoupled
import freechips.rocketchip.diplomacy.{AddressSet, BundleBridgeSource, InModuleBody, LazyModule, RegionType, SimpleLazyModule, TransferSizes}
import freechips.rocketchip.interrupts.{IntSinkNode, IntSinkPortSimple, IntSourceNode, IntSourcePortSimple}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink.{TLManagerNode, TLSlaveParameters, TLSlavePortParameters}
import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.tile.{NMI, PriorityMuxHartIdFromSeq, RocketTile}
import org.chipsalliance.tilelink.bundle._
import org.chipsalliance.cde.config.{Config, Field}
import freechips.rocketchip.diplomacy._

class DUT(p: Parameters) extends Module {
  implicit val implicitP = p
  val tileParams = p(RocketTileParamsKey)
  val ldut = LazyModule(new SimpleLazyModule with BindingScope {
    implicit val implicitP = p
    val rocketTile = LazyModule(new RocketTile(tileParams, RocketCrossingParams(), PriorityMuxHartIdFromSeq(Seq(tileParams))))
    val masterNode = TLManagerNode(Seq(TLSlavePortParameters.v1(
      Seq(TLSlaveParameters.v1(
        address = List(AddressSet(0x0, 0xffffffffL)),
        regionType = RegionType.UNCACHED,
        executable = true,
        supportsGet = TransferSizes(1, 64),
        supportsAcquireT = TransferSizes(1, 64),
        supportsAcquireB = TransferSizes(1, 64),
        supportsPutPartial = TransferSizes(1, 64),
        supportsPutFull = TransferSizes(1, 64),
        supportsLogical = TransferSizes(1, 64),
        supportsArithmetic = TransferSizes(1, 64),
        fifoId = Some(0))),
      beatBytes = 8,
      endSinkId = 4,
      minLatency = 1
    )))
    masterNode :=* rocketTile.masterNode
    val memory = InModuleBody {
      masterNode.makeIOs()
    }

    val intNode = IntSourceNode(IntSourcePortSimple())
    rocketTile.intInwardNode :=* intNode
    val intIn = InModuleBody {
      intNode.makeIOs()
    }

    val haltNode = IntSinkNode(IntSinkPortSimple())
    haltNode :=* rocketTile.haltNode
    val haltOut = InModuleBody {
      haltNode.makeIOs()
    }

    val ceaseNode = IntSinkNode(IntSinkPortSimple())
    ceaseNode :=* rocketTile.ceaseNode
    val ceaseOut = InModuleBody {
      ceaseNode.makeIOs()
    }

    val wfiNode = IntSinkNode(IntSinkPortSimple())
    wfiNode :=* rocketTile.wfiNode
    val wfiOut = InModuleBody {
      wfiNode.makeIOs()
    }
    val resetVectorNode = BundleBridgeSource(() => UInt(32.W))
    rocketTile.resetVectorNode := resetVectorNode
    val resetVector = InModuleBody {
      resetVectorNode.makeIO()
    }
    val hartidNode = BundleBridgeSource(() => UInt(4.W))
    rocketTile.hartIdNode := hartidNode
    InModuleBody {
      hartidNode.bundle := 0.U
    }
    val nmiNode = BundleBridgeSource(Some(() => new NMI(32)))
    rocketTile.nmiNode := nmiNode
    val nmi = InModuleBody {
      nmiNode.makeIO()
    }
  })

  val tlAParam = TileLinkChannelAParameter(32,2,64,3)
  val tlBParam = TileLinkChannelBParameter(32,2,64,3)
  val tlCParam = TileLinkChannelCParameter(32,2,64,3)
  val tlDParam = TileLinkChannelDParameter(2,3,64,3)
  val tlEParam = TileLinkChannelEParameter(2)

  val memory_0_a = IO(Decoupled(new TLChannelA(tlAParam)))
  val memory_0_b = IO(Flipped(Decoupled(new TLChannelB(tlBParam))))
  val memory_0_c = IO(Decoupled(new TLChannelC(tlCParam)))
  val memory_0_d = IO(Flipped(Decoupled(new TLChannelD(tlDParam))))
  val memory_0_e = IO(Decoupled(new TLChannelE(tlEParam)))

  val nmi = IO(Input(new NMI(32)))
  val intIn = IO(Input(Bool()))
  val resetVector = IO(Input(UInt(32.W)))
  val wfi = IO(Output(Bool()))
  val cease = IO(Output(Bool()))
  val halt = IO(Output(Bool()))


  chisel3.experimental.DataMirror.fullModulePorts(
    // instantiate the LazyModule
    Module(ldut.module)
  ).filterNot(_._2.isInstanceOf[Aggregate]).foreach { case (name, ele) =>
    if (!(name == "clock" || name == "reset")) {
      if( name== "nmi_rnmi" ) ele := nmi.rnmi
      else if (name== "nmi_rnmi_interrupt_vector") ele := nmi.rnmi_interrupt_vector
      else if (name== "nmi_rnmi_exception_vector") ele := nmi.rnmi_exception_vector
      else if (name== "intIn_0_0") ele := intIn
      else if (name== "resetVector") ele := resetVector
      else if (name== "wfiOut_0_0") wfi := ele
      else if (name== "ceaseOut_0_0") cease := ele
      else if (name== "haltOut_0_0") halt := ele

      else if (name== "memory_0_a_ready") ele := memory_0_a.ready
      else if (name== "memory_0_a_valid") memory_0_a.valid := ele
      else if (name== "memory_0_a_bits_opcode") memory_0_a.bits.opcode  := ele
      else if (name== "memory_0_a_bits_param")  memory_0_a.bits.param   := ele
      else if (name== "memory_0_a_bits_size")   memory_0_a.bits.size    := ele
      else if (name== "memory_0_a_bits_source") memory_0_a.bits.source   := ele
      else if (name== "memory_0_a_bits_address") memory_0_a.bits.address := ele
      else if (name== "memory_0_a_bits_mask") memory_0_a.bits.mask    := ele
      else if (name== "memory_0_a_bits_data") memory_0_a.bits.data    := ele
      else if (name== "memory_0_a_bits_corrupt") memory_0_a.bits.corrupt := ele

      else if (name == "memory_0_c_ready")        ele := memory_0_c.ready
      else if (name == "memory_0_c_valid")        memory_0_c.valid := ele
      else if (name == "memory_0_c_bits_opcode")  memory_0_c.bits.opcode := ele
      else if (name == "memory_0_c_bits_param")   memory_0_c.bits.param := ele
      else if (name == "memory_0_c_bits_size")    memory_0_c.bits.size := ele
      else if (name == "memory_0_c_bits_source")  memory_0_c.bits.source := ele
      else if (name == "memory_0_c_bits_address") memory_0_c.bits.address := ele
      else if (name == "memory_0_c_bits_data")    memory_0_c.bits.data := ele
      else if (name == "memory_0_c_bits_corrupt") memory_0_c.bits.corrupt := ele

      else if (name == "memory_0_e_ready") ele := memory_0_e.ready
      else if (name == "memory_0_e_valid")        memory_0_e.valid := ele
      else if (name == "memory_0_e_bits_sink") memory_0_e.bits.sink := ele

      else if (name == "memory_0_b_ready")        memory_0_b.ready := ele
      else if (name == "memory_0_b_valid")        ele := memory_0_b.valid
      else if (name == "memory_0_b_bits_opcode")  ele := memory_0_b.bits.opcode
      else if (name == "memory_0_b_bits_param")   ele := memory_0_b.bits.param
      else if (name == "memory_0_b_bits_size")    ele := memory_0_b.bits.size
      else if (name == "memory_0_b_bits_source")  ele := memory_0_b.bits.source
      else if (name == "memory_0_b_bits_address") ele := memory_0_b.bits.address
      else if (name == "memory_0_b_bits_mask")    ele := memory_0_b.bits.mask
      else if (name == "memory_0_b_bits_data")    ele := memory_0_b.bits.data
      else if (name == "memory_0_b_bits_corrupt") ele := memory_0_b.bits.corrupt

      else if (name == "memory_0_d_ready")        memory_0_d.ready := ele
      else if (name == "memory_0_d_valid")        ele := memory_0_d.valid
      else if (name == "memory_0_d_bits_opcode")  ele := memory_0_d.bits.opcode
      else if (name == "memory_0_d_bits_param")   ele := memory_0_d.bits.param
      else if (name == "memory_0_d_bits_size")    ele := memory_0_d.bits.size
      else if (name == "memory_0_d_bits_source")  ele := memory_0_d.bits.source
      else if (name == "memory_0_d_bits_data")    ele := memory_0_d.bits.data
      else if (name == "memory_0_d_bits_corrupt") ele := memory_0_d.bits.corrupt
      else if (name == "memory_0_d_bits_denied") ele := memory_0_d.bits.denied
      else if (name == "memory_0_d_bits_sink") ele := memory_0_d.bits.sink




      else{
        chisel3.experimental.DataMirror.directionOf(ele) match {
          case ActualDirection.Output =>
            val io = IO(Output(chiselTypeOf(ele))).suggestName(name)
            println(s"output $name")
            io := ele
          case ActualDirection.Input =>
            val io = IO(Input(chiselTypeOf(ele))).suggestName(name)
            println(s"input $name")
            ele := io
        }
      }
    }
  }
}
