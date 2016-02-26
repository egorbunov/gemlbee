package org.jetbrains.bio.browser.command

import java.util.*


class History {

    private val undo = Stack<Command>()
    private val redo = Stack<Command>()

    fun execute(cmd: Command?) {
        if (cmd == null) {
            return
        }
        undo.push(cmd)
        redo.clear()
        truncateStack(undo)
        cmd.redo()
    }

    fun undo(): Boolean {
        if (undo.isEmpty()) {
            return false
        }
        val cmd = undo.pop()
        redo.push(cmd)
        cmd.undo()
        return true
    }

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
        val UNDO_STACK_MAX_SIZE = 100

        private fun truncateStack(stack: Stack<Command>) {
            if (stack.size > 2 * UNDO_STACK_MAX_SIZE) {
                val lastCommands = arrayOfNulls<Command>(UNDO_STACK_MAX_SIZE)
                for (i in lastCommands.indices.reversed()) {
                    lastCommands[i] = stack.pop()
                }
                stack.clear()
                for (command in lastCommands) {
                    stack.push(command)
                }
            }
        }
    }
}
