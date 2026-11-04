package com.example.feature_editor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Approval
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.feature_editor.model.EditorTool
import com.example.feature_editor.model.EraserMode
import com.example.feature_editor.model.ShapeType
import com.example.feature_editor.model.StampType
import com.example.feature_editor.model.defaultEditorColors
import com.example.feature_editor.model.highlighterColors
import com.example.ui.theme.BrandIndigo

@Composable
fun EditorToolbar(
    activeTool: EditorTool,
    selectedColor: Color,
    selectedShapeType: ShapeType,
    selectedStampType: StampType,
    eraserMode: EraserMode = EraserMode.STROKE,
    sizeControl: SizeControlSpec? = null,
    onToolSelected: (EditorTool) -> Unit,
    onColorSelected: (Color) -> Unit,
    onShapeTypeSelected: (ShapeType) -> Unit,
    onStampSelected: (StampType) -> Unit,
    onEraserModeSelected: (EraserMode) -> Unit = {},
    onOpenSignatureDialog: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 12.dp)
        ) {
            // Secondary Options Bar (Colors / Width / Sub-tools / Eraser options)
            AnimatedVisibility(
                visible = (activeTool != EditorTool.VIEW && activeTool != EditorTool.SELECT) || sizeControl != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    // Pinned (does not scroll away with the colours / chips): always one tap from the slider.
                    if (sizeControl != null) {
                        SizeChip(spec = sizeControl)
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                    when (activeTool) {
                        EditorTool.ERASER -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    FilterChip(
                                        selected = eraserMode == EraserMode.STROKE,
                                        onClick = { onEraserModeSelected(EraserMode.STROKE) },
                                        label = { Text("Stroke", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandIndigo.copy(alpha = 0.15f),
                                            selectedLabelColor = BrandIndigo
                                        )
                                    )
                                    FilterChip(
                                        selected = eraserMode == EraserMode.OBJECT,
                                        onClick = { onEraserModeSelected(EraserMode.OBJECT) },
                                        label = { Text("Object", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandIndigo.copy(alpha = 0.15f),
                                            selectedLabelColor = BrandIndigo
                                        )
                                    )
                                }

                            }
                        }

                        EditorTool.PEN, EditorTool.HIGHLIGHTER, EditorTool.TEXT -> {
                            val colors = if (activeTool == EditorTool.HIGHLIGHTER) highlighterColors else defaultEditorColors
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                colors.forEach { color ->
                                    val isSelected = selectedColor == color
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) BrandIndigo else Color.LightGray.copy(alpha = 0.5f),
                                                shape = CircleShape
                                            )
                                            .clickable { onColorSelected(color) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = if (color == Color.White) Color.Black else Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        EditorTool.SHAPE -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ShapeType.entries.forEach { shape ->
                                    FilterChip(
                                        selected = selectedShapeType == shape,
                                        onClick = { onShapeTypeSelected(shape) },
                                        label = { Text(shape.name.lowercase().replaceFirstChar { it.uppercase() }) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BrandIndigo.copy(alpha = 0.15f),
                                            selectedLabelColor = BrandIndigo
                                        )
                                    )
                                }
                            }
                        }

                        EditorTool.STAMP -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                StampType.entries.forEach { stamp ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(android.graphics.Color.parseColor(stamp.colorHex)).copy(alpha = 0.15f),
                                        border = BorderStroke(
                                            1.dp,
                                            Color(android.graphics.Color.parseColor(stamp.colorHex))
                                        ),
                                        modifier = Modifier
                                            .clickable { onStampSelected(stamp) }
                                            .padding(2.dp)
                                    ) {
                                        Text(
                                            text = stamp.label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(android.graphics.Color.parseColor(stamp.colorHex)),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        else -> {}
                    }
                    }
                  }

                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Primary Tools Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditorToolButton(
                    icon = Icons.Default.TouchApp,
                    label = "Select",
                    isSelected = activeTool == EditorTool.SELECT,
                    onClick = { onToolSelected(EditorTool.SELECT) }
                )

                EditorToolButton(
                    icon = Icons.Default.PanTool,
                    label = "View/Pan",
                    isSelected = activeTool == EditorTool.VIEW,
                    onClick = { onToolSelected(EditorTool.VIEW) }
                )

                EditorToolButton(
                    icon = Icons.Default.Draw,
                    label = "Pen",
                    isSelected = activeTool == EditorTool.PEN,
                    onClick = { onToolSelected(EditorTool.PEN) }
                )

                EditorToolButton(
                    icon = Icons.Default.BorderColor,
                    label = "Highlight",
                    isSelected = activeTool == EditorTool.HIGHLIGHTER,
                    onClick = { onToolSelected(EditorTool.HIGHLIGHTER) }
                )

                EditorToolButton(
                    icon = Icons.Default.AutoFixNormal,
                    label = "Eraser",
                    isSelected = activeTool == EditorTool.ERASER,
                    onClick = { onToolSelected(EditorTool.ERASER) }
                )

                EditorToolButton(
                    icon = Icons.Default.TextFields,
                    label = "Text",
                    isSelected = activeTool == EditorTool.TEXT,
                    onClick = { onToolSelected(EditorTool.TEXT) }
                )

                EditorToolButton(
                    icon = Icons.Default.CropSquare,
                    label = "Shapes",
                    isSelected = activeTool == EditorTool.SHAPE,
                    onClick = { onToolSelected(EditorTool.SHAPE) }
                )

                EditorToolButton(
                    icon = Icons.Default.Approval,
                    label = "Stamps",
                    isSelected = activeTool == EditorTool.STAMP,
                    onClick = { onToolSelected(EditorTool.STAMP) }
                )

                EditorToolButton(
                    icon = Icons.Default.Gesture,
                    label = "Sign",
                    isSelected = activeTool == EditorTool.SIGNATURE,
                    onClick = {
                        onToolSelected(EditorTool.SIGNATURE)
                        onOpenSignatureDialog()
                    }
                )

                EditorToolButton(
                    icon = Icons.Default.VisibilityOff,
                    label = "Redact",
                    isSelected = activeTool == EditorTool.REDACT,
                    onClick = { onToolSelected(EditorTool.REDACT) }
                )
            }
        }
    }
}

@Composable
fun EditorToolButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) BrandIndigo else Color.Transparent,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) BrandIndigo else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}
