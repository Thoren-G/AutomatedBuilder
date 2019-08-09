name := "AutoBuilder"

version := "1.0"

scalaVersion := "2.12.8"

mainClass := some("de.EliteKuchen.AutoBuilder.Main")

//for assembly merge conflicts
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}

//Ant API
libraryDependencies += "org.apache.ant" % "ant" % "1.10.5"

//Maven API
libraryDependencies += "org.apache.maven" % "maven-core" % "3.6.0"

//Gradle API, jar from: https://mvnrepository.com/artifact/org.gradle
libraryDependencies += "org.gradle" % "gradle-tooling-api" % "5.4.1" from "file://lib/gradle-tooling-api-5.4.1.jar"

//logger for gradle
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.26"

//json api
libraryDependencies ++= Seq("com.typesafe.play" %% "play-json" % "2.7.0")

//xml api
libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.1.1"

//encoding detector
libraryDependencies += "com.ibm.icu" % "icu4j" % "64.2"

//crawler stuff
libraryDependencies += "org.scalaj" %% "scalaj-http" % "2.4.1"

trapExit := false
