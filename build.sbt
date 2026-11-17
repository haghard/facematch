import sbt.Resolver

import scala.sys.process.Process
import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

import scalariform.formatter.preferences._

scalariformPreferences := scalariformPreferences.value
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)

lazy val scalaV = "2.13.18"
lazy val requiredJvmVersion = "25"
lazy val projectName = "facematch"

initialize := {
  val _ = initialize.value
  val current  = sys.props("java.specification.version")
  if (current != requiredJvmVersion)
    sys.error(s"Java $requiredJvmVersion is required for this project. Found $current instead.")
}

//https://github.com/com-lihaoyi/Ammonite/releases
val AmmoniteVersion = "3.0.9"

//show scalacOptions
val scalac2_13Options = Seq(
  //Migration mode. Preparing your code to be fully moved to Scala 3.
  //It enables Scala 3 specific syntax (like * for wildcards instead of _) and changes certain compiler behaviors to match Scala 3’s stricter rules.
  "-Xsource:3",
  "-language:experimental.macros",
  s"-release:$requiredJvmVersion",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Yrangepos",
  "-Xlog-reflective-calls",
  "-Xlint",
  "-Vtype-diffs",
  "-Ylog-classpath", //log classpath
  //"-Wunused:imports",
  //"-Xfatal-warnings",
  "-Wconf:cat=other-match-analysis:error" // Transform exhaustivity warnings into errors.
)


lazy val server = (project in file("server")).settings(
  name := projectName,
  promptTheme := com.scalapenos.sbt.prompt.PromptThemes.ScalapenosTheme,
  scalaVersion := scalaV,
  scalacOptions := scalac2_13Options,
  scalaJSProjects := Seq(client),
  Assets / pipelineStages := Seq(scalaJSPipeline),
  Compile / compile := ((Compile / compile) dependsOn (scalaJSPipeline, copyJsArtifacts)).value,
  resolvers ++= Seq(Resolver.typesafeRepo("releases")),

  libraryDependencies ++= Seq(
    "com.lihaoyi"          %%   "scalatags"            % "0.13.1",
    "org.webjars"          %    "bootstrap"            % "5.3.8",
    "io.netty"             %    "netty-all"            % "4.2.12.Final", // withSources() withJavadoc(),
    "ai.djl"               %    "api"                  % "0.39.0",
    "ai.djl.pytorch"       %    "pytorch-model-zoo"    % "0.39.0",
    "com.lihaoyi"  % "ammonite" % AmmoniteVersion % "test" cross CrossVersion.full,
  ),

  version := "0.2.0",
  Assets / WebKeys.packagePrefix := "public/",
  Runtime / managedClasspath += (Assets / packageBin).value,
  assembly / mainClass := Some("facematch.WebServer"),
  assembly / assemblyJarName := s"$projectName-${version.value}.jar",
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
    case PathList("META-INF", "versions", "11", "module-info.class") => MergeStrategy.discard
    case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.discard
    case PathList("module-info.class")                              => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  },

  //Set "false" for ammonite (test:run)
  run / fork := true,
  run / connectInput := true,

  // ammonite repl
  Test / sourceGenerators += Def.task {
    val file = (Test / sourceManaged).value / "amm.scala"
    IO.write(file, """object amm extends App { ammonite.Main().run() }""")
    Seq(file)
  }.taskValue

).enablePlugins(SbtWeb).dependsOn(sharedJvm)

lazy val client = (project in file("client")).settings(
  name := projectName,
  scalaVersion := scalaV,
  scalacOptions := scalac2_13Options,
  resolvers ++= Seq(Resolver.jcenterRepo),
  scalaJSUseMainModuleInitializer := false,

  jsDependencies +=
    "org.webjars" % "jquery" % "2.1.4" / "2.1.4/jquery.js",

  libraryDependencies ++= Seq(
    "org.scala-js"        %%%  "scalajs-dom" % "1.0.0",
    "org.querki"          %%%  "jquery-facade" % "2.0",
  )
).enablePlugins(ScalaJSPlugin, ScalaJSWeb).dependsOn(sharedJs)

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    scalaVersion := scalaV,
    scalacOptions := scalac2_13Options,
    name := projectName,
    libraryDependencies ++= Seq("com.lihaoyi" %%% "upickle" % "4.4.3")
  )

scalaVersion := scalaV
javacOptions ++= Seq("-source", requiredJvmVersion, "-target", requiredJvmVersion)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js


// loads the server project at sbt startup
onLoad in Global := (onLoad in Global).value andThen {s: State => "project server" :: s}

addCommandAlias("c", "compile")
addCommandAlias("r", "reload")

def copyJsArtifacts() = (baseDirectory) map { dir =>
  def execute() = {

    Process(s"mkdir $dir/target/web").!
    Process(s"mkdir $dir/target/web/web-modules").!
    Process(s"mkdir $dir/target/web/web-modules/main").!
    Process(s"mkdir $dir/target/web/web-modules/main/webjars").!
    Process(s"mkdir $dir/target/web/web-modules/main/webjars/lib").!
    Process(s"mkdir $dir/target/web/web-modules/main/webjars/lib/bootstrap").!
    Process(s"mkdir $dir/target/web/web-modules/main/webjars/lib/bootstrap/css").!

    Process(s"cp ${dir}/src/main/resources/jquery.js     ${dir}/target/web/web-modules/main/webjars/lib").!
    Process(s"cp ${dir}/src/main/resources/webcam.min.js ${dir}/target/web/web-modules/main/webjars/lib").!
    Process(s"cp ${dir}/src/main/resources/camera.js     ${dir}/target/web/web-modules/main/webjars/lib").!
    Process(s"cp ${dir}/src/main/resources/cam.css ${dir}/target/web/web-modules/main/webjars/lib/bootstrap/css").!
  }

  println("Coping js resources ...")
  haltOnCmdResultError(execute())
}

def haltOnCmdResultError(result: Int) {
  if (result != 0) throw new Exception("Build failed")
}