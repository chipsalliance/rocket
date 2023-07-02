// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket.tilelink

import Chisel._

class TLBufferNode (
  a: BufferParams,
  b: BufferParams,
  c: BufferParams,
  d: BufferParams,
  e: BufferParams)(implicit valName: ValName) extends TLAdapterNode(
    clientFn  = { p => p.v1copy(minLatency = p.minLatency + b.latency + c.latency) },
    managerFn = { p => p.v1copy(minLatency = p.minLatency + a.latency + d.latency) }
) {
  override lazy val nodedebugstring = s"a:${a.toString}, b:${b.toString}, c:${c.toString}, d:${d.toString}, e:${e.toString}"
  override def circuitIdentity = List(a,b,c,d,e).forall(_ == BufferParams.none)
}

class TLBuffer(
  a: BufferParams,
  b: BufferParams,
  c: BufferParams,
  d: BufferParams,
  e: BufferParams) extends Module
{
  def this(ace: BufferParams, bd: BufferParams) = this(ace, bd, ace, bd, ace)
  def this(abcde: BufferParams) = this(abcde, abcde)
  def this() = this(BufferParams.default)

  val node = new TLBufferNode(a, b, c, d, e)

  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      out.a <> a(in .a)
      in .d <> d(out.d)

      if (edgeOut.manager.anySupportAcquireB && edgeOut.client.anySupportProbe) {
        in .b <> b(out.b)
        out.c <> c(in .c)
        out.e <> e(in .e)
      } else {
        in.b.valid := Bool(false)
        in.c.ready := Bool(true)
        in.e.ready := Bool(true)
        out.b.ready := Bool(true)
        out.c.valid := Bool(false)
        out.e.valid := Bool(false)
      }
    }
  }
}

object TLBuffer
{
  def apply()                                   : TLNode = apply(BufferParams.default)
  def apply(abcde: BufferParams)                : TLNode = apply(abcde, abcde)
  def apply(ace: BufferParams, bd: BufferParams): TLNode = apply(ace, bd, ace, bd, ace)
  def apply(
      a: BufferParams,
      b: BufferParams,
      c: BufferParams,
      d: BufferParams,
      e: BufferParams): TLNode =
  {
    val buffer = Module(new TLBuffer(a, b, c, d, e))
    buffer.node
  }

  def chain(depth: Int, name: Option[String] = None): Seq[TLNode] = {
    val buffers = Seq.fill(depth) { Module(new TLBuffer()) }
    name.foreach { n => buffers.zipWithIndex.foreach { case (b, i) => b.suggestName(s"${n}_${i}") } }
    buffers.map(_.node)
  }

  def chainNode(depth: Int, name: Option[String] = None): TLNode = {
    chain(depth, name)
      .reduceLeftOption(_ :*=* _)
      .getOrElse(TLNameNode("no_buffer"))
  }
}

class TLBufferNodeAndNotCancel (
  a: BufferParams,
  b: BufferParams,
  c: BufferParams,
  d: BufferParams,
  e: BufferParams)(implicit valName: ValName) extends TLAdapterNodeAndNotCancel(
    clientFn  = { p => p.v1copy(minLatency = p.minLatency + b.latency + c.latency) },
    managerFn = { p => p.v1copy(minLatency = p.minLatency + a.latency + d.latency) }
) {
  override lazy val nodedebugstring = s"a:${a.toString}, b:${b.toString}, c:${c.toString}, d:${d.toString}, e:${e.toString}"
  override def circuitIdentity = List(a,b,c,d,e).forall(_ == BufferParams.none)
}

class TLBufferAndNotCancel(
  a: BufferParams,
  b: BufferParams,
  c: BufferParams,
  d: BufferParams,
  e: BufferParams) extends Module
{
  def this(ace: BufferParams, bd: BufferParams) = this(ace, bd, ace, bd, ace)
  def this(abcde: BufferParams) = this(abcde, abcde)
  def this() = this(BufferParams.default)

  val node = new TLBufferNodeAndNotCancel(a, b, c, d, e)

  lazy val module = new Impl
  class Impl extends ModuleImp(this) {
    (node.in zip node.out) foreach { case ((in, edgeIn), (out, edgeOut)) =>
      out.a <> a(in.a.asDecoupled)
      in .d <> d(out.d)

      if (edgeOut.manager.anySupportAcquireB && edgeOut.client.anySupportProbe) {
        in .b <> b(out.b)
        out.c <> c(in .c)
        out.e <> e(in .e)
      } else {
        in.b.valid := Bool(false)
        in.c.ready := Bool(true)
        in.e.ready := Bool(true)
        out.b.ready := Bool(true)
        out.c.valid := Bool(false)
        out.e.valid := Bool(false)
      }
    }
  }
}

object TLBufferAndNotCancel
{
  def apply()                                   : TLMixedNodeCancel = apply(BufferParams.default)
  def apply(abcde: BufferParams)                : TLMixedNodeCancel = apply(abcde, abcde)
  def apply(ace: BufferParams, bd: BufferParams): TLMixedNodeCancel = apply(ace, bd, ace, bd, ace)
  def apply(
      a: BufferParams,
      b: BufferParams,
      c: BufferParams,
      d: BufferParams,
      e: BufferParams): TLMixedNodeCancel =
  {
    val buffer = Module(new TLBufferAndNotCancel(a, b, c, d, e))
    buffer.node
  }
}
