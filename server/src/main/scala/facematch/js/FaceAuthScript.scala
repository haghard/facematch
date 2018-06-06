package facematch.js

import scalatags.Text.all._

object FaceAuthScript {
  val webCamera = "web_camera"
  val photo = "photo"

  def apply() =
    html(
      head(
        body(
          link(rel := "stylesheet", href := "/assets/lib/bootstrap/css/bootstrap.css"),
          link(rel := "stylesheet", href := "/assets/lib/bootstrap/css/cam.css")),

        //script(`type` := "text/javascript", src := "/assets/lib/jquery.js"),
        script(`type` := "text/javascript", src := "/assets/lib/camera.js"),
        script(`type` := "text/javascript", src := "/assets/lib/webcam.min.js"),

        script(`type` := "text/javascript", src := "/assets/client-jsdeps.js"),
        script(`type` := "text/javascript", src := "/assets/client-opt.js"),

        div(cls := "web-cam centered")(div(id := webCamera)),
        div(id := "photo", style := "centered"),
        script(s"""facematch.FaceAuthApp().main('$webCamera', '$photo')""")))

}
