package de.EliteKuchen.AutoBuilder.Fixer

import de.EliteKuchen.AutoBuilder.Data.{BuildSettings, InstructionData}
import de.EliteKuchen.AutoBuilder.Errors.IdentifiedError

/**
  * The root interface for all fixers.
  * A fixers job is work with a list of identified errors and update/edit both files in
  * the project and the instruction itself to provide a fix for the build procedure
  */
abstract class Fixer(buildSettings: BuildSettings) {
  /**
    * Will try to fix both the project files and the given instruction using
    * a list of already identified Errors.
    *
    * May download or edit files
    *
    * @param usedInstruction instruction that caused the failure
    * @param errors list of identified errors
    * @return fixed instructionData or null, if no fix was applied
    */
  def fix(usedInstruction: InstructionData, errors: List[IdentifiedError]): InstructionData
}
