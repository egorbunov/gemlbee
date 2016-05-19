package org.jetbrains.bio.browser.query.desktop

import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenMap
import org.jetbrains.bio.query.parse.LangParser
import org.jetbrains.bio.util.Lexeme
import java.util.*
import javax.swing.text.Segment

/**
 * @author Egor Gorbunov
 * *
 * @since 19.05.16
 */

/**
 * Class, which describes which tokens must be considered as keywords
 */
class LangTokenMaker : AbstractTokenMaker() {

    override fun getWordsToHighlight(): TokenMap {
        val tokenMap = TokenMap()
        for (l in kws) {
            val strings = LangParser.kwStrMap[l]!!
            for (s in strings) {
                if (l == LangParser.Keywords.TRUE || l == LangParser.Keywords.FALSE) {
                    tokenMap.put(s, Token.LITERAL_BOOLEAN)
                } else {
                    tokenMap.put(s, Token.RESERVED_WORD)
                }
            }
        }
        return tokenMap
    }

    /**
     * Method is based on already written tokenizer
     * Need to accurately process whitespaces here
     */
    override fun getTokenList(text: Segment, initialTokenType: Int, startOffset: Int): Token {
        resetTokenList()
        val matches = LangParser.getMatches(text.toString())
        var previousTokenEnd = text.offset
        for (m in matches) {
            if (text.offset + m.start != previousTokenEnd) {
                addToken(text, previousTokenEnd, text.offset + m.start - 1,
                        Token.WHITESPACE, startOffset - text.offset + previousTokenEnd)
            }

            val type: Int
            if (m.lexeme == LangParser.Keywords.TRUE || m.lexeme == LangParser.Keywords.FALSE) {
                type = Token.LITERAL_BOOLEAN
            } else if (m.lexeme in kws) {
                type = Token.RESERVED_WORD
            } else {
                type = Token.IDENTIFIER
            }

            addToken(text, text.offset +  m.start, text.offset + m.end - 1, type, startOffset - text.offset + m.start)
            previousTokenEnd = text.offset + m.end
        }
        if (previousTokenEnd < text.offset + text.count || matches.isEmpty() && text.count > 0) {
            addToken(text, previousTokenEnd, text.offset + text.count - 1,
                    Token.WHITESPACE, startOffset - text.offset + previousTokenEnd)
        }
        addNullToken()
        return firstToken
    }

    companion object {
        internal val kws: HashSet<Lexeme> = object : HashSet<Lexeme>() {
            init {
                add(LangParser.Keywords.ASSIGN)
                add(LangParser.Keywords.AND)
                add(LangParser.Keywords.OR)
                add(LangParser.Keywords.NOT)
                add(LangParser.Keywords.IF)
                add(LangParser.Keywords.ELSE)
                add(LangParser.Keywords.THEN)
                add(LangParser.Keywords.SHOW)
                add(LangParser.Keywords.TRUE)
                add(LangParser.Keywords.FALSE)
            }
        }
    }
}
