package facematch.netty

import java.io.ByteArrayOutputStream
import java.io.InputStream
import io.netty.buffer.{ ByteBuf, Unpooled }
import io.netty.util.CharsetUtil

object ByteBufs {

  def toByteBuf(str: String): ByteBuf =
    if (str.isEmpty) Unpooled.EMPTY_BUFFER
    else Unpooled.wrappedBuffer(str.getBytes(CharsetUtil.UTF_8))

  def bytesToByteBuf(bs: Array[Byte]): ByteBuf =
    Unpooled.wrappedBuffer(bs)

  def chunkedBlockingRead(is: InputStream): Array[Byte] = {
    var bytesAvailableToRead = 0
    val out = new ByteArrayOutputStream()
    try {
      val chunk = new Array[Byte](1024 * 10)
      while ({
        bytesAvailableToRead = is.read(chunk) //blocking call
        bytesAvailableToRead != -1
      }) {
        out.write(chunk, 0, bytesAvailableToRead)
      }
      out.flush()
      out.toByteArray()
    } finally out.close()
  }
}