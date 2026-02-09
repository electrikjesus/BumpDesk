package com.bass.bumpdesk

import java.util.Stack

interface Command {
    fun undo()
    fun redo()
}

class UndoManager {
    private val undoStack = Stack<Command>()
    private val redoStack = Stack<Command>()

    fun execute(command: Command) {
        command.redo()
        undoStack.push(command)
        redoStack.clear()
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.pop()
            command.undo()
            redoStack.push(command)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.pop()
            command.redo()
            undoStack.push(command)
        }
    }
}

data class MoveCommand(
    private val item: BumpItem,
    private val oldPos: FloatArray,
    private val oldSurface: BumpItem.Surface,
    private val newPos: FloatArray,
    private val newSurface: BumpItem.Surface
) : Command {
    override fun undo() {
        item.position[0] = oldPos[0]
        item.position[1] = oldPos[1]
        item.position[2] = oldPos[2]
        item.surface = oldSurface
    }

    override fun redo() {
        item.position[0] = newPos[0]
        item.position[1] = newPos[1]
        item.position[2] = newPos[2]
        item.surface = newSurface
    }
}
