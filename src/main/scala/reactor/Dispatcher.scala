// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package reactor

import reactor.api.{Event, EventHandler}
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashMap

final class Dispatcher(private val queueLength: Int = 10) {
  require(queueLength > 0)

  // Signal that a thread just stopped
  private val workerStoppedSentinel = new Object

  // Thread safe, mutated directly by worker threads
  private val eventQueue = new BlockingEventQueue[Any](queueLength)

  // handlerMap serves a dual role:
  //    the keys is the set of added, non-removed handlers,
  //    while the values contain all the running threads
  //
  // Rest of dispatcher single threaded, so this map can be very basic
  private val handlerMap: HashMap[EventHandler[_], WorkerThread[_]] =
    new HashMap();
  private def workers = handlerMap.values

  @throws[InterruptedException]
  def handleEvents(): Unit = {
    // A concurrency problem is the following:
    // - A worker thread has to force the dispatcher to re-count the number of
    //   running workers when the worker stops, otherwise the dispatcher can
    //   be stuck waiting here forever
    // - Solution:
    //      - Strictly after switching to a non-running state,
    //        send a `sentinel` message which does nothing
    //        but wake up the dispatcher
    while (
      workers.exists(h => h.running) // Worker that could enqueue more events
      || eventQueue.getSize > 0 // Existing events to handle
    ) {
      val event = eventQueue.dequeue
      if (
        // The sentinel should not and could not be dispatched
        event.getData != workerStoppedSentinel
        // A removed handler should not be dispatched to
        && handlerMap.contains(event.getHandler)
      ) {
        event.dispatch()
      }
    }
  }

  def addHandler[T](h: EventHandler[T]): Unit = {
    val worker = new WorkerThread[T](h)
    worker.start()
    handlerMap.addOne { h -> worker };
  }

  def removeHandler[T](h: EventHandler[T]): Unit = {
    handlerMap(h).cancel()
    handlerMap.remove(h) // Disallows dispatching to this handler
  }

  // Subclass of Dispatcher so queue etc. don't have to be passed
  // along to constructor
  private class WorkerThread[T](
      private val h: EventHandler[T]
  ) extends Thread {
    private var _running = true;

    def running = { _running }

    override def run(): Unit = {
      while (running) {
        h.getHandle.read() match {
          case null => _running = false
          case data => eventQueue enqueue new Event(data, h)
        }
      }
      // Signal that this worker is now done; no more messages will be sent
      // Its `running` status will always be false here, so that 
      // when the dispatcher checks again, this worker is not running
      eventQueue enqueue new Event(workerStoppedSentinel.asInstanceOf[T], h)
    }

    // Does not need to be interrupted, reading an extra time is allowed,
    // while the dispatching after cancel was disallowed
    def cancel() = {
      _running = false
    }
  }
}
