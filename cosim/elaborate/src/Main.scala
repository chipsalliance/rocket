package cosim.elaborate

import chisel3._
import chisel3.aop.Select
import chisel3.aop.injecting.InjectingAspect
import chisel3.stage.ChiselGeneratorAnnotation
import firrtl.AnnotationSeq
import firrtl.stage.FirrtlCircuitAnnotation
import mainargs._
import upickle.default._

object Main {
  @main def elaborate(@arg(name = "dir") dir: String, @arg("xlen") xlen: Int) = {
    val config = ujson.Arr(
      ujson.Obj("xlen" -> xlen),
    )
    os.remove(os.pwd/ "out" /"config.json")
    os.write.append(os.pwd/ "out" /"config.json",ujson.write(config, indent = 4))

    var topName: String = null
    val annos: AnnotationSeq = Seq(
      new chisel3.stage.phases.Elaborate,
      new chisel3.tests.elaborate.Convert
    ).foldLeft(
      Seq(
        ChiselGeneratorAnnotation(() => new TestBench(cosimConfig(xlen)) )
      ): AnnotationSeq
    ) { case (annos, stage) => stage.transform(annos) }
      .flatMap {
        case FirrtlCircuitAnnotation(circuit) =>
          topName = circuit.main
          os.write(os.Path(dir) / s"$topName.fir", circuit.serialize)
          None
        case _: chisel3.stage.DesignAnnotation[_] => None
        case a => Some(a)
      }
    os.write(os.Path(dir) / s"$topName.anno.json", firrtl.annotations.JsonProtocol.serialize(annos))
  }

  def main(args: Array[String]): Unit = ParserForMethods(this).runOrExit(args)
}
