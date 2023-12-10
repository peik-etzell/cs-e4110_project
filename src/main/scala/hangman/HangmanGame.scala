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

/** Hangman Game: Documentation
  *
  * The Hangman server utilizes a single-threaded Reactor pattern for managing
  * concurrency, Handles multiple client interactions and network events
  * concurrently.
  *
  * There are two core handlers from ./util/ that spin the dispatcher:
  * AcceptHandle and TCPTextHandle. The singleton SocketHandler wraps an
  * AcceptHandle, and creates new PlayerHandlers every time a new client joins.
  * The game object keeps track of all created handlers, such that it can remove
  * them from the dispatcher when the game has come to a finished state.
  *
  * The PlayerHandler wraps a TCPTextHandle, and listens to input from the
  * player. There could have been a split into a RegisteringHandler and then a
  * PlayingHandler (or similar), such that the RegisteringHandler got the name
  * and quit, creating an initialized PlayingHandler (with player name already
  * set). Instead we decided to do this in one object that has internal state:
  * namely the PlayerHandler has the `nameOption` member variable, and handles
  * incoming messages differently depending on if the name is initialized (if
  * the player is registered). The PlayerHandler ignores newlines and extra
  * characters.
  *
  * When the game is finished, the server will always immediately exit cleanly,
  * as all handlers are removed one-by-one.
  */

class HangmanGame(val hiddenWord: String, val initialGuessCount: Int) {
  require(hiddenWord != null && hiddenWord.length > 0)
  require(initialGuessCount > 0)

  private val dispatcher = new Dispatcher()
  private val playerHandlers = new HashSet[PlayerHandler]

  private var state =
    new GameState(hiddenWord, initialGuessCount, Set.empty[Char])

  // Initializes and starts the dispatcher.
  def start() = {
    dispatcher.addHandler(SocketHandler)
    dispatcher.handleEvents()
  }

  // Changes the internal state, outputs the new status, then checks if
  // the game should exit.
  private def guess(c: Char, guesser: String) = {
    state = state.makeGuess(c)
    playerHandlers filter { _.registered } foreach {
      _ << s"${c} ${state.getMaskedWord} ${state.guessCount} ${guesser}"
    }
    if (state.isGameOver) { stop() }
  }

  // To get a clean exit, we need to remove all handlers from the dipatcher.
  // The dispatcher then exits, as the underlying handlers return on their closes().
  private def stop() = {
    playerHandlers foreach { p =>
      dispatcher.removeHandler(p)
      p.close()
    }
    dispatcher.removeHandler(SocketHandler)
    SocketHandler.close()
  }

  // Singleton object that enables new players to join the game.
  // When a player connects to the port, a new PlayerHandler is created to
  // communicate with the player.
  private object SocketHandler extends EventHandler[Socket] {
    private val handle = new AcceptHandle()

    // Closes the handle, which closes its socket,
    // which makes handle.read() return null.
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

  // Object that keeps a connection to a single player. Handles all input from
  // the player, only outputs to the player after registration, otherwise
  // the HangmanGame.guess() method outputs the status updates to all players.
  private class PlayerHandler(socket: Socket) extends EventHandler[String] {
    private val handle = new TCPTextHandle(socket)
    private var nameOption: Option[String] = None

    // Closes the socket, which makes handle.read() return null.
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
          this << s"${state.getMaskedWord} ${state.guessCount}"
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

// Only starts a new HangmanGame, which finishes when the game is over.
object HangmanGame {

  def main(args: Array[String]): Unit = {
    val word: String = args(0) // first program argument
    val guessCount: Int = args(1).toInt // second program argument
    val game: HangmanGame = new HangmanGame(word, guessCount)

    game.start()
  }
}
