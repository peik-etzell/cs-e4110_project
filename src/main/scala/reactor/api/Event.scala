package reactor.api

case class Event[T](private val data: T, private val handler: EventHandler[T]) {
  require(handler != null)

  def getData: T = { data }

  def getHandler: EventHandler[T] = { handler }

  def dispatch(): Unit = { handler.handleEvent(data) }

}
