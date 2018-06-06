package facematch.netty

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.Base64

import io.netty.channel._
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import HttpVersion._
import HttpResponseStatus._
import io.netty.channel.ChannelHandler.Sharable

import scala.util.control.NonFatal
import sys.process._

object RestApiHandler {
  val JsonContentType = "application/json"

  case class Compare2Faces(base64ImageCnt: String)

  case class FindOnPic(base64ImageCnt: String)

  implicit def instance0(implicit ex: ExecutionContext): RestCall[Compare2Faces] =
    (arg: Compare2Faces) =>
      Future {
        val haghard = Base64.getEncoder.encodeToString(Files.readAllBytes(Paths.get("./users/haghard.jpg")))
        /*
        println(haghard.length + " /" + base64ImageCnt.length)
        println(legorby.length + " /" + base64ImageCnt.length)
        println(mads.length + " /" + base64ImageCnt.length)
        */

        val result = s"python python/facematch.py --face1=${arg.base64ImageCnt} --face2=$haghard".!!
        println(result)
        val seqments = result.split("\n")
        val r = new DefaultFullHttpResponse(HTTP_1_1, OK, ByteBufs.stringToByteBuf(s"""{ "${facematch.shared.HttpRoutes.key}":${seqments.last} }"""))
        r.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
        r.headers.set(HttpHeaderNames.CONTENT_LENGTH, r.content.readableBytes)
        Right(r)
      } recover {
        case NonFatal(ex) =>
          ex.printStackTrace
          Left(ex)
      }

  implicit def instance1(implicit ex: ExecutionContext): RestCall[FindOnPic] =
    (arg: FindOnPic) =>
      Future {
        val uri = "./users/haghard_grandparents.jpg"
        val result = s"python python/detect_faces.py --img=$uri --face=${arg.base64ImageCnt}".!!
        println(result)
        val seqments = result.split("\n")
        val r = new DefaultFullHttpResponse(HTTP_1_1, OK, ByteBufs.stringToByteBuf(s"""{ "${facematch.shared.HttpRoutes.key}":${seqments.last} }"""))
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
      ByteBufs.stringToByteBuf(error.getMessage))
    val f = ctx.channel().writeAndFlush(r)
    f.addListener(ChannelFutureListener.CLOSE)
    f
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    import facematch.shared.HttpRoutes
    msg match {
      case req: FullHttpRequest =>
        req match {
          case HttpMethod.POST -> Root / "api" / HttpRoutes.compare if (req.decoderResult == DecoderResult.SUCCESS) =>
            RestCall[Compare2Faces].run(Compare2Faces(req.content.toString(StandardCharsets.UTF_8))).onComplete {
              case Success(response) =>
                response
                  .fold({ er => sendErrorResponse(ctx, er) }, { response =>
                    ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
                  })
              case Failure(error) =>
                sendErrorResponse(ctx, error)
            }
          case HttpMethod.POST -> Root / "api" / HttpRoutes.recognize if (req.decoderResult == DecoderResult.SUCCESS) =>
            RestCall[FindOnPic].run(FindOnPic(req.content.toString(StandardCharsets.UTF_8))).onComplete {
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
