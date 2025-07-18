import sbt.Keys.publish
import sbt.internal.ProjectMatrix

lazy val ScalaVersions = Seq("2.13.16", "3.3.3")

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(url("https://github.com/evolution-gaming/sequentially")),
  startYear := Some(2018),
  organizationName := "Evolution",
  organizationHomepage := Some(url("https://evolution.com")),
  Compile / scalacOptions ++= {
    if (scalaBinaryVersion.value == "2.13") {
      Seq(
        "-Xsource:3"
      )
    } else Seq.empty
  },
  Compile / doc / scalacOptions += "-no-link-warnings",
  publishTo := Some(Resolver.evolutionReleases),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  versionScheme := Some("semver-spec"),
)

// Your next release will be binary compatible with the previous one,
// but it may not be source compatible (ie, it will be a minor release).
ThisBuild / versionPolicyIntention := Compatibility.None

lazy val root = (project
  in file(".")
  settings (name := "sequentially-root")
  settings commonSettings
  settings (publish / skip := true)
  aggregate (
    sequentially.projectRefs ++
      benchmark.projectRefs ++
      `sequentially-metrics`.projectRefs: _*
  ))
lazy val PekkoVersion = "1.1.3"
lazy val sequentially = (projectMatrix
  in file("sequentially")
  settings (name := "sequentially")
  settings commonSettings
  settings (libraryDependencies ++= Seq(
    "com.evolutiongaming" %% "future-helper" % "1.0.7",
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  )))
  .jvmPlatform(
    scalaVersions = ScalaVersions,
    axisValues = Seq(ConfigAxis.Provider.pekko),
    configure = _.settings(
      moduleName := moduleName.value + "-pekko",
      libraryDependencies ++= Seq(
        "org.apache.pekko" %% "pekko-actor" % PekkoVersion,
        "org.apache.pekko" %% "pekko-stream" % PekkoVersion,
        "org.apache.pekko" %% "pekko-testkit" % PekkoVersion,
        "com.evolution" %% "akka-to-pekko-adapter-actor" % "0.0.5",
        "com.evolution" %% "akka-to-pekko-adapter-stream" % "0.0.5",
        "com.evolution" %% "akka-to-pekko-adapter-test-kit" % "0.0.5" % Test,
      ),
    ),
  )
  .jvmPlatform(
    scalaVersions = ScalaVersions,
    axisValues = Seq(ConfigAxis.Provider.akka),
    configure = _.settings(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-stream" % "2.6.21",
        "com.typesafe.akka" %% "akka-testkit" % "2.6.21" % Test,
      )
    ),
  )

lazy val benchmark = (projectMatrix
  in file("benchmark")
  enablePlugins JmhPlugin
  settings (name := "benchmark")
  settings commonSettings
  dependsOn sequentially % "compile->compile")
  .jvmPlatform(
    scalaVersions = ScalaVersions,
    axisValues = Seq(ConfigAxis.Provider.pekko),
    configure = _.settings(
      moduleName := moduleName.value + "-pekko"
    ),
  )
  .jvmPlatform(
    scalaVersions = ScalaVersions,
    axisValues = Seq(ConfigAxis.Provider.akka),
    configure = identity,
  )

lazy val `sequentially-metrics` = (projectMatrix
  in file("sequentially-metrics")
  settings (name := "sequentially-metrics")
  settings commonSettings
  dependsOn sequentially % "compile->compile"
  settings (libraryDependencies ++= Seq(
    "com.evolutiongaming" %% "prometheus-tools" % "1.1.0"
  )))
  .jvmPlatform(
    scalaVersions = ScalaVersions,
    axisValues = Seq(ConfigAxis.Provider.pekko),
    configure = _.settings(
      moduleName := moduleName.value + "-pekko"
    ),
  )
  .jvmPlatform(
    scalaVersions = ScalaVersions,
    axisValues = Seq(ConfigAxis.Provider.akka),
    configure = identity,
  )

//used by evolution-gaming/scala-github-actions
addCommandAlias(
  "check",
  "all versionPolicyCheck Compile/doc scalafmtCheckAll scalafmtSbtCheck; scalafixEnable; scalafixAll --check",
)

addCommandAlias("fmtAll", "all scalafmtAll scalafmtSbt; scalafixEnable; scalafixAll")
