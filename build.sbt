
lazy val root = (project in file("."))
  .settings(
    name := "smqd-rpt-svr",
    organization := "com.thing2x",
    version := "0.1",
    scalaVersion := "2.12.8",
    scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation"),
  )
  .settings(
    libraryDependencies ++= Dependencies.smqd ++
      Dependencies.test ++
      Dependencies.slick ++
      Dependencies.quartz ++
      Dependencies.h2db ++
      Dependencies.jasperreports,
    resolvers += Dependencies.smqdResolver,
    resolvers += Dependencies.jasperreportsResolver,
  )