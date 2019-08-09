package de.EliteKuchen.AutoBuilder.Utils

import java.io.{BufferedWriter, File, FileWriter, IOException}

import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.xml
import scala.xml.transform.{RewriteRule, RuleTransformer}
import scala.xml.{Attribute, Elem, Node, NodeSeq, Null, SAXException, XML}

/**
  * Helperclass for scala.xml
  */
object XMLHelper {
  def log: Logger = LoggerFactory.getLogger("XMLHelper")

  /**
    * will add the given path to a .jar to the given ant buildfile.
    * ! changes the buildFiles content !
    *
    * To do so, this function will add the element "<pathelement location=PATH_TO_JAR/>"
    * to all "classpath" elements found in the given buildfile and its includes
    *
    * @throws org.xml.sax.SAXParseException
    */
  def addLibraryToAntBuildfile(buildFile: File, pathToJar: File) : Boolean = {
    //the following rule will add a pathelement to all classpath-elements
    val addLibraryRule = new RewriteRule {
      override def transform(n: Node) = n match {
        case e: Elem if e.label == "classpath" =>
          val oldPathElements = e \ "_"
          val newPathElemnt = <pathelement location={pathToJar.getAbsolutePath}/>
          new Elem(e.prefix, "classpath", e.attributes, e.scope, e.minimizeEmpty, (oldPathElements ++ newPathElemnt).toSeq:_*)
        case x => x
      }
    }
    val rule = new RuleTransformer(addLibraryRule)

    executeRuleTransformer(buildFile, rule)
  }

  /**
    * will set the encoding attribute of all javac-elements in the buildFile and its imported files
    * ! changes the buildFiles content !
    *
    * @param buildFile
    * @param encoding
    * @throws IOException
    * @throws SAXException
    * @return whether the files has changed or not
    */
  def setEncodingOfAntBuildfile(buildFile: File, encoding: String): Boolean = {
    val setAttributeRule = new RewriteRule {
      override def transform(n: Node) = n match {
        case e if e.label == "javac" =>
          e.asInstanceOf[Elem] %
            Attribute(null, "encoding", encoding, Null)
        case _ => n
      }
    }
    val rule = new RuleTransformer(setAttributeRule)

    executeRuleTransformer(buildFile, rule)
  }

  /**
    * will set the encoding attribute of all javac-elements in the buildFile and its imported files
    * ! changes the buildFiles content !
    *
    * @param buildFile
    * @param encoding
    * @throws IOException
    * @throws SAXException
    * @return whether the files has changed or not
    */
  def setEncodingOfMavenBuildfile(buildFile: File, encoding: String): Boolean = {
    val setAttributeRule = new RewriteRule {
      override def transform(n: Node) = n match {
        case e: Elem if e.label == "properties" =>
          val oldPathElements = e \ "_"
          val newPathElemnt =
            <project.build.sourceEncoding>{encoding}</project.build.sourceEncoding>
            <project.reporting.outputEncoding>{encoding}</project.reporting.outputEncoding>
          new Elem(e.prefix, "properties", e.attributes, e.scope, e.minimizeEmpty, (oldPathElements ++ newPathElemnt).toSeq:_*)
        case x => x
      }
    }
    val rule = new RuleTransformer(setAttributeRule)

    executeRuleTransformer(buildFile, rule)
  }

  /**
    * will execute a rule for the given buildfile and all its imported files
    *
    *
    * @param buildFile
    * @param rule
    * @throws IOException
    * @throws SAXException
    * @return whether the files has changed or not
    */
  def executeRuleTransformer(buildFile: File, rule: RuleTransformer) : Boolean = {
    if(!buildFile.exists()) {
      log.error("Failed to execute rule: file not found!")
      return false
    }

    var hasChanged = false

    //get a list of the all xml files (inclusive imported ones)
    val rootXmlFile = XML.loadFile(buildFile)

    val allXmlFiles: ListBuffer[File] = new ListBuffer
    allXmlFiles += buildFile
    (rootXmlFile \ "import").foreach(file => allXmlFiles += new File( buildFile.getParentFile + "/" + (file \ "@file").text ) )

    for(file <- allXmlFiles) {
      val xmlFile = XML.loadFile(file)

      val result = rule.transform(xmlFile)

      //check whether something has changed
      if(xmlFile.toString != result(0).toString()) {
        log.debug("editing buildfile... : " + file)
        hasChanged = true

        val filewriter = new BufferedWriter(new FileWriter(file))
        filewriter.write(result(0).toString())
        filewriter.close()
      } else {
        log.trace("buildfile " + file + " has not changed!")
      }
    }

    return hasChanged
  }

  /**
    * will try to remove the specific dependency from the given maven buildfile
    *
    * @param buildFile
    * @param groupID
    * @param artifactID
    * @param version
    * @throws IOException
    * @throws SAXException
    * @return
    */
  def removeDependencyFromMavenBuildfile(buildFile: File, groupID: String, artifactID: String, version: String): Boolean = {
    val setAttributeRule = new RewriteRule {
      override def transform(n: Node) = n match {
        case e if e.label == "dependency" &&
          (e \\ "groupId").text == groupID &&
          (e \\ "artifactId").text == artifactID &&
          (e \\ "version").text == version =>
          NodeSeq.Empty
        case _ => n
      }
    }

    val rule = new RuleTransformer(setAttributeRule)
    executeRuleTransformer(buildFile, rule)
  }


  /**
    * will add the specific dependency to the given buildfile
    *
    * @param buildFile
    * @param groupID
    * @param artifactID
    * @param version
    * @throws IOException
    * @throws SAXException
    * @return whether the buildfile has changed or not
    */
  def addDependencyToMavenBuildfile(buildFile: File, groupID: String, artifactID: String, version: String): Boolean = {
    //check if a dependencie section already exists
    val xmlFile = XML.loadFile(buildFile)

    val newDependencyElement =
    <dependency>
      <groupId>{groupID}</groupId>
      <artifactId>{artifactID}</artifactId>
      <version>{version}</version>
      <type>jar</type>
    </dependency>

    if( (xmlFile \\ "project" \\ "dependencies").nonEmpty ) {
      //just add the new dependency element in the dependencies section
      val addDependencyRule = new RewriteRule {
        override def transform(n: Node) = n match {
          case e: Elem if e.label == "dependencies" =>
            val oldDependencyElements = e \ "_"
            new Elem(e.prefix, "dependencies", e.attributes, e.scope, e.minimizeEmpty, (oldDependencyElements ++ newDependencyElement).toSeq:_*)
          case x => x
        }
      }
      val rule = new RuleTransformer(addDependencyRule)

      executeRuleTransformer(buildFile, rule)
    } else {
      //add a new dependencies element containing the new dependency element
      val addDependencyRule = new RewriteRule {
        override def transform(n: Node) = n match {
          case e: Elem if e.label == "project" =>
            val oldProjectElements = e \ "_"
            val newDependenciesSection = <dependencies>{newDependencyElement}</dependencies>
            new Elem(e.prefix, "project", e.attributes, e.scope, e.minimizeEmpty, (oldProjectElements ++ newDependenciesSection).toSeq:_*)
          case x => x
        }
      }
      val rule = new RuleTransformer(addDependencyRule)

      executeRuleTransformer(buildFile, rule)
    }
  }

}
