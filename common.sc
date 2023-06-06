import mill._
import mill.scalalib._

// Basically we are migrating RocketCore which depends on RocketChip to bare chisel3.
// We put original codes inside DiplomaticModule, and extracting them file by file to RocketModule
// The RocketModule should only depend on chisel3Module and tilelinkModule, currently in submodule.
trait RocketModule extends ScalaModule {
  // TODO: add option to depend on Chisel Maven
  def chisel3Module: Option[ScalaModule]
  def chisel3PluginJar: T[Option[PathRef]]
  def tilelinkModule: Option[ScalaModule]
  def riscvopcodesModule: RiscvOpcodesModule
  override def generatedSources: T[Seq[PathRef]] = T {
    Seq(
      riscvopcodesModule.rvInstruction(),
      riscvopcodesModule.rv32Instruction(),
      riscvopcodesModule.rv64Instruction(),
      riscvopcodesModule.csrs(),
      riscvopcodesModule.causes()
      )
  }
  override def moduleDeps = Seq() ++ chisel3Module ++ tilelinkModule
  override def scalacPluginClasspath = T(super.scalacPluginClasspath() ++ chisel3PluginJar())
  override def scalacOptions = T(super.scalacOptions() ++ chisel3PluginJar().map(p => s"-Xplugin:${p.path}"))
}

// Test should depend on DiplomaticModule for now
trait DiplomaticModule extends ScalaModule {
  // The bare RocketModule
  def rocketModule: RocketModule
  // upstream RocketChip in dev branch
  def rocketchipModule: ScalaModule
  override def moduleDeps = super.moduleDeps :+ rocketModule :+ rocketchipModule
  override def scalacPluginClasspath = T(super.scalacPluginClasspath() ++ rocketModule.chisel3PluginJar())
  override def scalacOptions = T(super.scalacOptions() ++ rocketModule.chisel3PluginJar().map(p => s"-Xplugin:${p.path}"))
}

// Build targets to codegen instructions
// millSourcePath should be set to riscv/riscv-opcodes to generate required Scala file
trait RiscvOpcodesModule extends Module {
  // path to script calling python library in riscv/riscv-opcodes.git
  def script: T[PathRef]
  def rv64Instruction = T {
    val f = T.ctx.dest / "Instructions64.scala"
    os.proc("python", script().path, "rv64*").call(stdout = f, env = Map("PYTHONPATH"->millSourcePath.toString))
    PathRef(f)
  }
  def rv32Instruction = T {
    val f = T.ctx.dest / "Instructions32.scala"
    os.proc("python", script().path, "rv32*").call(stdout = f, env = Map("PYTHONPATH"->millSourcePath.toString))
    PathRef(f)
  }
  def rvInstruction = T {
    val f = T.ctx.dest / "Instructions.scala"
    os.proc("python", script().path, "rv_*").call(stdout = f, env = Map("PYTHONPATH"->millSourcePath.toString))
    PathRef(f)
  }
  def causes = T {
    val f = T.ctx.dest / "Causes.scala"
    os.proc("python", script().path, "causes").call(stdout = f, env = Map("PYTHONPATH"->millSourcePath.toString))
    PathRef(f)
  }
  def csrs = T {
    val f = T.ctx.dest / "CSRs.scala"
    os.proc("python", script().path, "csrs").call(stdout = f, env = Map("PYTHONPATH"->millSourcePath.toString))
    PathRef(f)
  }
}
