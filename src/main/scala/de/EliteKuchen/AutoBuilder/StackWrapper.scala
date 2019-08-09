package de.EliteKuchen.AutoBuilder

import scala.collection.mutable.ListBuffer

/**
  * Quick and dirty stack wrapper around ListBuffer
  *
  * @tparam A
  */
class StackWrapper[A >: Null] {

  private val list: ListBuffer[A] = ListBuffer()

  def isEmpty: Boolean = {
    list.isEmpty
  }

  def push(obj:A): Unit = {
    list.append(obj)
  }

  def pop():A = {
    if(list.nonEmpty) {
      val ret = list.last
      list.remove(list.size - 1)
      return ret
    } else {
      return null
    }
  }

  def size(): Int = list.size
}
