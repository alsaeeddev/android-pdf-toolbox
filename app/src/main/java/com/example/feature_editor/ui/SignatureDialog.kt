package com.example.feature_editor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.data.local.dao.SignatureDao
import com.example.core.data.local.entity.SignatureEntity
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandTeal
import kotlinx.coroutines.launch

import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureDialog(
    signatureDao: SignatureDao,
    onSignatureCreated: (strokes: List<List<Offset>>, filled: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    val savedSignatures by signatureDao.getAllSignatures().collectAsStateWithLifecycle(initialValue = emptyList())

    // Draw state
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var saveToLibrary by remember { mutableStateOf(true) }

    // Type state
    var typedName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .clip(RoundedCornerShape(24.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Add Digital Signature",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = BrandIndigo
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Draw") },
                        icon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Saved (${savedSignatures.size})") },
                        icon = { Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Type") },
                        icon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                when (selectedTab) {
                    // TAB 0: Draw Signature
                    0 -> {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFFAFAFA))
                                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { startOffset ->
                                                currentStroke = listOf(startOffset)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                currentStroke = currentStroke + change.position
                                            },
                                            onDragEnd = {
                                                if (currentStroke.isNotEmpty()) {
                                                    strokes.add(currentStroke)
                                                    currentStroke = emptyList()
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    // Guide line
                                    val guideY = size.height * 0.75f
                                    drawLine(
                                        color = Color.LightGray.copy(alpha = 0.6f),
                                        start = Offset(24f, guideY),
                                        end = Offset(size.width - 24f, guideY),
                                        strokeWidth = 2f
                                    )

                                    val allToDraw = if (currentStroke.isNotEmpty()) strokes + listOf(currentStroke) else strokes

                                    allToDraw.forEach { strokePoints ->
                                        if (strokePoints.size > 1) {
                                            val path = Path().apply {
                                                moveTo(strokePoints[0].x, strokePoints[0].y)
                                                for (i in 1 until strokePoints.size) {
                                                    val p0 = strokePoints[i - 1]
                                                    val p1 = strokePoints[i]
                                                    quadraticTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
                                                }
                                                lineTo(strokePoints.last().x, strokePoints.last().y)
                                            }
                                            drawPath(
                                                path = path,
                                                color = Color(0xFF1E1B4B),
                                                style = Stroke(
                                                    width = 4.5f,
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round
                                                )
                                            )
                                        }
                                    }
                                }

                                if (strokes.isEmpty() && currentStroke.isEmpty()) {
                                    Text(
                                        text = "Sign with finger or stylus here",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.align(Alignment.Center)
                                    )
                                }

                                if (strokes.isNotEmpty() || currentStroke.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            strokes.clear()
                                            currentStroke = emptyList()
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                    ) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Gray)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { saveToLibrary = !saveToLibrary }
                            ) {
                                Checkbox(
                                    checked = saveToLibrary,
                                    onCheckedChange = { saveToLibrary = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Save signature to library for quick reuse",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // TAB 1: Saved Signatures
                    1 -> {
                        if (savedSignatures.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No saved signatures yet.\nDraw a signature in the 'Draw' tab to save it.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(savedSignatures, key = { it.id }) { sig ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                jsonToStrokes(sig.strokeDataJson)?.let { (saved, isFilled) ->
                                                    onSignatureCreated(saved, isFilled)
                                                }
                                            },
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = sig.title,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Tap to insert into document",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = BrandTeal
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    scope.launch { signatureDao.deleteSignature(sig.id) }
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 2: Type Signature
                    2 -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            OutlinedTextField(
                                value = typedName,
                                onValueChange = { typedName = it },
                                label = { Text("Your Full Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFFAFAFA))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (typedName.isBlank()) "Preview Signature" else typedName,
                                    fontSize = 36.sp,
                                    fontFamily = FontFamily.Cursive,
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Normal,
                                    color = Color(0xFF1E1B4B)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (selectedTab == 0 && strokes.isNotEmpty()) {
                                if (saveToLibrary) {
                                    scope.launch {
                                        signatureDao.insertSignature(
                                            SignatureEntity(
                                                title = "Signature ${savedSignatures.size + 1}",
                                                strokeDataJson = strokesToJson(strokes.toList(), false)
                                            )
                                        )
                                    }
                                }
                                onSignatureCreated(strokes.toList(), false)
                            } else if (selectedTab == 2 && typedName.isNotBlank()) {
                                // Real glyph outlines of the typed name (was a hard-coded zig-zag).
                                val glyphContours = typedNameToContours(typedName.trim())
                                if (glyphContours.isNotEmpty()) {
                                    if (saveToLibrary) {
                                        scope.launch {
                                            signatureDao.insertSignature(
                                                SignatureEntity(
                                                    title = typedName.trim(),
                                                    strokeDataJson = strokesToJson(glyphContours, true)
                                                )
                                            )
                                        }
                                    }
                                    onSignatureCreated(glyphContours, true)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandIndigo,
                            contentColor = Color.White
                        ),
                        enabled = (selectedTab == 0 && strokes.isNotEmpty()) || (selectedTab == 2 && typedName.isNotBlank())
                    ) {
                        Text("Place Signature")
                    }
                }
            }
        }
    }
}


/** Typed name -> closed glyph contours (flattened curves) in a cursive italic face. Drawn FILLED by the editor. */
private fun typedNameToContours(name: String): List<List<Offset>> {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 120f
        typeface = android.graphics.Typeface.create("cursive", android.graphics.Typeface.ITALIC)
    }
    val outline = android.graphics.Path()
    paint.getTextPath(name, 0, name.length, 0f, 0f, outline)

    val measure = android.graphics.PathMeasure(outline, false)
    val pos = FloatArray(2)
    val contours = mutableListOf<List<Offset>>()
    do {
        val len = measure.length
        if (len > 1f) {
            val steps = (len / 1.5f).toInt().coerceIn(12, 600)
            val pts = ArrayList<Offset>(steps)
            for (i in 0 until steps) {
                measure.getPosTan(len * i / steps, pos, null)
                pts.add(Offset(pos[0], pos[1]))
            }
            contours.add(pts)
        }
    } while (measure.nextContour())
    return contours
}

private fun strokesToJson(strokes: List<List<Offset>>, filled: Boolean): String {
    val root = org.json.JSONObject()
    root.put("filled", filled)
    val arr = org.json.JSONArray()
    strokes.forEach { stroke ->
        val sa = org.json.JSONArray()
        stroke.forEach { p -> sa.put(org.json.JSONArray().put(p.x.toDouble()).put(p.y.toDouble())) }
        arr.put(sa)
    }
    root.put("strokes", arr)
    return root.toString()
}

private fun jsonToStrokes(json: String): Pair<List<List<Offset>>, Boolean>? = try {
    val root = org.json.JSONObject(json)
    val arr = root.getJSONArray("strokes")
    val strokes = (0 until arr.length()).map { i ->
        val sa = arr.getJSONArray(i)
        (0 until sa.length()).map { j ->
            val p = sa.getJSONArray(j)
            Offset(p.getDouble(0).toFloat(), p.getDouble(1).toFloat())
        }
    }
    if (strokes.isEmpty()) null else strokes to root.optBoolean("filled", false)
} catch (e: Exception) {
    null // old placeholder rows ("strokes_123...") cannot be restored
}
