lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(new URL("http://github.com/evolution-gaming/sequentially")),
  startYear := Some(2018),
  organizationName := "Evolution Gaming",
  organizationHomepage := Some(url("http://evolutiongaming.com")),
  bintrayOrganization := Some("evolutiongaming"),
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.12.4", "2.11.12"),
  scalacOptions ++= Seq(
    "-encoding", "UTF-8",
    "-feature",
    "-unchecked",
    "-deprecation",
    "-Xfatal-warnings",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Xfuture"),
  scalacOptions in(Compile, doc) ++= Seq("-groups", "-implicits", "-no-link-warnings"),
  resolvers += Resolver.bintrayRepo("evolutiongaming", "maven"),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),
  releaseCrossBuild := true)


lazy val root = (project
  in file(".")
  settings (name := "sequentially-root")
  settings commonSettings
  settings(skip in publish := true)
  aggregate(sequentially, benchmark))

lazy val sequentially = (project
  in file("sequentially")
  settings (name := "sequentially")
  settings commonSettings
  settings (libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-stream" % "2.5.9",
    "com.typesafe.akka" %% "akka-testkit" % "2.5.9" % Test,
    "com.evolutiongaming" %% "executor-tools" % "1.0.0",
    "org.scalatest" %% "scalatest" % "3.0.5" % Test)))

lazy val benchmark = (project
  in file("benchmark")
  enablePlugins(JmhPlugin)
  settings (name := "benchmark")
  settings commonSettings
  settings(skip in publish := true)
  dependsOn sequentially)