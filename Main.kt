package Interpreter
import java.io.File

fun main(args: Array<String>) {
    val src = File(args[0]).readText()
    val t = Tokenizer(src)
    val tokens = t.Tokenize()
    val p = Parser(tokens)
    val n = p.Parse()
    val env = Exec(n, stdEnv)
}