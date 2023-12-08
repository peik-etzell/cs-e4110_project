// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package hangman

import reactor.api.{EventHandler, Handle}
import reactor.Dispatcher
import hangman.util.AcceptHandle
import hangman.util.TCPTextHandle
import java.net.Socket
import scala.collection.mutable.HashSet

class HangmanGame(val hiddenWord: String, val initialGuessCount: Int) {
  require(hiddenWord != null && hiddenWord.length > 0)
  require(initialGuessCount > 0)

  private val dispatcher = new Dispatcher()
  private val socketHandler = new SocketHandler()
  private val playerHandlers = new HashSet[PlayerHandler]

  private var state =
    new GameState(hiddenWord, initialGuessCount, Set.empty[Char])

  def start() = {
    dispatcher.addHandler(socketHandler)
    dispatcher.handleEvents()
  }

  private def guess(c: Char, guesser: String) = {
    state = state.makeGuess(c)
    playerHandlers filter { _.registered } foreach {
      _ << s"${c} ${state.getMaskedWord} ${state.guessCount} ${guesser}"
    }
    if (state.isGameOver) { stop() }
  }

  private def stop() = {
    playerHandlers foreach { p =>
      dispatcher.removeHandler(p)
      p.close()
    }
    dispatcher.removeHandler(socketHandler)
    socketHandler.close()
  }

  private class SocketHandler() extends EventHandler[Socket] {
    private val handle = new AcceptHandle()

    def close() = {
      try { handle.close(); }
      catch { case _: Throwable => }
    }

    override def getHandle: Handle[Socket] = { handle }
    override def handleEvent(evt: Socket): Unit = {
      val p = new PlayerHandler(evt)
      playerHandlers += p
      dispatcher.addHandler(p)
    }
  }

  private class PlayerHandler(socket: Socket) extends EventHandler[String] {
    private val handle = new TCPTextHandle(socket)
    private var nameOption: Option[String] = None

    def close(): Unit = {
      try { socket.close(); }
      catch { case _: Throwable => }
    }

    def <<(msg: String): Unit = {
      handle.write(msg)
    }

    def registered: Boolean = { nameOption.isDefined }

    override def getHandle: Handle[String] = { handle }
    override def handleEvent(evt: String): Unit = {
      // Is player registered yet?
      nameOption match {
        // No => msg is the player name
        case None => {
          nameOption = Some(evt)
          playerHandlers += this
          handle.write(state.getMaskedWord)
        }
        // Yes => msg is a guess
        case Some(name) => {
          evt.headOption match {
            case Some(char) => guess(char, name)
            case None       =>
          }
        }
      }
    }
  }
}

object HangmanGame {

  def main(args: Array[String]): Unit = {
    val word: String = args(0) // first program argument
    val guessCount: Int = args(1).toInt // second program argument
    val game: HangmanGame = new HangmanGame(word, guessCount)

    game.start()
  }
}
