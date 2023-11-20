package reactor

import org.scalatest.concurrent.TimeLimitedTests
import reactor.api.{Event, EventHandler, Handle}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.{Seconds, Span}

import scala.util.Random
class QueueSemanticsTest extends AnyFunSuite with TimeLimitedTests {

  // The time limit is arbitrary and dependent on the computer
  override def timeLimit: Span = Span(10, Seconds)

  class IntegerHandle(val i: Integer) extends Handle[Integer] {
    def this() = { this(scala.util.Random.nextInt()) }
    override def read(): Integer = scala.util.Random.nextInt()
  }

  class IntegerHandler(h: Handle[Integer]) extends EventHandler[Integer] {
    override def getHandle: Handle[Integer] = h
    override def handleEvent(arg: Integer): Unit = {} // do nothing
  }

  def generateIntegerEvent: Event[Integer] = {
    val h = new IntegerHandle()
    Event(h.read(), new IntegerHandler(h))
  }

  test("the queue is empty when created") {
    val q = new BlockingEventQueue[Integer](10)

    assert(q.getCapacity === 10)
    assert(q.getSize === 0)
  }

  test("the queue returns inserted elements") {
    val q = new BlockingEventQueue[Integer](10)

    val e = generateIntegerEvent
    q.enqueue(e)

    assert(q.getSize == 1)
    assert(q.dequeue === e)
  }

  test("the queue retains the order of elements") {
    val q = new BlockingEventQueue[Integer](10)
    val e1 = generateIntegerEvent
    val e2 = generateIntegerEvent
    val e3 = generateIntegerEvent

    q.enqueue(e1)
    q.enqueue(e2)
    q.enqueue(e3)

    assert(q.getSize === 3)
    assert(q.dequeue === e1)
    assert(q.dequeue === e2)
    assert(q.dequeue === e3)
  }

  test("the queue implements getAll") {
    val q = new BlockingEventQueue[Integer](10)
    val e1 = generateIntegerEvent
    val e2 = generateIntegerEvent
    val e3 = generateIntegerEvent

    q.enqueue(e1)
    q.enqueue(e2)
    q.enqueue(e3)

    val everything = q.getAll

    assert(q.getSize === 0)
    assert(everything.length === 3)
    assert(everything(0) === e1)
    assert(everything(1) === e2)
    assert(everything(2) === e3)
  }

  test("the queue does not hang on random concurrent interaction") {
    val n_threads = 16
    val capacity = 3
    val q = new BlockingEventQueue[Integer](capacity)

    val enqueue = () => { q.enqueue(generateIntegerEvent) }
    val dequeue = () => { q.dequeue }

    val threads = Random
      .shuffle(
        Seq.fill(n_threads / 2)(enqueue) ++ Seq.fill(n_threads / 2)(dequeue)
      )
      .map(task =>
        new Thread {
          override def run(): Unit = { task() }
        }
      )

    threads.foreach(t => t.start())
    threads.foreach(t => t.join(500))
    assert(
      q.getSize == 0,
      s"the queue should be empty after ${n_threads / 2} enqueues and dequeues"
    )
  }

}
