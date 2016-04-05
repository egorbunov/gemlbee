package org.jetbrains.bio.util

import com.google.common.primitives.Ints


/**
 * Simple match event for [Tokenizer] and [Lexeme].
 */
data class Match(val lexeme: Lexeme, val start: Int, val end: Int) : Comparable<Match> {
    override fun compareTo(other: Match): Int = Ints.compare(start, other.start)
}

open class Lexeme constructor(val token: String) {
    override fun toString(): String = token

    open fun locate(text: String, offset: Int): Match? {
        val indexOf = text.indexOf(token, offset)
        return if (indexOf != -1) Match(this, indexOf, indexOf + token.length) else null
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Lexeme) return false
        if (token != other.token) return false
        return true
    }

    override fun hashCode(): Int {
        return token.hashCode()
    }
}

class RegexLexeme(regex: String) : Lexeme(regex) {
    override fun locate(text: String, offset: Int): Match? {
        val matchResult = Regex(token).find(text, offset) ?: return null
        return Match(this, matchResult.range.start, matchResult.range.endInclusive + 1)
    }
}

class Tokenizer(val text: String, val keywords: Set<Lexeme>) {

    var match: Match? = null
    var tokenOffset: Int = 0

    fun matchEnd(): Int {
        check(match != null) { "Nothing matched!" }
        return match!!.end
    }

    fun atEnd() = tokenOffset == text.length

    /**
     * Fetches next lexeme among keywords given or returns text, wrapped in [Lexeme] instance.
     */
    fun fetch(): Lexeme? {
        if (match != null) {
            return match!!.lexeme
        }
        // End of text reached
        if (atEnd()) {
            return null
        }
        val keyword = keywords.map {
            it.locate(text, tokenOffset)
        }
                .filterNotNull()
                .sorted()
                .firstOrNull()
        // No more keywords
        if (keyword == null) {
            // Trailing whitespace
            val token = text.substring(tokenOffset).trim()
            if (token.isEmpty()) {
                tokenOffset = text.length
                match = null
            }
            match = Match(Lexeme(token), tokenOffset, text.length)
        } else {
            // If keyword starts on offset, return it, otherwise return lexeme with text
            if (keyword.start == tokenOffset || text.substring(tokenOffset, keyword.start).isBlank())
                match = keyword

            else
            // Text before next lexeme
                match = Match(Lexeme(text.substring(tokenOffset, keyword.start).trim()),
                        tokenOffset, keyword.start)
        }
        return match!!.lexeme
    }

    /**
     * Moves to the next lexeme.
     */
    fun next(): Lexeme? {
        if (match != null) {
            tokenOffset = match!!.end
            match = null
        }
        return fetch()
    }

    /**
     * Enforces match
     */
    fun lookahead(match: Match) {
        tokenOffset = match.start
        this.match = match
    }

    /**
     * Checks for lexeme and moves forward.
     */
    fun check(expected: Lexeme) {
        val lexeme = fetch()
        check(lexeme == expected) { "Expected: $expected but got: $lexeme\n$this" }
        next()
    }

    fun checkEnd() {
        // Check everything is parsed
        check(atEnd()) { "Not all the params text was parsed $this" }
    }

    override fun toString(): String = "Text: $text; Offset: $tokenOffset; Text at offset: ${text.substring(tokenOffset)}; Match: $match"

    companion object {
        // Step notation, i.e. each number can be replaced with {start,end,step}.
        val LBRACE = Lexeme("{")
        val RBRACE = Lexeme("}")
        val COMMA = Lexeme(",")
    }
}

fun Tokenizer.parseInt(): Int {
    val lexeme = fetch()
    val msg = "Expected integer value. ${this}"
    checkNotNull(lexeme) { msg }
    try {
        return lexeme!!.token.toInt()
    } catch (e: Exception) {
        throw IllegalStateException(msg)
    } finally {
        next()
    }
}

fun Tokenizer.parseDouble(): Double {
    val lexeme = fetch()
    val msg = "Expected double value. ${this}"
    checkNotNull(lexeme) { msg }
    try {
        return lexeme!!.token.toDouble()
    } catch (e: Exception) {
        throw IllegalStateException(msg)
    } finally {
        next()
    }
}

fun Tokenizer.parseIntOrStep(): List<Int> {
    val lexeme = fetch()
    if (lexeme == Tokenizer.LBRACE) {
        next()
        val start = parseInt()
        check(Tokenizer.COMMA)
        val end = parseInt()
        check(Tokenizer.COMMA)
        val step = parseInt()
        check(Tokenizer.RBRACE)
        check(start <= end && step > 0 || start >= end && step < 0) {
            "Illegal start, end, step: $start, $end, $step"
        }
        // Return all the intermediate values
        return (start until end step step).toList()
    } else {
        return listOf(parseInt())
    }
}


fun Tokenizer.parseDoubleOrStep(): List<Double> {
    val lexeme = fetch()
    if (lexeme == Tokenizer.LBRACE) {
        next()
        val start = parseDouble()
        check(Tokenizer.COMMA)
        val end = parseDouble()
        check(Tokenizer.COMMA)
        val step = parseDouble()
        check(Tokenizer.RBRACE)
        check(start <= end && step > 0 || start >= end && step < 0) {
            "Illegal start, end, step: $start, $end, $step"
        }
        // Return all the intermediate values
        val result = mutableListOf<Double>()
        var x = start
        while (x < end) {
            result.add(x)
            x += step
        }
        return result
    } else {
        return listOf(parseDouble())
    }
}