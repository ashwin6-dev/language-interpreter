package Interpreter

class Token(val value: String, val type: String, val line: Int = 0) {
    override fun toString(): String {
        return "Token $type $value "
    }
}

class TokenizerState(val success: Boolean, val token: Token)

class Tokenizer(val src: String) {
    var index = 0
    var line = 1

    fun TakeWhile(p: (c: Char) -> Boolean ): String {
        if (index >= src.length) return ""

        val match = src.substring(index).takeWhile { p(it) }
        index += match.length

        return match
    }

    fun CaptureSequence(seq: String): String {
        if (index < src.length && src.substring(index).startsWith(seq)) {
            index += seq.length
            return seq
        }

        return ""
    }

    fun TokenizeNumber(): TokenizerState {
        var tokenValue: String = ""
        val minuses: String = TakeWhile { c -> c == '-' }

        if (minuses.length > 1) {
            return TokenizerState(false, Token("", ""))
        }

        tokenValue += minuses + TakeWhile { c -> c.isDigit() } + TakeWhile { c -> c == '.' } + TakeWhile { c -> c.isDigit() }

        return TokenizerState(tokenValue != "-" && tokenValue != "", Token(tokenValue, "number", line))
    }

    fun TokenizeIdentifier(): TokenizerState {
        var tokenValue: String = ""
        tokenValue += TakeWhile { c -> c.isLetter() } + TakeWhile { c -> c.isLetterOrDigit() }

        return TokenizerState(tokenValue != "", Token(tokenValue, "identifier", line))
    }

    fun TokenizeString(): TokenizerState {
        var tokenValue: String = ""

        if (CaptureSequence("\"") != "") {
            tokenValue += TakeWhile { c -> c != '"' }
            return if (CaptureSequence("\"") != "")
                TokenizerState(true, Token(tokenValue, "string", line))
            else {
                RaiseError("line $line: string is unclosed")
                TokenizerState(false, Token("", ""))
            }
        }

        return TokenizerState(false, Token("", ""))
    }

    fun TokenizeOperator(): TokenizerState {
        var operators: List<String> = listOf(
            "==", "!=", ">=", "<=", ">", "<", "=", "+", "-", "*", "/", "(", ")", "[", "]",
            "{", "}", ","
        )

        for (operator in operators) {
            val result = CaptureSequence(operator)

            if (result != "") {
                return TokenizerState(true, Token(operator, "operator", line))
            }
        }

        return TokenizerState(false, Token("", ""))
    }

    fun Tokenize(): List<Token> {
        var tokens: List<Token> = listOf()
        var tokenizers = listOf(
            ::TokenizeNumber,
            ::TokenizeIdentifier,
            ::TokenizeString,
            ::TokenizeOperator
        )

        while (index < src.length) {
            var incIndex = true
            var initialIndex = index
            if (src[index] == '\n') line += 1

            for (tokenizer in tokenizers) {
                val result = tokenizer()

                if (result.success) {
                    tokens += listOf(result.token)
                    incIndex = false
                    break
                }else {
                    index = initialIndex
                }
            }

            if (incIndex) index += 1
        }

        return tokens
    }
}