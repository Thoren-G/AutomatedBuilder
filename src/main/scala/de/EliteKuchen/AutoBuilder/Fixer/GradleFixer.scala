package de.EliteKuchen.AutoBuilder.Fixer
import java.io.{BufferedWriter, File, FileInputStream, FileNotFoundException, FileOutputStream, FileWriter, IOException}
import java.net.{HttpURLConnection, URL}
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.{PosixFilePermission, PosixFilePermissions}
import java.util
import java.util.zip.{ZipEntry, ZipInputStream}

import com.ibm.icu.text.CharsetDetector

import sys.process._
import de.EliteKuchen.AutoBuilder.Data.{BuildSettings, BuildTool, ExecutionResult, FixerFlags, InstructionData}
import de.EliteKuchen.AutoBuilder.Errors.{AndroidSdkError, DependencyError, DependencyFqnError, EncodingError, GradleVersionError, IdentifiedError, JavaVersionError, ToolingApiError}
import de.EliteKuchen.AutoBuilder.Execution.BasicExecutor
import de.EliteKuchen.AutoBuilder.Utils.{FileHelper, SimpleMavenDownloader, XMLHelper, ZipHelper}
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}

class GradleFixer(buildSettings: BuildSettings) extends Fixer(buildSettings) {
  def log: Logger = LoggerFactory.getLogger(classOf[GradleFixer])

  //url to download specific gradle version
  def gradleDistUrl = "https://services.gradle.org/distributions/gradle-"
  def gradleDistSuffix = "-bin.zip"

  def gradlePath = "AutoBuilder/gradle/"

  /**
    * will try to fix the instructionData.
    * May download libaries or edit files
    *
    * @param usedInstruction
    * @param errors
    * @return fixed instructionData or null, if no fix was applied
    */
  override def fix(usedInstruction: InstructionData, errors: List[IdentifiedError]): InstructionData = {
    log.info("start fixing using GradleFixer")

    var fixedInstruction = InstructionData(usedInstruction.execDir, usedInstruction.command,
      usedInstruction.tool, usedInstruction.buildFile, usedInstruction.fixerFlags)
    var fixApplied = false

    for(error <- errors) {
      error match {
        case ToolingApiError() =>
          log.debug("fixing api error by switching to BasicExecutor")
          fixedInstruction.fixerFlags += FixerFlags.ForceBasicExecutor
          fixApplied = true

        case GradleVersionError(requiredVersion) =>
          log.debug("fixing version error")

          //use wrapper or create one
          var wrapperScript = new File(fixedInstruction.execDir + "/gradlew")
          if (System.getProperty("os.name").toLowerCase.contains("win"))//for windows support
            wrapperScript = new File(fixedInstruction.execDir + "/gradlew.bat")
          if(wrapperScript.exists() || createWrapper(fixedInstruction, requiredVersion)) {
            //set execute permission for the script
            try {
              Files.setPosixFilePermissions(Paths.get(wrapperScript.getAbsolutePath), PosixFilePermissions.fromString("rwxr-x---"))
            } catch {
              case _: UnsupportedOperationException =>
                //not needed on windows systems
                log.trace("skipping permission edit")
              case _: Exception =>
                //unexpected behaviour. keep moving on, maybe the wrapper can be executed anyway
                log.warn("unexpected behaviour while setting permissions of gradle wrapper")
            }

            fixedInstruction.fixerFlags += FixerFlags.ForceBasicExecutor
            fixedInstruction = InstructionData(fixedInstruction.execDir, wrapperScript.getAbsolutePath,
              fixedInstruction.tool, null, fixedInstruction.fixerFlags)

            fixApplied = true
          }

        case AndroidSdkError() =>
          if(buildSettings.androidSdkPath == null) {
            log.warn("Cannot fix this project because AndroidSDK was not set!")
          } else if(!fixedInstruction.fixerFlags.contains(FixerFlags.AndroidSdkSet)){
            if(setAndroidSdk(fixedInstruction)) {
              fixApplied = true
            }
          }

        case EncodingError(file) =>
          val newInstruction = fixEncoding(fixedInstruction, file)
          if (newInstruction != null) {
            fixedInstruction = newInstruction
            fixApplied = true
            log.debug("successfully fixed" + error + ". New instruction: " + fixedInstruction)
          }

        case JavaVersionError() =>
          if(buildSettings.jdk8Path != null && !fixedInstruction.fixerFlags.contains(FixerFlags.UseJdk8)) {
            fixedInstruction.fixerFlags += FixerFlags.UseJdk8
            fixApplied = true
          }
      }
    }


    if(fixApplied) {
      log.info("applied fixes")
      fixedInstruction
    } else {
      log.info("no fixes available")
      null
    }
  }

  def setAndroidSdk(fixedInstruction: InstructionData): Boolean = {
    log.debug("fixing android sdk error")
    // create/override 'local.properties'
    var filewriter: BufferedWriter = null
    try {
      filewriter = new BufferedWriter(new FileWriter(fixedInstruction.execDir + "/local.properties"))
      filewriter.write("sdk.dir=" + buildSettings.androidSdkPath.getAbsolutePath)
      filewriter.flush()
      fixedInstruction.fixerFlags += FixerFlags.AndroidSdkSet
      return true
    } catch {
      case _: IOException =>
      log.warn("was not able to set the android sdk path. IOException for local.properties")
    } finally {
      filewriter.close()
    }
    return false
  }

  /**
    * downloads the required gradle version, create the wrapper script ("gradle wrapper")
    *
    * @param instruction
    * @param requiredVersion
    */
  def createWrapper(instruction: InstructionData, requiredVersion: String): Boolean = {

    //following uncommented code downloads the required binaries and unzip them
    //problem: no exec permission -.-"

    //download the binaries
    val url = gradleDistUrl + requiredVersion + gradleDistSuffix
    val zipFile = new File(instruction.execDir + "/" + gradlePath + "/" + requiredVersion + ".zip")
    if(!zipFile.exists()) {
      zipFile.getParentFile.mkdirs()
      log.info("started to download binaries for gradle " + requiredVersion)
      if (!FileHelper.downloadFile(url, zipFile)) {
        log.error("failed to download gradle binaries")
        return false
      }
      log.info("download succeeded")
    }

    //unzip
    ZipHelper.unzip(zipFile, zipFile.getParentFile)

    //generate wrapper using BasicExecutor
    val gradleExecutable = zipFile.getParentFile + "/gradle-" + requiredVersion + "/bin/gradle"
    val command = gradleExecutable + " wrapper"

    //set execute permission
    try {
      Files.setPosixFilePermissions(Paths.get(gradleExecutable), PosixFilePermissions.fromString("rwxr-x---"))
    } catch {
      case _: UnsupportedOperationException =>
        //not needed on windows systems
        log.trace("skipping permission edit")
    }

    //forward set of android sdk, because it's needed in most cases
    if(!new File(instruction.execDir + "/local.properties").exists())
      setAndroidSdk(instruction)

    val wrapperInstruction = InstructionData(instruction.execDir, command, BuildTool.Others, null)
    log.debug("generating wrapper...")
    val result = new BasicExecutor(buildSettings).execute(wrapperInstruction)
    if(result.endCode == 0) {
      log.debug("wrapper creation was successful")
      return true
    } else {
      log.error("could not create wrapper")
      return false
    }
  }

  /**
    * will analyse the file using ICU4L
    * @param instruction
    * @param file
    */
  def fixEncoding(instruction: InstructionData, file: File): InstructionData = {
    if(instruction.buildFile == null) {
      //may happen if we use instructions from a readme
      log.warn("was not able to apply fix because no buildfile was specified.")
      return null
    }

    if(!file.exists()) {
      log.error("encoding fix failed. File with wrong encoding was not found!")
      return null
    }

    val fileStream: FileInputStream = new FileInputStream(file)
    try {

      //detect encoding
      val byteData = IOUtils.toByteArray(fileStream)
      val encoding = new CharsetDetector().setText(byteData).detect().getName

      if(encoding == null) {
        log.warn("was not able to detect encoding")
        return null
      }

      log.info("detected encoding: " + encoding)

      //apply fix
      val fw = new FileWriter(file, true)
      try {
        fw.write("\norg.gradle.jvmargs='-Dfile.encoding=" + encoding + "'")
      } catch {
        case _: IOException =>
          log.warn("was not able to edit the buildfile.")
          return null
      }
      finally fw.close()
    } catch {
      case e: Exception =>
        log.error("failed to get encoding: " + e)
        return null
    } finally fileStream.close()

    return instruction
  }


}
