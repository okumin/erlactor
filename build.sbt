name := "erlactor"

version := "1.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.3.7",
  "org.erlang.otp" % "jinterface" % "1.5.6"
)

