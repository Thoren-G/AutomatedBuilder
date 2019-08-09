package de.EliteKuchen.AutoBuilder.Parsing
import java.io.File

import de.EliteKuchen.AutoBuilder.Data.ExecutionResult
import de.EliteKuchen.AutoBuilder.Errors._
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/**
  * Implementation of Parser for handling Ant-based Builderrors.
  */
class AntParser extends Parser {
  def log: Logger = LoggerFactory.getLogger(classOf[AntParser])

  /**
    * regex for missing packages:
    * group 1: java class containing the error
    * group 2: line in class
    * group 3: missing package as FQN
    */
  val missingPackagesRegex: Regex = "([0-9a-zA-Z\\/_\\.\\-]+\\.java):([0-9]+): error: package ([0-9a-zA-Z\\._\\-]+) does not exist".r

  /**
    * regex for encoding error:
    * group 1: java class containing the error
    * group 2: line in class
    * group 3: used encoding
    */
  val wrongEncodingRegex: Regex = "([0-9a-zA-Z\\\\\\/_\\.\\-]+\\.java):([0-9]+): error: unmappable character(?: \\([0-9a-zA-Z]+\\))? for encoding ([0-9a-zA-Z\\-\\._]+)".r

  /**
    * regex for missing dependencies in ivy
    * group 1: groupID of missing package
    * group 2: artifcatId of missing package
    * group 3: version
    */
  val ivyDependenciesRegex: Regex = "\\[ivy:resolve\\][ \\t]+:: ([0-9a-zA-Z.\\-_]+)#([0-9a-zA-Z\\.\\-_]+);([0-9a-zA-Z\\.\\-_]+): not found".r

  /**
    * Processes a given String into a list of IdentifiedErrors using the regular expressions defined above
    *
    * @param result the result of an execution
    * @return a list of successfully parsed Error. May be empty.
    */
  override def parse(result: ExecutionResult): List[IdentifiedError] = {
    log.info("start parsing using AntParser...")

    val errorList: ListBuffer[IdentifiedError] = new ListBuffer

    //check for missing packages

    //use additional list to skip multiple packages.
    val missingPackages: ListBuffer[String] = new ListBuffer

    for (patternMatch <- missingPackagesRegex.findAllMatchIn(result.output)) {
      val missingPackageFQN = patternMatch.group(3)

      //check whether same/higher package already in list
      if(missingPackages.exists(fqnItem => missingPackageFQN.startsWith(fqnItem))) {
        //skip
      } else {
        //check if this package is a lower level package of smth in the list
        val tmpMissingPackages: List[String] = missingPackages.toList
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


    //check for encoding error
    //assuming the whole project uses the same encoding, only the first match is needed
    val encodingMatch = wrongEncodingRegex.findFirstMatchIn(result.output)
    if(encodingMatch.isDefined) {
      log.debug("detected wrong encoding")
      errorList.append(EncodingError(new File(encodingMatch.get.group(1))))
    }


    //check for ivy dependency errors
    for (patternMatch <- ivyDependenciesRegex.findAllMatchIn(result.output)) {
      log.debug("detected ivy error for " + patternMatch.group(1))
      errorList.append(DependencyError(patternMatch.group(1), patternMatch.group(2), patternMatch.group(3)))
    }

    log.info("detected a total of " + errorList.length + " errors!")

    return errorList.toList
  }
}
