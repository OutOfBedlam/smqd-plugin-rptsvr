import sbt._

object Dependencies {

  val smqd: Seq[ModuleID] = Seq(
    ////// if snapshot version
    "com.thing2x" %% "smqd-core" % "0.4.13-SNAPSHOT" changing() withSources(),
    ////// else
    //"com.thing2x" %% "smqd-core" % "0.4.12" withSources(),
  )

  val fonts: Seq[ModuleID] = Seq(
    "com.thing2x" %% "jasperreports-font-nanum" % "0.1.0-SNAPSHOT" % Test changing()
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest" % "3.0.5",
    "com.typesafe.akka" %% "akka-http-testkit" % "10.1.5",
    "com.typesafe.akka" %% "akka-testkit" % "2.5.17",
  ).map(_ % Test)

  val slick: Seq[ModuleID] = Seq(
    "com.typesafe.slick" %% "slick" % "3.2.3",
    "com.typesafe.slick" %% "slick-hikaricp" % "3.2.3",
    "com.typesafe.slick" %% "slick-codegen" % "3.2.3",
  )

  val quartz: Seq[ModuleID] = Seq(
    "com.enragedginger" %% "akka-quartz-scheduler" % "1.8.0-akka-2.5.x"
  )

  val h2db: Seq[ModuleID] = Seq(
    "com.h2database" % "h2" % "1.4.198"
  )

  private val jasperReportVersion = "6.8.0"

  val jasperreports: Seq[ModuleID] = Seq(
    // "net.sf.jasperreports" % "jasperreports-chart-themes", // this extension requires spring framework
    // "net.sf.jasperreports" % "jasperreports-chart-customizers",
    // "net.sf.jasperreports" % "jasperreports-custom-visualization",
    "net.sf.jasperreports" % "jasperreports-functions",
    "net.sf.jasperreports" % "jasperreports-annotation-processors",
    "net.sf.jasperreports" % "jasperreports-metadata",
    "net.sf.jasperreports" % "jasperreports-fonts",
    "net.sf.jasperreports" % "jasperreports",
  ).map( _ % jasperReportVersion )

  // JasperReport requires dependencies
  // https://community.jaspersoft.com/wiki/jasperreports-library-requirements
  
  val rhino: Seq[ModuleID] = Seq(
    "org.mozilla" % "rhino" % "1.7.10"
  )

  val jfreechart: Seq[ModuleID] = Seq(
    "org.jfree" % "jfreechart" % "1.5.0"
  )

  //
  // apache poi is required for exporting report in excel format
  //
  val poi: Seq[ModuleID] = Seq(
    "org.apache.poi" % "poi-ooxml" %  "3.17"
  )

  //
  // !!! add resolver to solve the issue related "com.lowagie" % "itext" % "2.1.7.js6"
  // https://community.jaspersoft.com/questions/1071031/itext-js6-dependency-issue
  //
  def jasperreportsResolver: Resolver = {
    "jaspersoft-third-party" at "http://jaspersoft.jfrog.io/jaspersoft/third-party-ce-artifacts/"
  }
}
