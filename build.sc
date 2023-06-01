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

import mill.modules.Util
import mill.define.{Sources, TaskModule}

object v {
  val scala = "2.13.10"
  val mainargs = ivy"com.lihaoyi::mainargs:0.3.0"
  val osLib = ivy"com.lihaoyi::os-lib:0.9.1"
  val upickle = ivy"com.lihaoyi::upickle:0.7.1"
}

object myfirrtl extends dependencies.firrtl.build.firrtlCrossModule(v.scala) {
  override val checkSystemAntlr4Version = false
  override val checkSystemProtocVersion = false
  override val protocVersion = os.proc("protoc", "--version").call().out.text.dropRight(1).split(' ').last
  override val antlr4Version = os.proc("antlr4").call().out.text.split('\n').head.split(' ').last

  override def millSourcePath = os.pwd / "dependencies" / "firrtl"
}

object mytreadle extends dependencies.treadle.build.treadleCrossModule(v.scala) {
  override def millSourcePath = os.pwd / "dependencies" / "treadle"

  def firrtlModule: Option[PublishModule] = Some(myfirrtl)
}

object mychisel3 extends dependencies.chisel3.build.chisel3CrossModule(v.scala) {
  override def millSourcePath = os.pwd / "dependencies" / "chisel3"

  def firrtlModule: Option[PublishModule] = Some(myfirrtl)

  def treadleModule: Option[PublishModule] = Some(mytreadle)

  def chiseltestModule: Option[PublishModule] = Some(mychiseltest)
}

object mychiseltest extends dependencies.chiseltest.build.chiseltestCrossModule(v.scala) {
  override def millSourcePath = os.pwd / "dependencies" / "chiseltest"

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  def treadleModule: Option[PublishModule] = Some(mytreadle)
}

object mycde extends dependencies.cde.build.cde(v.scala) with PublishModule {
  override def millSourcePath = os.pwd / "dependencies" / "cde" / "cde"
}

object myhardfloat extends dependencies.`berkeley-hardfloat`.build.hardfloat {
  override def millSourcePath = os.pwd / "dependencies" / "berkeley-hardfloat"

  override def scalaVersion = v.scala

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  override def scalacPluginClasspath = T {
    super.scalacPluginClasspath() ++ Agg(mychisel3.plugin.jar())
  }

  override def scalacOptions = T(Seq(s"-Xplugin:${mychisel3.plugin.jar().path}"))
}

object myrocketchip extends dependencies.`rocket-chip`.common.CommonRocketChip {
  override def millSourcePath = os.pwd / "dependencies" / "rocket-chip"

  def chisel3Module: Option[PublishModule] = Some(mychisel3)

  def hardfloatModule: PublishModule = myhardfloat

  def cdeModule: PublishModule = mycde

  override def scalaVersion = v.scala

  override def scalacPluginClasspath = T {
    super.scalacPluginClasspath() ++ Agg(mychisel3.plugin.jar())
  }

  override def scalacOptions = T(Seq(s"-Xplugin:${mychisel3.plugin.jar().path}"))
}

object mytilelink extends dependencies.tilelink.common.TileLinkModule {
  override def millSourcePath = os.pwd / "dependencies" / "tilelink" / "tilelink"

  def scalaVersion = T(v.scala)

  def chisel3Module = mychisel3

  def chisel3PluginJar = T(mychisel3.plugin.jar())
}

object rocket extends common.RocketModule {
  m =>
  def scalaVersion = T(v.scala)

  def chisel3Module = Some(mychisel3)

  def chisel3PluginJar = T(Some(mychisel3.plugin.jar()))

  def tilelinkModule = Some(mytilelink)
}

object diplomatic extends common.DiplomaticModule {
  m =>
  def scalaVersion = T(v.scala)

  def rocketModule = rocket

  def rocketchipModule = myrocketchip
}

object cosim extends Module {
  class elaborate(xLen: String) extends ScalaModule with ScalafmtModule {

    def sources = T.sources(millSourcePath)

    def millSourcePath = super.millSourcePath / os.up

    override def scalacPluginClasspath = T {
      Agg(mychisel3.plugin.jar())
    }

    override def scalacOptions = T {
      super.scalacOptions() ++ Some(mychisel3.plugin.jar()).map(path => s"-Xplugin:${path.path}") ++ Seq("-Ymacro-annotations")
    }

    override def scalaVersion = v.scala

    override def moduleDeps = Seq(mycde, myrocketchip, mytilelink, diplomatic)

    override def ivyDeps = T {
      Seq(
        v.mainargs,
        v.osLib,
        v.upickle
      )
    }

    def crossprint = T {
      println("xlen" + xLen)
    }

    def chiselAnno = T {
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("anno.json") => p }.map(PathRef(_)).get
    }

    def elaborate = T {
      // class path for `moduleDeps` is only a directory, not a jar, which breaks the cache.
      // so we need to manually add the class files of `moduleDeps` here.
      upstreamCompileOutput()
      mill.modules.Jvm.runLocal(
        finalMainClass(),
        runClasspath().map(_.path),
        Seq(
          "--dir", T.dest.toString,
          "--xlen", xLen
        ),
      )
      PathRef(T.dest)
    }

    def topName = T {
      chirrtl().path.last.split('.').head
    }

    def chirrtl = T {
      os.walk(elaborate().path).collectFirst { case p if p.last.endsWith("fir") => p }.map(PathRef(_)).get
    }

  }

  class mfccompile(xLen: String) extends Module {

    def sources = T.sources(millSourcePath)

    def millSourcePath = super.millSourcePath / os.up

    def rtls = T.persistent {
      os.read(compile().path / "filelist.f").split("\n").map(str =>
        try {
          /** replace relative path with absolute path */
          val absstr = compile().path.toString() ++ "/" ++ str.drop(2)
          val path = if (str.startsWith("./")) absstr else str
          os.Path(path)

        } catch {
          case e: IllegalArgumentException if e.getMessage.contains("is not an absolute path") =>
            compile().path / str
        }
      ).filter(p => p.ext == "v" || p.ext == "sv").map(PathRef(_)).toSeq
    }

    def annotations = T {
      os.walk(compile().path).filter(p => p.last.endsWith("mfc.anno.json")).map(PathRef(_))
    }

    def compile = T {
      os.proc("firtool",
        elaborate(xLen).chirrtl().path,
        s"--annotation-file=${elaborate(xLen).chiselAnno().path}",
        "--disable-annotation-unknown",
        "-disable-infer-rw",
        "-dedup",
        "-O=debug",
        "--split-verilog",
        "--preserve-values=named",
        "--output-annotation-file=mfc.anno.json",
        "--lowering-options=verifLabels",
        s"-o=${T.dest}"
      ).call(T.dest)
      PathRef(T.dest)
    }
  }

  class emulator(xLen: String) extends Module {

    val ncores: Int = Runtime.getRuntime.availableProcessors()

    val emulatorCores: Int = if(ncores > 8) 8 else ncores

    val topName = "TestBench"

    def sources = T.sources(millSourcePath)

    def millSourcePath = super.millSourcePath / os.up

    def elf = T.persistent {
      val path = T.dest / "CMakeLists.txt"
      os.write.over(path, CMakeListsString())
      T.log.info(s"CMake project generated in $path,\nverilating...")
      os.proc(
        // format: off
        "cmake",
        "-G", "Ninja",
        T.dest.toString
        // format: on
      ).call(T.dest)
      T.log.info("compile rtl to emulator...")
      os.proc(
        // format: off
        "ninja"
        // format: on
      ).call(T.dest)
      val elf = T.dest / topName
      T.log.info(s"verilated exe generated: ${elf.toString}")
      PathRef(elf)
    }

    def CMakeListsString = T {
      // format: off
      s"""cmake_minimum_required(VERSION 3.20)
         |set(CMAKE_CXX_STANDARD 17)
         |set(CMAKE_CXX_COMPILER_ID "clang")
         |set(CMAKE_C_COMPILER "clang")
         |set(CMAKE_CXX_COMPILER "clang++")
         |
         |project(emulator)
         |
         |find_package(args REQUIRED)
         |find_package(glog REQUIRED)
         |find_package(fmt REQUIRED)
         |find_package(libspike REQUIRED)
         |find_package(verilator REQUIRED)
         |find_package(Threads REQUIRED)
         |set(THREADS_PREFER_PTHREAD_FLAG ON)
         |
         |set(CMAKE_CXX_FLAGS "$${CMAKE_CXX_FLAGS} -DVERILATOR")
         |
         |add_executable(${topName}
         |${allCSourceFiles().map(_.path).mkString("\n")}
         |)
         |
         |target_include_directories(${topName} PUBLIC ${csources().path.toString})
         |
         |target_link_libraries(${topName} PUBLIC $${CMAKE_THREAD_LIBS_INIT})
         |target_link_libraries(${topName} PUBLIC libspike fmt glog)  # note that libargs is header only, nothing to link
         |
         |verilate(${topName}
         |  SOURCES
         |${vsrcs().map(_.path).mkString("\n")}
         |  TRACE_FST
         |  TOP_MODULE TestBench
         |  PREFIX VTestBench
         |  OPT_FAST
         |  THREADS ${emulatorCores}
         |  VERILATOR_ARGS ${verilatorArgs().mkString(" ")}
         |)
         |""".stripMargin
      // format: on
    }

    def csources = T.source {
      millSourcePath / "src"
    }

    def vsrcs = T.persistent {
      mfccompile(xLen).rtls().filter(p => p.path.ext == "v" || p.path.ext == "sv")
    }

    def allCSourceFiles = T {
      Lib.findSourceFiles(Seq(csrcDir()), Seq("S", "s", "c", "cpp", "cc")).map(PathRef(_))
    }

    def csrcDir = T {
      PathRef(millSourcePath / "src")
    }

    def verilatorArgs = T.input {
      Seq(
        // format: off
        "-Wno-UNOPTTHREADS", "-Wno-STMTDLY", "-Wno-LATCH", "-Wno-WIDTH",
        "--x-assign unique",
        "+define+RANDOMIZE_GARBAGE_ASSIGN",
        "--output-split 20000",
        "--output-split-cfuncs 20000",
        "--max-num-width 1048576",
        "--main",
        "--timing"
        // format: on
      )
    }
  }

  object elaborate extends Cross[elaborate]("32", "64")

  object mfccompile extends Cross[mfccompile]("32", "64")

  object emulator extends Cross[emulator]("32", "64")
}

object cases extends Module {
  trait Case extends Module {
    def compile: T[PathRef] = T {
      os.proc(Seq(s"clang-rv${xlen}", "-o", name() + ".elf", s"--target=riscv${xlen}", s"-march=rv${xlen}gc", "-mno-relax", s"-T${linkScript().path}") ++ allSourceFiles().map(_.path.toString)).call(T.ctx.dest)
      os.proc(Seq("llvm-objcopy", "-O", "binary", "--only-section=.text", name() + ".elf", name())).call(T.ctx.dest)
      T.log.info(s"${name()} is generated in ${T.dest},\n")
      PathRef(T.ctx.dest / name())
    }

    def name: T[String] = millSourcePath.last

    def allSourceFiles = T {
      Lib.findSourceFiles(sources(), Seq("S", "s", "c", "cpp")).map(PathRef(_))
    }

    def sources = T.sources {
      millSourcePath
    }

    def xlen: Int = 64

    def linkScript: T[PathRef] = T {
      os.write(T.ctx.dest / "linker.ld",
        s"""
           |SECTIONS
           |{
           |  . = 0x1000;
           |  .text.start : { *(.text.start) }
           |}
           |""".stripMargin)
      PathRef(T.ctx.dest / "linker.ld")
    }
  }

  object entrance64 extends Case {
    override def xlen = 64
  }

  object entrance32 extends Case {
    override def xlen = 32
  }

  object riscvtests extends Module {

    def alltests = os.walk(testsRoot).filterNot(p => p.last.endsWith("dump")).filter(p => p.last.startsWith("rv")).map(c => c.last)

    def testsRoot =
      os.Path(sys.env("RISCV_TESTS_ROOT")) / "share" / "riscv-tests"

    def init = T {
      target().map(bin => {
        os.proc("cp", bin.path, "./" + bin.path.last + ".elf").call(T.dest)
        os.proc("llvm-objcopy", "-O", "binary", bin.path.last + ".elf", bin.path.last).call(T.dest)
      })
      PathRef(T.dest)
    }

    def target = T {
      os.walk(testsRoot).filter(p => p.last.startsWith("rv")).filterNot(p => p.last.endsWith("dump")).map(PathRef(_))
    }

    class Suite(casename: String) extends ScalaModule with ScalafmtModule {
      override def scalaVersion = v.scala

      def binaries = T {
        casename match {
          case "rv64" => {
            os.walk(init().path).filter(p => p.last.startsWith(casename)).filterNot(p => p.last.endsWith("elf")).filter(p =>
              p.last.startsWith("rv64mi-p") | p.last.startsWith("rv64si-p") | p.last.startsWith("rv64ui-p") | p.last.startsWith("rv64uf-p") | p.last.startsWith("rv64ua-p") | p.last.startsWith("rv64ud-p") | p.last.startsWith("rv64uc-p") | p.last.startsWith("rv64um-p") | p.last.startsWith("rv64uzfh-p")).filterNot(p =>
              p.last.startsWith("rv64ui-p-simple") //have no <pass> symbol in elf
                | p.last.endsWith("csr") //MISA differs between Rocket and Spike
                | p.last.endsWith("rv64mi-p-breakpoint") // Requires debug module
                | p.last.endsWith("rv64si-p-icache-alias") // infinit loop after trap_machine_ecall at 0x8000012b8
                | p.last.endsWith("rv64ui-p-ma_data") // https://github.com/riscv-software-src/riscv-tests/issues/419
                | p.last.endsWith("rv64si-p-wfi") // infinit loop after trap_supervisor_ecall at 0x800001ac
                | p.last.endsWith("rv64mi-p-scall")) // infinit loop after trap_supervisor_ecall at 0x800001ec
              .map(PathRef(_))
          }
          case "rv32" => {
            os.walk(init().path).filter(p => p.last.startsWith(casename)).filterNot(p => p.last.endsWith("elf")).filter(p =>
              p.last.startsWith("rv32mi-p") | p.last.startsWith("rv32si-p") | p.last.startsWith("rv32ui-p") | p.last.startsWith("rv32uf-p") | p.last.startsWith("rv32ua-p") | p.last.startsWith("rv32um-p") | p.last.startsWith("rv32uzfh-p")).filterNot(p =>
              p.last.startsWith("rv32ui-p-simple")
                | p.last.endsWith("csr") // MISA differs
                | p.last.endsWith("rv32mi-p-breakpoint") // Requires debug module
                | p.last.endsWith("rv32mi-p-scall") // infinit loop after trap_user_ecall at 0x800001c8
                | p.last.endsWith("rv32si-p-wfi") // infinit loop after trap_supervisor_ecall at 0x800001a8
                | p.last.endsWith("rv32si-p-dirty")) // infinit loop after trap_machine_ecall at 0x80000027c
              .map(PathRef(_))
          }
          case _ => {
            os.walk(init().path).filter(p => p.last.startsWith(casename)).filterNot(p => p.last.endsWith("elf")).map(PathRef(_))
          }
        }
      }
    }

    object rvcase extends Cross[Suite](alltests :+ "rv64" :+ "rv32": _*)

  }
}

object tests extends Module() {
  object riscvtests extends Module {
    class run(casename: String) extends ScalaModule with ScalafmtModule {
      override def scalaVersion = v.scala

      override def defaultCommandName() = "test"

      def test(args: String*) = T.command {
        bin().map { c =>
          val name = c.path.last
          val entrancePath = if (xlen == "64") cases.entrance64.compile().path.toString else cases.entrance32.compile().path.toString
          val out = os.proc("llvm-nm", s"${c.path.toString()}" + ".elf").call().out.toString
          val pass = os.proc("grep", "pass").call(stdin = out).toString().split(" ")
          val pass_address = pass(1).split("\n")(1)
          val runEnv = Map(
            "COSIM_bin" -> c.path.toString,
            "COSIM_entrance_bin" -> entrancePath,
            "COSIM_wave" -> (T.dest / "wave").toString,
            "COSIM_reset_vector" -> "80000000",
            "COSIM_timeout" -> "100000",
            "passaddress" -> pass_address,
            "xlen" -> xlen
          )
          val proc = os.proc(Seq(cosim.emulator(xlen).elf().path.toString()))
          T.log.info(s"run test: ${c.path.last} ")
          val p = proc.call(stdout = T.dest / s"$name.running.log", mergeErrIntoOut = true, env = runEnv, check = false)

          PathRef(if (p.exitCode != 0) {
            os.move(T.dest / s"$name.running.log", T.dest / s"$name.failed.log")
            System.err.println(s"Test $name failed with exit code ${p.exitCode}")
            System.exit(1)
            T.dest / s"$name.failed.log"
          } else {
            os.move(T.dest / s"$name.running.log", T.dest / s"$name.passed.log")
            T.dest / s"$name.passed.log"
          })
        }
      }

      def bin = cases.riscvtests.rvcase(casename).binaries

      def xlen = if (casename.startsWith("rv64")) "64" else "32"

    }

    object run extends Cross[run](cases.riscvtests.alltests :+ "rv64" :+ "rv32": _*)

  }
}
