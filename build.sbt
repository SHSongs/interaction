ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.11"
maintainer := "som <solver.som@gmail.com>"

lazy val root = (project in file("."))
  .settings(
    name := "interaction"
  )

scalacOptions ++= Seq(
  "-Ymacro-annotations"
)
addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")

val V = new {
  val circe = "0.14.5"
  val scalafx = "18.0.1-R28"
  val zioConfig = "4.0.0-RC13"
  val zio = "2.0.13"
  val zioTestContainer = "0.10.0"
}

libraryDependencies ++= Seq(
  "ch.epfl.scala" %%
    "scalafix-core" %
    _root_.scalafix.sbt.BuildInfo.scalafixVersion %
    ScalafixConfig,
  "dev.zio" %% "zio" % V.zio,
  "dev.zio" %% "zio-macros" % V.zio,
  "dev.zio" %% "zio-test" % V.zio % Test,
  "dev.zio" %% "zio-test-sbt" % V.zio % Test,
  "dev.zio" %% "zio-test-magnolia" % V.zio % Test,
  "dev.zio" %% "zio-config" % V.zioConfig,
  "dev.zio" %% "zio-config-magnolia" % V.zioConfig,
  "dev.zio" %% "zio-config-typesafe" % V.zioConfig,
  "dev.zio" %% "zio-logging" % "2.1.13",
  "dev.zio" %% "zio-logging-slf4j-bridge" % "2.1.13",
  "io.github.gaelrenoux" %% "tranzactio" % "4.1.0",
  "dev.zio" %% "zio-prelude" % "1.0.0-RC17",
  "net.dv8tion" % "JDA" % "5.0.0-beta.6"
)

enablePlugins(JavaAppPackaging, JDebPackaging)