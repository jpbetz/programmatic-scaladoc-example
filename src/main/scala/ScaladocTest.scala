import scala.tools.nsc.doc.model.comment._
import scala.tools.nsc.doc.model._
import scala.tools.nsc.doc._
import scala.tools.nsc.reporters.{ConsoleReporter, Reporter}
import scala.tools.nsc.util.Position
import scala.tools.nsc.{Interpreter, Global}

/**
 * Here's a class.
 */
object ScaladocTest {

  /**
   * Here's the main method
   * @param args
   */
  def main(args: Array[String]) {
    val settings = new Settings(error => print(error))
    settings.sourcepath.append("/Users/jbetz/projects/scaladoc-test/src/main/scala")
    println(settings.sourcepath)
    settings.usejavacp.value = true

    val reporter = new ConsoleReporter(settings)
    val docFactory = new DocFactory(reporter, settings)

    val universe = docFactory.makeUniverse(List("/Users/jbetz/projects/scaladoc-test/src/main/scala/com/sample/scaladoc/SampleResource.scala"))

    println(universe.get.rootPackage.isInstanceOf[DocTemplateEntity])
    val templateEntity = universe.get.rootPackage.asInstanceOf[DocTemplateEntity]

    printTemplate(templateEntity)
  }

  def printTemplate(template: DocTemplateEntity): Unit =  {
    template.comment foreach { comment =>
      println("resource: " + template.name + " - " + commentToString(comment).trim)
    }
    template.methods filter(_.comment.isDefined) map { method =>
      val comment = method.comment.get
      println("\tmethod:" + method.name + " - " + commentToString(comment).trim)
      method.valueParams map { currySet =>
        currySet map { param =>
          println("\t\tparam: " + param.name + " - " + bodyToStr(comment.valueParams(param.name)))
        }
      }
      println("\t\treturns: " + method.resultType.name + " - " + bodyToStr(comment.result.get).trim)

    }
    template.templates map { printTemplate(_)}
  }

  def commentToString(comment: Comment): String = {
    bodyToStr(comment.body)
  }

  private def bodyToStr(body: comment.Body): String =
    body.blocks flatMap (blockToStr(_)) mkString ""

  private def blockToStr(block: comment.Block): String = block match {
    case comment.Paragraph(in) => inlineToStr(in)
    case _ => block.toString
  }

  private def inlineToStr(inl: comment.Inline): String = inl match {
    case comment.Chain(items) => items flatMap (inlineToStr(_)) mkString ""
    case comment.Italic(in) => inlineToStr(in)
    case comment.Bold(in) => inlineToStr(in)
    case comment.Underline(in) => inlineToStr(in)
    case comment.Monospace(in) => inlineToStr(in)
    case comment.Text(text) => text
    case comment.Summary(in) => inlineToStr(in)
    case _ => inl.toString
  }
}