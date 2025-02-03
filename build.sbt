lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(url("https://github.com/evolution-gaming/sequentially")),
  startYear := Some(2018),
  organizationName := "Evolution",
  organizationHomepage := Some(url("https://evolution.com")),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.14", "3.3.5"),
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
ThisBuild / versionPolicyIntention := Compatibility.BinaryAndSourceCompatible

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
