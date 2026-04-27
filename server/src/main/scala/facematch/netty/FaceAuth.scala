package facematch.netty

import ai.djl.modality.cv.Image
import ai.djl.repository.zoo.ZooModel

final case class FaceAuth(
  facenetModel: ZooModel[Image, Array[Float]],
  imageUrl: String)