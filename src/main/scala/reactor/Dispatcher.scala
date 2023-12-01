// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package reactor
import java.util.concurrent.{LinkedBlockingQueue, Semaphore}
import scala.collection.mutable
import reactor.api.{Event, EventHandler, Handle}

final class Dispatcher(private val queueLength: Int = 10) {
  require(queueLength > 0)

  private val eventQueue = new LinkedBlockingQueue[Event[_]](queueLength)
  private val handlers = mutable.Map[Handle[_], EventHandler[_]]()
  private val semaphore = new Semaphore(0) // Initialize semaphore with 0 permits
  private val sentinel = new Object

  // TODO: Remove handler
  @throws[InterruptedException]
  def handleEvents(): Unit = {
    // WTF going on ??? inf-loop?
    while (handlers.nonEmpty || !eventQueue.isEmpty) {
      semaphore.acquire() // Wait for a permit, efficiently pausing execution
      val event = eventQueue.poll() // Should not block since semaphore was acquired
      if (event != null) {
        if (event.getData == sentinel) {
          handlers.synchronized {
            handlers.remove(event.getHandler.getHandle)
          }
        } else {
          event.dispatch()
        }
      }
    }
  }

  def addHandler[T](handler: EventHandler[T]): Unit = {
    handlers.synchronized {
      val handle = handler.getHandle
      if (!handlers.contains(handle)) {
        handlers(handle) = handler
        val worker = new WorkerThread[T](handle, handler, eventQueue, this, semaphore)
        worker.start()
      }
    }
  }

  def removeHandler[T](handler: EventHandler[T]): Unit = {
    handlers.synchronized {
      handlers.remove(handler.getHandle)
    }
  }

  private class WorkerThread[T](handle: Handle[T], handler: EventHandler[T], eventQueue: LinkedBlockingQueue[Event[_]], dispatcher: Dispatcher, semaphore: Semaphore) extends Thread {
    override def run(): Unit = {
      try {
        var continue = true
        while (!isInterrupted && continue) {
          val data = handle.read()
          if (data != null) {
            eventQueue.put(new Event[T](data, handler)) // Add event to queue
            semaphore.release() // Release a permit for the dispatcher
          } else {
            continue = false
            eventQueue.put(new Event[T](sentinel.asInstanceOf[T], handler)) // Signal to remove handler
            semaphore.release() // Release a permit for the dispatcher
          }
        }
      } catch {
        case _: InterruptedException => // Thread interrupted, exit gracefully
        case e: Exception => // Handle other exceptions ???
      }
    }
  }
}

/*
// Hint:
final class WorkerThread[T](???) extends Thread {

 override def run(): Unit = ???

 def cancelThread(): Unit = ???

}
 */
