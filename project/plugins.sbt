externalResolvers += Resolver.bintrayIvyRepo("evolutiongaming", "sbt-plugins")

addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

addSbtPlugin("org.scoverage" % "sbt-coveralls" % "1.2.5")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.11")

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.3.6")

addSbtPlugin("com.evolutiongaming" % "sbt-scalac-opts-plugin" % "0.0.4")