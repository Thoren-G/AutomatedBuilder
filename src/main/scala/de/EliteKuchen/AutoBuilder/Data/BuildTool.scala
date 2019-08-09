package de.EliteKuchen.AutoBuilder.Data


object BuildTool extends Enumeration {
  type BuildTool = Value
  val Ant, Maven, Gradle, Others = Value
}
