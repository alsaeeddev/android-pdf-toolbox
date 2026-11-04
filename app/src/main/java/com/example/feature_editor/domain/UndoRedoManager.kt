package com.example.feature_editor.domain

import com.example.feature_editor.model.Annotation

data class EditorSnapshot(
    val annotations: List<Annotation>,
    val selectedAnnotationId: String? = null
)

class UndoRedoManager(
    private val maxHistory: Int = 30
) {
    private val undoStack = mutableListOf<EditorSnapshot>()
    private val redoStack = mutableListOf<EditorSnapshot>()

    fun pushSnapshot(snapshot: EditorSnapshot) {
        undoStack.add(snapshot)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun pushState(currentState: List<Annotation>, selectedId: String? = null) {
        pushSnapshot(EditorSnapshot(currentState.toList(), selectedId))
    }

    fun undoSize(): Int = undoStack.size

    /** Drops undo entries above [size] (used to forget the history of a discarded, never-used object). */
    fun truncateUndoTo(size: Int) {
        while (undoStack.size > size.coerceAtLeast(0)) {
            undoStack.removeAt(undoStack.lastIndex)
        }
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undoSnapshot(currentSnapshot: EditorSnapshot): EditorSnapshot? {
        if (!canUndo()) return null
        val previousState = undoStack.removeAt(undoStack.lastIndex)
        redoStack.add(currentSnapshot)
        return previousState
    }

    fun redoSnapshot(currentSnapshot: EditorSnapshot): EditorSnapshot? {
        if (!canRedo()) return null
        val nextState = redoStack.removeAt(redoStack.lastIndex)
        undoStack.add(currentSnapshot)
        return nextState
    }

    fun undo(currentState: List<Annotation>): List<Annotation>? {
        return undoSnapshot(EditorSnapshot(currentState))?.annotations
    }

    fun redo(currentState: List<Annotation>): List<Annotation>? {
        return redoSnapshot(EditorSnapshot(currentState))?.annotations
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
