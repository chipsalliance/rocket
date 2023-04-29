package org.chipsalliance.rocket

import chisel3._
import chisel3.util._

package object util {
        implicit def uintToBitPat(x: UInt): BitPat = BitPat(x)
}
