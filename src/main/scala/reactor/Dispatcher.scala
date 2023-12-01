// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package reactor

import reactor.api.{Event, EventHandler}
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashMap

class WorkerThread[T](
    private val q: BlockingEventQueue[Any],
    private val h: EventHandler[T]
) extends Thread {
  var canceled = false;
  override def run(): Unit = {
    while (!canceled) {
      // try {
      val data = h.getHandle.read()
      data match {
        case null => this.canceled = true
        case data => q.enqueue(new Event(data, h))
      }
      // } catch {
      //   case e: InterruptedException => {
      //     if (!canceled) throw e
      //     println("canceled normally")
      //   }
      // }
    }
  }
  def cancel() = {
    canceled = true
    interrupt()
  }
}

final class Dispatcher(private val queueLength: Int = 10) {
  require(queueLength > 0)

  val q = new BlockingEventQueue[Any](queueLength)
  val handlers: HashMap[EventHandler[_], WorkerThread[_]] = new HashMap();

  @throws[InterruptedException]
  def handleEvents(): Unit = synchronized {
    while (handlers.values.exists(h => !h.canceled) || q.getSize > 0) {
      // println(
      //   handlers.size + " handlers, running: " + handlers.values.count(h =>
      //     !h.canceled
      //   ) + ", cancelled: " + handlers.values.count(h => h.canceled)
      // )
      q.dequeue.dispatch()
    }
  }

  def addHandler[T](h: EventHandler[T]): Unit = {
    val worker = new WorkerThread[T](q, h)
    worker.start()
    handlers addOne { h -> worker };
  }

  def removeHandler[T](h: EventHandler[T]): Unit = {
    handlers(h).cancel()
  }
}
