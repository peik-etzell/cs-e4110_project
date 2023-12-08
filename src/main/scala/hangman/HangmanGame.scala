// group 13
// 793317 Peik Etzell
// 100701063 Christian Häggblom

package hangman

import reactor.api.{EventHandler, Handle}
import reactor.Dispatcher
import hangman.util.AcceptHandle
import hangman.util.TCPTextHandle
import java.net.Socket

class HangmanGame(val hiddenWord: String, val initialGuessCount: Int) {
  require(hiddenWord != null && hiddenWord.length > 0)
  require(initialGuessCount > 0)

  private var state =
    new GameState(hiddenWord, initialGuessCount, Set.empty[Char])

  def guess(guess: Char) = {
    state = state.makeGuess(guess)
  }
}

object HangmanGame {
  private val dispatcher = new Dispatcher()

  def main(args: Array[String]): Unit = {
    val word: String = args(0) // first program argument
    val guessCount: Int = args(1).toInt // second program argument

    // Optionally, pass port number as command line parameter
    val port =
      if (args.length >= 3) {
        args.lastOption.flatMap(s => s.toIntOption)
      } else None

    dispatcher.addHandler(new NewConnectionHandler)
    dispatcher.handleEvents()

    val game: HangmanGame = new HangmanGame(word, guessCount)
  }

  class NewConnectionHandler extends EventHandler[Socket] {
    private val handle = new AcceptHandle
    override def getHandle: Handle[Socket] = { handle }
    override def handleEvent(evt: Socket): Unit = {
      println("New connection")
      dispatcher.addHandler(new ClientHandler(evt))
    }
  }

  class ClientHandler(socket: Socket) extends EventHandler[String] {
    private val handle = new TCPTextHandle(socket)
    override def getHandle: Handle[String] = { handle }
    override def handleEvent(evt: String): Unit = {
      println(s"Got event: '${evt}'")
    }
  }
}
