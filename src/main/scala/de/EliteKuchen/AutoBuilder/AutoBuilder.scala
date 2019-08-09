package de.EliteKuchen.AutoBuilder

import java.io.{BufferedWriter, File, FileWriter, IOException}
import java.nio.file.{Files, Paths}
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeoutException

import de.EliteKuchen.AutoBuilder.Data.{BuildData, BuildSettings, BuildTool, ExecutionResult, FixerFlags}
import de.EliteKuchen.AutoBuilder.Execution.{AntExecutor, BasicExecutor, Executor, GradleExecutor}
import de.EliteKuchen.AutoBuilder.Fixer.{AntFixer, Fixer, GradleFixer, MavenFixer}
import de.EliteKuchen.AutoBuilder.Parsing._
import de.EliteKuchen.AutoBuilder.Preparation.BuildDataPreparator
import de.EliteKuchen.AutoBuilder.Utils.{FileHelper, TimeoutHelper}
import org.slf4j.{Logger, LoggerFactory}

import scala.io.{BufferedSource, Source}
import scala.sys.process.{Process, ProcessLogger}

/**
  * This class is responsible for the communication and coordination of the single components
  * this projects exists of.
  */
class AutoBuilder {
  def log: Logger = LoggerFactory.getLogger(classOf[AutoBuilder])

  /**
    * pretend daemon failures but may increase build time.
    * Unix only!
    */
  def KILL_GRADLE_DAEMONS_AFTER_BUILD = true

  def IGNORED_DIRECTORIES_FOR_BYTECODE: Set[String] = Set("lib", "libs", "gradle", ".mvn")

  def SETTINGS_FILENAME = "AutoBuilder.settings"

  def GRADLE_KILLSCRIPT_FILENAME = "GradleDaemonKiller.sh"

  def NEW_BINARIES_FILENAME = "builtBinaries.txt" //set to null if no file should be generated

  def loadSettingsFile(): BuildSettings = {
    log.info("Start loading settings from " + SETTINGS_FILENAME)

    val settingsFile = new File(SETTINGS_FILENAME)

    if(!settingsFile.exists()) {
      //create empty settings file
      var filewriter: BufferedWriter = null
      try {
        filewriter = new BufferedWriter(new FileWriter(settingsFile))
        filewriter.write(
          "AndroidSDK=\"\"\n" +
          "JDK8=\"\"\n" +
          "Timeout=1200000\n" +
          "Buildtries=20"
        )
      } catch {
        case _: IOException =>
          log.error("failed to create new settingsfile.")
      } finally
        if(filewriter != null) filewriter.close()

      //load empty settings
      log.info("No Settingsfile was found. An empty one was created. Will now continue with standard settings!")
      return BuildSettings()
    }

    //load file
    val settingsRegex = "(AndroidSDK|JDK8|Timeout|Buildtries)=[\\\"]?([^\"]+)[\\\"]?".r
    var androidSdk: File = null
    var jdk8: File = null
    var timeout = 1200000 //20 min
    var maxTries = 20

    var bufferedFile: BufferedSource = null
    try {
      bufferedFile = Source.fromFile(settingsFile)
      for (line <- bufferedFile.getLines) {
        try {
          val setMatch = settingsRegex.findFirstMatchIn(line)
          if (setMatch.isDefined) {

            setMatch.get.group(1) match {
              case "AndroidSDK" =>
                val path = new File(setMatch.get.group(2))
                if(!path.exists() || !path.isDirectory) {
                  throw new IOException()
                }
                androidSdk = path
                log.debug("Android SDK set to: " + path)
              case "JDK8" =>
                val path = new File(setMatch.get.group(2))
                if(!path.exists() || !path.isDirectory) {
                  throw new IOException()
                }
                jdk8 = path
                log.debug("JDK8 path set to: " + path)
              case "Timeout" =>
                timeout = Integer.parseInt(setMatch.get.group(2))
              case "Buildtries" =>
                maxTries = Integer.parseInt(setMatch.get.group(2))
              case _ =>
                log.warn("failed to parse following line from settings file: " + line)
            }
          }
        } catch {
          case _: IOException =>
            log.warn("seems like the given path does not exist: " + line)
          case _: Exception =>
            log.warn("failed to parse following line from settings file: " + line)
        }
      }
      return BuildSettings(androidSdk, jdk8, timeout, maxTries)
    } catch {
      case _: IOException =>
        log.error("Failed to read settingsfile. Will run with standard settings!")
        return BuildSettings()
      case _: Exception =>
        log.error("Unexpected behaviour while reading settingsfile. Will run with standard settings!")
        return BuildSettings()
    } finally {
      if(bufferedFile != null)
        bufferedFile.close
    }
  }

  private def killGradleDaemons(): Unit = {
    try {
      val os = System.getProperty("os.name").toLowerCase
      if (os.contains("nix") || os.contains("aix") || os.contains("nux")) {
        val gradleKillScript = new File(GRADLE_KILLSCRIPT_FILENAME)

        if(!gradleKillScript.exists()) {
          //generate new script
          var filewriter: BufferedWriter = null
          try {
            filewriter = new BufferedWriter(new FileWriter(gradleKillScript))
            filewriter.write(
              "#!/bin/bash\n" +
                "pkill -f '.*GradleDaemon.*'"
            )
            Files.setPosixFilePermissions(Paths.get(gradleKillScript.getAbsolutePath), PosixFilePermissions.fromString("rwxr-x---"))
          } catch {
            case _: IOException =>
              log.error("failed to create new settingsfile.")
            case _: Exception =>
              log.error("unexpected behaviour while creating daemon kill script")
          } finally
            if(filewriter != null) filewriter.close()
        }

        val GRADLE_DAEMON_KILL_TIMEOUT = 60000;//1 min
        TimeoutHelper.runWithTimeout(GRADLE_DAEMON_KILL_TIMEOUT) {Process("./" + gradleKillScript).!}
        log.info("killed all gradle daemons")
      }
    } catch {
      case _: TimeoutException =>
        log.warn("timeout on killing gradle daemons.")
      case _: Exception =>
        //unexpected behaviour. keep moving on, hopefully gradle will handle it by itself
        log.warn("unexpected behaviour when trying to kill gradle daemons")
    }
  }

  /**
    * By calling this function the building process of the specified project will start.
    * For this purpose there will be executed potential build-instruction generated by the preparation component.
    * If they failed, their output will be analysed and processed by the parser and fixer components.
    *
    * @param path path to the root of the project
    * @return Data concerning to the building procedures. Containing success indicator, used instruction and their outputs
    */
  def buildProject(path: File): BuildData = {
    //check project path
    if(path == null || !path.isDirectory) {
      log.error("Can't find project!")
      return null
    }

    //load settings
    val buildSettings = loadSettingsFile()

    log.info("start building of " + path.toString)

    //Preparation
    log.trace("preparing list of instructions")

    val preparator = new BuildDataPreparator
    val buildData : BuildData = preparator.generateBuildData(path)

    if(buildData == null) {
      log.error("was not able to build project!")
      return null
    }

    //Building
    var execTry: Integer = 0 //counter for executions
    while(!buildData.instructions.isEmpty && (execTry < buildSettings.buildtries)) {
      execTry += 1

      //get the instruction with highest priority
      val instr = buildData.instructions.pop()

      //init needed executor, parser and fixer
      var executor : Executor = null
      var parser : Parser = null
      var fixer : Fixer = null

      instr.tool match {
        case BuildTool.Ant =>
          if(instr.fixerFlags.contains(FixerFlags.ForceBasicExecutor))
            executor = new BasicExecutor(buildSettings)
          else
            executor = new AntExecutor(buildSettings)
          parser = new AntParser
          fixer = new AntFixer(buildSettings)

        case BuildTool.Maven =>
          executor = new BasicExecutor(buildSettings)
          parser = new MavenParser
          fixer = new MavenFixer(buildSettings)

        case BuildTool.Gradle =>
          if(instr.fixerFlags.contains(FixerFlags.ForceBasicExecutor))
            executor = new BasicExecutor(buildSettings)
          else
            executor = new GradleExecutor(buildSettings)
          parser = new GradleParser
          fixer = new GradleFixer(buildSettings)

        case BuildTool.Others => //for generated ant buildfiles
          if(instr.fixerFlags.contains(FixerFlags.ForceBasicExecutor))
            executor = new BasicExecutor(buildSettings)
          else
            executor = new AntExecutor(buildSettings)
          parser = new AntParser
          fixer = new AntFixer(buildSettings)
      }

      //Execution (with timeout)
      var result: ExecutionResult = null
      try {
        result = TimeoutHelper.runWithTimeout(buildSettings.timeout) {executor.execute(instr)}
      } catch {
        case e: TimeoutException =>
          log.error("Timeout on execution: " + e.getMessage)
      }

      //regardless of the result, kill the gradle daemon if needed
      if(KILL_GRADLE_DAEMONS_AFTER_BUILD && instr.tool == BuildTool.Gradle) {
        killGradleDaemons()
      }

      if(result == null){
        //unexpected behaviour
        log.error("error occured during execution")
      } else {
        //save results in buildData
        buildData.results.append(result)

        if (result.endCode != 0) {
          log.info("building failed!")

          //Parsing
          val errorList = parser.parse(result)

          if (errorList == null || errorList.isEmpty) {
            log.warn("parser was not able to assign the error")
          } else {
            //Fixing
            val newInstruction = fixer.fix(instr, errorList)

            if (newInstruction != null)
              buildData.instructions.push(newInstruction)
          }
        } else {
          //get the compiled files
          val allBinaries = FileHelper.getAllFiles(buildData.projectPath, IGNORED_DIRECTORIES_FOR_BYTECODE).filter(f => f.getName.matches(".+\\.(class|jar)"))
          buildData.postBuildBinaries = allBinaries.filterNot(buildData.preBuildBinaries.toSet).toList

          if(buildData.postBuildBinaries.isEmpty) {
            log.info("technically successful build failed because nothing was compiled!")
            buildData.success = false
          } else {
            log.info("build was SUCCESSFUL!")
            buildData.success = true

            //write a list of all new binaries to a file
            if(NEW_BINARIES_FILENAME != null) {
              var filewriter: BufferedWriter = null
              try {
                val file = new File(buildData.projectPath + "/" + NEW_BINARIES_FILENAME)
                filewriter = new BufferedWriter(new FileWriter(file))
                val binaryList = new StringBuilder
                for(f <- buildData.postBuildBinaries) {
                  binaryList.append(f.getAbsolutePath + "\n")
                }
                filewriter.write(binaryList.toString())
                log.info("List of new binaries written to " + file.getAbsolutePath)
              } catch {
                case _:IOException =>
                  log.error("Exception was thrown when trying to write a list of the new binaries to file")
              }finally {
                if(filewriter != null)
                  filewriter.close()
              }
            }

            return buildData //done
          }
        }
      }

    }

    log.info("build FAILED!")

    return buildData
  }

}
