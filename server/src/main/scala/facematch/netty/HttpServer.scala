package facematch.netty

import java.util.concurrent.Executors

import facematch.{ Admin, netty }
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http._
import io.netty.util.concurrent.DefaultEventExecutorGroup

import scala.concurrent.ExecutionContext

object HttpServer {

  val restApiPool: java.util.concurrent.ExecutorService =
    Executors.newFixedThreadPool(Runtime.getRuntime.availableProcessors, netty.NamedThreadFactory("api"))

  def start(interface: String, port: Int, admin: Admin): Unit = {
    val bossGroup = new NioEventLoopGroup(1, NamedThreadFactory("boss"))
    val workerGroup = new NioEventLoopGroup(Runtime.getRuntime.availableProcessors, NamedThreadFactory("worker"))
    val blockingGroup = new DefaultEventExecutorGroup(4, NamedThreadFactory("blocking-io"))

    try {
      val bootstrap = new ServerBootstrap
      val backlog =
        Option(System.getProperty("http.netty.backlog"))
          .map(Integer.parseInt)
          .getOrElse(1024)
      val assets = new AssetsHandler()
      val restApi = new RestApiHandler()(ExecutionContext.fromExecutor(restApiPool))

      bootstrap.group(bossGroup, workerGroup)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new ChannelInitializer[SocketChannel] {
          override def initChannel(ch: SocketChannel): Unit = {
            val pipeline = ch.pipeline()
            pipeline.addLast(new HttpServerCodec())
            pipeline.addLast(new HttpObjectAggregator(Int.MaxValue))
            pipeline.addLast("rest-api", restApi)
            pipeline.addLast(blockingGroup, "content", assets)
          }
        })
        //https://www.ybrikman.com/writing/2014/02/18/maxing-out-at-50-concurrent-connections/
        //For mac: sysctl -w kern.ipc.somaxconn=1024
        .option[java.lang.Integer](ChannelOption.SO_BACKLOG, backlog) //allows 1024 concurrent connections
        .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)

      val serverFuture = bootstrap.bind(interface, port).sync
      println(s"(facematch) Listening on ${interface}:${port}")
      serverFuture.channel.closeFuture.sync
    } finally {
      bossGroup.shutdownGracefully()
      workerGroup.shutdownGracefully()
      blockingGroup.shutdownGracefully()
      ()
    }

    Runtime.getRuntime.addShutdownHook(new Thread(new Runnable {
      override def run = {
        bossGroup.shutdownGracefully()
        workerGroup.shutdownGracefully()
        blockingGroup.shutdownGracefully()
      }
    }))
  }
}