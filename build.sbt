lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(url("https://github.com/evolution-gaming/sequentially")),
  startYear := Some(2018),
  organizationName := "Evolution",
  organizationHomepage := Some(url("https://evolution.com")),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.14", "3.3.3"),
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
  releaseCrossBuild := true,
  versionScheme := Some("semver-spec"),
)

// Your next release will be binary compatible with the previous one,
// but it may not be source compatible (ie, it will be a minor release).
//
// It is not set to BinaryAndSourceCompatible because prometheus-tools was bumped from 1.0.8 to 1.1.0.
// Otherwise, 1.2.0 is both source and binary compatible with 1.1.5.
ThisBuild / versionPolicyIntention := Compatibility.BinaryCompatible

lazy val root = (project
  in file(".")
  settings (name := "sequentially-root")
  settings commonSettings
  settings (publish / skip := true)
  aggregate (sequentially, benchmark, `sequentially-metrics`))

lazy val sequentially = (project
  in file("sequentially")
  settings (name := "sequentially")
  settings commonSettings
  settings (libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.6.21",
    "com.typesafe.akka" %% "akka-testkit" % "2.6.21" % Test,
    "com.evolutiongaming" %% "future-helper" % "1.0.7",
    "org.scalatest" %% "scalatest" % "3.2.19" % Test,
  )))

lazy val benchmark = (project
  in file("benchmark")
  enablePlugins JmhPlugin
  settings (name := "benchmark")
  settings commonSettings
  dependsOn sequentially)

lazy val `sequentially-metrics` = (project
  in file("sequentially-metrics")
  settings (name := "sequentially-metrics")
  settings commonSettings
  dependsOn sequentially
  settings (libraryDependencies ++= Seq(
    "com.evolutiongaming" %% "prometheus-tools" % "1.1.0"
  )))

//used by evolution-gaming/scala-github-actions
addCommandAlias(
  "check",
  "all versionPolicyCheck Compile/doc scalafmtCheckAll scalafmtSbtCheck; scalafixEnable; scalafixAll --check",
)

addCommandAlias("fmtAll", "all scalafmtAll scalafmtSbt; scalafixEnable; scalafixAll")
