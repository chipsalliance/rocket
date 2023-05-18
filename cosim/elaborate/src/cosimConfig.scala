package cosim.elaborate

import freechips.rocketchip.devices.debug.DebugModuleKey
import freechips.rocketchip.diplomacy.MonitorsEnabled
import freechips.rocketchip.subsystem.{CacheBlockBytes, SystemBusKey, SystemBusParams}
import org.chipsalliance.cde.config.{Config, Field}
import org.chipsalliance.rockettile._
import org.chipsalliance.rocket._

object RocketTileParamsKey extends Field[RocketTileParams]

case class cosimConfig(xLength: Int) extends Config((site, here, up) => {
  case MonitorsEnabled => false
  case XLen => xLength
  case MaxHartIdBits => 4
  case PgLevels => if (site(XLen) == 64) 3 else 2
  case RocketTileParamsKey => RocketTileParams(
    core = RocketCoreParams(mulDiv = Some(MulDivParams(
      mulUnroll = xLength,
      mulEarlyOut = true,
      divEarlyOut = true)),
      fpu = Some(FPUParams(fLen = xLength))),
    dcache = Some(DCacheParams(
      rowBits = site(SystemBusKey).beatBits,
      nMSHRs = 0,
      blockBytes = site(CacheBlockBytes))),
    icache = Some(ICacheParams(
      rowBits = site(SystemBusKey).beatBits,
      blockBytes = site(CacheBlockBytes))))
  case SystemBusKey => SystemBusParams(
    beatBytes = site(XLen) / 8,
    blockBytes = site(CacheBlockBytes))
  case DebugModuleKey => None
})
