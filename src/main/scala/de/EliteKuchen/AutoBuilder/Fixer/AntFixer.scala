package de.EliteKuchen.AutoBuilder.Fixer
import java.io.{File, FileInputStream, IOException, InputStreamReader}

import com.ibm.icu.text.CharsetDetector
import de.EliteKuchen.AutoBuilder.Data.{BuildSettings, BuildTool, FixerFlags, InstructionData}
import de.EliteKuchen.AutoBuilder.Errors._
import de.EliteKuchen.AutoBuilder.Utils.{FileHelper, MavenCentralHelper, SimpleMavenDownloader, XMLHelper}
import de.EliteKuchen.AutoBuilder.Preparation.BuildXmlGenerator
import org.apache.commons.io.IOUtils
import org.slf4j.{Logger, LoggerFactory}

import scala.xml.SAXParseException

class AntFixer(buildSettings: BuildSettings) extends Fixer(buildSettings) {
  def log: Logger = LoggerFactory.getLogger(classOf[AntFixer])

  //let the fixer drop all remaining dependency errors.
  //(since a new Fixer is created for each attempt, this variable does not have to be resetted)
  var dropDependencyErrors = false

  /**
    * will try to fix the instructionData.
    * May download libaries or edit files
    *
    * @param usedInstruction
    * @param errors
    * @return fixed instructionData or null, if no fix was applied
    */
  override def fix(usedInstruction: InstructionData, errors: List[IdentifiedError]): InstructionData = {
    log.info("start fixing using AntFixer")

    var fixedInstruction = InstructionData(usedInstruction.execDir, usedInstruction.command,
      usedInstruction.tool, usedInstruction.buildFile, usedInstruction.fixerFlags)
    var fixApplied = false

    for(error <- errors) {
      error match {
        case DependencyFqnError(packageFQN) =>
          if(dropDependencyErrors) {
            log.trace("dropping dependency error: " + packageFQN)
          } else {
            log.debug("fixing package: " + packageFQN)

            var newInstruction: InstructionData = null

            //on the very first dependency error we just add all *.jar's we can find in the project
            //(not needed for generated buildfiles!)
            if (!fixedInstruction.fixerFlags.contains(FixerFlags.FirstDepFix) &&
              fixedInstruction.tool != BuildTool.Others)
              newInstruction = fixFirstDependency(fixedInstruction)

            //if it is not the first attempt or the other fix failed, give fixDependency(..) a try
            if (newInstruction == null && !fixedInstruction.fixerFlags.contains(FixerFlags.SecondDepFix)) {
              newInstruction = fixDependencyByFQN(fixedInstruction, packageFQN)
            }

            if (newInstruction != null) {
              fixedInstruction = newInstruction
              fixApplied = true
              log.debug("successfully fixed" + error + ". New instruction: " + fixedInstruction)
            }
          }
        case EncodingError(file) =>
          val newInstruction = fixEncoding(fixedInstruction, file)
          if (newInstruction != null) {
            fixedInstruction = newInstruction
            fixApplied = true
            log.debug("successfully fixed" + error + ". New instruction: " + fixedInstruction)
          }

        case DependencyError(groupID, artifactId, version) =>
          log.debug("fixing ivy error: " + groupID + " : " + artifactId + " - " + version)

          val newInstruction = fixDependency(fixedInstruction, groupID, artifactId, version)
          if (newInstruction != null) {
            fixedInstruction = newInstruction
            fixApplied = true
            log.debug("successfully fixed" + error + ". New instruction: " + fixedInstruction)
          }
        case _ =>
          log.warn("dropped error: " + error)
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

  /**
    * will add all jar files found in the project to the buildfile and set the FirstDepFix flag
    *
    * @param fixedInstruction
    * @return the new instruction or null, if no *.jar file found
    */
  def fixFirstDependency(fixedInstruction: InstructionData): InstructionData = {
    log.debug("trying to fix dependencies using existent jar files")

    fixedInstruction.fixerFlags += FixerFlags.FirstDepFix

    //get all *.jar available
    val dependencies = FileHelper.getAllFiles(fixedInstruction.execDir).toList.filter(_.getName.endsWith(".jar"))

    if (dependencies.nonEmpty) {
      //tell the fixer to drop all remaining DependencyErrors from errors
      dropDependencyErrors = true

      //add the files to the buildfile
      for (f <- dependencies) {
        if(fixedInstruction.buildFile == null) {
          //may happen if we use instructions from a readme
          log.warn("was not able to apply fix because no buildfile was specified.")
        } else {
          val fileUpdated: Boolean = XMLHelper.addLibraryToAntBuildfile(fixedInstruction.buildFile, f)

          if (!fileUpdated) {
            log.warn("was not able to fix the buildfile. Switching to generated buildfile...")
            val generatedBuildfile = new BuildXmlGenerator().GenerateBuildfile(new File("build_autobuild.xml"))
            if(generatedBuildfile == null)
              return null
            else
              return InstructionData(fixedInstruction.execDir, "ant -buildfile " + generatedBuildfile.getAbsolutePath, BuildTool.Others, generatedBuildfile)
          }
        }
      }
    } else {
      log.debug("no jar file was found!")
      return null
    }

    return fixedInstruction
  }

  /**
    * will search and download the missing package using its fqn
    *
    * sets the SecondDepFix flag in the instruction to pretend a loop.
    *
    * @param instruction
    * @param fqn
    * @return fixed instruction or null, if no fix can be applied
    */
  def fixDependencyByFQN(instruction: InstructionData, fqn: String): InstructionData = {
    log.debug("trying to fix dependencies using MavenCentral")

    instruction.fixerFlags += FixerFlags.SecondDepFix

    //get artifact data for fqn
    try {
      val artifactData = MavenCentralHelper.getArtifactDataByFQN(fqn)

      if (artifactData == null) {
        log.warn("fix failed")
        return null
      }

      fixDependency(instruction, artifactData(0), artifactData(1), artifactData(2))
    } catch {
      case _: IOException =>
        log.error("failed to download libraries")
        return null
    }
  }

  /**
    * will download given package and add it to the buildfile
    *
    * @param instruction
    * @param groupID
    * @param artifactID
    * @param version
    * @return fixed instruction or null, if error occures on download
    */
  def fixDependency(instruction: InstructionData, groupID: String, artifactID: String, version: String): InstructionData = {

    //val downloadedFile: File = new AetherHandler().downloadDependency(artifactData, instruction.execDir.getAbsolutePath + "/AutoBuilder/")
    val downloadedFile = SimpleMavenDownloader.downloadJar(
      groupID, artifactID, version,
      new File(instruction.execDir.getAbsolutePath + "/AutoBuilder/libs/"))

    if(downloadedFile == null) {
      log.warn("failed to fix dependency: " + groupID + ":" + artifactID + ":" + version)
      return null
    }

    //the generated ant buildfile already includes all jars in the project
    if(instruction.tool != BuildTool.Others) {
      if(instruction.buildFile == null) {
        //may happen if we use instructions from a readme
        log.warn("was not able to apply fix because no buildfile was specified.")
      } else {
        var fileUpdated: Boolean = false
        try {
          fileUpdated = XMLHelper.addLibraryToAntBuildfile(instruction.buildFile, downloadedFile)
        } catch {
          case _: SAXParseException =>
            log.warn("was not able to edit Buildfile. (malformed?)")
            fileUpdated = false
        }

        if (!fileUpdated) {
          log.warn("was not able to fix the buildfile. Switching to generated buildfile...")
          val generatedBuildfile = new BuildXmlGenerator().GenerateBuildfile(new File("build_autobuild.xml"))
          if(generatedBuildfile == null)
            return InstructionData(instruction.execDir, "ant -buildfile " + generatedBuildfile.getAbsolutePath, BuildTool.Others, generatedBuildfile)
          else
            return null
        }
      }
    }

    //since the instruction itself has not changed, just return it
    return instruction
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
      val fileUpdated: Boolean = XMLHelper.setEncodingOfAntBuildfile(instruction.buildFile, encoding)

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
