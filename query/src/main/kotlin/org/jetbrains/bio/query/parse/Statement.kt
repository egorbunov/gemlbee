package org.jetbrains.bio.query.parse

import org.jetbrains.bio.genome.ChromosomeRange


/**
 * @author Egor Gorbunov
 * @since 01.05.16
 */


/**
 * Root AST node class
 */
abstract class Statement: Comparable<Statement> {
    abstract fun <T> accept(visitor: TreeVisitor<T>): T
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
    override fun compareTo(other: Statement): Int {
        return if (other !is ShowTrackStatement) 1 else return track.compareTo(other.track)
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}

/**
 * id <- expr
 */
class AssignStatement(val id: String, val track: GeneratedTrack) : Statement() {
    override fun compareTo(other: Statement): Int {
        return if (other !is AssignStatement || id != other.id) 1 else track.compareTo(other.track)
    }

    override fun <T> accept(visitor: TreeVisitor<T>): T {
        return visitor.visit(this)
    }
}