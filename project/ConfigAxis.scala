import sbt.{VirtualAxis, *}

case class ConfigAxis(idSuffix: String, directorySuffix: String) extends VirtualAxis.StrongAxis {}

object ConfigAxis {

  object Provider {
    val pekko: ConfigAxis = ConfigAxis("-pekko", "pekko")
    val akka: ConfigAxis = ConfigAxis("", "")
  }

}
