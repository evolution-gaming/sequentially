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
    private val Version = "1.2.1"

    val Stream = "org.apache.pekko" %% "pekko-stream" % Version
    val Testkit = "org.apache.pekko" %% "pekko-testkit" % Version
  }

  object AkkaToPekkoAdapter {
    private val Version = "1.0.0"

    val Actor = "com.evolution" %% "akka-to-pekko-adapter-actor" % Version
    val Stream = "com.evolution" %% "akka-to-pekko-adapter-stream" % Version
    val Testkit = "com.evolution" %% "akka-to-pekko-adapter-test-kit" % Version
  }
}
