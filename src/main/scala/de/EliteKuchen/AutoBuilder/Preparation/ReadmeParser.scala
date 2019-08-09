package de.EliteKuchen.AutoBuilder.Preparation

import java.io.File
import java.nio.charset.MalformedInputException

import de.EliteKuchen.AutoBuilder.Data.BuildTool.BuildTool
import de.EliteKuchen.AutoBuilder.Data.{BuildTool, FixerFlags, InstructionData}
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer
import scala.io.Source._
import scala.util.matching.Regex

class ReadmeParser {
  def log: Logger = LoggerFactory.getLogger(classOf[ReadmeParser])

  def MAX_README_FILESIZE: Int = 5 * 1024 * 1024; //5mb

  /**
    * regex for ant build commands, delimited by newline
    * group 1: command
    * group 2: buildtool (ant, mvn, gradle, gradlew)
    */
  val instructionRegex: Regex = "(?:[ \\n])(?:\\\"\\`\\'\\$)?((ant|mvn|gradle|\\.\\/gradlew) [0-9a-zA-Z\\.\\:\\*\\,\\_\\-\\='!<> ]+)(?:\\n|(?!\\\"\\`\\'))[.]?".r

  /**
    * lists of filtered commands
    */
  val badInstructionList : List[String] = List("exec:", "test")
  val badInstructionEndList: List[String] = List("clean", "eclipse", "idea", "netbeans", "javadoc", "tasks")


  def parseInstructions(file: File): List[InstructionData] = {

    val instructionList = new ListBuffer[InstructionData]

    var fileContent: String = null
    try {
      //check file size before reading
      if(file.length() >= MAX_README_FILESIZE) {
        log.info("skipping to big readme file")
      }

      val buffer = fromFile(file)
      fileContent = buffer.mkString
      buffer.close()
    } catch {
      case _: MalformedInputException =>
        log.warn("exception thrown while parsing ReadMe-file. Bad Encoding?")
        return instructionList.toList
    }

    if(fileContent == null) {
      log.warn("ReadMe Parsing failed!")
      return instructionList.toList
    }

    for(patternMatch <- instructionRegex.findAllMatchIn(fileContent)) {
      var command = patternMatch.group(1).trim

      //remove trailing dots from the command
      if(command.endsWith(".")) {
        command = command.substring(0, command.length - 1)
      }

      var badInstruction = false

      //skip matches that are too big
      if(command.length >= 100)
        badInstruction = true

      //instructions containing one of badInstructionList will be skipped
      badInstructionList.foreach(f =>
        if(command.contains(f))
          badInstruction = true)

      //same here
      badInstructionEndList.foreach(f =>
        if(command.endsWith(f))
          badInstruction = true)

      if(!badInstruction) {
        var buildTool: BuildTool = BuildTool.Others
        patternMatch.group(2) match {
          case "ant" => buildTool = BuildTool.Ant
          case "mvn" =>
            buildTool = BuildTool.Maven
            command += " -B -DskipTests" //"-B" to disable color encoding (batch-mode)
          case "gradle" | "./gradlew" =>
            buildTool = BuildTool.Gradle
            command += " -x test"
          case _ => //nop
        }

        val foundInstruction = InstructionData(file.getParentFile, command, buildTool, null)
        foundInstruction.fixerFlags += FixerFlags.ForceBasicExecutor
        instructionList.append(foundInstruction)

        log.debug("found instruction in readme: " + foundInstruction)
      }
    }

    instructionList.toList
  }

}
