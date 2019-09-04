package facematch

import org.scalajs.dom

import scala.scalajs.js.Dynamic.{ global => jscript }
import scala.scalajs.js.annotation.JSExport
import org.querki.jquery._
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.HTMLImageElement

import scala.scalajs.js.JSON
import scala.util.{ Failure, Success }

@JSExport
object FaceAuthApp {
  val photoId = "user_img"
  val timeoutMillis = 15000

  import scala.concurrent.ExecutionContext.Implicits.global

  @JSExport
  def main(where: String, photo: String): Unit = {
    $(dom.document).ready(() => {
      jscript.startCamera(s"#$where")

      dom.window.setTimeout({ () =>
        dom.console.log("take snapshot")
        jscript.takeSnapshot(photo, photoId)

        val image = dom.document.getElementById(photoId).asInstanceOf[HTMLImageElement]
        val base64ImageCnt = image.src.substring(23)

        Ajax.post(
          url = "/api/" + facematch.shared.HttpRoutes.compare,
          data = base64ImageCnt,
          timeout = timeoutMillis)
          .map(r => JSON.parse(r.responseText))
          .onComplete {
            case Success(parsedJson) =>
              val json = parsedJson.asInstanceOf[scala.scalajs.js.Dictionary[scala.scalajs.js.Any]]
              val acc = json(facematch.shared.HttpRoutes.key).toString
              dom.console.log(acc)
            case Failure(error) =>
              dom.console.log(s"error: ${error.getMessage}")
          }

        /*val reqSettings: JQueryAjaxSettings =
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
        */
      }, 3000L)
    })
  }
}