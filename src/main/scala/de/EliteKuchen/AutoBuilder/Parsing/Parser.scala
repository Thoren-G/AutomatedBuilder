package de.EliteKuchen.AutoBuilder.Parsing

import de.EliteKuchen.AutoBuilder.Data.{ExecutionResult, InstructionData}
import de.EliteKuchen.AutoBuilder.Errors.IdentifiedError

/**
  * The root interface for the all parsers.
  * A parsers job in this context is to analyse and process a given String
  * containing build failures and map them to a suitable Error-Class (IdentifiedError)
  */
abstract class Parser {

  /**
    * Processes a given String into a list of IdentifiedErrors
    *
    * @param result the result of an execution
    * @return a list of successfully parsed Error. May be empty but should not be null
    */
  def parse(result: ExecutionResult): List[IdentifiedError]
}
