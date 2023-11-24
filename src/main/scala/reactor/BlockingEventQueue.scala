// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package reactor

import reactor.api.Event
import scala.collection.mutable.Queue

class Semaphore(private var permits: Int) {

  // Method to acquire a permit, throws InterruptedException if interrupted
  @throws[InterruptedException]
  def acquire(): Unit = synchronized {
    while (permits == 0) { wait() } // If no permits are available, wait
    permits -= 1 // Decrease the permit count by one
  }

  // Method to acquire all available permits and set the count to zero
  def acquireAll(): Int = synchronized {
    val n = permits // Store the current number of permits
    permits = 0
    n // Return the original number of permits
  }

  // Method to release a permit, increasing the permit count by one
  def release(): Unit = synchronized {
    permits += 1
    // Notify a single waiting thread if such exists, 
    // no need to wake all of them
    notify()
  }

  // Method to release multiple permits at once
  def releaseMany(n: Int): Unit = synchronized {
    permits += n
    // Has to notify all waiting threads
    // (could kind of notify n waiting threads but whatever, good enough)
    notifyAll()
  }

  // Method to get the number of available permits
  def availablePermits(): Int = synchronized { permits }
}

final class BlockingEventQueue[T](private val capacity: Int) {

  var queue = new Queue[Event[T]] // Initialize a queue to store events of type T
  private val emptySlotsSem = new Semaphore(capacity) // Semaphore to track empty slots
  private val elementsSem = new Semaphore(0) // Semaphore to track elements in the queue
  private val mutationLock = new Semaphore(1) // Semaphore as a lock for mutating the queue

  // Method to enqueue an event into the queue
  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = {
    // task-a.md line 43:
    // The event queue may not accept `null` input to `enqueue`, but ...
    if (e != null) {
      emptySlotsSem.acquire() // Acquire a slot
      mutationLock.acquire() // Acquire lock for mutation
      queue.enqueue(e.asInstanceOf[Event[T]]) // Enqueue the event
      mutationLock.release() // Release the lock
      elementsSem.release() // Signal that a new element is available
    }
  }

  // Method to dequeue an event from the queue
  @throws[InterruptedException]
  def dequeue: Event[T] = {
    elementsSem.acquire() // Wait for an element to be available
    mutationLock.acquire() // Acquire lock for mutation
    val e = queue.dequeue() // Dequeue the event
    mutationLock.release() // Release the lock
    emptySlotsSem.release() // Release a slot
    return e // Return the dequeued event
  }

  // Method to get all events from the queue
  @throws[InterruptedException]
  def getAll: Seq[Event[T]] = {
    val n = elementsSem.acquireAll() // Acquire all available elements
    mutationLock.acquire() // Acquire lock for mutation
    val elems = queue.dequeueAll(_ => true) // Dequeue all elements
    mutationLock.release() // Release the lock
    emptySlotsSem.releaseMany(n) // Release slots equal to the number of elements dequeued
    return elems // Return the dequeued elements
  }

  // Method to get the current size of the queue
  def getSize: Int = { synchronized { queue.size } }

  // Method to get the capacity of the queue
  def getCapacity: Int = { capacity }
}
