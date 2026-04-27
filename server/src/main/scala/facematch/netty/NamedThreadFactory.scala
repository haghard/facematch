package facematch.netty

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

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