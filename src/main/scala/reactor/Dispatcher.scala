// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package reactor

import reactor.api.{Event, EventHandler}
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashMap

// # Dispatcher
// Main idea: Use thread safety of BlockingEventQueue from task A,
// combined with multiple threads created in addHandler().
//
// ## Avoiding an infinite wait
// The biggest risk here seems to be (was the biggest problem) to keep the
// dispatcher from waiting forever for a new message, when the last worker
// stopped running after the dispatcher went requesting a new event.
// The solution here was to use an empty message after decrementing the active-
// worker-counter, which wakes up the dispatcher, which then sees that no more
// workers are available, then exits.
//
// ## Atomic integer counter
// To ensure the running -> non-running switch of a thread is seen immediately
// by the dispatcher, it was extracted to an atomic integer instead of the
// dispatcher checking the status of the threads one-by-one. This should ensure
// changes are seen immediately, and they happen before sending sentinel
// messages, which implies the counter is up-to-date before the dispatcher
// checks it again.
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

  // Atomic integer tracking number of active workers
  object workerCounter {
    private var n = 0
    def increment = synchronized { n += 1 }
    def decrement = synchronized { n -= 1 }
    def get = synchronized { n }
  }

  @throws[InterruptedException]
  def handleEvents(): Unit = {
    // - A worker thread has to force the dispatcher to re-check the number of
    //   running workers when the worker stops, otherwise the dispatcher can
    //   be stuck waiting here forever
    // - Solution:
    //      - Strictly after decrementing the workerCounter,
    //        send a `sentinel` message which does nothing
    //        but wake up the dispatcher to re-check the workers
    while (
      workerCounter.get > 0 // Worker that could enqueue more events
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
    handlerMap.addOne { h -> worker };
    workerCounter.increment
    worker.start()
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
    private var running = true;

    override def run(): Unit = {
      while (running) {
        h.getHandle.read() match {
          case null => running = false
          case data => eventQueue enqueue new Event(data, h)
        }
      }
      workerCounter.decrement
      // Signal that this worker is now done; no more messages will be sent.
      // `numWorkers` is decremented strictly before this happens, and because
      // it is synchronized, it should always be visible before the eventqueue
      // is appended here thanks to happens-before semantics.
      // If this was the last running worker, the dispatcher checks again when
      // dequeueing this sentinel; at this point the `numWorkers` is at zero.
      eventQueue enqueue new Event(workerStoppedSentinel.asInstanceOf[T], h)
    }

    // Thread does not need to be interrupted, reading an extra time is allowed,
    // while the dispatching after cancel was disallowed
    def cancel() = {
      running = false
    }
  }
}
