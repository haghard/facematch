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
import jdk.incubator.vector.{ FloatVector, VectorOperators }

import java.io.{ File, FileOutputStream }

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

  trait FunCall[T] {
    def run(arg: T): Future[Either[Throwable, FullHttpResponse]]
  }

  object FunCall {

    def apply[T: FunCall]: FunCall[T] = implicitly[FunCall[T]]
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

    val similarityThreashold = 0.935

    implicit def faceAuth(implicit ex: ExecutionContext): FunCall[FaceAuth] =
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
            println(s"✅ Similarity score=$score. Threashold=${similarityThreashold}")
          } else {
            println(s"❌ Similarity score=$score, Threashold=${similarityThreashold}")
          }
          val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, ByteBufs.toByteBuf(s"""{"${HttpRoutes.key}":$score}"""))
          resp.headers.set(HttpHeaderNames.CONTENT_TYPE, JsonContentType)
          resp.headers.setInt(HttpHeaderNames.CONTENT_LENGTH, resp.content.readableBytes)
          Right(resp)
        }.recover {
          case NonFatal(ex) =>
            ex.printStackTrace()
            Left(ex)
        }

    def calculateSimilar(candidate: Array[Float], origin: Array[Float]): Float = {
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

      /*while (i < length) {
        dotProduct += candidate(i) * origin(i)
        normA += candidate(i) * candidate(i)
        normB += origin(i) * origin(i)
        i += 1
      }*/

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

    /*
          public float[] scalarNormOfTwoArrays(float[] arr1, float[] arr2) {
              float[] finalResult = new float[arr1.length];
              for (int i = 0; i < arr1.length; i++) {
                  finalResult[i] = (float) Math.sqrt(arr1[i] * arr1[i] + arr2[i] * arr2[i]);
              }
              return finalResult;
          }

          public float[] vectorNormalForm(float[] arr1, float[] arr2) {
              float[] finalResult = new float[arr1.length];
              int i = 0;
              int upperBound = SPECIES.loopBound(arr1.length);
              for (; i < upperBound; i += SPECIES.length()) {
                  var va = FloatVector.fromArray(PREFERRED_SPECIES, arr1, i);
                  var vb = FloatVector.fromArray(PREFERRED_SPECIES, arr2, i);
                  var vc = va.mul(va)
                    .add(vb.mul(vb))
                    .sqrt();
                  vc.intoArray(finalResult, i);
              }

              // tail cleanup
              for (; i < arr1.length; i++) {
                  finalResult[i] = (float) Math.sqrt(arr1[i] * arr1[i] + arr2[i] * arr2[i]);
              }
              return finalResult;
          }
          */

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
