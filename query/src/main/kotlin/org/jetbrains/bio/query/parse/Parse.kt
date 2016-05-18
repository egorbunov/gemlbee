package org.jetbrains.bio.query.parse

import org.jetbrains.bio.util.*
import java.util.*

/**
 * Recursive parser (with backtracking) for queries over tracks
 *
 * @author Egor Gorbunov
 * @since 01.05.16
 */

class ParseException(msg: String, errorOffset: Int, text: String): Exception(msg) {
}

class LangParser(text: String,
                 val arithmeticTracks: Map<String, ArithmeticTrack>, // already known arithmetic (bb) tracks
                 val predicateTracks: Map<String, PredicateTrack>) { // already known predicate (location aware) tracks

    var tokenizer = Tokenizer(text, keywordSet)

    object Keywords {
        val ASSIGN = Lexeme(":=")
        val LEQ = Lexeme("<=")
        val GEQ = Lexeme(">=")
        val EQ = Lexeme("==")
        val LE = Lexeme("<")
        val GE = Lexeme(">")
        val NEQ = Lexeme("!=")
        val PLUS = Lexeme("+")
        val MINUS = Lexeme("-")
        val MUL = Lexeme("*")
        val DIV = Lexeme("/")
        val LPAREN = Lexeme("(")
        val RPAREN = Lexeme(")")
        val AND = RegexLexeme("(and|AND)")
        val OR = RegexLexeme("(or|OR)")
        val NOT = RegexLexeme("(not|NOT)")
        val IF = RegexLexeme("(if|IF)")
        val ELSE = RegexLexeme("(else|ELSE)")
        val THEN = RegexLexeme("(then|THEN)")
        val SHOW = RegexLexeme("(show|SHOW)")
        val FALSE = RegexLexeme("(false|FALSE)")
        val TRUE = RegexLexeme("(true|TRUE)")
    }

    companion object {
        val keywordSet = with(Keywords) {
            setOf(NEQ, LEQ, GEQ, EQ, LE, GE, AND, OR, NOT, IF, ELSE, THEN, ASSIGN, SHOW, PLUS, MINUS, MUL, DIV,
                    LPAREN, RPAREN, FALSE, TRUE)
        }

        val relationOpSet = with(Keywords) {
            setOf(LEQ, GEQ, EQ, LE, GE, NEQ)
        }

        /**
         * Get matches for only given keywords
         */
        fun getMatches(str: String, keywords: Set<Lexeme>): List<Match> {
            val kws = ArrayList<Match>()
            val tokenizer = Tokenizer(str, keywordSet)
            while (tokenizer.fetch() != null) {
                val lexeme = tokenizer.fetch()
                if (lexeme in keywords) {
                    kws.add(tokenizer.match!!)
                }
                tokenizer.next()
            }
            return kws
        }
    }

    fun parse(): Statement {
        return parseStatement()!! // TODO: throw exception in case of nullptr
    }

    /**
     * Root parsing routine
     */
    private fun parseStatement(): Statement? {
        tokenizer.addBookmark()

        /**
         * Don't be afraid of ?: chains, please
         */
        val result: Statement? = parseAssign() ?: {
            // assign parsing failed
            tokenizer.returnBack()
            parseShow() ?: {
                // show parsing failed
                tokenizer.returnBack()
                parseArithmeticTrack() ?: {
                    // arithmetic track parsing failed
                    tokenizer.returnBack()
                    parsePredicate() ?: {
                        // predicate parsing failed
                        tokenizer.popBookmark()
                        throw ParseException(
                                "Cannot parse track expression, error on suffix: " +
                                        "${tokenizer.text.substring(tokenizer.tokenOffset)}",
                                tokenizer.tokenOffset,
                                tokenizer.text)
                    }()
                }()
            }()
        }()
        if (!tokenizer.atEnd()) {
            throw ParseException("Not whole sentence parsed =(", tokenizer.tokenOffset, tokenizer.text)
        }

        tokenizer.popBookmark()
        return result
    }

    private fun parseAssign(): Statement? {
        val id = parseIdentifier() ?: return null
        if (!tokenizer.softCheck(Keywords.ASSIGN)) {
            return null
        }

        tokenizer.addBookmark()
        var expr: GeneratedTrack? = parseArithmeticTrack()
        if (expr == null || !tokenizer.atEnd()) {
            tokenizer.returnBack()
            expr = parsePredicate()
            tokenizer.popBookmark()
        }
        if (expr == null) {
            return null
        }
        return AssignStatement(id, expr)
    }

    private fun parseShow(): Statement? {
        if (!tokenizer.softCheck(Keywords.SHOW)) {
            return null
        }
        tokenizer.addBookmark()
        val id = parseNamedArithmeticTrack() ?: {
            tokenizer.returnBack()
            tokenizer.popBookmark()
            parseNamedPredicateTrack()
        }() ?: return null
        return ShowTrackStatement(id)
    }

    private fun parseArithmeticTrack(): ArithmeticTrack? {
        var lhs = parseArithmeticTerm() ?: null

        var lexeme = tokenizer.fetch()
        while (lexeme == Keywords.PLUS || lexeme == Keywords.MINUS) {
            tokenizer.next()
            val rhs = parseArithmeticTerm() ?: return null
            val op = if (lexeme == Keywords.PLUS) ArithmeticOp.PLUS else ArithmeticOp.MINUS
            lhs = BinaryArithmeticTrack(op, lhs!!, rhs)
            lexeme = tokenizer.fetch()
        }

        return lhs
    }

    private fun parseArithmeticTerm(): ArithmeticTrack? {
        var lhs = parseArithmeticFactor() ?: return null

        var lexeme = tokenizer.fetch()
        while (lexeme == Keywords.MUL || lexeme == Keywords.DIV) {
            tokenizer.next()
            val rhs = parseArithmeticFactor() ?: return null
            val op = if (lexeme == Keywords.MUL) ArithmeticOp.MUL else ArithmeticOp.DIV
            lhs = BinaryArithmeticTrack(op, lhs, rhs)
            lexeme = tokenizer.fetch()
        }

        return lhs
    }

    private fun parseArithmeticFactor(): ArithmeticTrack? {
        val lexeme = tokenizer.fetch()

        val res: ArithmeticTrack?
        when (lexeme) {
            Keywords.LPAREN -> {
                tokenizer.next()
                res = parseArithmeticTrack()
                if (!tokenizer.softCheck(Keywords.RPAREN)) return null
            }
            Keywords.IF -> {
                res = parseIfStatement()
            }
            else -> {
                tokenizer.addBookmark()
                res = parseDouble() ?: {
                    // double parsing failed
                    tokenizer.returnBack()
                    parseNamedArithmeticTrack()
                }()
                tokenizer.popBookmark()
            }
        }
        return res
    }

    private fun parseIfStatement(): IfStatementTrack? {
        if (!tokenizer.softCheck(Keywords.IF)) {
            return null
        }

        val predicate = parsePredicate() ?: return null
        if (!tokenizer.softCheck(Keywords.THEN)) {
            return null
        }

        val ifTrue = parseArithmeticTrack() ?: return null
        if (!tokenizer.softCheck(Keywords.ELSE)) {
            return null
        }

        val ifFalse = parseArithmeticTrack() ?: return null
        return IfStatementTrack(predicate, ifTrue, ifFalse)
    }

    private fun parseDouble(): ArithmeticTrack? {
        try {
            val res = tokenizer.parseDouble()
            return NumericTrack(res)
        } catch (e: IllegalStateException) {
            return null
        }
    }

    private fun parseIdentifier(): String? {
        val idRegex = Regex("[a-zA-Z_][\\w]*")
        val lexeme = tokenizer.fetch()
        if (lexeme in keywordSet || !idRegex.matches(lexeme.toString())) {
            return null
        }
        tokenizer.next()
        return lexeme.toString()
    }

    private fun parseNamedArithmeticTrack(): NamedArithmeticTrack? {
        val id = parseIdentifier() ?: return null
        if (arithmeticTracks[id] == null) return null
        return NamedArithmeticTrack(id, arithmeticTracks[id]!!)
    }

    private fun parseNamedPredicateTrack(): NamedPredicateTrack? {
        val id = parseIdentifier() ?: return null
        if (predicateTracks[id] == null) return null
        return NamedPredicateTrack(id, predicateTracks[id]!!)
    }

    private fun parsePredicate(): PredicateTrack? {
        var lhs = parsePredicateTerm() ?: return null

        var lexeme = tokenizer.fetch()
        while (lexeme == Keywords.OR) {
            tokenizer.next()
            val rhs = parsePredicateTerm() ?: return null
            lhs = OrPredicateTrack(lhs, rhs)
            lexeme = tokenizer.fetch()
        }

        return lhs
    }

    private fun parsePredicateTerm(): PredicateTrack? {
        var lhs = parseNotFactor() ?: return null

        var lexeme = tokenizer.fetch()
        while (lexeme == Keywords.AND) {
            tokenizer.next()
            val rhs = parseNotFactor() ?: return null
            lhs = AndPredicateTrack(lhs, rhs)
            lexeme = tokenizer.fetch()
        }

        return lhs
    }

    private fun parseNotFactor(): PredicateTrack? {
        val lexeme = tokenizer.fetch()
        val res: PredicateTrack?

        if (lexeme == Keywords.NOT) {
            tokenizer.next()
            res = parsePredicateFactor() ?: return null
            return NotPredicateTrack(res)
        }

        return parsePredicateFactor()
    }

    private fun parseParenthesizedPredicate(): PredicateTrack? {
        if (!tokenizer.softCheck(Keywords.LPAREN)) {
            return null
        }
        val res = parsePredicate() ?: return null
        if (!tokenizer.softCheck(Keywords.RPAREN)) {
            return null
        }
        return res
    }

    private fun parsePredicateFactor(): PredicateTrack? {
        val lexeme = tokenizer.fetch()

        val res: PredicateTrack?
        when (lexeme) {
            Keywords.LPAREN -> {
                tokenizer.addBookmark()
                res = parseParenthesizedPredicate() ?: {
                    // case if relational predicate contains operands in parenthesis
                    tokenizer.returnBack()
                    parseRelationPredicate()
                }()
                tokenizer.popBookmark()
            }
            Keywords.FALSE -> {
                res = FalsePredicateTrack()
                tokenizer.next()
            }
            Keywords.TRUE -> {
                res = TruePredicateTrack()
                tokenizer.next()
            }
            else -> {
                tokenizer.addBookmark()
                res = parseNamedPredicateTrack() ?: {
                    // case it is not named predicate track
                    tokenizer.returnBack()
                    parseRelationPredicate()
                }()
                tokenizer.popBookmark()
            }
        }
        return res
    }


    private fun parseRelationPredicate(): PredicateTrack? {
        val lhs = parseArithmeticTrack() ?: return null

        val opLexeme = tokenizer.fetch()
        if (opLexeme !in relationOpSet) {
            return null
        }
        tokenizer.next()
        val rhs = parseArithmeticTrack() ?: return null
        return RelationPredicateTrack(RelationOp.fromString(opLexeme.toString())!!, lhs, rhs)
    }
}