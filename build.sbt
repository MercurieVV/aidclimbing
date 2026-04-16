ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.18"

ThisBuild / libraryDependencies += compilerPlugin(
  "org.typelevel" %% "kind-projector" % "0.13.4" cross CrossVersion.full,
)
ThisBuild / scalafixDependencies += "org.typelevel" %% "typelevel-scalafix" % "0.5.0"

lazy val prePush = taskKey[Unit]("Run all checks: format, fix, clean compile, test")

val catsVersion = "2.10.0"
val catsEffectVersion = "3.5.4"
val fs2Version = "3.10.0"
val log4catsVersion = "2.6.0"

inThisBuild(
  List(
    scalaVersion      := "2.13.18",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions     += "-Wunused:imports",
  ),
)

lazy val commonSettings = Seq(
  scalacOptions        += "-Wunused:imports",
  semanticdbEnabled    := true,
  semanticdbVersion    := scalafixSemanticdb.revision,
  wartremoverWarnings ++= Seq(
    Wart.Var,
    Wart.MutableDataStructures,
    Wart.NonUnitStatements,
    Wart.Throw,
    Wart.Return,
    Wart.AsInstanceOf,
    Wart.IsInstanceOf,
    Wart.Null,
  ),
)

lazy val root = (project in file("."))
  .aggregate(core, ce, filePersister, all)
  .settings(
    name    := "aidclimbing",
    prePush := Def
      .sequential(
        clean,
        scalafmtAll,
        scalafixAll.toTask(""),
        Test / test,
      )
      .value,
  )

lazy val all = (project in file("modules/all"))
  .dependsOn(core, ce, filePersister)
  .settings(
    commonSettings,
    name                 := "checkpoint-all",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "munit-cats-effect-3" % "1.0.7"         % Test,
      "org.typelevel" %% "log4cats-noop"       % log4catsVersion % Test,
    ),
  )

lazy val core = (project in file("modules/core"))
  .settings(
    commonSettings,
    name                 := "checkpoint-core",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % catsVersion,
    ),
  )

lazy val ce = (project in file("modules/ce"))
  .dependsOn(core)
  .settings(
    commonSettings,
    name                 := "checkpoint-ce",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect"   % catsEffectVersion,
      "org.typelevel" %% "cats-mtl"      % "1.3.0",
      "org.typelevel" %% "log4cats-core" % log4catsVersion,
    ),
  )

lazy val filePersister = (project in file("modules/file"))
  .dependsOn(ce)
  .settings(
    commonSettings,
    name                 := "checkpoint-file",
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-io" % fs2Version,
    ),
  )
