
val versionString = "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "smqd-rpt-svr",
    organization := "com.thing2x",
    version := versionString,
    scalaVersion := "2.12.8",
    scalacOptions in ThisBuild ++= Seq("-feature", "-deprecation"),
  )
  .settings(
    fork in Test := true
  )
  .settings(
    libraryDependencies ++= Dependencies.smqd ++
      Dependencies.test ++
      Dependencies.slick ++
      Dependencies.quartz ++
      Dependencies.h2db ++
      Dependencies.rhino ++
      Dependencies.fonts ++
      Dependencies.jfreechart ++
      Dependencies.jasperreports ++ Dependencies.poi,
    resolvers ++= Seq(Resolver.sonatypeRepo("public"), Resolver.sonatypeRepo("releases")),
    resolvers += Dependencies.jasperreportsResolver,
  ).settings(
    // Publishing
    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    //credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials"),
    credentials += Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org",
      sys.env.getOrElse("SONATYPE_USER", ""), sys.env.getOrElse("SONATYPE_PASS", "")),
    homepage := Some(url("https://github.com/smqd/")),
    scmInfo := Some(ScmInfo(url("https://github.com/smqd/smqd-plugin-rptsvr"), "scm:git@github.com:smqd/smqd-plugin-rptsvr.git")),
    developers := List(
      Developer("OutOfBedlam", "Kwon, Yeong Eon", sys.env.getOrElse("SONATYPE_DEVELOPER_0", ""), url("http://www.uangel.com"))
    ),
    publishArtifact in Test := false, // Not publishing the test artifacts (default)
    publishMavenStyle := true
  ).settings(
    // PGP signing
    pgpPublicRing := file("./travis/local.pubring.asc"),
    pgpSecretRing := file("./travis/local.secring.asc"),
    pgpPassphrase := sys.env.get("PGP_PASS").map(_.toArray),
    useGpg := false
  ).settings(
    // License
    organizationName := "UANGEL",
    startYear := Some(2019),
    licenses += ("LGPL-3.0", new URL("http://www.gnu.org/licenses/lgpl-3.0.en.html")),
    headerMappings := headerMappings.value + (HeaderFileType.scala -> HeaderCommentStyle.cppStyleLineComment),
    headerMappings := headerMappings.value + (HeaderFileType.conf -> HeaderCommentStyle.hashLineComment)
  ).enablePlugins(AutomateHeaderPlugin)