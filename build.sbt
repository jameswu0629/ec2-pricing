name := """ec2-pricing"""

version := "1.0"

scalaVersion := "2.11.8"

mainClass in Compile := Some("HelloPricing")

libraryDependencies ++= List(
  "info.folone" %% "poi-scala" % "0.15",
  "com.amazonaws" % "aws-java-sdk" % "1.10.73",
  "com.typesafe.play" % "play-json_2.11" % "2.5.2"
)

javacOptions ++= Seq("-source", "1.8", "-target", "1.8", "-Xlint")

lazy val root = (project in file(".")).
  settings(
    name := "lambda-demo",
    version := "1.0",
    scalaVersion := "2.11.4",
    retrieveManaged := true,
    libraryDependencies += "com.amazonaws" % "aws-lambda-java-core" % "1.0.0",
    libraryDependencies += "com.amazonaws" % "aws-lambda-java-events" % "1.0.0"
  )

mergeStrategy in assembly := {
  {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
  }
}
