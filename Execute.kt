package Interpreter

typealias Env = MutableMap<String, Node>
val RETURN: String = "%%RETURN"

fun JoinEnvs(envA: Env, envB: Env): Env {
    val joinedEnv = envA.toMutableMap()
    val entries = envB.entries

    for (entry in entries) {
        joinedEnv.put(entry.key, entry.value)
    }

    return joinedEnv
}

fun OuterScope(outerEnv: Env, innerEnv: Env): Env {
    val outerVars = outerEnv.keys
    return outerVars.map(fun (id: String): Pair<String, Node> {
        val v = innerEnv.get(id)
        return if (v != null) id to v else id to Node()
    }).toMap().toMutableMap()
}

fun Exec(node: Node, env: Env): Env = when (node) {
    is Body -> ExecBody(node, env)
    is Assign -> ExecAssign(node, env)
    is WhileNode -> ExecWhile(node, env)
    is IfNode -> ExecIf(node, env)
    is ForNode -> ExecFor(node, env)
    is Number, is Str, is ListNode, is FunNode, is Ident, is Apply, is BinOp -> ExecReturn(node, env)
    else -> env
}

fun ExecReturn(node: Node, env: Env): Env {
    val envCopy = env.toMutableMap()
    val value = Eval(node, env)

    envCopy.put(RETURN, value)

    return envCopy
}

fun ExecBody(body: Body, env: Env): Env = body.block.fold(env) { currEnv, node ->
    Exec(node, currEnv)
}

fun IterateNode(node: Node, env: Env, f: (Node, Env) -> Env): Env = when(node) {
    is ListNode -> if (node.list.size > 0) IterateNode(ListNode(node.list.drop(1)), f(node.list[0], env), f)
                   else env
    is Str -> if (node.s.length > 0) IterateNode(Str(node.s.substring(1)), f(Str(node.s.substring(0, 1)), env), f)
              else env
    else -> throw Error("experession is not iterable")
}

fun ExecFor(node: ForNode, env: Env): Env {
    val id = node.id
    val initialEnv = env
    val iter = Eval(node.iter, env)
    val body = node.body

    return OuterScope(initialEnv, IterateNode(iter, env, fun (elem: Node, givenEnv: Env): Env {
        val copyEnv = givenEnv.toMutableMap()
        copyEnv.put(id, elem)
        return Exec(body, copyEnv)
    }))
}

fun ExecWhile(node: WhileNode, env: Env): Env = when {
    IsTrue(Eval(node.expr, env)) -> ExecWhile(node, Exec(node.body, env))
    else -> env
}

fun ExecIf(node: IfNode, env: Env): Env = when {
    IsTrue(Eval(node.expr, env)) -> Exec(node.trueBranch, env)
    else -> Exec(node.falseBranch, env)
}

fun ExecAssign(node: Assign, env: Env): Env {
    val newEnv = env.toMutableMap()
    val id = node.id
    val expr = Eval(node.expr, env)

    newEnv.put(id, expr)

    return newEnv
}

fun IsTrue(node: Node) = when(node) {
    is Bool -> node.b
    else -> false
}

fun Eval(node: Node, env: Env): Node = when (node) {
    is Number -> node
    is Str -> node
    is ListNode -> node
    is FunNode -> node
    is StdFun -> node
    is Ident -> EvalIdent(node, env)
    is Apply -> EvalApply(node, env)
    is BinOp -> EvalBinOp(node, env)
    else -> throw Error("invalid node for expression")
}

fun GetReturn(env: Env): Node {
    val ret = env.get(RETURN)

    return if (ret != null) ret else Node()
}

fun EvalApply(node: Apply, env: Env): Node {
    val f = Eval(node.f, env)
    val envCopy = env.toMutableMap()
    envCopy.put(RETURN, Node())

    return when (f) {
        is FunNode ->
            GetReturn(Exec(f.body, JoinEnvs(env, f.params.zip(node.args.map({ Eval(it, env) })).toMap().toMutableMap())))
        is StdFun -> GetReturn(f.f(node.args.map({ Eval(it, env) }), env))
        else -> throw Error("not function")
    }
}

fun EvalIdent(id: Ident, env: Env): Node {
    val value = env.get(id.id)

    if (value == null) throw Error("variable ${id.id} is not defined")

    return Eval(value, env)
}

fun EvalBinOp(node: BinOp, env: Env): Node {
    val left = Eval(node.left, env)
    val right = Eval(node.right, env)

    return when (node.op) {
        "+" -> Add(left, right)
        "-" -> Sub(left, right)
        "*" -> Mul(left, right)
        "/" -> Div(left, right)
        "==" -> Eq(left, right)
        ">=" -> Geq(left, right)
        "<=" -> Leq(left, right)
        "<" -> Less(left, right)
        ">" -> Greater(left, right)
        "and" -> And(left, right)
        "or" -> Or(left, right)
        else -> throw Error("invalid op ${node.op}")
    }
}

fun Add(left: Node, right: Node): Node {
    return when {
        left is Number && right is Number -> Number(left.n + right.n)
        left is Str && right is Str -> Str(left.s + right.s)
        else -> throw Error("bad")
    }
}

fun Sub(left: Node, right: Node): Node {
    return when {
        left is Number && right is Number -> Number(left.n - right.n)
        else -> throw Error("bad")
    }
}

fun Mul(left: Node, right: Node): Node {
    return when {
        left is Number && right is Number -> Number(left.n * right.n)
        left is Str && right is Str -> Str(left.s + right.s)
        else -> throw Error("bad")
    }
}

fun Div(left: Node, right: Node): Node = when {
    left is Number && right is Number -> Number(left.n / right.n)
    else -> throw Error("bad")
}

fun Eq(left: Node, right: Node): Node = when {
    left is Number && right is Number -> Bool(left.n == right.n)
    left is Str && right is Str -> Bool(left.s == right.s)
    else -> throw Error("bad")
}

fun Geq(left: Node, right: Node): Node = when {
    left is Number && right is Number -> Bool(left.n >= right.n)
    else -> throw Error("bad")
}

fun Leq(left: Node, right: Node): Node = when {
    left is Number && right is Number -> Bool(left.n <= right.n)
    else -> throw Error("bad")
}

fun Less(left: Node, right: Node): Node = when {
    left is Number && right is Number -> Bool(left.n < right.n)
    else -> throw Error("bad")
}

fun Greater(left: Node, right: Node): Node = when {
    left is Number && right is Number -> Bool(left.n > right.n)
    else -> throw Error("bad")
}

fun And(left: Node, right: Node): Node = when {
    left is Bool && right is Bool -> Bool(left.b && right.b)
    else -> throw Error("bad")
}

fun Or(left: Node, right: Node): Node = when {
    left is Bool && right is Bool -> Bool(left.b || right.b)
    else -> throw Error("bad")
}