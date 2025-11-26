import Dependencies.*
import ProjectMatrixSyntax.*
import sbt.Keys.*
import sbt.Project.projectToRef
import sbt.internal.ProjectMatrix

lazy val scalaVersions = Seq("2.13.18", "3.3.7")

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
      `sequentially-ce`.projectRefs ++
      `sequentially-ce-metrics`.projectRefs ++
      `sequentially-metrics`.projectRefs :+
      projectToRef(benchmark)): _*
  )

lazy val sequentially = projectMatrix
  .in(file("sequentially"))
  .settings(commonSettings)
  .settings(
    name := "sequentially",
    Test / scalacOptions -= "-Xfatal-warnings", // Allow deprecation warnings in tests
  )
  .settings(
    libraryDependencies ++= Seq(
      FutureHelper,
      Dependencies.catsCore,
      Dependencies.CatsEffect.effect,
      Dependencies.CatsEffect.laws,
      Scalatest % Test,
    )
  )
  .configureMatrix(asAkkaPekkoModule(
    akkaDependencies = Seq(
      Akka.Stream,
      Akka.Testkit % Test,
    ),
    pekkoDependencies = Seq(
      Pekko.Stream,
      Pekko.Testkit % Test,
      AkkaToPekkoAdapter.Actor,
      AkkaToPekkoAdapter.Stream,
      AkkaToPekkoAdapter.Testkit % Test,
    ),
  ))

lazy val `sequentially-ce` = projectMatrix
  .in(file("sequentially-ce"))
  .settings(commonSettings)
  .settings(
    name := "sequentially-ce"
  )
  .settings(
    libraryDependencies ++= Seq(
      FutureHelper,
      Dependencies.catsCore,
      Dependencies.CatsEffect.effect,
      Dependencies.CatsEffect.laws,
      Scalatest % Test,
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.akka"),
      ExclusionRule("org.apache.pekko"),
    ),
  )
  .jvmPlatform(scalaVersions = scalaVersions)

lazy val `sequentially-ce-metrics` = projectMatrix
  .in(file("sequentially-ce-metrics"))
  .settings(commonSettings)
  .settings(
    name := "sequentially-ce-metrics"
  )
  .settings(
    libraryDependencies ++= Seq(
      PrometheusTools,
      Dependencies.CatsEffect.effect,
      Scalatest % Test,
    ),
    excludeDependencies ++= Seq(
      ExclusionRule("com.typesafe.akka"),
      ExclusionRule("org.apache.pekko"),
    ),
  )
  .jvmPlatform(scalaVersions = scalaVersions)
  .dependsOn(`sequentially-ce`)

lazy val benchmark = project
  .in(file("benchmark"))
  .settings(commonSettings)
  .enablePlugins(JmhPlugin)
  .settings(
    name := "benchmark",
    publish / skip := true,
    scalaVersion := scalaVersions.head,
    crossScalaVersions := Seq(scalaVersions.head),
    Compile / scalacOptions -= "-Xfatal-warnings", // Allow deprecation warnings for benchmark
  )
  .dependsOn(
    sequentially.finder(ConfigAxis.Provider.akka)(scalaVersions.head),
    `sequentially-ce`.finder()(scalaVersions.head),
  )

lazy val `sequentially-metrics` = projectMatrix
  .in(file("sequentially-metrics"))
  .settings(commonSettings)
  .settings(name := "sequentially-metrics")
  .dependsOn(sequentially)
  .settings(
    libraryDependencies ++= Seq(
      PrometheusTools
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
