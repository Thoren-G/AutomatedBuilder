package de.EliteKuchen.AutoBuilder.Execution

import de.EliteKuchen.AutoBuilder.Data.{BuildSettings, ExecutionResult, InstructionData}

//interface?
abstract class Executor(buildSettings: BuildSettings) {

  def execute(instruction: InstructionData): ExecutionResult

}
