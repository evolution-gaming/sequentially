lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(new URL("http://github.com/evolution-gaming/sequentially")),
  startYear := Some(2018),
  organizationName := "Evolution",
  organizationHomepage := Some(url("http://evolution.com")),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.7", "2.12.17"),
  Compile / doc / scalacOptions += "-no-link-warnings",
  publishTo := Some(Resolver.evolutionReleases),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  releaseCrossBuild := true,
  versionScheme := Some("semver-spec"))


lazy val root = (project
  in file(".")
  settings (name := "sequentially-root")
  settings commonSettings
  settings (publish / skip := true)
  aggregate(sequentially, benchmark, `sequentially-metrics`))

lazy val sequentially = (project
  in file("sequentially")
  settings (name := "sequentially")
  settings commonSettings
  settings (libraryDependencies ++= Seq(
    "com.typesafe.akka"   %% "akka-stream"    % "2.6.19",
    "com.typesafe.akka"   %% "akka-testkit"   % "2.6.19" % Test,
    "com.evolutiongaming" %% "executor-tools" % "1.0.3",
    "com.evolutiongaming" %% "future-helper"  % "1.0.6",
    "org.scalatest"       %% "scalatest"      % "3.2.10" % Test)))

lazy val benchmark = (project
  in file("benchmark")
  enablePlugins(JmhPlugin)
  settings (name := "benchmark")
  settings commonSettings
  dependsOn sequentially)

lazy val `sequentially-metrics` = (project
  in file("sequentially-metrics")
  settings (name := "sequentially-metrics")
  settings commonSettings
  dependsOn sequentially
  settings (libraryDependencies ++= Seq(
    "com.evolutiongaming" %% "prometheus-tools" % "1.0.7"
  )))
