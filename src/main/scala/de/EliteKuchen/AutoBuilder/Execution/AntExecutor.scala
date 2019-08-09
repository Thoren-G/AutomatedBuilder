package de.EliteKuchen.AutoBuilder.Execution
import java.io.{ByteArrayOutputStream, PrintStream}

import de.EliteKuchen.AutoBuilder.Data.{BuildSettings, ExecutionResult, InstructionData}
import org.apache.tools.ant._
import org.slf4j.{Logger, LoggerFactory}

/**
  * Will execute an instruction using Ant API
  */
class AntExecutor(buildSettings: BuildSettings) extends Executor(buildSettings) {
  def log: Logger = LoggerFactory.getLogger(classOf[AntExecutor])

  /**
    * Simple implementation of BuildListener that will
    * save all messages with highest priority
    */
  class AntLogger extends BuildListener {
    val buffer: StringBuilder = new StringBuilder

    override def buildStarted(event: BuildEvent): Unit = {}

    override def buildFinished(event: BuildEvent): Unit = {}

    override def targetStarted(event: BuildEvent): Unit = {}

    override def targetFinished(event: BuildEvent): Unit = {}

    override def taskStarted(event: BuildEvent): Unit = {}

    override def taskFinished(event: BuildEvent): Unit = {}

    override def messageLogged(event: BuildEvent): Unit = {
      //we only care about high priority messages
      if(event.getPriority <= 1)
        buffer.append("(Priority " + event.getPriority + " : " + event.getMessage + "\n")
    }

    override def toString: String = buffer.toString()
  }

  /**
    * Executes the given instruction using Ant API
    *
    * Should not be called in multi-threaded application, since
    * System.err will temporary be modified. Use BasicExecutor instead
    *
    * @param instruction instruction to execute
    * @return ExecutionResult containing the used instruction, result of execution as either 0 (success) or -1 (fail) and its output
    *         or null on unexpected behaviour
    */
  override def execute(instruction: InstructionData): ExecutionResult = {
    log.info("executing instruction " + instruction)

    //set up org.apache.tools.ant.project
    val project: Project = new Project
    try {
      project.init()
      project.setBaseDir(instruction.execDir)
      ProjectHelper.configureProject(project, instruction.buildFile)
    } catch {
      case e:Exception =>
        log.error("unexpected behaviour on project initialization " + e.getMessage)
        return null
    }

    //For any reason sometimes the output is written to System.err instead of using the AntLogger.
    //So, we have to get both outputs and concatenate them

    val buildLogger = new AntLogger
    project.addBuildListener( buildLogger )

    val oldSysErr = System.err
    val errMessage = new StringBuilder
    var execResult = 0 //0: success
    try {
      val sysErrBuffer = new ByteArrayOutputStream()
      val sysErrBufferStream = new PrintStream(sysErrBuffer)
      System.setErr(sysErrBufferStream)
      try {
        //execute default target
        project.executeTarget(project.getDefaultTarget)
      } catch {
        case _: BuildException =>
          execResult = -1 //failed
      }

      System.setErr(oldSysErr)
      sysErrBufferStream.close()

      //add output of System.err
      errMessage.append(sysErrBuffer.toString("utf-8"))

      //add output of buildLogger (Listener)
      errMessage.append(buildLogger.toString)

    } finally {
      System.setErr(oldSysErr)
    }

    return ExecutionResult(instruction, execResult, errMessage.toString())
  }
}
