import sbt.Resolver

import scala.sys.process.Process
import com.scalapenos.sbt.prompt.SbtPrompt.autoImport._
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

import scalariform.formatter.preferences._

scalariformPreferences := scalariformPreferences.value
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)

val scalaV = "2.13.18"

lazy val projectName = "facematch"

lazy val server = (project in file("server")).settings(
  name := projectName,
  promptTheme := com.scalapenos.sbt.prompt.PromptThemes.ScalapenosTheme,
  scalaVersion := scalaV,
  scalaJSProjects := Seq(client),
  Assets / pipelineStages := Seq(scalaJSPipeline),
  Compile / compile := ((Compile / compile) dependsOn (scalaJSPipeline, copyJsArtifacts)).value,

  resolvers ++= Seq(Resolver.typesafeRepo("releases"), Resolver.sonatypeRepo("snapshots")/*, Resolver.sonatypeRepo("releases")*/),

  libraryDependencies ++= Seq(
    //"com.vmunier"          %%   "scalajs-scripts"      % "1.1.0",
    "com.lihaoyi"          %%   "scalatags"            % "0.13.1",
    "org.webjars"          %    "bootstrap"            % "5.3.8",
    "io.netty"             %    "netty-all"            % "4.1.29.Final" withSources() withJavadoc(),
    "ai.djl"               %    "api"                  % "0.36.0",
    "ai.djl.pytorch"       %    "pytorch-model-zoo"    % "0.36.0",
  ),
  version := "0.1",
  Assets / WebKeys.packagePrefix := "public/",
  Runtime / managedClasspath += (Assets / packageBin).value,
  assembly / mainClass := Some("facematch.WebServer"),
  assembly / assemblyJarName := s"$projectName-${version.value}.jar",
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", "versions", "9", "module-info.class") => MergeStrategy.discard
    case PathList("module-info.class")                              => MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }


).enablePlugins(SbtWeb).dependsOn(sharedJvm)

lazy val client = (project in file("client")).settings(
  name := projectName,
  scalaVersion := scalaV,
  resolvers ++= Seq(Resolver.jcenterRepo),
  scalaJSUseMainModuleInitializer := false,

  jsDependencies +=
     //"org.webjars" % "jquery" % "2.2.1" / "jquery.js" minified "jquery.min.js",
    "org.webjars" % "jquery" % "2.1.4" / "2.1.4/jquery.js",

  libraryDependencies ++= Seq(
    "org.scala-js"        %%%  "scalajs-dom" % "1.0.0",
    "org.querki"          %%%  "jquery-facade" % "2.0",
    "com.lihaoyi"         %%%  "scalatags" %  "0.9.1",
  )
).enablePlugins(ScalaJSPlugin, ScalaJSWeb).dependsOn(sharedJs)

lazy val shared = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Pure)
  .settings(
    scalaVersion := scalaV,
    name := projectName,
    libraryDependencies ++= Seq("com.lihaoyi" %%% "upickle" % "4.4.3")
  )

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