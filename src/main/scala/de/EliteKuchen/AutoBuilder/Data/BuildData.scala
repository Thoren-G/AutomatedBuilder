package de.EliteKuchen.AutoBuilder.Data

import java.io.File

import de.EliteKuchen.AutoBuilder.StackWrapper

import scala.collection.mutable.ListBuffer

case class BuildData(projectPath: File,
                     preBuildBinaries: List[File],
                     instructions: StackWrapper[InstructionData] = new StackWrapper,
                     var success: Boolean = false,
                     results: ListBuffer[ExecutionResult] = new ListBuffer,
                     var postBuildBinaries: List[File] = null)