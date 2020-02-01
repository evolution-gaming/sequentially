lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(new URL("http://github.com/evolution-gaming/sequentially")),
  startYear := Some(2018),
  organizationName := "Evolution Gaming",
  organizationHomepage := Some(url("http://evolutiongaming.com")),
  bintrayOrganization := Some("evolutiongaming"),
  scalaVersion := crossScalaVersions.value.head,
  crossScalaVersions := Seq("2.13.1", "2.12.10"),
  scalacOptions in(Compile, doc) += "-no-link-warnings",
  resolvers += Resolver.bintrayRepo("evolutiongaming", "maven"),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  releaseCrossBuild := true)


lazy val root = (project
  in file(".")
  settings (name := "sequentially-root")
  settings commonSettings
  settings (skip in publish := true)
  aggregate(sequentially, benchmark))

lazy val sequentially = (project
  in file("sequentially")
  settings (name := "sequentially")
  settings commonSettings
  settings (libraryDependencies ++= Seq(
    "com.typesafe.akka"   %% "akka-stream"    % "2.6.3",
    "com.typesafe.akka"   %% "akka-testkit"   % "2.6.3" % Test,
    "com.evolutiongaming" %% "executor-tools" % "1.0.2",
    "com.evolutiongaming" %% "future-helper"  % "1.0.6",
    "org.scalatest"       %% "scalatest"      % "3.1.0" % Test)))

lazy val benchmark = (project
  in file("benchmark")
  enablePlugins(JmhPlugin)
  settings (name := "benchmark")
  settings commonSettings
  dependsOn sequentially)