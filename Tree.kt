package Interpreter

typealias Block = List<Node>
open class Node

class Number(val n: Double): Node() {
    override fun toString(): String {
        return n.toString()
    }
}

class Str(val s: String): Node() {
    override fun toString(): String {
        return s
    }
}

class Bool(val b: Boolean): Node() {
    override fun toString(): String {
        return if (b) "true" else "false"
    }
}

class Ident(val id: String): Node()

class ListNode(val list: List<Node>): Node() {
    override fun toString(): String {
        var s = ""
        for (node in list) {
            s += "$node"
        }

        return s
    }
}

class FunNode(val params: List<String>, val body: Node): Node()
class Assign(val id: String, val expr: Node): Node()
class Apply(val f: Node, val args: List<Node>): Node()
class BinOp(val op: String, val left: Node, val right: Node): Node()
class WhileNode(val expr: Node, val body: Node): Node()
class ForNode(val id: String, val iter: Node, val body: Node): Node()
class IfNode(val expr: Node, val trueBranch: Node, val falseBranch: Node): Node()
class Body(val block: Block): Node()
class StdFun(val f: (args: List<Node>, env: Env) -> Env): Node()