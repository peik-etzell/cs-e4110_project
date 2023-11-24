// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package reactor

import reactor.api.Event
import scala.collection.mutable.Queue

class Semaphore(private var permits: Int) {

  // Acquire a permit, waits if no available
  @throws[InterruptedException]
  def acquire(): Unit = synchronized {
    while (permits == 0) { wait() }
    permits -= 1
  }

  // Helper to acquire all available permits,
  // does not wait even if none available
  def acquireAll(): Int = synchronized {
    val n = permits
    permits = 0
    n
  }

  // Release single permit
  def release(): Unit = synchronized {
    permits += 1
    // Notify a single waiting thread if such exists,
    // no need to wake all of them
    notify()
  }

  // Helper to release multiple permits at once
  def releaseMany(n: Int): Unit = synchronized {
    permits += n
    // Has to notify at least n waiting threads
    (0 to n) foreach { _ => notify() }
  }

  // Get the number of available permits
  def availablePermits(): Int = synchronized { permits }
}

final class BlockingEventQueue[T](private val capacity: Int) {

  var queue = new Queue[Event[T]]
  // Semaphore to track empty slots
  private val emptySlotsSem = new Semaphore(capacity)
  // Semaphore to track elements in the queue
  private val elementsSem = new Semaphore(0)
  // Queue mutation lockout
  private val mutationLock = new Semaphore(1)

  // Method to enqueue an event into the queue
  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = {
    // task-a.md line 43:
    // The event queue may not accept `null` input to `enqueue`, but ...
    if (e != null) {
      // Reserve an empty slot
      emptySlotsSem.acquire()
      // Acquire lock for mutation, mutate
      mutationLock.acquire()
      queue.enqueue(e.asInstanceOf[Event[T]])
      mutationLock.release()
      // Signal one more event available
      elementsSem.release()
    }
  }

  // Method to dequeue an event from the queue
  @throws[InterruptedException]
  def dequeue: Event[T] = {
    // Reserve an Event
    elementsSem.acquire()
    // Acquire lock for mutation, mutate
    mutationLock.acquire()
    val e = queue.dequeue()
    mutationLock.release()
    // Signal more room in queue
    emptySlotsSem.release()
    return e
  }

  // Method to get all events from the queue
  @throws[InterruptedException]
  def getAll: Seq[Event[T]] = {
    // Reserve everything
    val n = elementsSem.acquireAll()
    mutationLock.acquire()
    val elems = queue.dequeueAll(_ => true)
    mutationLock.release()
    // Signal that n more empty slot are available
    emptySlotsSem.releaseMany(n)
    return elems
  }

  def getSize: Int = { synchronized { queue.size } }

  def getCapacity: Int = { capacity }
}
