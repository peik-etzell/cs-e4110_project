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
/**
 * Hangman Game: Documentation
 *
 * The Hangman server utilizes a single-threaded Reactor pattern for managing concurrency, 
 * specifically designed to handle multiple client interactions and network events efficiently 
 * without the complexity of multi-threading or synchronization.
 *
 * 1. Dispatcher Role:
 *    - The 'Dispatcher' class is the central point in the Reactor pattern implementation.
 *    - It manages a queue of events and dispatches them to the appropriate handlers.
 *    - This design simplifies the concurrency model, as the dispatcher serially processes events in the main thread.
 *
 * 2. EventHandler Abstraction:
 *    - `SocketHandler` and `PlayerHandler` are implementations of the `EventHandler`.
 *    - `SocketHandler` deals with incoming socket connections. It accepts new connections and creates a new `PlayerHandler` for each player.
 *    - `PlayerHandler` manages player interactions, including receiving guesses and sending game state updates.
 *    - This separation of concerns keeps the code modular and maintainable.
 *
 * 3. Single-Threaded Event Processing:
 *    - Both `SocketHandler` and `PlayerHandler` process events in a non-blocking manner.
 *    - The main thread, via the dispatcher, invokes handlers' `handleEvent` method, ensuring all event processing happens in a single thread.
 *    - This approach aligns with the requirement of avoiding direct multi-threading or synchronization mechanisms in the Hangman package.
 *
 * 4. Game State Management:
 *    - A shared `GameState` object is used to track the current state of the game, including the hidden word, remaining guesses, and guessed letters.
 *    - `GameState` updates happen in the `guess` method of `HangmanGame`, triggered by player actions, ensuring thread safety as it's accessed only in the main thread.
 *
 * 5. Network Communication:
 *    - Network communication is handled using TCP sockets, with `TCPTextHandle` managing the sending and receiving of messages.
 *    - Each `PlayerHandler` has a `TCPTextHandle` instance, isolating network operations at the player level.
 *
 * 6. Clean Resource Management:
 *    - The `stop` method in `HangmanGame` ensures clean termination of the game.
 *    - It closes all player handlers and the socket handler, releasing network resources gracefully.
 *    - This method is invoked once the game ends, either by guessing the word correctly or exhausting all attempts.
 *
 * This design, centered around the Reactor pattern with a dispatcher and event handlers, effectively manages concurrency in the Hangman game server. 
 * It ensures responsive handling of multiple client connections and game events, within the constraints of a single-threaded application environment.
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

// Only starts a new HangmanGame, which finishes when the game is over.
object HangmanGame {

  def main(args: Array[String]): Unit = {
    val word: String = args(0) // first program argument
    val guessCount: Int = args(1).toInt // second program argument
    val game: HangmanGame = new HangmanGame(word, guessCount)

    game.start()
  }
}
