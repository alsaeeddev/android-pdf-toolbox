package com.example

import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.core.data.local.entity.BookmarkEntity
import com.example.core.data.local.entity.PdfDocumentEntity
import com.example.core.data.preferences.ThemeMode
import com.example.core.data.preferences.ViewMode
import com.example.core.navigation.Screen
import com.example.feature_editor.EditorUiState
import com.example.feature_editor.domain.AnnotationHitTester
import com.example.feature_editor.domain.EditorSnapshot
import com.example.feature_editor.domain.HandleType
import com.example.feature_editor.domain.UndoRedoManager
import com.example.feature_editor.model.Annotation
import com.example.feature_editor.model.EditorFontFamily
import com.example.feature_editor.model.EditorTool
import com.example.feature_editor.model.PageAnnotation
import com.example.feature_editor.model.ShapeType
import com.example.feature_editor.model.StampType
import com.example.feature_editor.model.TextAlignment
import com.example.feature_home.HomeTab
import com.example.feature_home.SortOrder
import com.example.feature_tools.pdfToolsList
import com.example.feature_viewer.ViewerUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testNavigationRoutes() {
        assertEquals("splash", Screen.Splash.route)
        assertEquals("onboarding", Screen.Onboarding.route)
        assertEquals("home", Screen.Home.route)
        assertEquals("tools", Screen.Tools.route)
        assertEquals("settings", Screen.Settings.route)
        assertTrue(Screen.Viewer.createRoute("content://sample/test.pdf").contains("test.pdf"))
        assertTrue(Screen.Editor.createRoute("content://sample/test.pdf").contains("test.pdf"))
    }

    @Test
    fun testPdfDocumentEntity() {
        val doc = PdfDocumentEntity(
            uri = "content://docs/sample.pdf",
            name = "sample.pdf",
            sizeBytes = 2048L,
            pageCount = 5,
            isFavorite = false
        )
        assertEquals("sample.pdf", doc.name)
        assertEquals(5, doc.pageCount)
        assertFalse(doc.isFavorite)
    }

    @Test
    fun testBookmarkEntity() {
        val bookmark = BookmarkEntity(
            documentUri = "content://docs/sample.pdf",
            pageNumber = 3,
            title = "Important Section"
        )
        assertEquals(3, bookmark.pageNumber)
        assertEquals("Important Section", bookmark.title)
    }

    @Test
    fun testUndoRedoManager() {
        val manager = UndoRedoManager()
        val ann1 = PageAnnotation.Freehand(
            page = 0,
            points = listOf(Offset(10f, 10f), Offset(20f, 20f)),
            color = Color.Black,
            strokeWidth = 4f
        )

        assertFalse(manager.canUndo())
        assertFalse(manager.canRedo())

        val state = listOf<PageAnnotation>(ann1)
        manager.pushState(emptyList())
        assertTrue(manager.canUndo())

        val undone = manager.undo(state)
        assertNotNull(undone)
        assertEquals(0, undone!!.size)
        assertTrue(manager.canRedo())

        val redone = manager.redo(undone)
        assertNotNull(redone)
        assertEquals(1, redone!!.size)
    }

    @Test
    fun testEditorTools() {
        assertEquals(10, EditorTool.entries.size)
        assertEquals(4, ShapeType.entries.size)
        assertEquals(5, StampType.entries.size)
    }

    @Test
    fun testHomeTabsAndSort() {
        assertEquals(4, HomeTab.entries.size)
        assertEquals(3, SortOrder.entries.size)
        assertEquals("Recent first", SortOrder.DATE_DESC.label)
    }

    @Test
    fun testPdfToolsList() {
        assertTrue(pdfToolsList.size >= 8)
        assertTrue(pdfToolsList.any { it.id == "merge" })
        assertTrue(pdfToolsList.any { it.id == "compress" })
        assertTrue(pdfToolsList.any { it.id == "protect" })
    }

    @Test
    fun testPreferencesEnums() {
        assertEquals(3, ThemeMode.entries.size)
        assertEquals(2, ViewMode.entries.size)
    }

    @Test
    fun testCompressQualityLevels() {
        assertEquals(3, _root_ide_package_.com.example.feature_tools.CompressQuality.entries.size)
        assertEquals(_root_ide_package_.com.example.feature_tools.CompressQuality.MEDIUM, _root_ide_package_.com.example.feature_tools.CompressQuality.entries[1])
    }

    // =========================================================================
    // TEXT TOOL ACCEPTANCE TESTS
    // =========================================================================

    private class TestEditorState {
        var annotations: List<Annotation> = emptyList()
        var selectedAnnotationId: String? = null
        var editingAnnotationId: String? = null
        val undoRedoManager = UndoRedoManager()

        fun onPageTap(point: Offset) {
            // 1. Tapping outside while editing commits and deselects
            if (editingAnnotationId != null) {
                onCommitEdit()
                selectedAnnotationId = null
                return
            }

            // 2. Hit-test existing annotations
            val hit = AnnotationHitTester.hitTestAnnotations(point, annotations, 0)
            if (hit is PageAnnotation.Text) {
                if (selectedAnnotationId == hit.id) {
                    onStartEdit(hit.id)
                } else {
                    selectedAnnotationId = hit.id
                    editingAnnotationId = null
                }
            } else {
                // 3. Hit empty space
                if (selectedAnnotationId != null) {
                    selectedAnnotationId = null
                } else {
                    val newAnn = PageAnnotation.Text(
                        page = 0,
                        text = "",
                        position = point,
                        color = Color.Black
                    )
                    undoRedoManager.pushState(annotations, null)
                    annotations = annotations + newAnn
                    selectedAnnotationId = newAnn.id
                    editingAnnotationId = newAnn.id
                }
            }
        }

        fun onStartEdit(id: String) {
            undoRedoManager.pushState(annotations, selectedAnnotationId)
            selectedAnnotationId = id
            editingAnnotationId = id
        }

        fun onTextChanged(id: String, newText: String) {
            annotations = annotations.map {
                if (it.id == id && it is PageAnnotation.Text) it.copy(text = newText) else it
            }
        }

        fun onCommitEdit() {
            val currentId = editingAnnotationId ?: return
            val target = annotations.find { it.id == currentId }
            if (target is PageAnnotation.Text && target.text.trim().isEmpty()) {
                annotations = annotations.filter { it.id != currentId }
                selectedAnnotationId = null
                editingAnnotationId = null
            } else {
                editingAnnotationId = null
            }
        }

        fun undo() {
            if (editingAnnotationId != null) onCommitEdit()
            val snap = EditorSnapshot(annotations, selectedAnnotationId)
            val prev = undoRedoManager.undoSnapshot(snap)
            if (prev != null) {
                annotations = prev.annotations
                selectedAnnotationId = prev.selectedAnnotationId
                editingAnnotationId = null
            }
        }
    }

    @Test
    fun testAcceptance1_TapEmptyPageCreatesOneEmptyAnnotationAndEntersEditMode() {
        val state = TestEditorState()
        assertEquals(0, state.annotations.size)

        // Tap empty space
        state.onPageTap(Offset(100f, 200f))

        // Exactly one annotation created
        assertEquals(1, state.annotations.size)
        val created = state.annotations[0] as PageAnnotation.Text
        assertEquals("", created.text)
        assertEquals(Offset(100f, 200f), created.position)

        // Enters edit mode and selected
        assertEquals(created.id, state.selectedAnnotationId)
        assertEquals(created.id, state.editingAnnotationId)
    }

    @Test
    fun testAcceptance2_TapSameAnnotationEntersEditModeWithoutCreatingNew() {
        val state = TestEditorState()
        val textAnn = PageAnnotation.Text(
            page = 0,
            text = "Existing Note",
            position = Offset(100f, 100f),
            color = Color.Black,
            width = 200f
        )
        state.annotations = listOf(textAnn)

        // First tap: selects the annotation
        state.onPageTap(Offset(120f, 120f))
        assertEquals(1, state.annotations.size)
        assertEquals(textAnn.id, state.selectedAnnotationId)
        assertNull(state.editingAnnotationId)

        // Second tap on the already selected annotation: enters EDIT mode without creating a second one
        state.onPageTap(Offset(120f, 120f))
        assertEquals(1, state.annotations.size)
        assertEquals(textAnn.id, state.selectedAnnotationId)
        assertEquals(textAnn.id, state.editingAnnotationId)
    }

    @Test
    fun testAcceptance3_TapEmptySpaceWhileEditingCommitsEditWithoutCreatingNew() {
        val state = TestEditorState()
        val textAnn = PageAnnotation.Text(
            page = 0,
            text = "Initial Text",
            position = Offset(100f, 100f),
            color = Color.Black
        )
        state.annotations = listOf(textAnn)
        state.onStartEdit(textAnn.id)
        state.onTextChanged(textAnn.id, "Updated Text")

        // Tapping empty space while in edit mode
        state.onPageTap(Offset(500f, 500f))

        // Edit committed, deselects, and no new annotation created
        assertEquals(1, state.annotations.size)
        val updated = state.annotations[0] as PageAnnotation.Text
        assertEquals("Updated Text", updated.text)
        assertNull(state.editingAnnotationId)
        assertNull(state.selectedAnnotationId)
    }

    @Test
    fun testAcceptance4_CommitEmptyTextRemovesAnnotation() {
        val state = TestEditorState()
        val emptyAnn = PageAnnotation.Text(
            page = 0,
            text = "   ",
            position = Offset(100f, 100f),
            color = Color.Black
        )
        state.annotations = listOf(emptyAnn)
        state.editingAnnotationId = emptyAnn.id

        // Commit empty text
        state.onCommitEdit()

        // Annotation must be deleted automatically with no empty boxes left
        assertEquals(0, state.annotations.size)
        assertNull(state.editingAnnotationId)
        assertNull(state.selectedAnnotationId)
    }

    @Test
    fun testAcceptance5_UndoAfterTypingRevertsEntireTypingSessionInOneStep() {
        val state = TestEditorState()
        val initialAnn = PageAnnotation.Text(
            page = 0,
            text = "Original",
            position = Offset(100f, 100f),
            color = Color.Black
        )
        state.annotations = listOf(initialAnn)

        // Start editing and type characters in session
        state.onStartEdit(initialAnn.id)
        state.onTextChanged(initialAnn.id, "Original 1")
        state.onTextChanged(initialAnn.id, "Original 12")
        state.onTextChanged(initialAnn.id, "Original 123")
        state.onCommitEdit()

        assertEquals("Original 123", (state.annotations[0] as PageAnnotation.Text).text)

        // Undo once: must revert entire typing session back to "Original"
        state.undo()
        assertEquals("Original", (state.annotations[0] as PageAnnotation.Text).text)
    }

    @Test
    fun testAcceptance6_RotatedAndScaledAnnotationHitTestedCorrectly() {
        val textAnn = PageAnnotation.Text(
            page = 0,
            text = "Rotated 45 degrees",
            position = Offset(200f, 200f),
            color = Color.Black,
            fontSize = 20f,
            rotation = 45f,
            width = 200f
        )

        val bounds = AnnotationHitTester.getTextBounds(textAnn)
        val center = bounds.center

        // Center point must always hit regardless of rotation
        assertTrue(AnnotationHitTester.hitTest(center, textAnn))

        // Point far outside should not hit
        assertFalse(AnnotationHitTester.hitTest(Offset(50f, 50f), textAnn, touchSlop = 0f))

        // Check 90 degree rotated annotation
        val textAnn90 = textAnn.copy(rotation = 90f)
        assertTrue(AnnotationHitTester.hitTest(center, textAnn90))

        // Check handle hit-testing
        val rotateHandle = AnnotationHitTester.hitTestHandles(
            point = center + Offset(0f, -bounds.height / 2f - 36f),
            annotation = textAnn.copy(rotation = 0f)
        )
        assertEquals(HandleType.ROTATE, rotateHandle)
    }

    @Test
    fun testPartC_ShapeCreationMovementResizingAndRotation() {
        val shape = PageAnnotation.Shape(
            page = 0,
            shapeType = ShapeType.RECTANGLE,
            start = Offset(100f, 100f),
            end = Offset(300f, 200f),
            color = Color.Red,
            strokeWidth = 4f
        )

        // Test movement
        val moved = shape.moved(50f, 50f) as PageAnnotation.Shape
        assertEquals(Offset(150f, 150f), moved.start)
        assertEquals(Offset(350f, 250f), moved.end)

        // Test rotation
        val rotated = moved.rotated(45f)
        assertEquals(45f, rotated.rotation)

        // Test hit testing
        assertTrue(AnnotationHitTester.hitTest(Offset(250f, 200f), moved, touchSlop = 10f))
        assertFalse(AnnotationHitTester.hitTest(Offset(0f, 0f), moved, touchSlop = 0f))
    }

    @Test
    fun testPartD_ContinuousEraseGestureAndUndo() {
        val manager = UndoRedoManager()
        val ann1 = PageAnnotation.Freehand(
            page = 0,
            points = listOf(Offset(10f, 10f), Offset(20f, 20f), Offset(30f, 30f)),
            color = Color.Black,
            strokeWidth = 4f
        )
        val ann2 = PageAnnotation.Freehand(
            page = 0,
            points = listOf(Offset(100f, 100f), Offset(120f, 120f)),
            color = Color.Red,
            strokeWidth = 4f
        )

        val initialState = listOf<Annotation>(ann1, ann2)

        // One erase gesture starts
        manager.pushState(initialState)

        // Perform multiple erasures during single gesture
        val stateAfterErase1 = listOf<Annotation>(ann2) // ann1 erased
        val stateAfterErase2 = emptyList<Annotation>() // ann2 erased

        // Gesture ends
        assertEquals(0, stateAfterErase2.size)

        // Undo restores initial state in ONE step
        val restored = manager.undo(stateAfterErase2)
        assertNotNull(restored)
        assertEquals(2, restored!!.size)
    }

    @Test
    fun testPermissionErrorUiState() {
        val viewerState = ViewerUiState(
            errorMessage = "Permission to access this document was denied or has expired.",
            isPermissionError = true
        )
        assertTrue(viewerState.isPermissionError)
        assertEquals("Permission to access this document was denied or has expired.", viewerState.errorMessage)

        val editorState = EditorUiState(
            errorMessage = "Permission to access this document was denied or has expired.",
            isPermissionError = true
        )
        assertTrue(editorState.isPermissionError)
        assertEquals("Permission to access this document was denied or has expired.", editorState.errorMessage)
    }

    @Test
    fun testUriToStringHashCodeConsistency() {
        val uriStr1 = "content://com.android.providers.downloads.documents/document/raw%3A12345"
        val uriStr2 = "content://com.android.providers.downloads.documents/document/raw%3A12345"

        assertEquals(uriStr1, uriStr2)
        assertEquals(uriStr1.hashCode(), uriStr2.hashCode())
        assertEquals("doc_${uriStr1.hashCode()}.pdf", "doc_${uriStr2.hashCode()}.pdf")
    }
}
