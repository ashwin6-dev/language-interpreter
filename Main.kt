package Interpreter
import java.io.File

fun main(args: Array<String>) {
    val src = File(args[0]).readText()
    val tokenizer = Tokenizer(src)
    val tokens = tokenizer.Tokenize()
    val parser = Parser(tokens)
    val tree = parser.Parse()
    val env = Exec(tree, stdEnv)
}