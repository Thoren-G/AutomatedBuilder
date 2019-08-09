package de.EliteKuchen.AutoBuilder.Preparation

import java.io.{BufferedWriter, File, FileWriter, IOException}

import org.slf4j.{Logger, LoggerFactory}


class BuildXmlGenerator {
  def log: Logger = LoggerFactory.getLogger(classOf[BuildXmlGenerator])

  val BuildfileContent = "<?xml version=\"1.0\"?>\n<project name=\"generated\" default=\"compile\">\n\t<target name=\"clean\">\n\t\t<delete dir=\"bin_ab\"/>\n\t</target>\n\n\t<target name=\"compile\">\n\t\t<mkdir dir=\"bin_ab\"/>\n\t\t<javac includeantruntime=\"false\" srcdir=\".\" destdir=\"bin_ab/\" includes=\"**/*.java\">\n\t\t\t<classpath>\n\t\t\t      <fileset dir=\".\">\n\t\t\t\t<include name=\"**/*.jar\"/>\n\t\t\t      </fileset>\n\t\t\t</classpath>\n\t\t</javac>\n\t</target>\n</project>"

  def GenerateBuildfile(buildFile: File): File = {
    if(!buildFile.getParentFile.exists()) {
      log.warn("faild to create buildfile")
      return null
    }

    var filewriter: BufferedWriter = null
    try {
      filewriter = new BufferedWriter(new FileWriter(buildFile))
      filewriter.write(BuildfileContent)
    } catch {
      case e: IOException =>
        log.error("failed to create generated buildfile: " + e.getMessage)
        return null
    } finally
      if(filewriter != null) filewriter.close()

    return buildFile
  }
}