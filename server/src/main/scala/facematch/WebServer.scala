package facematch

import ai.djl.engine.Engine

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

  def checkEngine(): Unit = {
    System.setProperty("ai.djl.debug", "true")
    /*
                PyTorch Engine is LOADED.
                Version: 2.4.0
                GPU Count: 0
             */
    // 1. Get a list of all engines DJL currently "sees" on your classpath
    System.out.println("Loaded Engines: " + Engine.getAllEngines)
    // 2. Check specifically for PyTorch
    if (Engine.hasEngine("PyTorch")) {
      val ptEngine = Engine.getEngine("PyTorch")
      System.out.println("✅ PyTorch Engine is LOADED.")
      System.out.println("   Version: " + ptEngine.getVersion)
      System.out.println("   GPU Count: " + ptEngine.getGpuCount)
    } else {
      System.err.println("❌ PyTorch Engine NOT found!")
      // Use this built-in DJL tool to see exactly what went wrong
      Engine.debugEnvironment()
    }
  }

  def main(args: Array[String]) {
    checkEngine()

    val opts: Map[String, String] = argsToOpts(args.toList)
    applySystemProperties(opts)

    val logo =
      """
      ___   ___   ___  __   __  ___   ___
     / __| | __| | _ \ \ \ / / | __| | _ \
     \__ \ | _|  |   /  \ V /  | _|  |   /
      ___/ |___| |_|_\   \_/   |___| |_|_\
    """

    println(Console.GREEN + logo + Console.RESET)
    netty.HttpServer.start("0.0.0.0", 8080)
  }
}
