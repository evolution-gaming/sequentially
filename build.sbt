import ProjectMatrixSyntax.*
import sbt.Keys.*
import sbt.internal.ProjectMatrix

lazy val scalaVersions = Seq("2.13.16", "3.3.6")

lazy val commonSettings = Seq(
  organization := "com.evolutiongaming",
  homepage := Some(url("https://github.com/evolution-gaming/sequentially")),
  startYear := Some(2018),
  organizationName := "Evolution",
  organizationHomepage := Some(url("https://evolution.com")),
  licenses := Seq(("MIT", url("https://opensource.org/licenses/MIT"))),

  // compiler settings
  Compile / scalacOptions ++= {
    if (scalaBinaryVersion.value == "2.13") {
      Seq(
        "-Xsource:3"
      )
    } else Seq.empty
  },
  Compile / doc / scalacOptions += "-no-link-warnings",

  // publishing and versioning settings
  publishTo := Some(Resolver.evolutionReleases),
  versionScheme := Some("semver-spec"),
  versionPolicyIntention := Compatibility.BinaryCompatible,
)

lazy val root = project
  .in(file("."))
  .settings(commonSettings)
  .settings(
    name := "sequentially-root",
    publish / skip := true,
    // setting the Scala version so this module does not pull scalafix & others for 2.12
    scalaVersion := scalaVersions.head,
  )
  .aggregate(
    (sequentially.projectRefs ++
      benchmark.projectRefs ++
      `sequentially-metrics`.projectRefs) *
  )

lazy val sequentially = projectMatrix
  .in(file("sequentially"))
  .settings(commonSettings)
  .settings(
    name := "sequentially"
  )
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.FutureHelper,
      Dependencies.Scalatest % Test,
    )
  )
  .configureMatrix(asAkkaPekkoModule(
    akkaDependencies = Seq(
      Dependencies.Akka.Stream,
      Dependencies.Akka.Testkit % Test,
    ),
    pekkoDependencies = Seq(
      Dependencies.Pekko.Stream,
      Dependencies.Pekko.Testkit % Test,
      Dependencies.AkkaToPekkoAdapter.Actor,
      Dependencies.AkkaToPekkoAdapter.Stream,
      Dependencies.AkkaToPekkoAdapter.Testkit % Test,
    ),
  ))

lazy val benchmark = projectMatrix
  .in(file("benchmark"))
  .settings(commonSettings)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "benchmark",
    publish / skip := true,
  )
  .dependsOn(sequentially)
  .configureMatrix(asAkkaPekkoModule())

lazy val `sequentially-metrics` = projectMatrix
  .in(file("sequentially-metrics"))
  .settings(commonSettings)
  .settings(name := "sequentially-metrics")
  .dependsOn(sequentially)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.PrometheusTools
    )
  )
  .configureMatrix(asAkkaPekkoModule())

//used by evolution-gaming/scala-github-actions
addCommandAlias(
  "check",
  "all versionPolicyCheck Compile/doc scalafmtCheckAll scalafmtSbtCheck; scalafixEnable; scalafixAll --check",
)

addCommandAlias("fmtAll", "all scalafmtAll scalafmtSbt; scalafixEnable; scalafixAll")

def asAkkaPekkoModule(
  akkaDependencies: Seq[ModuleID] = Seq.empty,
  pekkoDependencies: Seq[ModuleID] = Seq.empty,
)(
  p: ProjectMatrix
): ProjectMatrix = {
  p
    .jvmPlatform(
      scalaVersions = scalaVersions,
      axisValues = Seq(ConfigAxis.Provider.pekko),
      configure = _.settings(
        moduleName := moduleName.value + "-pekko",
        libraryDependencies ++= pekkoDependencies,
      ),
    )
    .jvmPlatform(
      scalaVersions = scalaVersions,
      axisValues = Seq(ConfigAxis.Provider.akka),
      configure = _.settings(
        libraryDependencies ++= akkaDependencies
      ),
    )
}
