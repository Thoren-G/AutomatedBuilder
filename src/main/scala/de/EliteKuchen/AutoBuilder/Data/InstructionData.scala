package de.EliteKuchen.AutoBuilder.Data

import java.io.File

import de.EliteKuchen.AutoBuilder.Data.BuildTool.BuildTool
import de.EliteKuchen.AutoBuilder.Data.FixerFlags

case class InstructionData (execDir: File, command: String, tool: BuildTool, buildFile: File,
                            var fixerFlags: FixerFlags.ValueSet = FixerFlags.ValueSet.empty)
