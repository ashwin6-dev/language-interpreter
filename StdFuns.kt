package Interpreter

val stdEnv: Env = mutableMapOf(
    "println" to StdFun(::Println),
    "length" to StdFun(::Length)
)

fun Println(args: List<Node>, env: Env): Env {
    var line = ""
    for (arg in args) {
        line += "$arg "
    }

    println (line)

    return env
}

fun Length(args: List<Node>, env: Env): Env {
    val node = args[0]
    return when (node) {
        is Str -> {
            JoinEnvs(env, mutableMapOf(RETURN to Number(node.s.length.toDouble())))
        }
        is ListNode -> {
            JoinEnvs(env, mutableMapOf(RETURN to Number(node.list.size.toDouble())))
        }
        else -> throw Error("bad")
    }
}