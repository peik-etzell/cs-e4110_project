// group 1
// 123456 Firstname Lastname
// 654321 Firstname Lastname

package reactor

import reactor.api.Event

final class BlockingEventQueue[T] (private val capacity: Int) {

  @throws[InterruptedException]
  def enqueue[U <: T](e: Event[U]): Unit = ???

  @throws[InterruptedException]
  def dequeue: Event[T] = ???

  def getAll: Seq[Event[T]] = ???

  def getSize: Int = ???

  def getCapacity: Int = ???

}
