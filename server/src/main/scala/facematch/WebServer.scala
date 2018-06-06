package facematch

import scala.collection.{ Map, Seq }

trait OptsSupport {
  val Opt = """(\S+)=(\S+)""".r

  def argsToOpts(args: Seq[String]): Map[String, String] =
    args.collect { case Opt(key, value) => key -> value }.toMap

  def applySystemProperties(options: Map[String, String]): Unit =
    for ((key, value) <- options if key startsWith "-D") {
      println(s"Set $key: $value")
      System.setProperty(key substring 2, value)
    }
}

object WebServer extends OptsSupport {
  val HttpDispatcher = "akka.http.io-dispatcher"

  def main(args: Array[String]) {
    val opts: Map[String, String] = argsToOpts(args.toList)
    applySystemProperties(opts)
    val admin = Admin("admin.email", "admin.password", "admin.db-password")

    val logo =
      """
      ___   ___   ___  __   __  ___   ___
     / __| | __| | _ \ \ \ / / | __| | _ \
     \__ \ | _|  |   /  \ V /  | _|  |   /
      ___/ |___| |_|_\   \_/   |___| |_|_\
    """

    println(Console.GREEN + logo + Console.RESET)
    netty.HttpServer.start("0.0.0.0", 8080, admin)
  }
}
