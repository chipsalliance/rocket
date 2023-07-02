// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket.tilelink

import chisel3._

/** BlockDuringReset ensures that no channel admits to be ready or valid while reset is raised. */
class TLBlockDuringReset(stretchResetCycles: Int = 0)
                         extends Module
{
  val node = TLAdapterNode()
  override def shouldBeInlined = true
  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      out.a :<> BlockDuringReset(in .a, stretchResetCycles)
      in .d :<> BlockDuringReset(out.d, stretchResetCycles)
      if (edgeOut.manager.anySupportAcquireB && edgeOut.client.anySupportProbe) {
        in .b :<> BlockDuringReset(out.b, stretchResetCycles)
        out.c :<> BlockDuringReset(in .c, stretchResetCycles)
        out.e :<> BlockDuringReset(in .e, stretchResetCycles)
      } else {
        in.b.valid  := false.B
        in.c.ready  := true.B
        in.e.ready  := true.B
        out.b.ready := true.B
        out.c.valid := false.B
        out.e.valid := false.B
      }
    }
  }
}

object TLBlockDuringReset {
  def apply(stretchCycles: Int = 0): TLNode = {
    val block_during_reset = Module(new TLBlockDuringReset(stretchCycles))
    block_during_reset.node
  }
}
