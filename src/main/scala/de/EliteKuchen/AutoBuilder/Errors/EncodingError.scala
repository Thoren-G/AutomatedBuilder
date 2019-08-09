package de.EliteKuchen.AutoBuilder.Errors

import java.io.File

case class EncodingError(file: File) extends IdentifiedError
