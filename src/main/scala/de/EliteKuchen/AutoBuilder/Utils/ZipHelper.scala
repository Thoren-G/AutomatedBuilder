package de.EliteKuchen.AutoBuilder.Utils

import java.io.{File, FileInputStream, FileOutputStream, IOException}
import java.nio.file.{Path, Paths}
import java.util.zip.{ZipEntry, ZipInputStream}

object ZipHelper {

  /**
    * https://stackoverflow.com/questions/30640627/how-to-unzip-a-zip-file-using-scala
    *
    * @param zipFile
    * @param destination
    */
  def unzip(zipFile: File, destination: File): Unit = {
    val zipStream = new FileInputStream(zipFile)
    val path = Paths.get(destination.getAbsolutePath)

    val zis = new ZipInputStream(zipStream)

    Stream.continually(zis.getNextEntry).takeWhile(_ != null).foreach { file =>
      if (!file.isDirectory) {
        val outPath = path.resolve(file.getName)
        val outPathParent = outPath.getParent
        if (!outPathParent.toFile.exists()) {
          outPathParent.toFile.mkdirs()
        }

        val outFile = outPath.toFile
        val out = new FileOutputStream(outFile)
        val buffer = new Array[Byte](4096)
        Stream.continually(zis.read(buffer)).takeWhile(_ != -1).foreach(out.write(buffer, 0, _))
        out.close()
      }
    }
  }
}
