package de.EliteKuchen.AutoBuilder.Utils

import scala.concurrent.TimeoutException
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent._
import scala.concurrent.duration._

/**
  * Helps to execute code with a timeout. Will not throw any Exceptions!
  * Code by Duncan McGregor, (https://stackoverflow.com/questions/6227759/is-there-a-standard-scala-function-for-running-a-block-with-a-timeout)
  */
object TimeoutHelper {

  /**
    * will execute the given function.
    * It will be interrupted, if it runs longer than timeoutMs milliseconds.
    *
    * @param timeoutMs
    * @param f
    * @tparam T
    * @throws TimeoutException
    * @return
    */
  def runWithTimeout[T](timeoutMs: Long)(f: => T) : T = {
    Await.result(Future(f), timeoutMs milliseconds).asInstanceOf[T]
  }
}