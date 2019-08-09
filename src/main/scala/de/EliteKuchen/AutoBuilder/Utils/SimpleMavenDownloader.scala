package de.EliteKuchen.AutoBuilder.Utils

import java.io.{File, IOException}

import org.slf4j.{Logger, LoggerFactory}

object SimpleMavenDownloader {
  def log: Logger = LoggerFactory.getLogger("SimpleMavenDownloader")

  /**
    * will download the specified jar-file to destinationPath
    *
    * @param groupId
    * @param artifact
    * @param version
    * @param destinationPath
    * @return the downloaded file or null
    */
  def downloadJar(groupId: String, artifact: String, version: String, destinationPath: File): File = {

    val centralPrefix = "http://central.maven.org/maven2/"

    //download url of all repository have the following pattern:
    // <repositoryUrl>/<groupId>/<artifactId>/<version>/<artifactId>-<version>.<type>
    val encodedArtifact = groupId.replaceAll("\\.", "/") + "/" + artifact + "/" + version +
      "/" + artifact + "-" + version + ".jar"

    log.trace("try to download jar file using url: " + centralPrefix + encodedArtifact)
    destinationPath.mkdirs()
    val destination = new File(destinationPath + "/" + artifact + "-" + version + ".jar")

    try {
      if (!FileHelper.downloadFile(centralPrefix + encodedArtifact, destination)) {
        return null
      }
    } catch {
      case _: IOException =>
        log.warn("download of jar failed. URL: " + centralPrefix + encodedArtifact)
        return null
    }

    return destination
  }

}
