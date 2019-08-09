package de.EliteKuchen.AutoBuilder.Parsing
import java.io.File

import de.EliteKuchen.AutoBuilder.Data.ExecutionResult
import de.EliteKuchen.AutoBuilder.Errors.{AndroidSdkError, DependencyError, EncodingError, GradleVersionError, IdentifiedError, JavaVersionError, ToolingApiError}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.util.matching.Regex

/**
  * Implementation of Parser for handling Gradle-based Builderrors.
  */
class GradleParser extends Parser {
  def log : Logger = LoggerFactory.getLogger(classOf[GradleParser])

  /**
    * regex for version missmatch
    * group 1: required version
    * group 2: current version
    */
  val versionMismatchRegex: Regex = "[ ]+> Gradle version ([0-9.]+) is required. Current version is ([0-9.]+). If using the gradle wrapper, try editing".r

  /**
    * regex for unsupported gradle version with this api
    * group 1: required version
    * group 2: minimum supported
    */
  val versionUnsupportedRegex: Regex = "Support for builds using Gradle versions older than [0-9.]+ was removed in tooling API version [0-9.]+. You are currently using Gradle version ([0-9.]+). You should upgrade your Gradle build to use Gradle ([0-9.]+) or later".r

  /**
    * regex for tooling api fail
    * group 2: URL of used gradle distribution
    */
  val apiFailRegex: Regex = "(Could not create an instance of Tooling API implementation using the specified|Could not execute build using) Gradle distribution '([0-9a-zA-Z:\\/.\\-\\_]+)'.".r

  /**
    * regex for missing android sdk
    */
  val androidSDKRegex: Regex = "SDK location not found\\. Define location with".r

  /**
    * regex for encoding error:
    * group 1: java class containing the error
    * group 2: line in class
    * group 3: used encoding
    */
  val wrongEncodingRegex: Regex = "([0-9a-zA-Z\\\\\\/_.\\-]+.java):([0-9]+): error: unmappable character(?: \\([0-9a-zA-Z]+\\))? for encoding ([0-9a-zA-Z\\-._]+)".r

  /**
    * regex for wrong java version
    */
  val wrongJDKRegex: Regex = "Could not determine java version from '[a-zA-Z0-9\\.]+'".r

  /**
    * Processes a given String into a list of IdentifiedErrors using the regular expressions defined above
    *
    * @param result the result of an execution
    * @return a list of successfully parsed Error. May be empty.
    */
  override def parse(result: ExecutionResult): List[IdentifiedError] = {
    log.info("start parsing using GradleParser...")

    val errorList: ListBuffer[IdentifiedError] = new ListBuffer

    //check for unsupported gradle version (only appear 1 time)
    val unsupportedVersionMatch = versionUnsupportedRegex.findFirstMatchIn(result.output)
    if(unsupportedVersionMatch.isDefined) {
      log.debug("detected unsupported gradle version")
      errorList.append(GradleVersionError(unsupportedVersionMatch.get.group(1)))
    }
    val versionMismatchMatch = versionMismatchRegex.findFirstMatchIn(result.output)
    if(versionMismatchMatch.isDefined) {
      log.debug("detected error caused by version mismatch")
      errorList.append(GradleVersionError(versionMismatchMatch.get.group(1)))
    }

    //check for tooling api failure (only appear 1 time)
    val apiFailMatch = apiFailRegex.findFirstMatchIn(result.output)
    if(apiFailMatch.isDefined) {
      log.debug("detected a failure caused by gradle tooling api")
      errorList.append(new ToolingApiError)
    }

    //check for missing androidSDK (only appear 1 time)
    val androidSDKMatch = androidSDKRegex.findFirstMatchIn(result.output)
    if(androidSDKMatch.isDefined) {
      log.debug("detected missing Android SDK")
      errorList.append(new AndroidSdkError)
    }

    //check for encoding error
    //assuming the whole project uses the same encoding, only the first match is needed
    val encodingMatch = wrongEncodingRegex.findFirstMatchIn(result.output)
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
