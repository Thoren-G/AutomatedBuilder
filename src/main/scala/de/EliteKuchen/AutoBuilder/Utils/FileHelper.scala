package de.EliteKuchen.AutoBuilder.Utils

import java.io.{File, FileNotFoundException, FileOutputStream, IOException}
import java.net.{HttpURLConnection, SocketTimeoutException, URL}

import org.slf4j.{Logger, LoggerFactory}

object FileHelper {
  def log: Logger = LoggerFactory.getLogger("FileHelper")

  /**
    * @param directory
    * @return a list of all file contained by directory und subdirectorys (resursively)
    */
  def getAllFiles(directory: File, ignoreThisDirectories: Set[String] = null): Array[File] = {
    var files = directory.listFiles()

    if(ignoreThisDirectories != null)
      files = files.filterNot(f => f.isDirectory && ignoreThisDirectories.contains(f.getName))

    files ++ files.filter(_.isDirectory).flatMap(f => getAllFiles(f, ignoreThisDirectories))
  }


  /**
    * Will download a file from the given URL to destination
    *
    * @param url
    * @param destination
    * @param buffersize
    * @throws IOException
    * @throws SocketTimeoutException
    * @return true if download was successful, false on failure
    */
  def downloadFile(url: String, destination: File, buffersize: Integer = 4096, timeout: Integer = 5000): Boolean = {
    var connection: HttpURLConnection = null
    var fileOutputStream: FileOutputStream = null
    try {
      connection = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(timeout)
      connection.setReadTimeout(timeout)
      connection.connect()

      if (connection.getResponseCode >= 400) {
        log.error("was not able to download the file")
        return false
      }
      else {
        fileOutputStream = new FileOutputStream(destination)
        val httpIn = connection.getInputStream

        val buffer: Array[Byte] = new Array[Byte](buffersize)
        var bytesRead: Int = httpIn.read(buffer)
        while ( bytesRead != -1 ) {
          fileOutputStream.write(buffer, 0, bytesRead)
          bytesRead = httpIn.read(buffer)
        }
      }
    }
    finally {
      if(connection != null)
        connection.disconnect()
      if(fileOutputStream != null)
        fileOutputStream.close()
    }
    return true
  }
}
