package com.example.feature_splash

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BrandIndigo
import com.example.ui.theme.BrandTeal
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.OutfitFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit
) {
    val context = LocalContext.current

    // Check if system animations are disabled (reduce motion)
    val reduceMotion = remember {
        try {
            val durationScale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            durationScale == 0f
        } catch (_: Exception) {
            false
        }
    }

    val pageScale = remember { Animatable(if (reduceMotion) 1f else 0.7f) }
    val pageAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val strokeProgress = remember { Animatable(if (reduceMotion) 1f else 0f) }
    val textAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduceMotion) {
            delay(300)
            onSplashFinished()
            return@LaunchedEffect
        }

        // Phase 1: Document scale and fade in (0 -> 400ms)
        launch {
            pageScale.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }
        launch {
            pageAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = LinearEasing)
            )
        }

        delay(350)

        // Phase 2: Pen nib draws flourish stroke (350ms -> 750ms)
        launch {
            strokeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
            )
        }

        delay(300)

        // Phase 3: Wordmark and Tagline fade in (650ms -> 950ms)
        launch {
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 300, easing = LinearEasing)
            )
        }

        // Max 1.2s total presentation time before proceeding
        delay(550)
        onSplashFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("splash_screen_container"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Animated Logo Container
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .graphicsLayer {
                        scaleX = pageScale.value
                        scaleY = pageScale.value
                        alpha = pageAlpha.value
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // 1. Diagonal Gradient Background Badge
                    val badgeBrush = Brush.linearGradient(
                        colors = listOf(BrandIndigo, BrandTeal),
                        start = Offset(0f, 0f),
                        end = Offset(w, h)
                    )
                    drawRoundRect(
                        brush = badgeBrush,
                        topLeft = Offset(w * 0.08f, h * 0.08f),
                        size = Size(w * 0.84f, h * 0.84f),
                        cornerRadius = CornerRadius(w * 0.22f, h * 0.22f)
                    )

                    // 2. White Paper Page with Folded Corner
                    val pageLeft = w * 0.27f
                    val pageTop = h * 0.22f
                    val pageWidth = w * 0.46f
                    val pageHeight = h * 0.56f
                    val foldSize = w * 0.14f

                    // Shadow
                    drawRoundRect(
                        color = Color.Black.copy(alpha = 0.25f),
                        topLeft = Offset(pageLeft + 4.dp.toPx(), pageTop + 4.dp.toPx()),
                        size = Size(pageWidth, pageHeight),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    // Page Body
                    val paperPath = Path().apply {
                        moveTo(pageLeft, pageTop)
                        lineTo(pageLeft + pageWidth - foldSize, pageTop)
                        lineTo(pageLeft + pageWidth, pageTop + foldSize)
                        lineTo(pageLeft + pageWidth, pageTop + pageHeight)
                        lineTo(pageLeft, pageTop + pageHeight)
                        close()
                    }
                    drawPath(paperPath, Color.White)

                    // Dog-ear Fold Flap
                    val foldPath = Path().apply {
                        moveTo(pageLeft + pageWidth - foldSize, pageTop)
                        lineTo(pageLeft + pageWidth, pageTop + foldSize)
                        lineTo(pageLeft + pageWidth - foldSize, pageTop + foldSize)
                        close()
                    }
                    drawPath(foldPath, Color(0xFFE2E8F0))

                    // Document text lines
                    val lineStroke = 3.dp.toPx()
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(pageLeft + 8.dp.toPx(), pageTop + 24.dp.toPx()),
                        end = Offset(pageLeft + pageWidth - 8.dp.toPx(), pageTop + 24.dp.toPx()),
                        strokeWidth = lineStroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(pageLeft + 8.dp.toPx(), pageTop + 33.dp.toPx()),
                        end = Offset(pageLeft + pageWidth - 8.dp.toPx(), pageTop + 33.dp.toPx()),
                        strokeWidth = lineStroke,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFFCBD5E1),
                        start = Offset(pageLeft + 8.dp.toPx(), pageTop + 42.dp.toPx()),
                        end = Offset(pageLeft + pageWidth * 0.55f, pageTop + 42.dp.toPx()),
                        strokeWidth = lineStroke,
                        cap = StrokeCap.Round
                    )

                    // 3. Pen-Nib inside fold
                    val penPath = Path().apply {
                        moveTo(pageLeft + pageWidth - foldSize * 0.4f, pageTop + foldSize * 0.8f)
                        lineTo(pageLeft + pageWidth - foldSize * 1.3f, pageTop + foldSize * 1.6f)
                        lineTo(pageLeft + pageWidth - foldSize * 0.8f, pageTop + foldSize * 2.1f)
                        lineTo(pageLeft + pageWidth + foldSize * 0.1f, pageTop + foldSize * 1.3f)
                        close()
                    }
                    drawPath(penPath, BrandIndigo)

                    // 4. Animated Teal Flourish Stroke
                    if (strokeProgress.value > 0f) {
                        val flourishPath = Path().apply {
                            moveTo(pageLeft + 10.dp.toPx(), pageTop + pageHeight - 12.dp.toPx())
                            quadraticTo(
                                pageLeft + pageWidth * 0.6f,
                                pageTop + pageHeight + 4.dp.toPx(),
                                pageLeft + pageWidth - 4.dp.toPx(),
                                pageTop + pageHeight - 16.dp.toPx()
                            )
                        }

                        val pathMeasure = PathMeasure()
                        pathMeasure.setPath(flourishPath, false)
                        val length = pathMeasure.length
                        val animatedPath = Path()
                        pathMeasure.getSegment(0f, length * strokeProgress.value, animatedPath, true)

                        drawPath(
                            path = animatedPath,
                            color = BrandTeal,
                            style = Stroke(
                                width = 3.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        )

                        // Sparkle star near end of stroke
                        if (strokeProgress.value > 0.8f) {
                            val starAlpha = ((strokeProgress.value - 0.8f) / 0.2f).coerceIn(0f, 1f)
                            drawCircle(
                                color = BrandTeal.copy(alpha = starAlpha),
                                radius = 3.dp.toPx(),
                                center = Offset(
                                    pageLeft + pageWidth - 2.dp.toPx(),
                                    pageTop + pageHeight - 18.dp.toPx()
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Animated Wordmark and Tagline
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha.value
                    translationY = (1f - textAlpha.value) * 16.dp.toPx()
                }
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = Color.White,
                                fontWeight = FontWeight.Normal,
                                fontFamily = OutfitFontFamily
                            )
                        ) {
                            append("my")
                        }
                        withStyle(
                            SpanStyle(
                                color = BrandTeal,
                                fontWeight = FontWeight.Bold,
                                fontFamily = OutfitFontFamily
                            )
                        ) {
                            append("Prev")
                        }
                    },
                    fontSize = 38.sp,
                    letterSpacing = (-0.5).sp,
                    modifier = Modifier.testTag("splash_wordmark")
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "View. Edit. Sign.",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 2.sp,
                    modifier = Modifier.testTag("splash_tagline")
                )
            }
        }
    }
}
