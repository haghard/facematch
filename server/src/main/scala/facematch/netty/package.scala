package facematch

import ai.djl.modality.cv.{ Image, ImageFactory }
import ai.djl.repository.zoo.ZooModel

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import io.netty.handler.codec.http.{ FullHttpResponse, HttpResponseStatus, HttpVersion }

import scala.concurrent.{ ExecutionContext, Future }
import java.nio.file.Paths
import scala.util.control.NonFatal
import facematch.shared.HttpRoutes
import io.netty.handler.codec.http._

package object netty {
  val JsonContentType = "application/json"

  final case class NamedThreadFactory(name: String) extends ThreadFactory {
    private val namePrefix = s"$name-thread"
    private val threadNumber = new AtomicInteger(1)
    private val group: ThreadGroup = Thread.currentThread.getThreadGroup

    override def newThread(r: Runnable): Thread = {
      val t = new Thread(group, r, s"$namePrefix-${threadNumber.getAndIncrement}", 0L)
      t.setDaemon(true)
      t
    }
  }

  final case class FaceAuth(
    facenetModel: ZooModel[Image, Array[Float]],
    imageUrl: String)

  //final case class FindOnPicture(base64ImageCnt: String)

  trait RestCall[T] {
    def run(arg: T): Future[Either[Throwable, FullHttpResponse]]
  }

  object RestCall {

    def apply[T: RestCall]: RestCall[T] = implicitly[RestCall[T]]
    /*

    implicit def instance01(implicit ex: ExecutionContext): RestCall[Compare2Faces] =
      (arg: Compare2Faces) =>
        Future {
          val haghard = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/haghard.jpg")))

          //val a0 = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/Ariel_Sharon_0002.jpg")))
          //val b0 = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/Ariel_Sharon_0003.jpg")))
          //val c0 = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/Gerhard_Schroeder_0001.jpg")))

          println(haghard.length + " / " + arg.base64ImageCnt.length)
          //println(legorby.length + " /" + base64ImageCnt.length)

          val result = s"python3 python/facematch.py --face1=${arg.base64ImageCnt} --face2=$haghard".!!
          val segments = result.split("\n")

          val vector = segments.tail.mkString("").replaceAll("[\\[\\]]", "")

          val vs = vector.split(":")

          if (vs.size == 2) {
            /*
              in facematch.py 3 model can be used: 20170512-110547.pb, 20180408-102900.pb, and 20180408-102900.pb
              for
                20170512-110547.pb output size equal to 128
                20180408-102900.pb output size equal to 521
                20180408-102900.pb output size equal to 521
                so please pass 128 or 512 in parse method
            */

            val a = parse(vs(0), 512)
            val b = parse(vs(1), 512)
            //println(s"a: [${a.data.mkString(",")}]")
            //println(s"b: [${b.data.mkString(",")}]")
            println(s"lengths [src:${a.length} desc:${b.length}]")
            import breeze.linalg.functions.{ manhattanDistance, euclideanDistance, cosineDistance, chebyshevDistance }
            val euclidean = euclideanDistance(a, b)
            val cos = cosineDistance(a, b)
            val manh = manhattanDistance(a, b)
            val chebys = chebyshevDistance(a, b)
            println("euclidean:" + euclidean + " cosine:" + cos + " manhattan:" + manh + " chebyshev:" + chebys)
            val r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ByteBufs.toByteBuf(s"""{"${HttpRoutes.key}":$euclidean}"""))
            r.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
            r.headers.set(HttpHeaderNames.CONTENT_LENGTH, r.content.readableBytes)
            Right(r)
          } else throw new Exception("Facematch error")
        } recover {
          case NonFatal(ex) =>
            ex.printStackTrace
            Left(ex)
        }
    */

    implicit def faceAuth(implicit ex: ExecutionContext): RestCall[FaceAuth] =
      (arg: FaceAuth) =>
        Future {
          val haghard: Image = ImageFactory.getInstance().fromFile(Paths.get("./users/haghard_apr_26.jpeg"))
          //val candidate: Image = ImageFactory.getInstance().fromFile(Paths.get("./users/Anthony_Hopkins_0001.jpg"))
          val candidate: Image = ImageFactory.getInstance().fromUrl(arg.imageUrl)

          val predictor = arg.facenetModel.newPredictor()
          val haghardEmbeddings = predictor.predict(haghard)
          val candidateEmbeddings = predictor.predict(candidate)

          //https://github.com/deepjavalibrary/djl/blob/master/examples/src/main/java/ai/djl/examples/inference/face/FeatureExtraction.java
          /*
            95% – 100% (Definitive Match):
            75% – 94% (Strong Match):
            50% – 74% (Possible Match / Uncertain)
          */
          val simScore = calculateSimilar(haghardEmbeddings, candidateEmbeddings)
          println(s"Similarity score=$simScore")
          val r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ByteBufs.toByteBuf(s"""{"${HttpRoutes.key}":$simScore}"""))
          r.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
          r.headers.setInt(HttpHeaderNames.CONTENT_LENGTH, r.content.readableBytes)
          Right(r)
        } recover {
          case NonFatal(ex) =>
            ex.printStackTrace
            Left(ex)
        }

    def calculateSimilar(feature1: Array[Float], feature2: Array[Float]): Float = {
      var ret = 0.0f
      var mod1 = 0.0f
      var mod2 = 0.0f
      val length = feature1.length
      for (i <- 0 until length) {
        ret += feature1(i) * feature2(i)
        mod1 += feature1(i) * feature1(i)
        mod2 += feature2(i) * feature2(i)
      }
      ((ret / Math.sqrt(mod1) / Math.sqrt(mod2) + 1) / 2.0f).toFloat
    }

    /*implicit def instance1(implicit ex: ExecutionContext): RestCall[FindOnPicture] =
      (arg: FindOnPicture) =>
        Future {
          val uri = "./users/haghard_legorby1.jpg" //haghard_olga | haghard_grandparents | haghard_legorby2
          val result = s"python python/detect_faces.py --img=$uri --face=${arg.base64ImageCnt}".!!
          println(result)
          val segments = result.split("\n")
          val r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ByteBufs.toByteBuf(s"""{ "${HttpRoutes.key}":${segments.last} }"""))
          r.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
          r.headers.setInt(HttpHeaderNames.CONTENT_LENGTH, r.content.readableBytes)
          Right(r)
        } recover {
          case NonFatal(ex) =>
            ex.printStackTrace
            Left(ex)
        }*/
  }
}
