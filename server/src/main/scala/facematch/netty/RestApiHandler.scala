package facematch.netty

import java.nio.charset.StandardCharsets
import io.netty.channel._
import io.netty.handler.codec.http._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import HttpVersion._
import HttpResponseStatus._
import ai.djl.modality.cv.Image
import ai.djl.modality.cv.translator.ImageFeatureExtractorFactory
import ai.djl.repository.zoo._
import ai.djl.training.util.ProgressBar
import io.netty.channel.ChannelHandler.Sharable

import java.nio.file.Paths
import facematch.shared.HttpRoutes

@Sharable
class RestApiHandler(implicit ex: ExecutionContext) extends ChannelInboundHandlerAdapter {
  
  //512
  //https://github.com/deepjavalibrary/djl/blob/master/examples/src/main/java/ai/djl/examples/inference/face/FeatureExtraction.java
  private val facenetModel: ZooModel[Image, Array[Float]] = {
    val normalize: String = {
      val mean: java.util.List[Float] = java.util.Arrays.asList(127.5f / 255.0f, 127.5f / 255.0f, 127.5f / 255.0f, 128.0f / 255.0f, 128.0f / 255.0f, 128.0f / 255.0f)
      mean.stream.map(_.toString()).collect(java.util.stream.Collectors.joining(","))
    }
    Criteria.builder()
      .setTypes(classOf[Image], classOf[Array[Float]])
      .optModelPath(Paths.get("./facenet/face_feature.pt"))
      .optModelName("face_feature")
      .optArgument("normalize", normalize)
      .optTranslatorFactory(new ImageFeatureExtractorFactory())
      .optProgress(new ProgressBar())
      .optEngine("PyTorch")
      .build()
      .loadModel()
  }

  override def channelActive(ctx: ChannelHandlerContext): Unit =
    super.channelActive(ctx)

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    ctx.flush()

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
    val r = new DefaultFullHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR, ByteBufs.toByteBuf(error.getMessage))
    val f = ctx.channel().writeAndFlush(r)
    f.addListener(ChannelFutureListener.CLOSE)
    f
  }

  override def channelRead(ctx: ChannelHandlerContext, msg: Object): Unit = {
    msg match {
      case req: FullHttpRequest =>
        req match {
          case HttpMethod.POST -> Root / HttpRoutes.api / HttpRoutes.auth if req.decoderResult().isSuccess() =>
            FunCall[FaceAuth].run(
              FaceAuth(
                facenetModel,
                req.content.toString(StandardCharsets.UTF_8),
              )
            ).onComplete {
              case Success(response) =>
                response
                  .fold({ er => sendErrorResponse(ctx, er) }, { response =>
                    ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
                  })
              case Failure(error) =>
                sendErrorResponse(ctx, error)
            }
          /*case HttpMethod.POST -> Root / "api" / HttpRoutes.recognize if req.decoderResult == DecoderResult.SUCCESS =>
            RestCall[FindOnPicture].run(FindOnPicture(req.content.toString(StandardCharsets.UTF_8))).onComplete {
              case Success(response) =>
                response
                  .fold({ er => sendErrorResponse(ctx, er) }, { response =>
                    ctx.channel().writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
                  })
              case Failure(error) =>
                sendErrorResponse(ctx, error)
            }*/
          case other =>
            if (other.method == HttpMethod.GET && (other.uri == AssetsHandler.EmptyPath || other.uri.contains(AssetsHandler.ASSETS_SEGMENTS)))
              ctx.fireChannelRead(msg)
            else
              sendSimpleErrorResponse(ctx, BAD_REQUEST)
        }
      case _ =>
        ctx.fireChannelRead(msg)
    }
  }
}
