Using the "Programmatic API" for Scaladoc
-------------------------------------

(This works with scala 2.9.2, the basic approach works for 2.10.x+ but the code will be different.)

For rest.li, we needed to extract scaladoc strings from scala source files representing REST
resources and add the doc strings to the JSON representation of a REST interface.  We had
already done the same for Java using Javadoc’s programmatic APIs.

For Scaladoc programmatic APIs, there wasn’t a whole lot of information available.  A short
section from the [Scaladoc 2 wiki](https://wiki.scala-lang.org/display/SW/Scaladoc) stated that
Scaladoc 2 provides “A new API for writing programs that explore Scala libraries or systems at the API level”.

Scaladoc 2 is part of the “New Scala Compiler” (nsc).  So I added a dependency to “org.scala-lang:scala-compiler:2.9.2” to 
give it a try.

tools.nsc.doc.DocFactory from scala-compiler is the entry point.  The documentation for DocFactory states it uses 
a “simplified compiler instance” and when it's run “...a documentation model is extracted from 
the post-compilation symbol table”.  To build a DocFactory, we need to set a few things up.  Here’s a basic setup:

    val extractScaladoc(scalaSourceFiles: List[String]): Option[DocTemplateEntity] = {
      val settings = new Settings(error => print(error))            
      settings.usejavacp.value = true
      val reporter = new ConsoleReporter(settings)
      val docFactory = new DocFactory(reporter, settings)
      val universe = docFactory.makeUniverse(scalaSourceFiles)
      universe.map(_.rootPackage.asInstanceOf[DocTemplateEntity])
    }

This simple configuration takes a list of scala source files, reuses the classpath from the running jvm (usejavacp) 
and redirects all errors and such to stdout.  The call to makeUniverse function then returns the root of the document
model, which is a DocTemplateEntity, if one exists.

DocTemplateEntity is the root of a model representation of the scala source files we’re extracting Scaladoc from.  To
get to scaladoc comments, we first need to traverse down through the model to whatever entities we want to get
documentation for.  Here's a function that does the traverse:

    def findAtPath(docTemplate: DocTemplateEntity, pathToEntity: List[String]): Option[DocTemplateEntity] = {
      pathToEntity match {
        case Nil => None
        case pathPart :: Nil => docTemplate.templates.find(_.name == pathPart)
        case pathPart:: remainingPathPart => {
          docTemplate.templates.find(_.name == pathPart) match {
            case Some(childDocTemplate) => findAtPath(childDocTemplate, remainingPathPart)
            case None => None
          }
        }
      }
    }

This function takes a path through the models and tries to follow it.  For example,  the path to com.sample.scaladoc.SampleResource would be each package part and then the class name.  E.g.

    val root = extractScaladoc(scalaSourceFiles)
    val classScaladocModel = findAtPath(root, classOf[SampleResource].getCanonicalName.split('.').toList)

Once we have the DocTemplateEntity for whatever we want to get a comment for we can simply access the .comment field.

    classScaladocModel.comment match {
      case Some(comment) => toDocString(comment)
      case None => null
    }

In our case, we want to convert the comment to a string.  So we extract the .body field (there are also fields for the other scaladoc parts such as the @author tags, @see tags and so forth).

    private def toDocString(comment: Comment): String = {
      toDocString(comment.body).trim
    }

Then convert the body and all the inline and block classes it’s made up of to a string.

    private def toDocString(body: Body): String = {
      val comment = body.blocks.map(toDocString(_)) mkString ""
      comment.trim
    }
    
    private def toDocString(block: Block): String = block match {
      case Paragraph(inline) => "<p>" + toDocString(inline) + "</p>"
      // … handle all the other Block case classes
    }
    
    private def toDocString(in: Inline): String = in match {
      case Bold(inline) => "<b>" + toDocString(inline) + "</b>"
      // … handle all the other Inline case classes 
    }

And that's it.  Check out the source code to see the entire program.  I've trimmed up the code snippets here for readability.

Let's try it out.  Here's a scala source file:

    /**
     * Fortunes Resource.
     */
    class SampleResource {
  
      /**
       * Get method.
       * @param param1 provides a string
       * @param param2 provides a <b>boolean</b>
       * @return another string
       */
      def get(param1: String, param2: Boolean): String = {
        "test"
      }
    }

Here’s the output of our scaladoc extractor:

    classDoc: <p>Fortunes Resource.</p>
    methodDoc: <p>Get method.</p>
    paramDoc: <p>provides a string</p>
    paramDoc: <p>provides a <b>boolean</b></p>  

In the title of this document, I put "Programmatic API" in quotes because directly accessing the compiler model like 
we do here means that as the scala compiler changes, so to will the code to traverse the model and convert a comment 
to a string.  This program works for scala-compiler 2.9.2 but does not work with scala-compiler 2.10.x.