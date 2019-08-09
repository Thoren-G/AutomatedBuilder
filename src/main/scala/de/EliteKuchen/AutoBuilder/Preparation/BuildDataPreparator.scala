package de.EliteKuchen.AutoBuilder.Preparation

import java.io.File
import java.nio.file.{Files, Paths}
import java.nio.file.attribute.PosixFilePermissions

import de.EliteKuchen.AutoBuilder.Data.{BuildData, BuildTool, FixerFlags, InstructionData}
import de.EliteKuchen.AutoBuilder.Utils.FileHelper
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.mutable.ListBuffer

/**
  * This class implements the preparation component
  *
  * It helps getting a first approach of possible build instructions.
  * Therefore, it will scan a path for specific files and analyse them
  */
class BuildDataPreparator {
  def log: Logger = LoggerFactory.getLogger(classOf[BuildDataPreparator])

  /**
    * Helperclass to generate a case insensitive regular expression for a string
    *
    * @param sc
    */
  implicit class CaseInsensitiveRegex(sc: StringContext) {
    def ci = ( "(?i)" + sc.parts.mkString ).r
  }

  /**
    * Finds all buildfiles and ReadMes in a given directory and its subdirectories
    *
    * @param path the path in which the function should search
    * @return An Array containing 4 lists containing paths to the needed Files.
    *         0: Ant buildfiles
    *         1: Maven buildfiles
    *         2: Gradle buildfiles
    *         2: Gradle wrapper scripts
    *         3: Other buildfiles (Generated Ant)
    *         4: ReadMe's
    *         5: Binaries (.class & .jar)
    */
  private def seekEssentialFiles(path: File): Array[List[File]] = {
    if(!path.exists() || !path.isDirectory) {
      log.warn("was not able to find any essential file")
      return null
    }

    val antFiles, mavenFiles, gradleFiles, gradleWrapperFiles, othersFiles, readmeFiles, binaries: ListBuffer[File] = ListBuffer()

    //crawl all files and categorize the needed ones
    val binaryPattern = ".+\\.(class|jar)".r

    for(file: File <- FileHelper.getAllFiles(path)) {
      if(file.exists() && file.isFile) {
        file.getName.toLowerCase() match {
          case "build.xml" =>
            antFiles += file
          case "pom.xml" =>
            mavenFiles += file
          case "build.gradle" =>
            gradleFiles += file
          case "gradlew" =>
            //set permission to execute
            log.warn("set execution permission for gradle wrapper!")
            try {
              Files.setPosixFilePermissions(Paths.get(file.getAbsolutePath), PosixFilePermissions.fromString("rwxr-x---"))
            } catch {
              case _: UnsupportedOperationException =>
                //not needed on windows systems
                log.trace("skipping permission edit")
              case _: Exception =>
                //unexpected behaviour. keep moving on, maybe the wrapper can be executed anyway
                log.warn("unexpected behaviour while setting permissions of gradle wrapper")
            }
            gradleWrapperFiles += file
          case ci"README" | ci"README.md" | ci"readme.txt" | ci"Building.md" | ci"Building.txt" => //we dont care case sensitivity
            readmeFiles += file
          case binaryPattern(_) =>
            binaries += file
          case _ =>
            //nop
        }
      }
    }

    //generate new buildfile if none was found
    if(antFiles.isEmpty &&
      mavenFiles.isEmpty &&
      gradleFiles.isEmpty) {
      log.info("no buildfile was found. Will use generated Ant buildfile instead.")

      val generatedBuildfile: File = new BuildXmlGenerator().GenerateBuildfile(new File(path.getAbsolutePath + "/build.xml"))
      if(generatedBuildfile != null)
        othersFiles += generatedBuildfile
    }

    return Array(antFiles.toList, mavenFiles.toList, gradleFiles.toList, gradleWrapperFiles.toList, othersFiles.toList, readmeFiles.toList, binaries.toList)
  }

  /**
    * Generates the first approach of usable BuildData.
    *
    * Will try to get possible build-instruction parsing the ReadMe
    * files of the project and using buildfiles of known buildtools
    *
    * The instructions will be prioritized as follows:
    * 1. extracted from ReadMe
    * 2. Gradle Wrapper
    * 3. Gradle
    * 4. Maven
    * 5. Ant
    * 6. Other (generated)
    *
    * @param path path to the project
    * @return BuildData containing the found instructions or
    *         null if given project is unusable
    */
  def generateBuildData(path: File): BuildData = {

    if(!path.exists() || !path.isDirectory) {
      log.warn("project was not found")
      return null
    }

    //Seek/generate buildfile
    val essentialFiles: Array[List[File]] = seekEssentialFiles(path)

    val buildData: BuildData = BuildData(path, essentialFiles(5))

    //since the instructions are saved in a stack, we'll add the ones with lowest priority at first

    // 1 : buildfiles

    //Others (generated)
    essentialFiles(4).foreach(x => buildData.instructions.push(
      InstructionData(x.getParentFile, "ant -buildfile " + x.getAbsolutePath, BuildTool.Others, x)
    ))
    //Ant
    essentialFiles(0).sortWith((lhs, rhs) => lhs.getAbsolutePath.length() > rhs.getAbsolutePath.length())
      .foreach(x => buildData.instructions.push(
      InstructionData(x.getParentFile, "ant -buildfile " + x.getAbsolutePath, BuildTool.Ant, x)
    ))
    //Maven
    essentialFiles(1).sortWith((lhs, rhs) => lhs.getAbsolutePath.length() > rhs.getAbsolutePath.length())
      .foreach(x => buildData.instructions.push(
      InstructionData(x.getParentFile, "mvn compile -B -DskipTests", BuildTool.Maven, x) // "-B" to disable color encoding (batch-mode)
    ))
    //Gradle
    essentialFiles(2).sortWith((lhs, rhs) => lhs.getAbsolutePath.length() > rhs.getAbsolutePath.length())
      .foreach(x => buildData.instructions.push(
      InstructionData(path, "gradle build -x test -b " + x.getAbsolutePath, BuildTool.Gradle, x)
    ))
    //Gradle Wrapper, special handling for windows
    val os = System.getProperty("os.name").toLowerCase
    if (os.contains("nix") || os.contains("aix") || os.contains("nux")) {
      essentialFiles(3).sortWith((lhs, rhs) => lhs.getAbsolutePath.length() > rhs.getAbsolutePath.length())
        .foreach(x => buildData.instructions.push(
          InstructionData(x.getParentFile, x.getAbsolutePath + " build -x test", BuildTool.Gradle, null,
            FixerFlags.ValueSet.empty + FixerFlags.ForceBasicExecutor)
        ))
    } else if(os.contains("win")) {
      essentialFiles(3).sortWith((lhs, rhs) => lhs.getAbsolutePath.length() > rhs.getAbsolutePath.length())
        .foreach(x => buildData.instructions.push(
          InstructionData(x.getParentFile, x.getAbsolutePath + ".bat build -x test", BuildTool.Gradle, null,
            FixerFlags.ValueSet.empty + FixerFlags.ForceBasicExecutor)
        ))
    }


    // 2 : Use instructions extracted from the readmes
    val readmeParser = new ReadmeParser
    for(rm <- essentialFiles(5)) {
      val instructions: List[InstructionData] = readmeParser.parseInstructions(rm)
      instructions.foreach(x => buildData.instructions.push(x))
    }

    //for debugging only:
    log.debug("added " + buildData.instructions.size() + " instructions.")
    essentialFiles.foreach(b => b.foreach(i => log.trace("processed file: " + i)))

    return buildData
  }
}
