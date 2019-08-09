package de.EliteKuchen.AutoBuilder.Utils

import java.io.IOException
import java.net.{HttpURLConnection, SocketTimeoutException, URL}

import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsValue, Json}

object MavenCentralHelper {
  def log: Logger = LoggerFactory.getLogger("MavenCentralHelper")

  def CONNECT_TIMEOUT = 5000
  def README_TIMEOUT = 5000

  val SearchApiUrl = "https://search.maven.org/solrsearch/select?q=fc:"
  val SearchApiArtifactPrefix = "\" AND a:\""

  /**
    *
    * @param fqn
    * @return (0): groupID
    *         (1): artifactID
    *         (2): version
    * @throws IOException
    */
  def getArtifactDataByFQN(fqn: String): List[String] = {
    log.info("retrieving artifact data using internal MavenCentral API")

    //receive the searchresult as Json object from maven
    var content: String = null
    try {
      val connection = new URL(SearchApiUrl + fqn).openConnection.asInstanceOf[HttpURLConnection]
      connection.setConnectTimeout(CONNECT_TIMEOUT)
      connection.setReadTimeout(README_TIMEOUT)
      val inputStream = connection.getInputStream
      content = io.Source.fromInputStream(inputStream).mkString
      if (inputStream != null) inputStream.close()
    } catch {
      case _: IOException =>
        log.warn("IOException thrown while receiving artifact data")
        return null
      case _: SocketTimeoutException =>
        log.warn("TimeoutExeception thrown while receiving artifact data")
        return null
    }
    val searchResult: JsValue = Json.parse(content)

    //list of artifacts contained in "response" -> "docs"
    val artifactJsonList: List[JsValue] = ((searchResult \ "response") \ "docs").as[List[JsValue]]

    //just use the very first entry
    if(artifactJsonList.isEmpty) {
      log.error("Was not able to retrieve artifact data")
      return null
    }

    (artifactJsonList(0) \ "g").as[String] ::
      (artifactJsonList(0) \ "a").as[String] ::
      (artifactJsonList(0) \ "v").as[String] ::
      Nil
  }

}
