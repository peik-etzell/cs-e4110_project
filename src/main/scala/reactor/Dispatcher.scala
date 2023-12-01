// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package reactor

import reactor.api.{Event, EventHandler}
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.HashMap

class WorkerThread[T](private val h: EventHandler[T]) extends Thread {
  private var canceled = false;
  override def run(): Unit = {
    while (!canceled) {
      val ev: T = h.getHandle.read()
      h.handleEvent(ev)
    }
  }
  def cancel() = canceled = true
}

final class Dispatcher(private val queueLength: Int = 10) {
  require(queueLength > 0)

  val queue = new BlockingEventQueue(queueLength)
  val handlers: HashMap[AnyRef, Thread] = new HashMap();
// val mixedList: List[Any] = List(1, "hello", 3.14, true)
  @throws[InterruptedException]
  def handleEvents(): Unit = {
    handlers.values foreach { h => h.run(); }
  }

  def addHandler[T](h: EventHandler[T]): Unit = {
    handlers addOne (h, new WorkerThread[T](h));
  }

  def removeHandler[T](h: EventHandler[T]): Unit = {
  }
}

/*
// Hint:
final class WorkerThread[T](???) extends Thread {

 override def run(): Unit = ???

 def cancelThread(): Unit = ???

}
 */
