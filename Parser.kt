package Interpreter

import kotlin.system.exitProcess

fun RaiseError(msg: String) {
    println (msg)
    exitProcess(0)
}

class ParseState(val success: Boolean, val node: Node = Node(), val error : String = "")
typealias ParseFunction = () -> ParseState

class Parser(val tokens: List<Token>) {
    var index = 0

    fun MoveIndexTo(n: Int) {
        index = n
    }

    fun PeekValue(value: String): Boolean {
        if (index >= tokens.size) return false
        return value == tokens[index].value
    }

    fun PeekType(type: String): Boolean {
        if (index >= tokens.size) return false
        return type == tokens[index].type
    }

    fun Value(value: String, fmap: (Token) -> Node = { token -> Node() }): ParseState {
        if (!PeekValue(value)) {
            val error = if (index < tokens.size) "line ${tokens[index].line}: unexpected value ${tokens[index].value}. expected $value" else "expected $value"
            return ParseState(false, error = error)
        }

        val token = tokens[index]
        index += 1
        return ParseState(true, fmap(token))
    }

    fun Type(type: String, fmap: (Token) -> Node = { token -> Node() }): ParseState {
        if (!PeekType(type)) {
            val error = if (index < tokens.size) "line ${tokens[index].line}: unexpected token ${tokens[index].type}. expected $type" else "expected $type"
            return ParseState(false, error = error)
        }

        val token = tokens[index]
        index += 1
        return ParseState(true, fmap(token))
    }

    fun OneOf(vararg parsers : ParseFunction): ParseState {
        for (parser in parsers) {
            val startIndex = index
            val result = parser()
            if (result.success) {
                return result
            }
            MoveIndexTo(startIndex)
        }

        val error = if (index < tokens.size) "line ${tokens[index].line}: unexpected token ${tokens[index].value}" else "unexpected eof"
        return ParseState(false, error=error)
    }

    fun Expect(parser: ParseFunction, callback: (ParseState) -> ParseState): ParseState {
        val startIndex = index

        val result = parser()
        if (result.success) return callback(result)

        RaiseError(result.error)

        MoveIndexTo(startIndex)
        return result
    }

    fun MayExpect(parser: ParseFunction, callback: (ParseState) -> ParseState): ParseState {
        val startIndex = index

        val result = parser()
        if (result.success) return callback(result)

        MoveIndexTo(startIndex)
        return result
    }

    fun SepBy(sep: String, parser: ParseFunction): List<Node> {
        var list: List<Node> = listOf()

        MayExpect(parser, fun (state: ParseState): ParseState {
            list += state.node
            var prev = state

            while (prev.success && Value(sep).success) {
                prev = Expect(parser, fun (curr: ParseState): ParseState {
                    list += curr.node
                    return curr
                })
            }

            return prev
        })

        return list
    }

    fun List(): ParseState {
        return MayExpect({ Value("[") }, fun (_: ParseState): ParseState {
            val elems = SepBy(",", ::Expr)
            return Expect({ Value("]") }) { ParseState(true, ListNode(elems) ) }
        })
    }

    fun Fun(): ParseState {
        return MayExpect({ Value("fun") }) {
            Expect({ Value("(") }, fun (_: ParseState): ParseState {
                val initialIndex = index
                SepBy(",", { Type("identifier", { token -> Ident(token.value) }) })
                val params = tokens.subList(initialIndex, index).map { it.value }.filter { it != "," }

                return Expect({ Value(")") }) {
                    Expect(::ParseBlock) { block ->
                        ParseState(true, FunNode(params, block.node))
                    }
                }
            })
        }
    }

    fun FunCall(): ParseState {
        return OneOf({ MayExpect(::Literal, fun (expr: ParseState): ParseState {
            var result = MayExpect({ Value("(") }, fun (_: ParseState): ParseState {
                val args = SepBy(",", ::Expr)
                return Expect({ Value(")") }) { ParseState(true, Apply(expr.node, args) ) }
            })

            while (result.success && PeekValue("(")) {
                result = MayExpect({ Value("(") }, fun (_: ParseState): ParseState {
                    val args = SepBy(",", ::Expr)
                    return Expect({ Value(")") }) { ParseState(true, Apply(result.node, args) ) }
                })
            }

            return result
        }) }, ::Literal)
    }

    fun Literal(): ParseState {
        val number = { Type("number", { token -> Number(token.value.toDouble()) }) }
        val string = { Type("string", { token -> Str(token.value) }) }
        val ident  = { Type("identifier", { token -> Ident(token.value) }) }
        val trueBool  = { Value("true", { token -> Bool(true) }) }
        val falseBool  = { Value("false", { token -> Bool(false) }) }
        val paren  = {
            MayExpect({ Value("(") }) {
                Expect(::Expr) {expr ->
                    Expect({ Value(")") }) { expr }
                }
            }
        }

        return OneOf(number, string, trueBool, falseBool, ::Fun, ident, ::List, paren)
    }

    fun ParseBinOp(op: String, SubExpr: ParseFunction): ParseState {
        return MayExpect(SubExpr, fun (state: ParseState): ParseState {
            var left = state

            while (left.success && Value(op).success) {
                left = Expect(SubExpr) { right ->
                    ParseState(true, BinOp(op, left.node, right.node))
                }
            }

            return left
        })
    }

    fun Div(): ParseState = ParseBinOp("/", ::FunCall)
    fun Mul(): ParseState = ParseBinOp("*", ::Div)
    fun Add(): ParseState = ParseBinOp("+", ::Mul)
    fun Sub(): ParseState = ParseBinOp("-", ::Add)
    fun Eq(): ParseState = ParseBinOp("==", ::Sub)
    fun Geq(): ParseState = ParseBinOp(">=", ::Eq)
    fun Leq(): ParseState = ParseBinOp("<=", ::Geq)
    fun Less(): ParseState = ParseBinOp("<", ::Leq)
    fun Greater(): ParseState = ParseBinOp(">", ::Less)
    fun And(): ParseState = ParseBinOp("and", ::Greater)
    fun Or(): ParseState = ParseBinOp(">", ::And)
    fun Expr(): ParseState = Or()

    fun ParseAssign(): ParseState {
        return MayExpect({ Type("identifier") }, fun (_: ParseState): ParseState {
            val id = tokens[index - 1].value
            return MayExpect({ Value("=") }) {
                Expect(::Expr) { expr ->
                    ParseState(true, Assign(id, expr.node))
                }
            }
        })
    }

    fun ParseNext(): ParseState {
        return Expect({ OneOf(::ParseIf, ::ParseWhile, ::ParseFor, ::ParseAssign, ::Expr) }) { state ->
            state
        }
    }

    fun ParseBlock(): ParseState {
        var list: List<Node> = listOf()

        Expect({ Value("{") }, fun (stateA: ParseState): ParseState {
            while (!PeekValue("}")) {
                Expect({ ParseNext() }, fun (stateB: ParseState): ParseState {
                    list += stateB.node
                    return stateB
                })
            }

            return Expect({ Value("}") }) { stateC -> stateC }
        })

        return ParseState(true, Body(list))
    }

    fun ParseWhile(): ParseState {
        return MayExpect({ Value("while") }) {
            Expect(::Expr) { expr ->
                Expect(::ParseBlock) { block ->
                    ParseState(true, WhileNode(expr.node, block.node))
                }
            }
        }
    }

    fun ParseFor(): ParseState {
        return MayExpect({ Value("for") }) {
            Expect({ Type("identifier") }) {
                Expect({ Value("in") }, fun (_: ParseState): ParseState {
                    val id = tokens[index - 2].value
                    return Expect(::Expr) { iter ->
                        Expect(::ParseBlock) { block ->
                            ParseState(true, ForNode(id, iter.node, block.node))
                        }
                    }
                })
            }
        }
    }

    fun ParseIf(): ParseState {
        return MayExpect({ Value("if") }) {
            Expect(::Expr) { expr ->
                Expect(::ParseBlock) { then ->
                    if (PeekValue("else")) Expect({ Value("else") }) {
                        Expect({ OneOf(::ParseIf, ::ParseBlock) }) { el ->
                            ParseState(true, IfNode(expr.node, then.node, el.node))
                        }
                    } else ParseState(true, IfNode(expr.node, then.node, Node()))
                }
            }
        }
    }

    fun Parse(): Body {
        var nodes: List<Node> = listOf()

        while (index < tokens.size) {
            Expect({ ParseNext() }, fun (state: ParseState): ParseState {
                nodes += state.node
                return state
            })
        }

        return Body(nodes)
    }
}