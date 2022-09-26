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
  override def moduleDeps = Seq() ++ chisel3Module ++ tilelinkModule
  override def scalacPluginClasspath = T(super.scalacPluginClasspath() ++ chisel3PluginJar())
}

// Test should depend on DiplomaticModule for now
trait DiplomaticModule extends ScalaModule {
  // The bare RocketModule
  def rocketModule: RocketModule
  // upstream RocketChip in dev branch
  def rocketchipModule: ScalaModule
  override def moduleDeps = super.moduleDeps :+ rocketModule :+ rocketchipModule
}
