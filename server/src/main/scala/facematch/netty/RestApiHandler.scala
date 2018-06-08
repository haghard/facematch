package facematch.netty

import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Paths }
import java.util.Base64

import io.netty.channel._
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }
import HttpVersion._
import HttpResponseStatus._
import breeze.linalg.DenseVector
import facematch.shared.HttpRoutes
import io.netty.channel.ChannelHandler.Sharable

import scala.util.control.NonFatal
import sys.process._

object RestApiHandler {
  val JsonContentType = "application/json"

  case class Compare2Faces(base64ImageCnt: String)

  case class FindOnPicture(base64ImageCnt: String)

  case class State(vector: DenseVector[Double], builder: StringBuilder = new StringBuilder,
    ind: Int = 0, entered: Boolean = false)

  private def parse(originVector: String): DenseVector[Double] = {
    val state =
      originVector.foldLeft(State(DenseVector.zeros[Double](128))) { (state, ch) =>
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

  implicit def instance0(implicit ex: ExecutionContext): RestCall[Compare2Faces] =
    (arg: Compare2Faces) =>
      Future {
        val haghard = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/haghard_rotated.jpg")))
        /*
        println(haghard.length + " /" + base64ImageCnt.length)
        println(legorby.length + " /" + base64ImageCnt.length)
        println(mads.length + " /" + base64ImageCnt.length)
        */

        val result = s"python python/facematch.py --face1=${arg.base64ImageCnt} --face2=$haghard".!!
        val seqments = result.split("\n")
        val vector = seqments.tail.mkString("").replaceAll("[\\[\\]]", "")
        val vs = vector.split(":")
        val a = parse(vs(0))
        val b = parse(vs(1))
        println("vs: " + a.length)
        println("vs: " + b.length)

        /*
        import com.twitter.algebird.MinHasher32
        val hasher = new MinHasher32(1.0, 1024) //size 1 kb
        val sigA = a.map(elem => hasher.init(elem.toString)).reduce(hasher.plus(_, _))
        val sigB = b.map(elem => hasher.init(elem.toString)).reduce(hasher.plus(_, _))
        println(s"similarity ${hasher.similarity(sigA, sigB)}")
        */

        import breeze.stats._
        import breeze.linalg._
        import breeze.linalg.functions.{ manhattanDistance, euclideanDistance, cosineDistance, chebyshevDistance }
        val euclidean = euclideanDistance(a, b)
        val cos = cosineDistance(a, b)
        val manh = manhattanDistance(a, b)
        val chebys = chebyshevDistance(a, b)
        println(euclidean + "  " + cos + " " + manh + " " + chebys)

        val r = new DefaultFullHttpResponse(HTTP_1_1, OK, ByteBufs.toByteBuf(s"""{ "${HttpRoutes.key}":$euclidean }"""))
        r.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
        r.headers.set(HttpHeaderNames.CONTENT_LENGTH, r.content.readableBytes)
        Right(r)
      } recover {
        case NonFatal(ex) =>
          ex.printStackTrace
          Left(ex)
      }

  implicit def instance1(implicit ex: ExecutionContext): RestCall[FindOnPicture] =
    (arg: FindOnPicture) =>
      Future {
        val uri = "./users/haghard_grandparents.jpg"
        val result = s"python python/detect_faces.py --img=$uri --face=${arg.base64ImageCnt}".!!
        println(result)
        val seqments = result.split("\n")
        val r = new DefaultFullHttpResponse(HTTP_1_1, OK, ByteBufs.toByteBuf(s"""{ "${HttpRoutes.key}":${seqments.last} }"""))
        r.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
        r.headers.set(HttpHeaderNames.CONTENT_LENGTH, r.content.readableBytes)
        Right(r)
      }.recover {
        case NonFatal(ex) =>
          ex.printStackTrace
          Left(ex)
      }
}

@Sharable
class RestApiHandler(implicit ex: ExecutionContext) extends ChannelInboundHandlerAdapter {

  import RestApiHandler._

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    super.channelActive(ctx)

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    ctx.flush

  override def channelInactive(ctx: ChannelHandlerContext): Unit =
    super.channelInactive(ctx)

  private def sendSimpleErrorResponse(ctx: ChannelHandlerContext, status: HttpResponseStatus): ChannelFuture = {
    val r = new DefaultHttpResponse(HTTP_1_1, status)
    r.headers.set(HttpHeaderNames.CONNECTION, "close")
    r.headers.set(HttpHeaderNames.CONTENT_LENGTH, "0")
    val f = ctx.channel().writeAndFlush(r)
    f.addListener(ChannelFutureListener.CLOSE)
    f
  }

  private def sendErrorResponse(ctx: ChannelHandlerContext, error: Throwable) = {
    val r = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR,
      ByteBufs.toByteBuf(error.getMessage))
    val f = ctx.channel().writeAndFlush(r)
    f.addListener(ChannelFutureListener.CLOSE)
    f
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    import facematch.shared.HttpRoutes
    msg match {
      case req: FullHttpRequest =>
        req match {
          case HttpMethod.POST -> Root / "api" / HttpRoutes.compare if req.decoderResult == DecoderResult.SUCCESS =>
            RestCall[Compare2Faces].run(Compare2Faces(req.content.toString(StandardCharsets.UTF_8))).onComplete {
              case Success(response) =>
                response
                  .fold({ er => sendErrorResponse(ctx, er) }, { response =>
                    ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
                  })
              case Failure(error) =>
                sendErrorResponse(ctx, error)
            }
          case HttpMethod.POST -> Root / "api" / HttpRoutes.recognize if req.decoderResult == DecoderResult.SUCCESS =>
            RestCall[FindOnPicture].run(FindOnPicture(req.content.toString(StandardCharsets.UTF_8))).onComplete {
              case Success(response) =>
                response
                  .fold({ er => sendErrorResponse(ctx, er) }, { response =>
                    ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
                  })
              case Failure(error) =>
                sendErrorResponse(ctx, error)
            }
          case other =>
            if (other.method == HttpMethod.GET && (other.uri == AssetsHandler.EmptyPath || other.uri.contains(AssetsHandler.ASSETS_SEGMENTS)))
              ctx.fireChannelRead(msg)
            else
              sendSimpleErrorResponse(ctx, BAD_REQUEST)
        }
    }
  }
}
