package de.EliteKuchen.AutoBuilder.Errors

case class DependencyError(groupID: String, artifactID: String, version: String) extends IdentifiedError