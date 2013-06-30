import collection.JavaConversions._
import java.lang.reflect.Method
import tools.nsc.doc.model.comment._
import tools.nsc.doc.model.{DocTemplateEntity, Def}
import tools.nsc.doc.{DocFactory, Settings}
import tools.nsc.reporters.ConsoleReporter
import com.sample.scaladoc.SampleResource

object ScaladocDocsProvider {
  def main(args: Array[String]) {
    val provider = new ScaladocDocsProvider(args.toList)

    val method = classOf[SampleResource].getMethod("get", classOf[java.lang.String], classOf[Boolean])
    println("classDoc: " + provider.getClassDoc(classOf[SampleResource]))
    println("methodDoc: " + provider.getMethodDoc(method))
    println("paramDoc: " + provider.getParamDoc(method, "param1"))
    println("paramDoc: " + provider.getParamDoc(method, "param2"))
  }
}

/**
 * Scaladoc version of DocProvider.
 */
class ScaladocDocsProvider(scalaSourceFiles: List[String]) extends DocsProvider {

  private val root = extractScaladoc(scalaSourceFiles)

  def extractScaladoc(scalaSourceFiles: List[String]): Option[DocTemplateEntity] = {
    if(scalaSourceFiles.size == 0) {
      None
    } else {
      val settings = new Settings(error => print(error))
      settings.usejavacp.value = true
      val reporter = new ConsoleReporter(settings)
      val docFactory = new DocFactory(reporter, settings)
      val universe = docFactory.makeUniverse(scalaSourceFiles)
      universe.map(_.rootPackage.asInstanceOf[DocTemplateEntity])
    }
  }

  def getClassDoc(resourceClass: Class[_]): String = {
    findTemplate(resourceClass) match {
      case Some(template) => template.comment match {
        case Some(comment) => toDocString(comment)
        case None => null
      }
      case None => null
    }
  }

  def getMethodDoc(method: Method): String = {
    findMethod(method) match {
      case Some(templateMethod) => {
        templateMethod.comment match {
          case Some(comment) => toDocString(comment)
          case None => null
        }
      }
      case None => null
    }
  }

  def getParamDoc(method: Method, name: String): String = {
    findMethod(method) match {
      case Some(templateMethod) => {
        templateMethod.comment match {
          case Some(templateMethodComments) =>
            toDocString(templateMethodComments.valueParams(name))
          case None => null
        }
      }
      case None => null
    }
  }


  private def findTemplate(resourceClass: Class[_]): Option[DocTemplateEntity] = {
    def findAtPath(docTemplate: DocTemplateEntity, namespaceParts: List[String]): Option[DocTemplateEntity] = {
      namespaceParts match {
        case Nil => None
        case namespacePart :: Nil => docTemplate.templates.find(_.name == namespacePart)
        case namespacePart :: remainingNamespaceParts => {
          docTemplate.templates.find(_.name == namespacePart) match {
            case Some(childDocTemplate) => findAtPath(childDocTemplate, remainingNamespaceParts)
            case None => None
          }
        }
      }
    }

    root flatMap { r =>
      findAtPath(r, resourceClass.getCanonicalName.split('.').toList)
    }
  }

  private def findMethod(method: Method): Option[Def]  = {
    val matches = findTemplate(method.getDeclaringClass) map { template =>
      template.methods filter { templateMethod =>
        templateMethod.name == method.getName &&
        (
          (templateMethod.valueParams.length == 0 && method.getParameterTypes.length == 0) ||
          (
            templateMethod.valueParams.length == 1 &&
            templateMethod.valueParams(0).length == method.getParameterTypes.length
            // To be precise here, we should check all param types match, but this is exceedingly complex.
            // Method is from java.lang.reflect which has java types and templateMethod is from scala's AST
            // which has scala types.  The mapping between the two, particularly for primitive types, is involved.
            // Given that rest.li has strong method naming conventions,  name and param count should be sufficient
            // in all but the most pathological cases.  One option would be to check the annotations if
            // additional disambiguation is needed.
          )
        )
      } map { templateMethod =>
        templateMethod
      }
    }

    matches.flatten.headOption
  }

  private def toDocString(comment: Comment): String = {
    toDocString(comment.body).trim
  }

  private def toDocString(body: Body): String = {
    val comment = body.blocks.map(toDocString(_)) mkString ""
    comment.trim
  }

  private def toDocString(block: Block): String = block match {
    case Paragraph(inline) => "<p>" + toDocString(inline) + "</p>"
    case Title(text, level) => "<h" + level + ">" + toDocString(text) + "</h" + level + ">"
    case Code(data) => "<pre>" + data + "</pre>"
    case UnorderedList(items) => {
      "<ul>" + items.map("<li>" + toDocString(_) + "</li>").mkString + "</ul>"
    }
    case OrderedList(items, style) => {
      "<ol>" + items.map("<li>" + toDocString(_) + "</li>").mkString + "</ol>"
    }
    case DefinitionList(items) => {
      "<dl>" + items.map{ case (key, value) => "<dt>" + key + "</dt><dd>" + value + "</dd>"}.mkString + "</dl>"
    }
    case HorizontalRule() => "<hr>"
    // We've covered all the currently existing cases (we know that because Block is sealed),
    // but for forward compat, we'll just toString any we are unaware of
    case _ => block.toString.trim
  }

  // We're using html formatting here, like is done by rest.li already for javadoc
  private def toDocString(in: Inline): String = in match {
    case Bold(inline) => "<b>" + toDocString(inline) + "</b>"
    case Chain(items) => items.map(toDocString(_)).mkString
    case Italic(inline) => "<i>" + toDocString(inline) + "</i>"
    case Link(target, inline) => "<a href=" + target + ">" + toDocString(inline) + "</a>"
    case Monospace(inline) => "<code>" + toDocString(inline) + "</code>"
    case Summary(inline) => toDocString(inline)
    case Superscript(inline) => "<sup>" + toDocString(inline) + "</sup>"
    case Subscript(inline) => "<sub>" + toDocString(inline) + "</sub>"
    // we don't have a way to retain scaladoc (or javadoc) entity links, so we'll just include the fully qualified name
    case EntityLink(target) => target.qualifiedName
    case Text(text) => text
    // underlining is discouraged in html because it makes text resemble a link, so we'll go with em, a popular alternative
    case Underline(inline) => "<em>" + toDocString(inline) + "</em>"
    case HtmlTag(rawHtml) => rawHtml
    // We've covered all the currently existing cases (we know that because Inline is sealed),
    // but for forward compat, we'll just toString any we are unaware of
    case _ => in.toString
  }
}
