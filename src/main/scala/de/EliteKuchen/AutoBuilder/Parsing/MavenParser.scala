package de.EliteKuchen.AutoBuilder.Parsing
import java.io.File

import de.EliteKuchen.AutoBuilder.Data.ExecutionResult
import de.EliteKuchen.AutoBuilder.Errors.{DependencyError, DependencyFqnError, EncodingError, IdentifiedError, JavaVersionError}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/**
  * Implementation of Parser for handling Maven-based Builderrors.
  */
class MavenParser extends Parser {
  def log : Logger = LoggerFactory.getLogger(classOf[MavenParser])

  /**
    * regex for resolve errors
    * group 2: package as FQN
    * group 3: package name
    * group 4: version
  */
  //val resolveErrorRegex = "\\[ERROR\\][ ]+(Failed to execute goal|Non-resolvable parent POM for) ([0-9a-zA-Z.\\-_]+):([0-9a-zA-Z.\\-_]+):([0-9a-zA-Z.\\-_\\[\\]]+):[0-9a-zA-Z\\-\\.-;,\\[\\]\\(\\)' @<>_]*".r

  /**
    * regex for missing packages
    * group 1: package as FQN
    */
  val packageNotExistRegex: Regex = "\\[ERROR\\][0-9a-zA-Z\\-\\.-;,\\[\\]\\(\\)' @<>_]*package ([0-9a-zA-Z\\-._]+) does not exist".r

  /**
    * regex for resolve errors
    * group 1: groupID
    * group 2: artifactID
    * group 3: version
    */
  val resolveErrorRegex: Regex = "(?:Could not find( artifact)?|The following artifacts could not be resolved:) ([0-9a-zA-Z.\\-_]+):([0-9a-zA-Z.\\-_]+):(?:jar:)?([0-9a-zA-Z.\\-_\\[\\]]+)".r

  /**
    * regex for wrong encoding
    * group 1: java class containing the error
    * group 2: line in class
    * group 3: used encoding
    */
  val encodingRegex: Regex = "([0-9a-zA-Z\\\\\\/_.\\-]+.java):\\[([0-9, ]+)\\] unmappable character(?: \\([0-9a-zA-Z]+\\))? for encoding ([0-9a-zA-Z\\-._]+)".r

  /**
    * regex for wrong java version
    */
  val wrongJDKRegex: Regex = "(?:[Ss]ource (?:value|option) [0-9]+ is (?:no longer supported|obsolete and will be removed)|Unable to find package ([0-9a-zA-Z.\\-_]+)+ in classpath or bootclasspath)".r


  /**
    * regex for toolchain errors (5689!)
    * group 1: groupID
    * group 2: artifactID
    * group 3: version
    * group 4: target
    * group 5: project name
    * group 6: vendor of toolchain
    * group 7: needed version of toolchain
    */
  //val toolchainMismatch = "\\[ERROR\\] Failed to execute goal ([0-9a-zA-Z.\\-_]+):([0-9a-zA-Z.\\-_]+):([0-9a-zA-Z.\\-_\\[\\]]+):toolchain \\(([0-9a-zA-Z.\\-_]+)\\) on project ([0-9a-zA-Z.\\-_]+): Cannot find matching toolchain definitions for the following toolchain types:\\n\\[ERROR] jdk \\[ vendor='([0-9a-zA-Z.\\-_]+)' version='([0-9a-zA-Z.\\-_]+)' \\]".r
  //(un)fixable, needs different java version

  /**
    * Processes a given String into a list of IdentifiedErrors using the regular expressions defined above
    *
    * @param result the result of an execution
    * @return a list of successfully parsed Error. May be empty.
    */
  override def parse(result: ExecutionResult): List[IdentifiedError] = {
    log.info("start parsing using MavenParser...")

    val errorList: ListBuffer[IdentifiedError] = new ListBuffer

    //check for missing packages

    //first kind of dependency error occurs if it was not registered in the buildfile
    //(DependecyFqnError)

    //use additional list to skip multiple packages.
    val missingPackages: ListBuffer[String] = new ListBuffer
    for (patternMatch <- packageNotExistRegex.findAllMatchIn(result.output)) {
      val missingPackageFQN = patternMatch.group(1)

      //check whether same/higher package already in list
      if(missingPackages.exists(fqnItem => missingPackageFQN.startsWith(fqnItem))) {
        //skip
      } else {
        //check if this package is a lower level package of smth in the list
        val tmpMissingPackages = missingPackages.toList
        tmpMissingPackages.foreach(fqnItem =>
          if(fqnItem.startsWith(missingPackageFQN)){
            //if so, remove it
            missingPackages -= fqnItem
          }
        )
        //add missingPackageFQN
        missingPackages.append(missingPackageFQN)
      }
    }
    missingPackages.foreach(fqn => errorList.append(DependencyFqnError(fqn)))

    //second kind of dependency error on resolve error (DependencyError)
    for(patternMatch <- resolveErrorRegex.findAllMatchIn(result.output)) {
      errorList.append(DependencyError(patternMatch.group(1), patternMatch.group(2), patternMatch.group(3)))
    }

    //check for encoding error
    //assuming the whole project uses the same encoding, only the first match is needed
    val encodingMatch = encodingRegex.findFirstMatchIn(result.output)
    if(encodingMatch.isDefined) {
      log.debug("detected wrong encoding")
      errorList.append(EncodingError(new File(encodingMatch.get.group(1))))
    }

    //check for java version error
    //assuming the whole project uses the same jdk, only the first match is needed
    val wrongJDKMatch = wrongJDKRegex.findFirstMatchIn(result.output)
    if(wrongJDKMatch.isDefined) {
      log.debug("detected wrong java version")
      errorList.append(JavaVersionError())
    }

    log.info("detected a total of " + errorList.length + " errors!")

    return errorList.toList
  }
}
