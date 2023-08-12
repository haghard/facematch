package facematch

import breeze.linalg.DenseVector

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger
import io.netty.handler.codec.http.{ FullHttpResponse, HttpResponseStatus, HttpVersion }

import scala.concurrent.{ ExecutionContext, Future }
import java.nio.file.{ Files, Paths }
import java.util.Base64
import scala.util.control.NonFatal
import sys.process._
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

  final case class State(
    vector: DenseVector[Double],
    builder: StringBuilder = new StringBuilder,
    ind: Int = 0,
    entered: Boolean = false)

  final case class Compare2Faces(base64ImageCnt: String)
  final case class FindOnPicture(base64ImageCnt: String)

  trait RestCall[T] {
    def run(arg: T): Future[Either[Throwable, FullHttpResponse]]
  }

  object RestCall {

    private def parse(originVector: String, size: Int): DenseVector[Double] = {
      val state =
        originVector.foldLeft(State(DenseVector.zeros[Double](size))) { (state, ch) =>
          if (Character.isDigit(ch) || ch == '-' || ch == '.' || ch == 'e') {
            if (!state.entered)
              state.copy(entered = true, builder = state.builder.append(ch))
            else
              state.copy(builder = state.builder.append(ch))
          } else {
            if (state.entered) {
              state.vector.update(state.ind, java.lang.Double.parseDouble(state.builder.toString))
              state.builder.clear
              state.copy(entered = false, ind = state.ind + 1)
            } else state
          }
        }

      val last = state.builder.toString
      if (!last.isEmpty)
        state.vector.update(state.ind, java.lang.Double.parseDouble(last))

      //println("digits length:" + state.v.length)
      //println(state.v(0) + " - " + state.v(state.v.length - 1))
      state.vector
    }

    def apply[T: RestCall]: RestCall[T] = implicitly[RestCall[T]]

    implicit def instance0(implicit ex: ExecutionContext): RestCall[Compare2Faces] =
      (arg: Compare2Faces) =>
        Future {
          val haghard = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/haghard.jpg")))

          //val a0 = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/Ariel_Sharon_0002.jpg")))
          //val b0 = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/Ariel_Sharon_0003.jpg")))
          //val c0 = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/Gerhard_Schroeder_0001.jpg")))

          println(haghard.length + " / " + arg.base64ImageCnt.length)
          //println(legorby.length + " /" + base64ImageCnt.length)

          val result = s"python python/facematch.py --face1=${arg.base64ImageCnt} --face2=$haghard".!!
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

    implicit def instance1(implicit ex: ExecutionContext): RestCall[FindOnPicture] =
      (arg: FindOnPicture) =>
        Future {
          val uri = "./users/haghard_legorby1.jpg" //haghard_olga | haghard_grandparents | haghard_legorby2
          val result = s"python python/detect_faces.py --img=$uri --face=${arg.base64ImageCnt}".!!
          println(result)
          val segments = result.split("\n")
          val r = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ByteBufs.toByteBuf(s"""{ "${HttpRoutes.key}":${segments.last} }"""))
          r.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
          r.headers.set(HttpHeaderNames.CONTENT_LENGTH, r.content.readableBytes)
          Right(r)
        } recover {
          case NonFatal(ex) =>
            ex.printStackTrace
            Left(ex)
        }
  }
}
