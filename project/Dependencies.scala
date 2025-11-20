import sbt.*

object Dependencies {

  val PrometheusTools = "com.evolutiongaming" %% "prometheus-tools" % "1.1.0"
  val FutureHelper = "com.evolutiongaming" %% "future-helper" % "1.0.7"
  val Scalatest = "org.scalatest" %% "scalatest" % "3.2.19"

  object Akka {
    private val Version = "2.6.21" // last OSS Akka version

    val Stream = "com.typesafe.akka" %% "akka-stream" % Version
    val Testkit = "com.typesafe.akka" %% "akka-testkit" % Version
  }

  object Pekko {
    private val Version = "1.3.0"

    val Stream = "org.apache.pekko" %% "pekko-stream" % Version
    val Testkit = "org.apache.pekko" %% "pekko-testkit" % Version
  }

  object AkkaToPekkoAdapter {
    private val Version = "1.0.3"

    val Actor = "com.evolution" %% "akka-to-pekko-adapter-actor" % Version
    val Stream = "com.evolution" %% "akka-to-pekko-adapter-stream" % Version
    val Testkit = "com.evolution" %% "akka-to-pekko-adapter-test-kit" % Version
  }

  object CatsEffect {
    private val Version = "3.6.3"

    val effect = "org.typelevel" %% "cats-effect" % Version
    val laws = "org.typelevel" %% "cats-effect-laws" % Version % Test
  }

  val catsCore = "org.typelevel" %% "cats-core" % "2.13.0"
}
