ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.18"
ThisBuild / libraryDependencies += compilerPlugin("org.typelevel" %% "kind-projector" % "0.13.4" cross CrossVersion.full)

val catsVersion       = "2.10.0"
val catsEffectVersion = "3.5.4"
val fs2Version        = "3.10.0"

lazy val root = (project in file("."))
  .aggregate(core, ce, filePersister, all)
  .settings(name := "aidclimbing")

lazy val all = (project in file("modules/all"))
  .dependsOn(core, ce, filePersister)
  .settings(
    name := "checkpoint-all",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.7" % Test
    )
  )

lazy val core = (project in file("modules/core"))
  .settings(
    name := "checkpoint-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion
    )
  )

lazy val ce = (project in file("modules/ce"))
  .dependsOn(core)
  .settings(
    name := "checkpoint-ce",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % catsEffectVersion
    )
  )

lazy val filePersister = (project in file("modules/file"))
  .dependsOn(ce)
  .settings(
    name := "checkpoint-file",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-io" % fs2Version
    )
  )