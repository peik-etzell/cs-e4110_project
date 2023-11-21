// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package reactor

import reactor.api.Event
import scala.collection.mutable.Queue

class Semaphore(private var permits: Int) {

  @throws[InterruptedException]
  def acquire(): Unit = synchronized {
    while (permits == 0) { wait() }
    permits -= 1
  }

  def acquireAll(): Int = synchronized {
    val n = permits
    permits = 0
    n
  }

  def release(): Unit = synchronized {
    permits += 1
    notify()
  }

  def releaseMany(n: Int): Unit = synchronized {
    permits += n
    notifyAll()
  }

  def availablePermits(): Int = synchronized { permits }
}

final class BlockingEventQueue[T](private val capacity: Int) {

  var queue = new Queue[Event[T]]
  private val emptySlotsSem = new Semaphore(capacity)
  private val elementsSem = new Semaphore(0)
  private val mutationLock = new Semaphore(1)

  // Note on efficiency: separate full/empty -locks for performance?

  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = {
    // task-a.md line 43:
    // The event queue may not accept `null` input to `enqueue`, but ...
    if (e != null) {
      // TODO handle exceptions?
      emptySlotsSem.acquire()
      mutationLock.acquire()
      queue.enqueue(e.asInstanceOf[Event[T]])
      mutationLock.release()
      elementsSem.release()
    }
  }

  @throws[InterruptedException]
  def dequeue: Event[T] = {
    elementsSem.acquire()
    mutationLock.acquire()
    val e = queue.dequeue()
    mutationLock.release()
    emptySlotsSem.release()
    return e
  }

  @throws[InterruptedException]
  def getAll: Seq[Event[T]] = {
    val n = elementsSem.acquireAll()
    mutationLock.acquire()
    val elems = queue.dequeueAll(_ => true)
    mutationLock.release()
    emptySlotsSem.releaseMany(n)
    return elems
  }

  def getSize: Int = { synchronized { queue.size } }
  def getCapacity: Int = { capacity }
}
