// group 1
// 123456 Firstname Lastname
// 654321 Firstname Lastname

package reactor

import reactor.api.{Event, EventHandler}

final class Dispatcher(private val queueLength: Int = 10) {
  require(queueLength > 0)

  @throws[InterruptedException]
  def handleEvents(): Unit = ???

  def addHandler[T](h: EventHandler[T]): Unit = ???

  def removeHandler[T](h: EventHandler[T]): Unit = ???

}

/*
// Hint:
final class WorkerThread[T](???) extends Thread {

 override def run(): Unit = ???

 def cancelThread(): Unit = ???

}
*/
