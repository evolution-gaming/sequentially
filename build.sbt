lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(new URL("http://github.com/evolution-gaming/sequentially")),
  startYear := Some(2018),
  organizationName := "Evolution Gaming",
  organizationHomepage := Some(url("http://evolutiongaming.com")),
  bintrayOrganization := Some("evolutiongaming"),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.3", "2.12.11"),
  scalacOptions in(Compile, doc) += "-no-link-warnings",
  resolvers += Resolver.bintrayRepo("evolutiongaming", "maven"),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  releaseCrossBuild := true)


lazy val root = (project
  in file(".")
  settings (name := "sequentially-root")
  settings commonSettings
  settings (skip in publish := true)
  aggregate(sequentially, benchmark, `sequentially-metrics`))

lazy val sequentially = (project
  in file("sequentially")
  settings (name := "sequentially")
  settings commonSettings
  settings (libraryDependencies ++= Seq(
    "com.typesafe.akka"   %% "akka-stream"    % "2.6.4",
    "com.typesafe.akka"   %% "akka-testkit"   % "2.6.4" % Test,
    "com.evolutiongaming" %% "executor-tools" % "1.0.2",
    "com.evolutiongaming" %% "future-helper"  % "1.0.6",
    "org.scalatest"       %% "scalatest"      % "3.2.4" % Test)))

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
    "com.evolutiongaming" %% "prometheus-tools" % "1.0.3"
  )))
