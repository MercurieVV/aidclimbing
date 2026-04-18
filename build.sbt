import scala.sys.process._

val scala213 = "2.13.18"
val scala3 = "3.3.7"

ThisBuild / tlBaseVersion              := "0.2"
ThisBuild / licenses                   += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0"))
ThisBuild / startYear                  := Some(2026)
ThisBuild / scalaVersion               := scala213
ThisBuild / crossScalaVersions         := Seq(scala213, scala3)
ThisBuild / tlSitePublishBranch        := Some("master")
ThisBuild / tlCiReleaseBranches        := Seq.empty
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("11"))

ThisBuild / libraryDependencies ++= {
  if (scalaBinaryVersion.value == "2.13") {
    Seq(
      compilerPlugin(
        "org.typelevel" %% "kind-projector" % "0.13.4" cross CrossVersion.full,
      ),
    )
  } else Nil
}
ThisBuild / scalafixDependencies += "org.typelevel" %% "typelevel-scalafix" % "0.5.0"

lazy val prePush = taskKey[Unit]("Run the local pre-push check suite")

val catsVersion = "2.10.0"
val catsEffectVersion = "3.5.4"
val fs2Version = "3.10.0"
val log4catsVersion = "2.6.0"

inThisBuild(
  List(
//    scalaVersion      := "2.13.18",
    semanticdbEnabled := true,
    semanticdbVersion := scalafixSemanticdb.revision,
    scalacOptions    ++= Seq("-Wunused:imports", "-Werror"),
  ),
)

lazy val commonSettings = Seq(
  organization          := "io.github.mercurievv.aidclimbing",
  pgpPassphrase         := sys.env.get("GPG_PASSPHRASE").map(_.toArray),
  headerLicense         := Some(HeaderLicense.ALv2("2026", "Viktors Kalinins")),
  semanticdbEnabled     := true,
  semanticdbVersion     := scalafixSemanticdb.revision,
  publish / skip        := isAlreadyPublished.value,
  mimaPreviousArtifacts := {
    if (scalaBinaryVersion.value == "3") Set.empty
    else Set(organization.value %% moduleName.value % "0.2.0")
  },
  wartremoverWarnings ++= {
    if (scalaBinaryVersion.value == "2.13") {
      Seq(
        Wart.Var,
        Wart.MutableDataStructures,
        Wart.NonUnitStatements,
        Wart.Throw,
        Wart.Return,
        Wart.AsInstanceOf,
        Wart.IsInstanceOf,
        Wart.Null,
      )
    } else Nil
  },
)

lazy val root = (project in file("."))
  .aggregate(core, ce, filePersister, all, docs)
  .settings(
    name            := "aidclimbing",
    publish         := {},
    publishLocal    := {},
    publish / skip  := true,
    publishArtifact := false,
    publishTo       := None,
    prePush         := {
      val state = Keys.state.value
      val commands = Seq(
        """set ThisBuild / scalacOptions ~= (_.filterNot(_ == "-Werror"))""",
        "clean",
        "githubWorkflowCheck",
        "headerCheckAll",
        "scalafmtAll",
        "scalafmtSbt",
        "test",
        "mimaReportBinaryIssues",
        "doc",
        "docs/tlSite",
      )

      commands.foldLeft(state) { (current, command) =>
        Command.process(command, current)
      }
      ()
    },
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
      "org.typelevel" %% "cats-mtl"  % "1.3.0",
    ),
  )

lazy val ce = (project in file("modules/ce"))
  .dependsOn(core)
  .settings(
    commonSettings,
    name                 := "checkpoint-ce",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect"   % catsEffectVersion,
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

lazy val docs = project
  .in(file("site"))
  .enablePlugins(TypelevelSitePlugin)
  .dependsOn(all)
  .settings(
    tlSiteIsTypelevelProject := Some(TypelevelProject.Affiliate),
    libraryDependencies      += "org.typelevel" %% "log4cats-noop" % log4catsVersion,
    mdocVariables            := Map(
      "AIDCLIMBING_VERSION" -> version.value,
    ),
  )
  .settings(NoPublishPlugin.projectSettings)

lazy val isAlreadyPublished = Def.setting {
  val org = organization.value
  val artifactId =
    if (crossPaths.value) s"${moduleName.value}_${scalaBinaryVersion.value}"
    else moduleName.value
  val ver = version.value
  if (isSnapshot.value) false
  else isPublished(org, artifactId, ver)
}

def isPublished(organization: String, artifactId: String, version: String): Boolean = {
  val url =
    s"https://repo1.maven.org/maven2/${organization.replace('.', '/')}/$artifactId/$version/$artifactId-$version.pom"
  try Seq("curl", "--head", "--fail", url).! == 0
  catch {
    case _: Throwable => false
  }
}
