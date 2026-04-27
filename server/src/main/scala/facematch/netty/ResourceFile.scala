package facematch.netty

import java.io.File
import java.net.{ URI, URL }
import scala.util.Using

object ResourceFile {

  final case class AssetFile(url: URL, length: Long, lastModified: Long)

  def apply(url: URL): Option[AssetFile] = url.getProtocol match {
    case "file" =>
      val file = new File(url.toURI)
      if (file.isDirectory) None else Some(AssetFile(url, file.length(), file.lastModified()))
    case "jar" =>
      val path = new URI(url.getPath).getPath // remove "file:" prefix and normalize whitespace
      val bangIndex = path.indexOf('!')
      val filePath = path.substring(0, bangIndex)
      val resourcePath = path.substring(bangIndex + 2)
      Using.resource(new java.util.zip.ZipFile(filePath)) { jar =>
        val entry = jar.getEntry(resourcePath)
        if (entry.isDirectory) None
        else Option(jar.getInputStream(entry)) map { is =>
          is.close()
          AssetFile(url, entry.getSize, entry.getTime)
        }
      }
    case _ =>
      val conn = url.openConnection()
      try {
        conn.setUseCaches(false) // otherwise the JDK will keep the connection open when we close!
        val len = conn.getContentLength
        val lm = conn.getLastModified
        Some(AssetFile(url, len, lm))
      } finally conn.getInputStream.close()

  }
}