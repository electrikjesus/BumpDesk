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
    private val oldPos: Vector3,
    private val oldSurface: BumpItem.Surface,
    private val newPos: Vector3,
    private val newSurface: BumpItem.Surface
) : Command {
    override fun undo() {
        item.position = oldPos
        item.surface = oldSurface
    }

    override fun redo() {
        item.position = newPos
        item.surface = newSurface
    }
}
