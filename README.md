Programmatic access to Scaladoc.

For rest.li, we needed to extract scaladoc strings from scala source files representing REST resources and add the doc strings to the JSON representation of a REST interface.  We had already done the same for Java using Javadoc’s programmatic APIs.

For Scaladoc programmatic APIs, there wasn’t a whole lot of information available.  A short section from the Scaladoc 2 wiki stated that Scaladoc 2 provides “A new API for writing programs that explore Scala libraries or systems at the API level”.

We dug in a bit and found that Scala’s “New Scala Compiler” (nsc) is there this API resides.  So we added a dependency to “org.scala-lang:scala-compiler:2.9.2” to give it a try.

tools.nsc.doc.DocFactory appeared to be the right starting point.  It uses a “simplified compiler instance” and that “a documentation model is extracted from the post-compilation symbol table”.

Okay, to build a DocFactory, we need to set a few things up.  Here’s a basic setup:


    val extractScaladoc(files: String[]): Option[DocTemplateEntity] = {
      val settings = new Settings(error => print(error))            
      settings.usejavacp.value = true
      val reporter = new ConsoleReporter(settings)
      val docFactory = new DocFactory(reporter, settings)
      val filelist = if (files == null || files.size == 0) List() else collectionAsScalaIterable(files).toList
      val universe = docFactory.makeUniverse(filelist)
      universe.map(_.rootPackage.asInstanceOf[DocTemplateEntity])
    }

We also called the makeUniverse method and then return the root DocTemplateEntity, if one exists.

DocTemplateEntity is the root of a model representation of the scala source files we’re extracting Scaladoc from.  To get to scaladoc comments, we need to first traverse down to the class, object or trait we want to get documentation for.  We wrote up a function that does the traverse:

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

Now, if we have a classname we can get to it’s scaladoc model:

    val root = extractScaladoc(scalaSourceFiles)
    val classScaladocModel = findAtPath(root, someClass.getCanonicalName.split("\\.").toList)

And then we access the model to get it’s scaladoc comment, and do what we want with it:

    classScaladocModel.comment match {
      case Some(comment) => toDocString(comment)
      case None => null
    }

In our case, we want to convert the comment to a string.  So we extract the body field (there are also fields for the other scaladoc parts such as the @author tags, @see tags and so forth).

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

Given a class like:

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

Here’s the output:

    classDoc: <p>Fortunes Resource.</p>
    methodDoc: <p>Get method.</p>
    paramDoc: <p>provides a string</p>
    paramDoc: <p>provides a <b>boolean</b></p>  
