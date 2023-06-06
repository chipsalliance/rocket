// See LICENSE.SiFive for license details.
// See LICENSE.Berkeley for license details.

package org.chipsalliance.rocket

import chisel3._
import chisel3.util._

// wait for CSR
object PRV
{
  val SZ = 2
  val U = 0
  val S = 1
  val H = 2
  val M = 3
}