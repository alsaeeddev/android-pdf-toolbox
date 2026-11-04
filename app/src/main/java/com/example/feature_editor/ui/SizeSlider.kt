package com.example.feature_editor.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.Popup
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val RailTouchWidth = 40.dp
private val RailTrackWidth = 6.dp
private val BallSize = 26.dp
private val BallSizePressed = 32.dp

/**
 * A vertical "ball" slider: a thin rail with a round ball you drag up (bigger) / down (smaller).
 * Tapping anywhere on the rail jumps the ball there.
 */
@Composable
fun VerticalBallSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accent: Color,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val min = valueRange.start
    val max = valueRange.endInclusive
    val span = (max - min).coerceAtLeast(0.0001f)
    val fraction = ((value - min) / span).coerceIn(0f, 1f)

    var pressed by remember { mutableStateOf(false) }
    val currentOnChange by rememberUpdatedState(onValueChange)
    val ballSize by animateDpAsState(if (pressed) BallSizePressed else BallSize, label = "ball")
    val ringAlpha by animateFloatAsState(if (pressed) 0.28f else 0f, label = "ring")

    val railBg = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    Box(
        modifier = modifier
            .width(RailTouchWidth)
            .semantics {
                this.contentDescription = contentDescription
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(min, max), valueRange)
                setProgress { target ->
                    currentOnChange(target.coerceIn(min, max))
                    true
                }
            }
            .pointerInput(min, max) {
                awaitEachGesture {
                    val ballPx = BallSize.toPx()
                    val usable = (size.height - ballPx).coerceAtLeast(1f)

                    fun valueAt(y: Float): Float {
                        val f = 1f - ((y - ballPx / 2f) / usable).coerceIn(0f, 1f)
                        return min + f * (max - min)
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    currentOnChange(valueAt(down.position.y))
                    down.consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        currentOnChange(valueAt(change.position.y))
                        change.consume()
                    }
                    pressed = false
                }
            },
        contentAlignment = Alignment.TopCenter
    ) {
        // Rail (inactive) – inset by half a ball so the ball never overflows the ends
        Box(
            modifier = Modifier
                .padding(vertical = BallSize / 2)
                .fillMaxHeight()
                .width(RailTrackWidth)
                .clip(CircleShape)
                .background(railBg)
                .align(Alignment.Center)
        )

        // Active part of the rail (bottom -> ball)
        Canvas(
            modifier = Modifier
                .padding(vertical = BallSize / 2)
                .fillMaxHeight()
                .width(RailTrackWidth)
                .align(Alignment.Center)
        ) {
            val h = size.height
            val activeH = h * fraction
            if (activeH > 0f) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(0f, h - activeH),
                    size = Size(size.width, activeH),
                    cornerRadius = CornerRadius(size.width / 2f)
                )
            }
        }

        // The ball
        BoxWithConstraints(modifier = Modifier.matchParentSize()) {
            val usable = (maxHeight - BallSize).coerceAtLeast(0.dp)
            val y = usable * (1f - fraction)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = y)
                    .size(BallSize),
                contentAlignment = Alignment.Center
            ) {
                // soft halo while touching
                Box(
                    modifier = Modifier
                        .requiredSize(ballSize + 14.dp)
                        .background(accent.copy(alpha = ringAlpha), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .requiredSize(ballSize)
                        .shadow(4.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(BorderStroke(3.dp, accent), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(accent, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Floating size panel: live preview of the size on top, vertical ball slider in the middle,
 * numeric value at the bottom. Meant to be overlaid on the canvas edge so it is always visible
 * (no horizontal scrolling inside the toolbar).
 */
@Composable
fun SizeControlPanel(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    unit: String,
    previewColor: Color,
    accent: Color,
    contentDescription: String,
    modifier: Modifier = Modifier,
    previewAsRing: Boolean = false
) {
    val safeValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val frac = ((safeValue - valueRange.start) /
        (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    val previewDp = (6f + frac * 20f).dp  // 6dp .. 26dp

    Surface(
        modifier = modifier.width(52.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Live preview of the current size
            Box(
                modifier = Modifier.size(30.dp),
                contentAlignment = Alignment.Center
            ) {
                if (previewAsRing) {
                    Box(
                        modifier = Modifier
                            .size(previewDp)
                            .border(2.dp, previewColor, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(previewDp)
                            .background(previewColor, CircleShape)
                    )
                }
            }

            VerticalBallSlider(
                value = safeValue,
                onValueChange = onValueChange,
                valueRange = valueRange,
                accent = accent,
                contentDescription = contentDescription,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            Text(
                text = "${safeValue.roundToInt()}",
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/** Describes the size control available for the current tool. */
data class SizeControlSpec(
    val label: String,
    val unit: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val previewColor: Color,
    val accent: Color,
    val onChange: (Float) -> Unit,
    val previewAsRing: Boolean = false
)

/** Places the popup centred above its anchor (never off-screen). */
private class AboveAnchorPositionProvider(private val gapPx: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = (anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2)
            .coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val y = (anchorBounds.top - popupContentSize.height - gapPx).coerceAtLeast(0)
        return IntOffset(x, y)
    }
}

/**
 * Compact chip that lives inside the editing panel (live preview + value).
 * Tap it to open the vertical ball slider in a popup right above the chip; tap anywhere
 * outside (or the chip again) to close it. This keeps the panel slim and the size control
 * one tap away for every tool, without scrolling.
 */
@Composable
fun SizeChip(
    spec: SizeControlSpec,
    modifier: Modifier = Modifier
) {
    // Reset when the tool (and so the control) changes.
    var open by remember(spec.label) { mutableStateOf(false) }
    val safeValue = spec.value.coerceIn(spec.range.start, spec.range.endInclusive)
    val frac = ((safeValue - spec.range.start) /
        (spec.range.endInclusive - spec.range.start).coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    val previewDp = (6f + frac * 14f).dp // 6..20dp inside the 24dp preview slot

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (open) spec.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.5.dp, if (open) spec.accent else Color.Transparent),
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .clickable { open = !open }
                .semantics {
                    this.contentDescription = "${spec.label}: ${safeValue.roundToInt()} ${spec.unit}. Double tap to adjust."
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                    if (spec.previewAsRing) {
                        Box(Modifier.size(previewDp).border(2.dp, spec.previewColor, CircleShape))
                    } else {
                        Box(Modifier.size(previewDp).background(spec.previewColor, CircleShape))
                    }
                }
                // Reserve room for the widest value of this range (e.g. "48") and use tabular figures,
                // so going from 9 -> 10 never changes the chip's width / shifts the options next to it.
                val maxDigits = spec.range.endInclusive.roundToInt().toString().length
                Text(
                    text = "${safeValue.roundToInt()}",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    color = if (open) spec.accent else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width((maxDigits * 9 + 4).dp)
                )
                Icon(
                    imageVector = if (open) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = if (open) spec.accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (open) {
            val gapPx = with(LocalDensity.current) { 6.dp.roundToPx() }
            val popupHeight = (LocalConfiguration.current.screenHeightDp * 0.38f).coerceIn(150f, 240f).dp
            Popup(
                popupPositionProvider = remember(gapPx) { AboveAnchorPositionProvider(gapPx) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true)
            ) {
                // Padding leaves room for the panel's shadow so it isn't clipped.
                Box(modifier = Modifier.padding(10.dp)) {
                    SizeControlPanel(
                        value = spec.value,
                        onValueChange = spec.onChange,
                        valueRange = spec.range,
                        unit = spec.unit,
                        previewColor = spec.previewColor,
                        accent = spec.accent,
                        contentDescription = spec.label,
                        previewAsRing = spec.previewAsRing,
                        modifier = Modifier.height(popupHeight)
                    )
                }
            }
        }
    }
}
