package org.jetbrains.bio.query.parse

import org.jetbrains.bio.genome.ChromosomeRange


/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */


/**
 * Root AST node class
 */
abstract class Statement {
    abstract fun accept(visitor: TreeVisitor)
}

/**
 * Base track class
 */
abstract class GeneratedTrack: Statement() {
}

/**
 * show id
 */
class ShowTrackStatement(val track: GeneratedTrack) : Statement() {
    override fun accept(visitor: TreeVisitor) {
        visitor.visit(this)
    }
}

/**
 * id <- expr
 */
class AssignStatement(val id: String, val track: GeneratedTrack) : Statement() {
    override fun accept(visitor: TreeVisitor) {
        visitor.visit(this)
    }
}