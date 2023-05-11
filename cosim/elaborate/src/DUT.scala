package cosim.elaborate

import chisel3._
import chisel3.util.Decoupled
import freechips.rocketchip.diplomacy.{AddressSet, BundleBridgeSource, InModuleBody, LazyModule, RegionType, SimpleLazyModule, TransferSizes}
import freechips.rocketchip.interrupts.{IntSinkNode, IntSinkPortSimple, IntSourceNode, IntSourcePortSimple}
import freechips.rocketchip.subsystem._
import freechips.rocketchip.tilelink.{TLManagerNode, TLSlaveParameters, TLSlavePortParameters}
import org.chipsalliance.cde.config.Parameters
import org.chipsalliance.tilelink.bundle._
import freechips.rocketchip.diplomacy._
import org.chipsalliance.rockettile._


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
      //todo: config with xlen
      beatBytes = 4,
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
  //todo: config databits with xlen
  val tlAParam = TileLinkChannelAParameter(32,2,32,3)
  val tlBParam = TileLinkChannelBParameter(32,2,32,3)
  val tlCParam = TileLinkChannelCParameter(32,2,32,3)
  val tlDParam = TileLinkChannelDParameter(2,3,32,3)
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
      name match {
        case "clock" => {}
        case "reset" => {}
        case "nmi_rnmi" => ele := nmi.rnmi
        case "nmi_rnmi_interrupt_vector" => ele := nmi.rnmi_interrupt_vector
        case "nmi_rnmi_exception_vector" => ele := nmi.rnmi_exception_vector
        case "intIn_0_0" => ele := intIn
        case "resetVector" => ele := resetVector
        case "wfiOut_0_0" => wfi := ele
        case "ceaseOut_0_0" => cease := ele
        case "haltOut_0_0" => halt := ele

        case "memory_0_a_ready" => ele := memory_0_a.ready
        case "memory_0_a_valid" => memory_0_a.valid := ele
        case "memory_0_a_bits_opcode" => memory_0_a.bits.opcode := ele
        case "memory_0_a_bits_param" => memory_0_a.bits.param := ele
        case "memory_0_a_bits_size" => memory_0_a.bits.size := ele
        case "memory_0_a_bits_source" => memory_0_a.bits.source := ele
        case "memory_0_a_bits_address" => memory_0_a.bits.address := ele
        case "memory_0_a_bits_mask" => memory_0_a.bits.mask := ele
        case "memory_0_a_bits_data" => memory_0_a.bits.data := ele
        case "memory_0_a_bits_corrupt" => memory_0_a.bits.corrupt := ele

        case "memory_0_c_ready" => ele := memory_0_c.ready
        case "memory_0_c_valid" => memory_0_c.valid := ele
        case "memory_0_c_bits_opcode" => memory_0_c.bits.opcode := ele
        case "memory_0_c_bits_param" => memory_0_c.bits.param := ele
        case "memory_0_c_bits_size" => memory_0_c.bits.size := ele
        case "memory_0_c_bits_source" => memory_0_c.bits.source := ele
        case "memory_0_c_bits_address" => memory_0_c.bits.address := ele
        case "memory_0_c_bits_data" => memory_0_c.bits.data := ele
        case "memory_0_c_bits_corrupt" => memory_0_c.bits.corrupt := ele

        case "memory_0_e_ready" => ele := memory_0_e.ready
        case "memory_0_e_valid" => memory_0_e.valid := ele
        case "memory_0_e_bits_sink" => memory_0_e.bits.sink := ele

        case "memory_0_b_ready" => memory_0_b.ready := ele
        case "memory_0_b_valid" => ele := memory_0_b.valid
        case "memory_0_b_bits_opcode" => ele := memory_0_b.bits.opcode
        case "memory_0_b_bits_param" => ele := memory_0_b.bits.param
        case "memory_0_b_bits_size" => ele := memory_0_b.bits.size
        case "memory_0_b_bits_source" => ele := memory_0_b.bits.source
        case "memory_0_b_bits_address" => ele := memory_0_b.bits.address
        case "memory_0_b_bits_mask" => ele := memory_0_b.bits.mask
        case "memory_0_b_bits_data" => ele := memory_0_b.bits.data
        case "memory_0_b_bits_corrupt" => ele := memory_0_b.bits.corrupt

        case "memory_0_d_ready" => memory_0_d.ready := ele
        case "memory_0_d_valid" => ele := memory_0_d.valid
        case "memory_0_d_bits_opcode" => ele := memory_0_d.bits.opcode
        case "memory_0_d_bits_param" => ele := memory_0_d.bits.param
        case "memory_0_d_bits_size" => ele := memory_0_d.bits.size
        case "memory_0_d_bits_source" => ele := memory_0_d.bits.source
        case "memory_0_d_bits_data" => ele := memory_0_d.bits.data
        case "memory_0_d_bits_corrupt" => ele := memory_0_d.bits.corrupt
        case "memory_0_d_bits_denied" => ele := memory_0_d.bits.denied
        case "memory_0_d_bits_sink" => ele := memory_0_d.bits.sink

        case _ => sys.error(s"can't find $name in DUT")
      }
  }
}
