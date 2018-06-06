package facematch

import org.scalajs.dom

import scala.scalajs.js.Dynamic.{ global => jscript }
import scala.scalajs.js.annotation.JSExport
import org.querki.jquery._
import org.scalajs.dom.raw.HTMLImageElement
import scala.scalajs.js.{ Dynamic, JSON }

@JSExport
object FaceAuthApp {
  val imageId = "im"

  @JSExport
  def main(where: String, photo: String): Unit = {
    $(dom.document).ready(() => {
      val selector = s"#$where"
      jscript.startCamera(selector)

      //https://github.com/jhuckaby/webcamjs/blob/master/demos/basic.html
      dom.window.setTimeout({ () =>
        dom.console.log("take snapshot")
        jscript.takeSnapshot(photo, imageId)

        //val image = $(s"#${photo}")
        val image = dom.document.getElementById(imageId).asInstanceOf[HTMLImageElement]
        val base64ImageCnt = image.src.substring(23)
        dom.console.log(base64ImageCnt.length + " : " + base64ImageCnt.take(40))

        val reqSettings: JQueryAjaxSettings =
          Dynamic.literal(
            method = "POST",
            data = base64ImageCnt,
            url = "/api/" + facematch.shared.HttpRoutes.compare,
            success = (data: scala.scalajs.js.Any, textStatus: String, headers: JQueryXHR) => {
              val json = data.asInstanceOf[scala.scalajs.js.Dictionary[scala.scalajs.js.Any]]
              dom.console.log(json(facematch.shared.HttpRoutes.key).toString)
              //dom.console.log(JSON.parse(data.toString))
            },
            error = (jqXHR: JQueryXHR, textStatus: String, errorCode: String) => {
              dom.console.log(s"jqXHR=$jqXHR,text=$textStatus,http_code:$errorCode")
            }).asInstanceOf[JQueryAjaxSettings]

        $.ajax(reqSettings)
      }, 4000l)
    })
  }
}