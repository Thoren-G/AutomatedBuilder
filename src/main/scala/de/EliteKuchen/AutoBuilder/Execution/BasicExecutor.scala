package de.EliteKuchen.AutoBuilder.Execution
import java.io.{BufferedWriter, File, FileWriter, IOException}
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Paths}

import de.EliteKuchen.AutoBuilder.Data.{BuildSettings, BuildTool, ExecutionResult, FixerFlags, InstructionData}
import de.EliteKuchen.AutoBuilder.Utils.TimeoutHelper
import org.slf4j.{Logger, LoggerFactory}

import scala.sys.process.{Process, ProcessLogger}

/**
  * Will just execute an instruction creating a new process
  */
class BasicExecutor(buildSettings: BuildSettings) extends Executor(buildSettings) {
  def log: Logger = LoggerFactory.getLogger(classOf[BasicExecutor])

  /**
    * Executes the instruction creating a new process.
    * Will execute the command specified by instruction.command in
    * intruction.execDir
    *
    * @param instruction instruction to execute
    * @return ExecutionResult containing the used instruction, result of execution as integer and its output
    */
  override def execute(instruction: InstructionData): ExecutionResult = {
    log.info("executing instruction " + instruction)

    try {
      var command = instruction.command

      //for jdk8 we have to write a shell script to modify the paths
      if(instruction.fixerFlags.contains(FixerFlags.UseJdk8)) {
        val os = System.getProperty("os.name").toLowerCase
        if (os.contains("nix") || os.contains("aix") || os.contains("nux")) {

          var filewriter: BufferedWriter = null
          try {
            val scriptFile = new File(instruction.execDir + "/autobuilder_kickstart.sh")
            filewriter = new BufferedWriter(new FileWriter(scriptFile))
            filewriter.write(
              "#!/bin/bash\n" +
              "export JAVA_HOME=" + buildSettings.jdk8Path + "\n" +
              instruction.command + "\n"+
              "unset JAVA_HOME\n"
            )
            command = "./" + scriptFile.getName

            Files.setPosixFilePermissions(Paths.get(scriptFile.getAbsolutePath), PosixFilePermissions.fromString("rwxr-x---"))
          } catch {
              case _: UnsupportedOperationException =>
                //not needed on windows systems
                log.trace("skipping permission edit")
              case e: IOException =>
                log.error("failed to create generated buildfile: " + e.getMessage)
                return null
          } finally
            if(filewriter != null) filewriter.close()

        } else if(os.contains("win")) {
          log.warn("Windows is not supported for JDK8 fixes")
        }
      }

      val output = new StringBuilder
      var endCode = Process(command, instruction.execDir)
        .!(ProcessLogger(line => {
          output.append(line + "\n")
        }))

      //for any reason, the gradle wrapper returns 0 if the build failed, so we need a special handling for that
      if(instruction.tool == BuildTool.Gradle && endCode == 0) {
        if(output.toString().contains("BUILD FAILED")) {
          log.trace("gradle (wrapper) marked this project as successful, but it failed!")
          endCode = -1
        }
      }

      //if maven has no sources to compile, it is marked as successful..
      if(instruction.tool == BuildTool.Maven && endCode == 0) {
        if(output.toString().contains("No sources to compile")) {
          log.info("maven project failed because there were no sources to compile")
          endCode = -1
        }
      }

      return ExecutionResult(instruction, endCode, output.toString())
    } catch {
      case _: IOException =>
        log.error("execution failed. IOException thrown (does the executed file exist?) ")
      case _: Exception =>
        log.error("execution failed. Unexpected behaviour!")
    }
    return null
  }

}
