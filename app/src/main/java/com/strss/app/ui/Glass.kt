@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.strss.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.strss.app.Haptics
import com.strss.app.R
import kotlin.math.roundToInt

// ---------- Font (Urbanist, geometric — closest match to the video) ----------
private fun uf(w: Int) = Font(R.font.urbanist, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
val UFont = FontFamily(uf(300), uf(400), uf(500), uf(600), uf(700), uf(800), uf(900))
val Ink = Color(0xFF1B2733)

@Composable
fun Tx(
    text: String, size: Int, w: Int = 400, color: Color = Color.White,
    modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start, lh: Int = 0
) = BasicText(
    text, modifier,
    TextStyle(
        fontFamily = UFont, fontSize = size.sp, fontWeight = FontWeight(w), color = color, textAlign = align,
        lineHeight = if (lh > 0) lh.sp else TextUnit.Unspecified,
        shadow = Shadow(Color(0x22000000), Offset(0f, 1f), 6f)
    )
)

// ---------- Background shared by the screen and every frosted-glass panel ----------
val LocalRoot = compositionLocalOf { IntSize.Zero }

@Composable
fun Backdrop() {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFFAEB9C4), Color(0xFF7088A0), Color(0xFF93A6B8), Color(0xFFD3DCE5))))
        val warm = Offset(w * .55f, h * .12f)
        drawCircle(Brush.radialGradient(listOf(Color(0x99C98F76), Color.Transparent), warm, w * .7f), w * .7f, warm)
        for (i in 0 until 7) {
            val y = h * (0.30f + i * 0.075f)
            val p = Path().apply {
                moveTo(-20f, y)
                cubicTo(w * .3f, y - 90f - i * 8f, w * .6f, y + 110f, w + 20f, y - 40f + i * 10f)
            }
            drawPath(p, Color.White.copy(alpha = 0.16f + 0.02f * i), style = Stroke(width = (3 + i * 3).dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(Color.White.copy(alpha = 0.18f), w * .16f, Offset(w * .2f, h * .46f))
        drawCircle(Color(0xFF2B3E55).copy(alpha = 0.25f), w * .22f, Offset(w * .85f, h * .62f))
        drawCircle(Color.White.copy(alpha = 0.22f), w * .1f, Offset(w * .7f, h * .82f))
    }
}

// ---------- Frosted glass panel: blurred copy of the backdrop + tint + highlight edge ----------
@Composable
fun Glass(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Float = 0.16f,
    blur: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val root = LocalRoot.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { pos = it.positionInRoot() }.clip(shape)) {
        if (root != IntSize.Zero) {
            Box(Modifier.matchParentSize().blur(blur, BlurredEdgeTreatment.Rectangle)) {
                Layout(content = { Backdrop() }) { ms, c ->
                    val p = ms.first().measure(androidx.compose.ui.unit.Constraints.fixed(root.width, root.height))
                    layout(c.maxWidth, c.maxHeight) { p.place(-pos.x.roundToInt(), -pos.y.roundToInt()) }
                }
            }
        }
        Box(
            Modifier.matchParentSize()
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = tint + 0.14f), Color.White.copy(alpha = tint * 0.5f))))
                .border(
                    1.dp,
                    Brush.linearGradient(listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.4f))),
                    shape
                )
        )
        content()
    }
}

// ---------- Tap with press-scale and haptics ----------
@Composable
fun Modifier.tap(onClick: () -> Unit): Modifier {
    val ctx = LocalContext.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.95f else 1f, label = "press")
    LaunchedEffect(pressed) { if (pressed) Haptics.tick(ctx) }
    return this
        .graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null) { Haptics.click(ctx); onClick() }
}

@Composable
fun GlassButton(text: String, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    if (filled) {
        Box(
            modifier.tap(onClick).clip(RoundedCornerShape(26.dp)).background(Color.White.copy(alpha = 0.94f)).padding(vertical = 16.dp, horizontal = 22.dp),
            contentAlignment = Alignment.Center
        ) { Tx(text, 15, 600, Ink) }
    } else {
        Glass(modifier.tap(onClick), RoundedCornerShape(26.dp), 0.12f) {
            Box(Modifier.padding(vertical = 16.dp, horizontal = 22.dp).align(Alignment.Center)) { Tx(text, 15, 600) }
        }
    }
}

@Composable
fun GlassCircle(icon: String, size: Dp = 44.dp, onClick: () -> Unit) {
    Glass(Modifier.size(size).tap(onClick), CircleShape, 0.14f) {
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) { Ico(icon, size * 0.5f) }
    }
}

@Composable
fun GlassSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateFloatAsState(if (on) 1f else 0f, label = "sw")
    Box(
        Modifier.tap { onChange(!on) }.size(54.dp, 32.dp).clip(CircleShape)
            .background(if (on) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.25f))
            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
    ) {
        Box(
            Modifier.padding(3.dp).offset(x = (22 * x).dp).size(26.dp).clip(CircleShape)
                .background(if (on) Ink else Color.White)
        )
    }
}

// ---------- Line icons drawn in code ----------
@Composable
fun Ico(kind: String, size: Dp = 24.dp, color: Color = Color.White, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val st = Stroke(width = s * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun o(x: Float, y: Float) = Offset(s * x, s * y)
        when (kind) {
            "bed" -> {
                drawArc(color, 40f, 270f, false, o(.15f, .15f), Size(s * .7f, s * .7f), style = st)
                drawCircle(color, s * .06f, o(.7f, .3f))
            }
            "stress" -> {
                drawCircle(color, s * .24f, o(.36f, .5f), style = st)
                drawCircle(color, s * .24f, o(.64f, .5f), style = st)
            }
            "screen" -> {
                drawRoundRect(color, o(.28f, .1f), Size(s * .44f, s * .8f), CornerRadius(s * .1f), style = st)
                drawLine(color, o(.43f, .78f), o(.57f, .78f), s * .075f, StrokeCap.Round)
            }
            "timer" -> {
                drawCircle(color, s * .34f, o(.5f, .56f), style = st)
                drawLine(color, o(.5f, .56f), o(.5f, .4f), s * .075f, StrokeCap.Round)
                drawLine(color, o(.5f, .56f), o(.62f, .62f), s * .075f, StrokeCap.Round)
                drawLine(color, o(.4f, .1f), o(.6f, .1f), s * .075f, StrokeCap.Round)
            }
            "breathe" -> for (k in 0..2) {
                val y = s * (.3f + .2f * k)
                val p = Path().apply {
                    moveTo(s * .12f, y)
                    cubicTo(s * .3f, y - s * .14f, s * .4f, y + s * .14f, s * .5f, y)
                    cubicTo(s * .6f, y - s * .14f, s * .7f, y + s * .14f, s * .88f, y)
                }
                drawPath(p, color, style = st)
            }
            "insights" -> {
                drawLine(color, o(.25f, .8f), o(.25f, .5f), s * .1f, StrokeCap.Round)
                drawLine(color, o(.5f, .8f), o(.5f, .2f), s * .1f, StrokeCap.Round)
                drawLine(color, o(.75f, .8f), o(.75f, .4f), s * .1f, StrokeCap.Round)
            }
            "home" -> {
                val r = CornerRadius(s * .07f)
                drawRoundRect(color, o(.15f, .15f), Size(s * .3f, s * .42f), r)
                drawRoundRect(color, o(.55f, .15f), Size(s * .3f, s * .25f), r)
                drawRoundRect(color, o(.15f, .65f), Size(s * .3f, s * .2f), r)
                drawRoundRect(color, o(.55f, .5f), Size(s * .3f, s * .35f), r)
            }
            "heart" -> {
                val p = Path().apply {
                    moveTo(s * .5f, s * .85f)
                    cubicTo(s * .05f, s * .55f, s * .15f, s * .12f, s * .5f, s * .3f)
                    cubicTo(s * .85f, s * .12f, s * .95f, s * .55f, s * .5f, s * .85f)
                }
                drawPath(p, color)
            }
            "focus" -> {
                drawCircle(color, s * .34f, o(.5f, .5f), style = st)
                drawCircle(color, s * .09f, o(.5f, .5f))
                drawLine(color, o(.5f, .06f), o(.5f, .14f), s * .075f, StrokeCap.Round)
            }
            "back" -> {
                val p = Path().apply { moveTo(s * .62f, s * .2f); lineTo(s * .38f, s * .5f); lineTo(s * .62f, s * .8f) }
                drawPath(p, color, style = st)
            }
        }
    }
}
