package de.EliteKuchen.AutoBuilder.Data

import java.io.File

case class BuildSettings(androidSdkPath: File = null, jdk8Path: File = null, timeout: Int = 1200000, buildtries: Int = 20) {}
