name := "morefriend"

version := "1.0-SNAPSHOT"

scalaVersion := "2.10.3"

resolvers += "rediscala" at "https://raw.github.com/etaty/rediscala-mvn/master/releases/"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.2.2"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "2.2.2"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-annotations" % "2.2.2"

libraryDependencies += "com.etaty.rediscala" %% "rediscala" % "1.3"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.4"

libraryDependencies +=  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"

play.Project.playScalaSettings