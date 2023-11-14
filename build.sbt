name := "CS-E4110-assignment"

version := "22.11.01"

scalaVersion := "2.13.6"

libraryDependencies += "org.scalatest" %% "scalatest-funsuite" % "3.2.9"

// Define the main method
mainClass in (Compile, run) := Some("hangman.HangmanGame")
