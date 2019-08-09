package de.EliteKuchen.AutoBuilder.Execution
import de.EliteKuchen.AutoBuilder.Data.{BuildSettings, ExecutionResult, FixerFlags, InstructionData}
import org.gradle.tooling.{GradleConnectionException, GradleConnector, ProjectConnection}
import org.gradle.tooling.GradleConnectionException
import org.slf4j.{Logger, LoggerFactory}

/**
  * Will execute an instruction using Gradle Tooling API
  */
class GradleExecutor(buildSettings: BuildSettings) extends Executor(buildSettings) {
  def log: Logger = LoggerFactory.getLogger(classOf[GradleExecutor])

  /**
    * Executes the given instruction using Gradle Tooling API
    *
    * @param instruction instruction to execute
    * @return ExecutionResult containing the used instruction, result of execution as either 0 (success) or -1 (fail) and its output
    *         or null on unexpected behaviour
    */
  override def execute(instruction: InstructionData): ExecutionResult = {
    log.info("executing instruction " + instruction)

    //load project
    var execResult: Integer = 0
    var errorLog: String = new String()//empty at beginning
    var connection: ProjectConnection = null
    try {
      connection = GradleConnector.newConnector().forProjectDirectory(instruction.execDir).connect()

      val build = connection.newBuild()
      build.forTasks("build")
      build.addArguments("-x test")

      if(instruction.fixerFlags.contains(FixerFlags.UseJdk8) && buildSettings.jdk8Path != null) {
        build.setJavaHome(buildSettings.jdk8Path)
      }

      build.run()
    } catch {
      case ce: GradleConnectionException =>
        log.warn("error occured on execution: " + ce.getClass.getCanonicalName)
        errorLog = ce.getMessage
        execResult = -1 //failed
      case e: IllegalStateException =>
        log.error("unexpected behaviour. Connection seems to be closed. " + e.getClass.getCanonicalName)
        return null
    } finally {
      if(connection != null)
        connection.close()
    }

    return ExecutionResult(instruction, execResult, errorLog)
  }
}
