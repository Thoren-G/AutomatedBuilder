package de.EliteKuchen.AutoBuilder

import java.io.{BufferedWriter, File, FileWriter}

import de.EliteKuchen.AutoBuilder.Data.BuildData
import org.slf4j.{Logger, LoggerFactory}

object Main {

  def VERSION = "1.0"

  def main(args: Array[String]): Unit = {

    //configure logger
    System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE")
    System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_SHORT_LOG_NAME_KEY, "true")
    System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_THREAD_NAME_KEY, "false")
    System.setProperty(org.slf4j.impl.SimpleLogger.LEVEL_IN_BRACKETS_KEY, "true")

    def log: Logger = LoggerFactory.getLogger("Main")

    println("Automatisches Buildsystem für Java, Version " + VERSION)
    println()

    //check param
    if(args.length == 0) {
      log.info("Es wurden keine Projekte angegeben.")
      log.info("Bitte listen sie die Projektpfade bei Programmaufruf auf, separiert durch Leerzeichen.")
      log.info("")
      log.info("Beispiel: \"java -jar AutoBuilder.jar /pfad/zu/projekt1 /pfad/zu/projekt2\"")
      return
    }

    //amount of not build projects
    var buildFailureCounter = 0

    for(projectPath <- args) {
      try {

        val path = new File(projectPath)

        if(!path.exists()) {
          log.warn("Der angegebene Pfad (" + projectPath + ") existiert nicht. Es ist kein Building erfolgt!")
        } else if(!path.isDirectory) {
          log.warn("Der angegebene Pfad (" + projectPath + ") ist kein Verzeichnis. Es ist kein Building erfolgt!")
        } else {
          log.info("Starte Building von " + projectPath)
          log.info("")

          val buildData = new AutoBuilder().buildProject(path)

          if(buildData.success) {
            log.info("")
            log.info("Das Building von " + path.getName + " war erfolgreich!")
          } else {
            log.info("")
            log.info("Das Building von " + path.getName + " ist fehlgeschlagen!")
            buildFailureCounter += 1
          }
          //saveBuildData(buildData, new File(path.getName + "_BUILDINFO")) //for debugging
        }

      } catch {
        case _: Exception => log.error("Es wurde eine Exception geworfen. Building wird abgebrochen!")
      }
    }

    //force exit because daemons of gradle and maven may hinder a termination
    sys.exit(buildFailureCounter)
  }

  //DEBUG
//  /**
//    * For the purpose of Debugging
//    * Writes the given buildData to file.
//    *
//    * @param buildData
//    * @param file
//    */
//  private def saveBuildData(buildData: BuildData, file: File): Unit = {
//    val filewriter = new BufferedWriter(new FileWriter(file))
//
//    val beautifiedCompData: StringBuilder = new StringBuilder
//    beautifiedCompData.append("Successful: " + buildData.success + "  -  tries: " + buildData.results.length + "\n\n")
//    for(res <- buildData.results) {
//      try {
//        beautifiedCompData.append("START--------------\n")
//          .append("ExecDir: " + res.instruction.execDir + " - Instruction: " + res.instruction + "\n")
//          .append("---- OUTPUT ----\n" + res.output)
//          .append("-----OUTPUT END ----\n\n\n")
//      } catch {
//        case e: Exception => e.printStackTrace()
//      }
//    }
//    beautifiedCompData.append("new binaries:\n")
//    if(buildData.postBuildBinaries != null)
//      buildData.postBuildBinaries.foreach(b => beautifiedCompData.append(b.getCanonicalPath + "\n"))
//
//    filewriter.write(beautifiedCompData.toString())
//    filewriter.close()
//  }
}
