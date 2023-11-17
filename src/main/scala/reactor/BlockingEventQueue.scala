// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package reactor

import reactor.api.Event
import scala.collection.mutable.Queue

final class BlockingEventQueue[T](private val capacity: Int) {

  var queue = new Queue[Event[T]]

  // Note on efficiency: separate full/empty -locks for performance?

  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = {
    // task-a.md line 43:
    // The event queue may not accept `null` input to `enqueue`, but ...
    if (e != null) synchronized {
      // TODO handle exceptions?
      while (queue.size >= capacity) { wait() }
      queue.enqueue(e.asInstanceOf[Event[T]])
      notifyAll()
    }
  }

  @throws[InterruptedException]
  def dequeue: Event[T] = {
    synchronized {
      while (queue.isEmpty) { wait() }
      notifyAll()
      queue.dequeue()
    }
  }

  def getAll: Seq[Event[T]] = {
    synchronized {
      queue.dequeueAll(_ => true)
    }
  }

  def getSize: Int = { synchronized { queue.size } }
  def getCapacity: Int = { capacity }
}
