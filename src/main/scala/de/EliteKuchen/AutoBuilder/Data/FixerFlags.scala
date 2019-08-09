package de.EliteKuchen.AutoBuilder.Data

object FixerFlags extends Enumeration {
  type FixerFlags = Value
  val FirstDepFix, SecondDepFix, ForceBasicExecutor, AndroidSdkSet, UseJdk8 = Value

}