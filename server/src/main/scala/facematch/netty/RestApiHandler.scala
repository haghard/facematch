package facematch.netty

import java.nio.charset.StandardCharsets

import io.netty.channel._
import io.netty.handler.codec.DecoderResult
import io.netty.handler.codec.http._

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success }
import HttpVersion._
import HttpResponseStatus._
import io.netty.channel.ChannelHandler.Sharable

@Sharable
class RestApiHandler(implicit ex: ExecutionContext) extends ChannelInboundHandlerAdapter {

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
