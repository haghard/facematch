import sbt.Resolver

import scala.sys.process.Process
import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._

import scalariform.formatter.preferences._

scalariformPreferences := scalariformPreferences.value
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)

val scalaV = "2.12.6"


lazy val server = (project in file("server")).settings(
  promptTheme := com.scalapenos.sbt.prompt.PromptThemes.ScalapenosTheme,
  scalaVersion := scalaV,
  scalaJSProjects := Seq(client),
  pipelineStages in Assets := Seq(scalaJSPipeline),
  compile in Compile := ((compile in Compile) dependsOn (scalaJSPipeline, copyJsArtifacts)).value,

  resolvers ++= Seq(Resolver.typesafeRepo("releases"), Resolver.sonatypeRepo("snapshots"), Resolver.sonatypeRepo("releases")),

  libraryDependencies ++= Seq(
    "com.vmunier"          %%   "scalajs-scripts"      % "1.1.0",
    "com.lihaoyi"          %%   "scalatags"            % "0.6.7",
    "org.webjars"          %    "bootstrap"            % "3.3.6",
    "io.netty"             %    "netty-all"            % "4.1.24.Final" withSources() withJavadoc()
    //"com.github.mpilquist" %%   "simulacrum"           % "0.12.0"
    //"org.scalaz"          %%    "scalaz-ioeffect"      % "2.3.0",
  ),
  version := "0.1",
  WebKeys.packagePrefix in Assets := "public/",
  managedClasspath in Runtime += (packageBin in Assets).value,
  // Compile the project before generating Eclipse files, so that generated .scala or .class files for Twirl templates are present
  //EclipseKeys.preTasks := Seq(compile in Compile),
  mainClass in assembly := Some("facematch.WebServer"),
  assemblyJarName in assembly := s"facematch-${version.value}.jar"
).enablePlugins(SbtWeb, JavaAppPackaging).
  dependsOn(sharedJvm)

lazy val client = (project in file("client")).settings(
  scalaVersion := scalaV,
  resolvers ++= Seq(Resolver.jcenterRepo),
  scalaJSUseMainModuleInitializer := false,

  jsDependencies +=
     "org.webjars" % "jquery" % "2.2.1" / "jquery.js" minified "jquery.min.js",

  libraryDependencies ++= Seq(
    "org.scala-js"        %%%  "scalajs-dom" % "0.9.3",
    "org.querki"          %%%  "jquery-facade" % "1.2",
    "com.lihaoyi"         %%%  "scalatags" % "0.6.7"
  )
).enablePlugins(ScalaJSPlugin, ScalaJSWeb).
  dependsOn(sharedJs)

lazy val shared = (crossProject.crossType(CrossType.Pure) in file("shared"))
  .settings(
    scalaVersion := scalaV,
    libraryDependencies ++= Seq("com.lihaoyi"  %%% "upickle" % "0.6.6")
  ).jsConfigure(_ enablePlugins ScalaJSWeb)

lazy val sharedJvm = shared.jvm
lazy val sharedJs = shared.js

// loads the server project at sbt startup
onLoad in Global := (onLoad in Global).value andThen {s: State => "project server" :: s}


def copyJsArtifacts() = (baseDirectory) map { dir =>
  def execute() = {
    //IO.copyFile(file(s"${dir}/src/main/resources/jsgantt-improved/gantt.js"), file("licensing.bat")),
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