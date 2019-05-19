package beagle

import chisel3._

import freechips.rocketchip.config.{Field, Parameters, Config}
import freechips.rocketchip.subsystem.{ExtMem, RocketTilesKey, BankedL2Key, WithJtagDTM, WithRationalRocketTiles, WithNMemoryChannels, WithNBanks, SystemBusKey, MemoryBusKey, ControlBusKey, CacheBlockBytes}
import freechips.rocketchip.diplomacy.{LazyModule, ValName, AddressSet}
import freechips.rocketchip.tile.{LazyRoCC, BuildRoCC, OpcodeSet, TileKey, RocketTileParams}
import freechips.rocketchip.rocket.{RocketCoreParams, BTBParams, DCacheParams, ICacheParams, MulDivParams}

import sifive.blocks.devices.gpio._
import sifive.blocks.devices.spi._
import sifive.blocks.devices.uart._
import sifive.blocks.devices.i2c._
import sifive.blocks.devices.jtag._

import hbwif.tilelink._
import hbwif._

import hwacha.{Hwacha}

import boom.system.{BoomTilesKey}

import systolic.{SystolicArray, SystolicArrayKey, SystolicArrayConfig, Dataflow}

// --------------
// Special MIXINS
// --------------

class WithBeagleChanges extends Config((site, here, up) => {
  case SystemBusKey => up(SystemBusKey).copy(beatBytes = 16)
  case MemoryBusKey => up(MemoryBusKey).copy(beatBytes = 8)
  case ControlBusKey => {
    val cBus = up(ControlBusKey)
    cBus.copy(errorDevice = cBus.errorDevice.map(e => e.copy(maxTransfer=64)))
  }
  case BeaglePipelineResetDepth => 5
  case HbwifPipelineResetDepth => 5
  case CacheBlockStriping => 2
  case BeagleSinkIds => 32
  case LbwifBitWidth => 4
})

/**
 * Mixin for adding external I/O
 */
class WithBeagleSiFiveBlocks extends Config((site, here, up) => {
  case PeripheryGPIOKey => Seq(GPIOParams(address = 0x9000, width = 16))
  case PeripherySPIKey => Seq(SPIParams(rAddress = 0xa000))
  case PeripheryI2CKey => Seq(I2CParams(address = 0xb000))
  case PeripheryUARTKey => Seq(UARTParams(address = 0xc000))
  case PeripheryBeagleKey => BeagleParams(scrAddress = 0x110000)
})

class WithHierTiles extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) map { r =>
    r.copy(boundaryBuffers = true) }
  case BoomTilesKey => up(BoomTilesKey, site) map { r =>
    r.copy(boundaryBuffers = true) }
})

/**
 * Mixin to change generic serdes parameters
 *
 * banks should be 1
 * lanes should be 2 or 4
 * clientPort = false
 * managerTL(UH\C) = false
 */
class WithBeagleSerdesChanges extends Config((site, here, up) => {
  case HbwifNumLanes => 2
  case HbwifTLKey => up(HbwifTLKey, site).copy(
    numBanks = 2,
    clientPort = false,
    managerTLUH = false,
    managerTLC = false)
})

class WithSystolicParams extends Config((site, here, up) => {
  case SystolicArrayKey =>
    SystolicArrayConfig(
      tileRows = 1,
      tileColumns = 8,
      meshRows = 8,
      meshColumns = 1,
      ld_str_queue_length = 10,
      ex_queue_length = 10,
      sp_banks = 7,
      sp_bank_entries = 16, // has to be a multiply of meshRows*tileRows
      sp_width = 8 * 16, // has to be meshRows*tileRows*dataWidth // TODO should this be changeable?
      shifter_banks = 1, // TODO add separate parameters for left and up shifter banks
      depq_len = 256,
      acc_rows = 64,
      dataflow = Dataflow.BOTH)
})

// ------ NOTE: This can be a bit confusing with the MultiRoCC stuff

/**
 * Map from a hartId to a particular RoCC accelerator
 */
case object MultiRoCCKey extends Field[Map[Int, Seq[Parameters => LazyRoCC]]](Map.empty[Int, Seq[Parameters => LazyRoCC]])

/**
 * Mixin to add Hwachas to N cores
 *
 * For ex:
 *   Core 0, 1, 2, 3 have been defined earlier
 *     with hardIds of 0, 1, 2, 3 respectively
 *   And you call WithMultiRoCCHwacha(2)
 *   Then Core 2 and 3 will get a Hwacha
 *
 * @param n amount of Hwachas to add
 */
class WithMultiRoCCHwacha(n: Int = 1) extends Config((site, here, up) => {
  case MultiRoCCKey => {
    val m = (up(RocketTilesKey, site).length + up(BoomTilesKey, site).length) - 1
    up(MultiRoCCKey, site) ++ Seq.tabulate(n)(i =>
      ((m - i) -> Seq((p: Parameters) => {
        val hwacha = LazyModule.apply(new Hwacha()(p))
        hwacha})))}
})

/**
 * Mixin to enable different RoCCs based on the hardId
 */
class WithMultiRoCC extends Config((site, here, up) => {
  case BuildRoCC => site(MultiRoCCKey).getOrElse(site(TileKey).hartId, Nil)
})

/**
 * Mixin to add a new Rocket core with hartId of X (last hartId in the system) with a Systolic Array
 */
class WithSystolicAcceleratorMiniCore extends Config((site, here, up) => {
  case RocketTilesKey => up(RocketTilesKey, site) :+
    RocketTileParams(
      core = RocketCoreParams(
        useVM = true,
        mulDiv = Some(MulDivParams(mulUnroll = 8))),
      btb = Some(BTBParams(nEntries = 14, nRAS = 2)),
      dcache = Some(DCacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes))),
      icache = Some(ICacheParams(
        rowBits = site(SystemBusKey).beatBits,
        blockBytes = site(CacheBlockBytes))),
      hartId = up(BoomTilesKey, site).length + up(RocketTilesKey, site).length)

  // map the last hartId RoCC port to a Systolic Array
  case MultiRoCCKey => up(MultiRoCCKey, site) +
    ((up(BoomTilesKey, site).length + up(RocketTilesKey, site).length) ->
      Seq((p: Parameters) => {
        implicit val q = p
        implicit val v = implicitly[ValName]
        LazyModule(new SystolicArray(SInt(16.W), SInt(16.W), SInt(32.W), OpcodeSet.custom3))
      }))
})

// -------
// CONFIGS
// -------

/**
 * Rocket
 */
class BeagleRocketConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++
  // rocket mixins
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)


/**
 * Dual (Rocket + Hwacha)
 */
class BeagleDualRocketPlusHwachaConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++
  // hwacha mixins
  new hwacha.DefaultHwachaConfig ++
  // rocket mixins
  new freechips.rocketchip.subsystem.WithNBigCores(2) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Dual (BOOM + Hwacha)
 */
class BeagleDualBoomPlusHwachaConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++
  // hwacha mixins
  new hwacha.DefaultHwachaConfig ++
  // boom mixins
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(2) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Heterogeneous (BOOM + Rocket)
 */
class BeagleBoomAndRocketNoHwachaConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++
  // boom mixins
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++
  // rocket mixins
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Heterogeneous ((BOOM + Hwacha) + (Rocket + Hwacha))
 */
class BeagleBoomAndRocketHwachaConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithHierTiles ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++
  // hwacha mixins
  new hwacha.DefaultHwachaConfig ++
  // boom mixins
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++
  // rocket mixins
  new freechips.rocketchip.subsystem.WithNBigCores(1) ++
  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)

/**
 * Heterogeneous ((BOOM + Hwacha) + (Rocket + Systolic))
 *
 * Note: ORDER OF MIXINS MATTERS
 */
class BeagleConfig extends Config(
  // uncore mixins
  new example.WithBootROM ++
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new WithBeagleChanges ++
  new WithBeagleSiFiveBlocks ++
  new WithJtagDTM ++
  new WithRationalRocketTiles ++
  new WithNMemoryChannels(2) ++
  new WithNBanks(2) ++
  new WithBeagleSerdesChanges ++
  new WithGenericSerdes ++

  // note: THIS MUST BE ABOVE hwacha.DefaultHwachaConfig TO WORK
  new WithMultiRoCC ++ // attach particular RoCC accelerators based on the hart

  // systolic parameter setup mixins
  new WithSystolicParams ++
  // hwacha parameter setup mixins
  new hwacha.DefaultHwachaConfig ++

  // rocket mixins
  new WithSystolicAcceleratorMiniCore ++

    // boom mixins
  new WithMultiRoCCHwacha(1) ++ // add a hwacha to just boom
  new boom.common.WithRVC ++
  new boom.common.DefaultBoomConfig ++
  new boom.system.WithNBoomCores(1) ++

  // subsystem mixin
  new freechips.rocketchip.system.BaseConfig)
