import mill._
import mill.scalalib._
import mill.scalalib.publish._
import mill.scalalib.scalafmt._
import coursier.maven.MavenRepository
import $file.dependencies.cde.build
import $file.dependencies.`berkeley-hardfloat`.build
import $file.dependencies.`rocket-chip`.common
import $file.dependencies.chisel3.build
import $file.dependencies.firrtl.build
import $file.dependencies.treadle.build
import $file.dependencies.chiseltest.build
import $file.dependencies.tilelink.common
import $file.common

object v {
  val scala = "2.13.10"
  val mainargs = ivy"com.lihaoyi::mainargs:0.3.0"
  val osLib = ivy"com.lihaoyi::os-lib:latest.integration"
}

object myfirrtl extends dependencies.firrtl.build.firrtlCrossModule(v.scala) {
  override def millSourcePath = os.pwd / "dependencies" / "firrtl"
  override val checkSystemAntlr4Version = false
  override val checkSystemProtocVersion = false
  override val protocVersion = os.proc("protoc", "--version").call().out.text.dropRight(1).split(' ').last
  override val antlr4Version = os.proc("antlr4").call().out.text.split('\n').head.split(' ').last
}
object mytreadle extends dependencies.treadle.build.treadleCrossModule(v.scala) {
  override def millSourcePath = os.pwd /  "dependencies" / "treadle"
  def firrtlModule: Option[PublishModule] = Some(myfirrtl)
}
object mychisel3 extends dependencies.chisel3.build.chisel3CrossModule(v.scala) {
  override def millSourcePath = os.pwd / "dependencies" / "chisel3"
  def firrtlModule: Option[PublishModule] = Some(myfirrtl)
  def treadleModule: Option[PublishModule] = Some(mytreadle)
  def chiseltestModule: Option[PublishModule] = Some(mychiseltest)
}
object mychiseltest extends dependencies.chiseltest.build.chiseltestCrossModule(v.scala) {
  override def millSourcePath = os.pwd /  "dependencies" / "chiseltest"
  def chisel3Module: Option[PublishModule] = Some(mychisel3)
  def treadleModule: Option[PublishModule] = Some(mytreadle)
}
object mycde extends dependencies.cde.build.cde(v.scala) with PublishModule {
  override def millSourcePath = os.pwd /  "dependencies" / "cde" / "cde"
}
object myhardfloat extends dependencies.`berkeley-hardfloat`.build.hardfloat {
  override def millSourcePath = os.pwd /  "dependencies" / "berkeley-hardfloat"
  override def scalaVersion = v.scala
  def chisel3Module: Option[PublishModule] = Some(mychisel3)
  override def scalacPluginClasspath = T { super.scalacPluginClasspath() ++ Agg(mychisel3.plugin.jar()) }
  override def scalacOptions = T(Seq(s"-Xplugin:${mychisel3.plugin.jar().path}"))
}
object myrocketchip extends dependencies.`rocket-chip`.common.CommonRocketChip {
  override def millSourcePath = os.pwd /  "dependencies" / "rocket-chip"
  def chisel3Module: Option[PublishModule] = Some(mychisel3)
  def hardfloatModule: PublishModule = myhardfloat
  def cdeModule: PublishModule = mycde
  override def scalaVersion = v.scala
  override def scalacPluginClasspath = T { super.scalacPluginClasspath() ++ Agg(mychisel3.plugin.jar()) }
  override def scalacOptions = T(Seq(s"-Xplugin:${mychisel3.plugin.jar().path}"))
}
object mytilelink extends dependencies.tilelink.common.TileLinkModule {
  override def millSourcePath = os.pwd /  "dependencies" / "tilelink" / "tilelink"
  def scalaVersion = T(v.scala)
  def chisel3Module = mychisel3
  def chisel3PluginJar = T(mychisel3.plugin.jar())
}
object rocket extends common.RocketModule { m =>
  def scalaVersion = T(v.scala)
  def chisel3Module = Some(mychisel3)
  def chisel3PluginJar = T(Some(mychisel3.plugin.jar()))
  def tilelinkModule = Some(mytilelink)
}
object diplomatic extends common.DiplomaticModule { m =>
  def scalaVersion = T(v.scala)
  def rocketModule = rocket
  def rocketchipModule = myrocketchip
  override def scalacOptions = T(Seq(s"-Xplugin:${mychisel3.plugin.jar().path}"))
}

object cosim extends Module {
  object elaborate extends ScalaModule with ScalafmtModule {
    def scalaVersion = T {
      v.scala
    }

    override def moduleDeps = Seq(mycde, myrocketchip)

    override def scalacOptions = T {
      Seq(s"-Xplugin:${mychisel3.plugin.jar().path}")
    }

    override def ivyDeps = Agg(
      v.mainargs,
      v.osLib
    )

    def elaborate = T.persistent {
      mill.modules.Jvm.runSubprocess(
        finalMainClass(),
        runClasspath().map(_.path),
        forkArgs(),
        forkEnv(),
        Seq(
          "--dir", T.dest.toString,
        ),
        workingDir = forkWorkingDir()
      )
      PathRef(T.dest)
    }

    def rtls = T.persistent {
      os.read(elaborate().path / "filelist.f").split("\n").map(str =>
        try {
          os.Path(str)
        } catch {
          case e: IllegalArgumentException if e.getMessage.contains("is not an absolute path") =>
            elaborate().path / str
        }
      ).filter(p => p.ext == "v" || p.ext == "sv").map(PathRef(_)).toSeq
    }
  }

  object myelaborate extends ScalaModule with ScalafmtModule {
    def scalaVersion = T {
      v.scala
    }

    override def moduleDeps = Seq(mycde, myrocketchip)

    override def scalacOptions = T {
      Seq(s"-Xplugin:${mychisel3.plugin.jar().path}")
    }

    override def ivyDeps = Agg(
      v.mainargs,
      v.osLib
    )

    def elaborate = T {
      // class path for `moduleDeps` is only a directory, not a jar, which breaks the cache.
      // so we need to manually add the class files of `moduleDeps` here.
      upstreamCompileOutput()
      mill.modules.Jvm.runLocal(
        finalMainClass(),
        runClasspath().map(_.path),
        Seq(
          "--dir", T.dest.toString,
        ),
      )
      PathRef(T.dest)
    }

    def chiselAnno = T {
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("anno.json") => p }.map(PathRef(_)).get
    }

    def chirrtl = T {
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("fir") => p }.map(PathRef(_)).get
    }

    def topName = T {
      chirrtl().path.last.split('.').head
    }


  }

  object mfccompile extends Module {

    def compile = T {
      os.proc("firtool",
        myelaborate.chirrtl().path,
        s"--annotation-file=${myelaborate.chiselAnno().path}",
        "-disable-infer-rw",
        "-dedup",
        "-O=debug",
        "--split-verilog",
        "--preserve-values=named",
        "--output-annotation-file=mfc.anno.json",
        s"-o=${T.dest}"
      ).call(T.dest)
      PathRef(T.dest)
    }

    def rtls = T {
      os.read(compile().path / "filelist.f").split("\n").map(str =>
        try {
          os.Path(str)
        } catch {
          case e: IllegalArgumentException if e.getMessage.contains("is not an absolute path") =>
            compile().path / str.stripPrefix("./")
        }
      ).filter(p => p.ext == "v" || p.ext == "sv").map(PathRef(_)).toSeq
    }

    def annotations = T {
      os.walk(compile().path).filter(p => p.last.endsWith("mfc.anno.json")).map(PathRef(_))
    }
  }
}
