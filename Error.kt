package Interpreter

import kotlin.system.exitProcess

fun RaiseError(msg: String) {
    println (msg)
    exitProcess(0)
}