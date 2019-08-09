package de.EliteKuchen.AutoBuilder.Fixer
import java.io.{File, FileInputStream, IOException}

import com.ibm.icu.text.CharsetDetector
import de.EliteKuchen.AutoBuilder.Data.{BuildSettings, FixerFlags, InstructionData}
import de.EliteKuchen.AutoBuilder.Errors.{DependencyError, DependencyFqnError, EncodingError, IdentifiedError, JavaVersionError}
import de.EliteKuchen.AutoBuilder.Utils.{MavenCentralHelper, XMLHelper}
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}
import org.xml.sax.SAXParseException
import play.api.libs.json.JsResultException

import scala.xml.SAXException

class MavenFixer(buildSettings: BuildSettings) extends Fixer(buildSettings) {
  def log: Logger = LoggerFactory.getLogger(classOf[MavenFixer])

  /**
    * will try to fix the instructionData.
    * May download libaries or edit files
    *
    * @param usedInstruction
    * @param errors
    * @return fixed instructionData or null, if no fix was applied
    */
  override def fix(usedInstruction: InstructionData, errors: List[IdentifiedError]): InstructionData = {
    log.info("start fixing using MavenFixer")

    var fixedInstruction = InstructionData(usedInstruction.execDir, usedInstruction.command,
      usedInstruction.tool, usedInstruction.buildFile, usedInstruction.fixerFlags)
    var fixApplied = false
    var setDependencyFlag = false

    for(error <- errors) {
      error match {
        case DependencyFqnError(fqn) =>
          //apply this fix only one time
          if(fixedInstruction.fixerFlags.contains(FixerFlags.FirstDepFix)) {
            log.debug("can not apply fix since FirstDepFix flag was already set")
          } else {
            setDependencyFlag = true
            //get groupID, artifactID & version and add it to the buildfile
            val newInstruction = fixDependencyByFQN(fixedInstruction, fqn)
            if (newInstruction != null) {
              fixedInstruction = newInstruction
              fixApplied = true
            }
          }
        case DependencyError(groupID, artifactID, version) =>
          //remove it from the buildfile
          if(fixedInstruction.buildFile == null) {
            //may happen if we use instructions from a readme
            log.warn("was not able to apply fix because no buildfile was specified.")
          } else {
            var fileUpdated = false

            try {
              fileUpdated = XMLHelper.removeDependencyFromMavenBuildfile(fixedInstruction.buildFile, groupID, artifactID, version)
            } catch {
              case _: IOException =>
                log.warn("IOException while removing dependency from buildfile")
              case _: SAXParseException =>
                log.warn("SAXException while removing dependency from buildfile")
            }

            if (!fileUpdated) {
              log.warn("was not able to remove the dependency to the buildfile.")
            } else {
              fixApplied = true
            }
          }

        case EncodingError(file) =>
          val newInstruction = fixEncoding(fixedInstruction, file)
          if(newInstruction != null) {
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

    if(setDependencyFlag) {
      fixedInstruction.fixerFlags += FixerFlags.FirstDepFix
    }


    if(fixApplied) {
      log.info("applied fixes")
      fixedInstruction
    } else {
      log.info("no fixes available")
      null
    }
  }

  /**
    * will resolve groupID, artifactID, version of dependency and add them in the buildfile
    *
    * @param fixedInstruction
    * @param fqn
    * @return
    */
  def fixDependencyByFQN(fixedInstruction: InstructionData, fqn: String): InstructionData = {
    if(fixedInstruction.buildFile == null) {
      //may happen if we use instructions from a readme
      log.warn("was not able to apply fix because no buildfile was specified.")
      return null
    }


    //get artifact data for given fqn
    try {
      val artifactData = MavenCentralHelper.getArtifactDataByFQN(fqn)

      if (artifactData == null) {
        log.warn("fix failed")
        return null
      }

      val fileUpdated = XMLHelper.addDependencyToMavenBuildfile(fixedInstruction.buildFile, artifactData(0), artifactData(1), artifactData(2))

      if(!fileUpdated) {
        log.warn("was not able to add the dependency to the buildfile.")
        return null
      }
    } catch {
      case _: IOException =>
        log.error("failed to download libraries")
        return null
      case _: JsResultException =>
        log.error("internal maven api error")
        return null
      case _: SAXException =>
        log.error("XML parser failed")
        return null
    }
    return fixedInstruction
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
      val fileUpdated: Boolean = XMLHelper.setEncodingOfMavenBuildfile(instruction.buildFile, encoding)

      if(!fileUpdated) {
        log.warn("was not able to fix the buildfile.")
        return null
      }
    } catch {
      case e: Exception =>
        log.error("failed to get encoding: " + e)
        return null
    } finally fileStream.close()

    return instruction
  }
}
