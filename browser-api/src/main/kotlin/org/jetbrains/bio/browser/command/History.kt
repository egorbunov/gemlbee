package org.jetbrains.bio.browser.command

import java.util.*

/** A GoF command as is. */
interface Command {
    fun redo()
    fun undo()
}

class History {
    // Stacks are emulated via 'ArrayDeque' as per 'Stack' JavaDoc
    // recommendation.
    private val undo = ArrayDeque<Command>()
    private val redo = ArrayDeque<Command>()

    /**
     * Executes a given command.
     *
     * Any sequence of redo command, accumulated during [undo] calls
     * is discard.
     */
    fun execute(cmd: Command?) {
        if (cmd == null) {
            return  // XXX unsure about allowing 'null' here.
        }

        undo.push(cmd)
        undo.truncate(UNDO_STACK_MAX_SIZE)
        redo.clear()
        cmd.redo()
    }

    /**
     * Undoes the last [Command].
     *
     * @returns `true` if the command was undone and `false` if there
     *           is no more commands to undo.
     */
    fun undo(): Boolean {
        if (undo.isEmpty()) {
            return false
        }

        val cmd = undo.pop()
        redo.push(cmd)
        cmd.undo()
        return true
    }

    /**
     * Redoes the last undone [Command].
     *
     * @returns `true` if the command was redone and `false` if there
     *           is no more commands to redo.
     */
    fun redo(): Boolean {
        if (redo.isEmpty()) {
            return false
        }

        val cmd = redo.pop()
        undo.push(cmd)
        cmd.redo()
        return true
    }

    fun clear() {
        undo.clear()
        redo.clear()
    }

    companion object {
        /**
         * Capacity of the [undo] stack.
         *
         * This also implicitly limits the capacity of the dual [redo] stack.
         */
        private val UNDO_STACK_MAX_SIZE = 100
    }
}

/**
 * Bounds the stack by dropping the least-recently added elements to
 * maintain the desired number of elements.
 *
 * Nothing is done if the size of the stack does not exceed [count].
 */
internal inline fun <reified T> Deque<T>.truncate(count: Int) {
    while (size > count) {
        removeLast()
    }
}