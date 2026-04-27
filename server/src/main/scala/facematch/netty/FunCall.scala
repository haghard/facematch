package facematch.netty

import ai.djl.modality.cv.{ Image, ImageFactory }
import facematch.shared.HttpRoutes
import io.netty.handler.codec.http.{ DefaultFullHttpResponse, FullHttpResponse, HttpHeaderNames, HttpResponseStatus, HttpVersion }
import jdk.incubator.vector.{ FloatVector, VectorOperators }

import java.io.{ File, FileOutputStream }
import java.nio.file.Paths
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

trait FunCall[T] {
  def run(arg: T): Future[Either[Throwable, FullHttpResponse]]
}

object FunCall {
  val JsonContentType = "application/json"

  def apply[T: FunCall]: FunCall[T] = implicitly[FunCall[T]]

  val similarityThreashold = 0.935

  implicit def faceAuth(implicit ec: ExecutionContext): FunCall[FaceAuth] =
    (arg: FaceAuth) =>
      Future {
        val haghard: Image = ImageFactory.getInstance().fromFile(Paths.get("./users/haghard.jpeg"))
        haghard.getWrappedImage()
        //val legorby: Image = ImageFactory.getInstance().fromFile(Paths.get("./users/legorby.jpeg"))
        //legorby.getWrappedImage()

        //val candidate: Image = ImageFactory.getInstance().fromFile(Paths.get("./users/Anthony_Hopkins_0001.jpg"))
        val candidate: Image = ImageFactory.getInstance().fromUrl(arg.imageUrl)
        candidate.getWrappedImage()

        candidate.save(new FileOutputStream(new File(s"./login-attempts/${System.nanoTime()}.jpeg")), "jpeg")

        val predictor = arg.facenetModel.newPredictor()
        val haghardEmbeddings = predictor.predict(haghard)
        val candidateEmbeddings = predictor.predict(candidate)

        val score = calculateSimilar(candidateEmbeddings, haghardEmbeddings)
        if (score >= similarityThreashold) {
          println(s"✅ Similarity score=$score. Threashold=$similarityThreashold")
        } else {
          println(s"❌ Similarity score=$score, Threashold=$similarityThreashold")
        }
        val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ByteBufs.toByteBuf(s"""{"${HttpRoutes.key}":$score}"""))
        resp.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
        resp.headers.setInt(HttpHeaderNames.CONTENT_LENGTH, resp.content.readableBytes)
        Right(resp)
      }.recover {
        case NonFatal(ex) =>
          Left(ex)
      }

  private def calculateSimilar(candidate: Array[Float], origin: Array[Float]): Float = {
    val SPECIES = FloatVector.SPECIES_PREFERRED
    val length = candidate.length
    val upperLimit = SPECIES.loopBound(length)

    var dotProduct = 0.0f
    var normA = 0.0f
    var normB = 0.0f

    var i = 0
    while (i < upperLimit) {
      val va = FloatVector.fromArray(SPECIES, candidate, i)
      val vb = FloatVector.fromArray(SPECIES, origin, i)
      // sum += a * b
      dotProduct += va.mul(vb).reduceLanes(VectorOperators.ADD)
      // mod1 += a * a
      normA += va.mul(va).reduceLanes(VectorOperators.ADD)
      // mod2 += b * b
      normB += vb.mul(vb).reduceLanes(VectorOperators.ADD)
      i += SPECIES.length()
    }

    val cosineSimilarity = dotProduct / (Math.sqrt(normA) * Math.sqrt(normB)).toFloat
    (1.0f + cosineSimilarity) / 2.0f
  }

  def dotProduct(origin: Array[Float], candidate: Array[Float]): Float = {
    val a = jdk.incubator.vector.FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, origin, 0)
    val b = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, candidate, 0)
    (1.0F + a.mul(b).reduceLanes(VectorOperators.ADD)) / 2.0F
  }

  def cosine(origin: Array[Float], candidate: Array[Float]): Float = {
    var vSum = FloatVector.zero(FloatVector.SPECIES_PREFERRED)
    var vma = FloatVector.zero(FloatVector.SPECIES_PREFERRED)
    var vmb = FloatVector.zero(FloatVector.SPECIES_PREFERRED)
    val vectorizedLength = FloatVector.SPECIES_PREFERRED.loopBound(origin.length)
    var i = 0
    while (i < vectorizedLength) {
      val a = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, origin, i)
      val b = FloatVector.fromArray(FloatVector.SPECIES_PREFERRED, candidate, i)
      vSum = a.fma(b, vSum)
      vma = a.fma(a, vma)
      vmb = b.fma(b, vmb)
      i += FloatVector.SPECIES_PREFERRED.length
    }

    var sum = vSum.reduceLanes(VectorOperators.ADD)
    var aMagnitude = vma.reduceLanes(VectorOperators.ADD)
    var bMagnitude = vmb.reduceLanes(VectorOperators.ADD)
    for (i <- vectorizedLength until origin.length) {
      sum = sum + origin(i) * candidate(i)
      aMagnitude = aMagnitude + origin(i) * origin(i)
      bMagnitude = bMagnitude + candidate(i) * candidate(i)
    }
    (sum.toDouble / Math.sqrt((aMagnitude * bMagnitude).toDouble)).toFloat
  }

  /*def calculateSimilar1(feature1: Array[Float], feature2: Array[Float]): Float = {
    var sum = 0.0f
    var mod1 = 0.0f
    var mod2 = 0.0f
    val length = feature1.length
    for (i <- 0 until length) {
      sum += feature1(i) * feature2(i)
      mod1 += feature1(i) * feature1(i)
      mod2 += feature2(i) * feature2(i)
    }
    ((sum / Math.sqrt(mod1) / Math.sqrt(mod2) + 1) / 2.0f).toFloat
  }*/
}

