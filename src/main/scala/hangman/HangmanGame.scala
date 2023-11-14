// group 1
// 123456 Firstname Lastname
// 654321 Firstname Lastname

package hangman

import reactor.api.{EventHandler, Handle}
import reactor.Dispatcher

class HangmanGame(val hiddenWord: String, val initialGuessCount: Int) {
  require(hiddenWord != null && hiddenWord.length > 0)
  require(initialGuessCount > 0)

  // TODO implement

}

object HangmanGame {

  def main(args: Array[String]): Unit = {
    val word: String = args(0) // first program argument
    val guessCount: Int = args(1).toInt // second program argument

    val game: HangmanGame = new HangmanGame(word, guessCount)
    // TODO start the game
  }

}
