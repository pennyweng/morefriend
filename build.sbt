name := "morefriend"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.3"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += "rediscala" at "http://dl.bintray.com/etaty/maven"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.2.2"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "2.2.2"

libraryDependencies += "com.fasterxml.jackson.core" % "jackson-annotations" % "2.2.2"

libraryDependencies += "com.etaty.rediscala" %% "rediscala" % "1.4.0"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.2.4"

libraryDependencies +=  "net.databinder.dispatch" %% "dispatch-core" % "0.11.2"

libraryDependencies += "org.jsoup" % "jsoup" % "1.8.1"

libraryDependencies += "commons-lang" % "commons-lang" % "2.6"

sources in (Compile,doc) := Seq.empty

publishArtifact in (Compile, packageDoc) := false