// See LICENSE.SiFive for license details.

package org.chipsalliance.rocket.util

import chisel3._
import chisel3.util._
import chisel3.util.experimental._

import org.chipsalliance.rocket._

object Memory {
  // The safe version will check the entire address
  def findSafe(address: UInt, slaves: Seq[MemSlaveParameters]) = VecInit(slaves.map(_.address.map(_.contains(address)).reduce(_ || _)))

  // Compute the simplest AddressSets that decide a key
  def fastPropertyGroup[K](p: MemSlaveParameters => K, slaves: Seq[MemSlaveParameters]): Seq[(K, Seq[AddressSet])] = {
    val groups = groupByIntoSeq(slaves.map(m => (p(m), m.address)))( _._1).map { case (k, vs) =>
      k -> vs.flatMap(_._2)
    }
    val reductionMask = AddressDecoder(groups.map(_._2))
    groups.map { case (k, seq) => k -> AddressSet.unify(seq.map(_.widen(~reductionMask)).distinct) }
  }
  // Select a property
  def fastProperty[K, D <: Data](address: UInt, p: MemSlaveParameters => K, d: K => D, slaves: Seq[MemSlaveParameters]): D =
    Mux1H(fastPropertyGroup(p, slaves).map { case (v, a) => (a.map(_.contains(address)).reduce(_||_), d(v)) })
}

/** Options for describing the attributes of memory regions */
object RegionType {
  // Define the 'more relaxed than' ordering
  val cases = Seq(CACHED, TRACKED, UNCACHED, IDEMPOTENT, VOLATILE, PUT_EFFECTS, GET_EFFECTS)
  sealed trait T extends Ordered[T] {
    def compare(that: T): Int = cases.indexOf(that) compare cases.indexOf(this)
  }

  case object CACHED      extends T // an intermediate agent may have cached a copy of the region for you
  case object TRACKED     extends T // the region may have been cached by another master, but coherence is being provided
  case object UNCACHED    extends T // the region has not been cached yet, but should be cached when possible
  case object IDEMPOTENT  extends T // gets return most recently put content, but content should not be cached
  case object VOLATILE    extends T // content may change without a put, but puts and gets have no side effects
  case object PUT_EFFECTS extends T // puts produce side effects and so must not be combined/delayed
  case object GET_EFFECTS extends T // gets produce side effects and so must not be issued speculatively
}

// An potentially empty inclusive range of 2-powers [min, max] (in bytes)
case class TransferSizes(min: Int, max: Int)
{
  def this(x: Int) = this(x, x)

  require (min <= max, s"Min transfer $min > max transfer $max")
  require (min >= 0 && max >= 0, s"TransferSizes must be positive, got: ($min, $max)")
  require (max == 0 || isPow2(max), s"TransferSizes must be a power of 2, got: $max")
  require (min == 0 || isPow2(min), s"TransferSizes must be a power of 2, got: $min")
  require (max == 0 || min != 0, s"TransferSize 0 is forbidden unless (0,0), got: ($min, $max)")

  def none = min == 0
  def contains(x: Int) = isPow2(x) && min <= x && x <= max
  def containsLg(x: Int) = contains(1 << x)
  def containsLg(x: UInt) =
    if (none) false.B
    else if (min == max) { log2Ceil(min).U === x }
    else { log2Ceil(min).U <= x && x <= log2Ceil(max).U }

  def contains(x: TransferSizes) = x.none || (min <= x.min && x.max <= max)

  def intersect(x: TransferSizes) =
    if (x.max < min || max < x.min) TransferSizes.none
    else TransferSizes(scala.math.max(min, x.min), scala.math.min(max, x.max))

  // Not a union, because the result may contain sizes contained by neither term
  // NOT TO BE CONFUSED WITH COVERPOINTS
  def mincover(x: TransferSizes) = {
    if (none) {
      x
    } else if (x.none) {
      this
    } else {
      TransferSizes(scala.math.min(min, x.min), scala.math.max(max, x.max))
    }
  }

  override def toString() = "TransferSizes[%d, %d]".format(min, max)
}

object TransferSizes {
  def apply(x: Int) = new TransferSizes(x)
  val none = new TransferSizes(0)

  def mincover(seq: Seq[TransferSizes]) = seq.foldLeft(none)(_ mincover _)
  def intersect(seq: Seq[TransferSizes]) = seq.reduce(_ intersect _)

  implicit def asBool(x: TransferSizes) = !x.none
}

// AddressSets specify the address space managed by the manager
// Base is the base address, and mask are the bits consumed by the manager
// e.g: base=0x200, mask=0xff describes a device managing 0x200-0x2ff
// e.g: base=0x1000, mask=0xf0f decribes a device managing 0x1000-0x100f, 0x1100-0x110f, ...
case class AddressSet(val bitSet: BitSet) extends Ordered[AddressSet]
{
  // TODO: This assumption might not hold true after BitSet intersection or subtraction. It is highly depended on the concrete implementation of BitSet.
  require(bitSet.terms.size == 1, "The wrapped BitSet should only have one BitPat")

  val base = bitSet.terms.head.value
  val mask = bitSet.terms.head.mask

  def contains(x: BigInt) = bitSet matches x.U
  def contains(x: UInt) = bitSet matches x

  // turn x into an address contained in this set
  def legalize(x: UInt): UInt = base.U | (mask.U & x)

  // overlap iff bitwise: both care (~mask0 & ~mask1) => both equal (base0=base1)
  def overlaps(x: AddressSet) = bitSet overlap x.bitSet
  // contains iff bitwise: x.mask => mask && contains(x.base)
  def contains(x: AddressSet) = bitSet cover x.bitSet

  // The number of bytes to which the manager must be aligned
  def alignment = ((mask + 1) & ~mask)
  // Is this a contiguous memory range
  def contiguous = alignment == mask+1

  def finite = mask >= 0
  def max = { require (finite, "Max cannot be calculated on infinite mask"); base | mask }

  // Widen the match function to ignore all bits in imask
  def widen(imask: BigInt) = AddressSet(base & ~imask, mask | imask)

  // Return an AddressSet that only contains the addresses both sets contain
  def intersect(x: AddressSet): Option[AddressSet] = {
    if (!overlaps(x)) {
      None
    } else {
      Some(AddressSet(bitSet intersect x.bitSet))
    }
  }

  def subtract(x: AddressSet): Seq[AddressSet] = {
    (bitSet intersect x.bitSet).terms.toSeq.map(p => AddressSet(BitSet(p)))
  }

  // AddressSets have one natural Ordering (the containment order, if contiguous)
  def compare(x: AddressSet) = {
    val primary   = (this.base - x.base).signum // smallest address first
    val secondary = (x.mask - this.mask).signum // largest mask first
    if (primary != 0) primary else secondary
  }

  // We always want to see things in hex
  override def toString() = {
    if (mask >= 0) {
      "AddressSet(0x%x, 0x%x)".format(base, mask)
    } else {
      "AddressSet(0x%x, ~0x%x)".format(base, ~mask)
    }
  }

  def toRanges = {
    require (finite, "Ranges cannot be calculated on infinite mask")
    val size = alignment
    val fragments = mask & ~(size-1)
    val bits = bitIndexes(fragments)
    (BigInt(0) until (BigInt(1) << bits.size)).map { i =>
      val off = bitIndexes(i).foldLeft(base) { case (a, b) => a.setBit(bits(b)) }
      AddressSet(off, size)
    }
  }
}

object AddressSet
{
  def apply(base: BigInt, mask: BigInt): AddressSet = {
    // Forbid misaligned base address (and empty sets)
    require ((base & mask) == 0, s"Mis-aligned AddressSets are forbidden, got: ${this.toString}")
    require (base >= 0, s"AddressSet negative base is ambiguous: $base") // TL2 address widths are not fixed => negative is ambiguous
    // We do allow negative mask (=> ignore all high bits)

    AddressSet(BitSet(new BitPat(base, mask, base.U.getWidth max mask.U.getWidth)))
  }

  val everything = AddressSet(0, -1)
  def misaligned(base: BigInt, size: BigInt, tail: Seq[AddressSet] = Seq()): Seq[AddressSet] = {
    if (size == 0) tail.reverse else {
      val maxBaseAlignment = base & (-base) // 0 for infinite (LSB)
      val maxSizeAlignment = BigInt(1) << log2Floor(size) // MSB of size
      val step =
        if (maxBaseAlignment == 0 || maxBaseAlignment > maxSizeAlignment)
          maxSizeAlignment else maxBaseAlignment
      misaligned(base+step, size-step, AddressSet(base, step-1) +: tail)
    }
  }

  def unify(seq: Seq[AddressSet], bit: BigInt): Seq[AddressSet] = {
    // Pair terms up by ignoring 'bit'
    seq.distinct.groupBy(x => AddressSet(x.base & ~bit, x.mask)).map { case (key, seq) =>
      if (seq.size == 1) {
        seq.head // singleton -> unaffected
      } else {
        AddressSet(key.base, key.mask | bit) // pair - widen mask by bit
      }
    }.toList
  }

  def unify(seq: Seq[AddressSet]): Seq[AddressSet] = {
    val bits = seq.map(_.base).foldLeft(BigInt(0))(_ | _)
    AddressSet.enumerateBits(bits).foldLeft(seq) { case (acc, bit) => unify(acc, bit) }.sorted
  }

  def enumerateMask(mask: BigInt): Seq[BigInt] = {
    def helper(id: BigInt, tail: Seq[BigInt]): Seq[BigInt] =
      if (id == mask) (id +: tail).reverse else helper(((~mask | id) + 1) & mask, id +: tail)
    helper(0, Nil)
  }

  def enumerateBits(mask: BigInt): Seq[BigInt] = {
    def helper(x: BigInt): Seq[BigInt] = {
      if (x == 0) {
        Nil
      } else {
        val bit = x & (-x)
        bit +: helper(x & ~bit)
      }
    }
    helper(mask)
  }
}

case class MemSlaveParameters(
   val address: Seq[AddressSet],
   val regionType:         RegionType.T  = RegionType.GET_EFFECTS,

   val executable:         Boolean       = false,

   val supportsAcquireT:   TransferSizes = TransferSizes.none,
   val supportsAcquireB:   TransferSizes = TransferSizes.none,
   val supportsArithmetic: TransferSizes = TransferSizes.none,
   val supportsLogical:    TransferSizes = TransferSizes.none,
   val supportsGet:        TransferSizes = TransferSizes.none,
   val supportsPutFull:    TransferSizes = TransferSizes.none,
   val supportsPutPartial: TransferSizes = TransferSizes.none,
   val supportsHint:       TransferSizes = TransferSizes.none,

   val name: String,
 )