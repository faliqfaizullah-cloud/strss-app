package com.strss.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.strss.app.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ---- palette taken from the reference: pure black, #1E1E20 cards, soft greys, periwinkle blue ----
private val FBg = Color.Black
private val FCard = Color(0xFF1E1E20)
private val FPill = Color(0xFF1D1D1F)
private val FGray = Color(0xFF8E8E93)
private val FDim = Color(0xFF5E5E63)
private val FBlue = Color(0xFF8CA6F4)
private val SF = FontFamily.SansSerif

@Composable
private fun T(text: String, size: Int, w: Int = 400, color: Color = Color.White, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) =
    BasicText(text, modifier, TextStyle(fontFamily = SF, fontSize = size.sp, fontWeight = FontWeight(w), color = color, textAlign = align))

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun fmtHMS(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

private fun periodTotal(tab: Int, days: Map<String, Long>): Long {
    val cal = Calendar.getInstance()
    val endKey = dayKey(cal.timeInMillis)
    when (tab) {
        1 -> cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        2 -> cal.set(Calendar.DAY_OF_MONTH, 1)
        3 -> cal.set(Calendar.DAY_OF_YEAR, 1)
    }
    var sum = 0L
    var guard = 0
    while (guard++ < 400) {
        val k = dayKey(cal.timeInMillis)
        sum += days[k] ?: 0L
        if (k == endKey) break
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return sum
}

private fun periodLabel(tab: Int): String {
    val l = Locale.getDefault()
    return when (tab) {
        0 -> SimpleDateFormat("EEEE, MMM d", l).format(Date())
        1 -> "This week"
        2 -> SimpleDateFormat("MMMM yyyy", l).format(Date())
        else -> SimpleDateFormat("yyyy", l).format(Date())
    }
}

@Composable
private fun Modifier.grain(): Modifier {
    val noise = remember {
        val n = 128
        val bmp = android.graphics.Bitmap.createBitmap(n, n, android.graphics.Bitmap.Config.ARGB_8888)
        val r = java.util.Random(7)
        for (x in 0 until n) for (y in 0 until n) {
            val v = r.nextInt(256)
            bmp.setPixel(x, y, android.graphics.Color.argb(255, v, v, v))
        }
        bmp.asImageBitmap()
    }
    return this.drawBehind {
        drawRect(ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated)), alpha = 0.06f)
    }
}

// =====================================================================================
@Composable
fun FocusScreen(go: (String) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val store = remember { Store(ctx) }

    var tab by remember { mutableIntStateOf(0) }
    var expanded by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }
    var startMs by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var goalHit by remember { mutableStateOf(false) }
    var breakUntil by remember { mutableLongStateOf(0L) }

    fun startSession(isManual: Boolean) {
        if (running) return
        manual = isManual
        startMs = System.currentTimeMillis(); now = startMs
        goalHit = false; breakUntil = 0L
        running = true
        Haptics.heavy(ctx)
    }

    fun finishSession() {
        if (!running) return
        val end = System.currentTimeMillis()
        running = false
        if (end - startMs >= 10_000) {
            store.addFocus(startMs, end)
            refresh++
            val g = store.goalMin
            if (g > 0 && end - startMs >= g * 60_000L) breakUntil = end + store.breakMin * 60_000L
        }
        Haptics.double(ctx)
    }

    // keep the screen on while this page is open
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    // dim to the minimum while a session runs
    DisposableEffect(running) {
        val win = ctx.findActivity()?.window
        if (running && win != null) { val lp = win.attributes; lp.screenBrightness = 0.01f; win.attributes = lp }
        onDispose { if (win != null) { val lp = win.attributes; lp.screenBrightness = -1f; win.attributes = lp } }
    }

    // flip detection: face down for 1.5s starts, face up for 0.8s ends
    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var downSince = 0L
        var upSince = 0L
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
                val t = System.currentTimeMillis()
                val faceDown = z < -7.5f && abs(x) < 3.5f && abs(y) < 3.5f
                val faceUp = z > 2.5f
                if (faceDown) {
                    upSince = 0L
                    if (downSince == 0L) downSince = t
                    if (!running && t - downSince >= 1500) startSession(false)
                } else {
                    downSince = 0L
                    if (running && !manual) {
                        if (faceUp) {
                            if (upSince == 0L) upSince = t
                            if (t - upSince >= 800) finishSession()
                        } else upSince = 0L
                    }
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        if (sensor != null) sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose { sm.unregisterListener(l); finishSession() }
    }

    // ticker: live clock, Pomodoro goal alert
    LaunchedEffect(running) {
        while (running) {
            now = System.currentTimeMillis()
            val g = store.goalMin
            if (g > 0 && !goalHit && now - startMs >= g * 60_000L) {
                goalHit = true
                Haptics.heavy(ctx); delay(220); Haptics.heavy(ctx)
                Notifs.post(ctx, 300, "focus", "Pomodoro complete", "You focused for $g minutes. Flip your phone up and take a break.")
            }
            delay(1000)
        }
    }

    // break countdown after a completed Pomodoro
    LaunchedEffect(breakUntil) {
        while (breakUntil > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(1000) }
        if (breakUntil != 0L) {
            breakUntil = 0L
            Haptics.double(ctx)
            Notifs.post(ctx, 301, "focus", "Break over", "Ready for the next focus session?")
        }
    }

    val days = remember(refresh) { store.focusDays() }
    val streak = remember(refresh, overlay) { store.streak() }
    val trophies = remember(refresh) { (store.focusTotal() / 3_600_000L).toInt() }
    val live = if (running) (now - startMs).coerceAtLeast(0) else 0L
    val total = periodTotal(tab, days) + live
    val hours = (total / 3_600_000L).toInt()
    val frac = (total % 3_600_000L) / 3_600_000f

    Box(Modifier.fillMaxSize().background(FBg).grain()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
            // ---------- top bar ----------
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                StatPill("flame", "$streak")
                Spacer(Modifier.width(8.dp))
                StatPill("trophy", "$trophies")
                Spacer(Modifier.weight(1f))
                RoundBtn("help") { overlay = "help" }
                Spacer(Modifier.width(8.dp))
                RoundBtn("gear") { overlay = "settings" }
            }

            // ---------- centre ----------
            if (!expanded) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (running) {
                            T(fmtHMS(live), 76, 700, Color.White, align = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            T(if (goalHit) "goal reached - keep going or flip back" else "focus session in progress", 16, 400, FGray, align = TextAlign.Center)
                            Spacer(Modifier.height(36.dp))
                            val g = store.goalMin
                            if (g > 0) Bar("0m", "${g}m", live / (g * 60_000f))
                            else Bar("${hours}h", "${hours + 1}h", frac)
                            Spacer(Modifier.height(36.dp))
                            T(if (manual) "end session" else "flip your phone back to finish", 16, 400, if (manual) FBlue else FDim,
                                if (manual) Modifier.tap { finishSession() } else Modifier, TextAlign.Center)
                        } else {
                            Badge(hours)
                            Spacer(Modifier.height(30.dp))
                            Bar("${hours}h", "${hours + 1}h", frac)
                            Spacer(Modifier.height(44.dp))
                            if (breakUntil > now) {
                                T("break time · ${fmtHMS((breakUntil - now).coerceAtLeast(0))} left", 17, 400, FBlue, align = TextAlign.Center)
                            } else {
                                T("flip your phone to start a focus session...", 17, 400, FDim,
                                    Modifier.tap { startSession(true) }, TextAlign.Center)
                            }
                        }
                    }
                }
            }

            // ---------- bottom sheet ----------
            Sheet(expanded, { expanded = it }, tab, { tab = it }, periodLabel(tab), total, store, refresh)
        }

        if (overlay.isNotEmpty()) {
            Box(
                Modifier.fillMaxSize().background(Color(0xCC000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { overlay = "" },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(FCard)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (overlay == "help") HelpContent() else SettingsContent(store) { refresh++ }
                    Box(
                        Modifier.fillMaxWidth().tap { overlay = "" }.clip(RoundedCornerShape(22.dp)).background(Color.White).padding(vertical = 15.dp),
                        contentAlignment = Alignment.Center
                    ) { T("Done", 16, 600, Color.Black) }
                }
            }
        }
    }
}

// =====================================================================================  pieces
@Composable
private fun StatPill(icon: String, value: String) {
    Row(
        Modifier.clip(CircleShape).background(FPill).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FIcon(icon, 22.dp)
        T(value, 19, 600)
    }
}

@Composable
private fun RoundBtn(icon: String, onClick: () -> Unit) {
    Box(Modifier.size(58.dp).tap(onClick).clip(CircleShape).background(FPill), contentAlignment = Alignment.Center) {
        if (icon == "help") {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { T("?", 16, 800, Color.Black) }
        } else FIcon(icon, 26.dp)
    }
}

@Composable
private fun FIcon(kind: String, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        fun o(x: Float, y: Float) = Offset(s * x, s * y)
        when (kind) {
            "flame" -> {
                val p = Path().apply {
                    moveTo(s * .5f, s * .04f)
                    cubicTo(s * .9f, s * .38f, s * .88f, s * .95f, s * .5f, s * .95f)
                    cubicTo(s * .12f, s * .95f, s * .1f, s * .55f, s * .34f, s * .38f)
                    cubicTo(s * .36f, s * .5f, s * .42f, s * .55f, s * .46f, s * .55f)
                    cubicTo(s * .42f, s * .35f, s * .44f, s * .2f, s * .5f, s * .04f)
                    close()
                }
                drawPath(p, Brush.verticalGradient(listOf(Color(0xFFFFB347), Color(0xFFFF6A00))))
            }
            "trophy" -> {
                val bowl = Path().apply {
                    moveTo(s * .25f, s * .12f); lineTo(s * .75f, s * .12f); lineTo(s * .75f, s * .4f)
                    cubicTo(s * .75f, s * .62f, s * .62f, s * .7f, s * .5f, s * .7f)
                    cubicTo(s * .38f, s * .7f, s * .25f, s * .62f, s * .25f, s * .4f)
                    close()
                }
                drawPath(bowl, Color.White)
                val st = Stroke(width = s * .08f)
                drawCircle(Color.White, s * .11f, o(.2f, .27f), style = st)
                drawCircle(Color.White, s * .11f, o(.8f, .27f), style = st)
                drawRect(Color.White, o(.46f, .68f), Size(s * .08f, s * .17f))
                drawRoundRect(Color.White, o(.3f, .84f), Size(s * .4f, s * .09f), androidx.compose.ui.geometry.CornerRadius(s * .03f))
            }
            "gear" -> {
                for (i in 0 until 8) {
                    val a = i * PI / 4
                    drawLine(Color.White, o(.5f + .3f * cos(a).toFloat(), .5f + .3f * sin(a).toFloat()),
                        o(.5f + .44f * cos(a).toFloat(), .5f + .44f * sin(a).toFloat()), s * .13f, StrokeCap.Round)
                }
                drawCircle(Color.White, s * .34f, o(.5f, .5f))
                drawCircle(Color(0xFF1D1D1F), s * .13f, o(.5f, .5f))
            }
        }
    }
}

@Composable
private fun Bar(left: String, right: String, frac: Float) {
    Column(Modifier.width(256.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            T(left, 14, 500, FGray); T(right, 14, 500, FGray)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color(0xFF3A3A3C))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac.coerceIn(0f, 1f)).background(Color(0xFFBDBDC2)))
        }
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        quadraticBezierTo(c.x, c.y, c.x + r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y)
        quadraticBezierTo(c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
}

/** Scalloped glass badge with the hour count and an orbiting periwinkle ring. */
@Composable
private fun Badge(hours: Int) {
    val inf = rememberInfiniteTransition(label = "badge")
    val pulse by inf.animateFloat(0.8f, 1f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "pulse")
    val numSize = if (hours >= 100) 60 else 92
    val ringA = 150.dp
    Box(Modifier.size(330.dp, 250.dp), contentAlignment = Alignment.Center) {
        // back layer: upper half of the ring + badge
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val rad = 76.dp.toPx()
            rotate(-14f, c) {
                drawArc(FBlue, 180f, 180f, false, Offset(c.x - ringA.toPx(), c.y - 52.dp.toPx()),
                    Size(ringA.toPx() * 2, 104.dp.toPx()), style = Stroke(11.dp.toPx(), cap = StrokeCap.Round))
            }
            val path = Path()
            val steps = 360
            for (i in 0..steps) {
                val a = i * 2.0 * PI / steps
                val r = (rad * (1.0 + 0.06 * cos(14 * a))).toFloat()
                val x = c.x + r * cos(a).toFloat()
                val y = c.y + r * sin(a).toFloat()
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, Brush.verticalGradient(listOf(Color(0xFF1B1B1E), Color(0xFF060607))))
            drawPath(path, Color.White.copy(alpha = 0.92f), style = Stroke(2.dp.toPx()))
            scale(0.9f, c) { drawPath(path, Color.White.copy(alpha = 0.16f), style = Stroke(1.dp.toPx())) }
        }
        // number
        Row(verticalAlignment = Alignment.Bottom) {
            BasicText(
                hours.toString(),
                style = TextStyle(
                    brush = Brush.verticalGradient(listOf(Color(0xFFEFF3FF), Color(0xFFA9BCFF), Color(0xFF6F8EF0), Color(0xFFBBCBFF))),
                    fontFamily = UFont, fontWeight = FontWeight(900), fontStyle = FontStyle.Italic, fontSize = numSize.sp,
                    shadow = Shadow(Color(0xFF6F8EF0), Offset(0f, 0f), 28f)
                )
            )
            T("hr", 24, 800, Color(0xFFE6ECFF), Modifier.padding(start = 2.dp, bottom = 12.dp))
        }
        // front layer: lower half of the ring + sparkles
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            rotate(-14f, c) {
                drawArc(FBlue, 0f, 180f, false, Offset(c.x - ringA.toPx(), c.y - 52.dp.toPx()),
                    Size(ringA.toPx() * 2, 104.dp.toPx()), style = Stroke(11.dp.toPx(), cap = StrokeCap.Round))
                val a = ringA.toPx(); val b = 52.dp.toPx()
                val t1 = 158 * PI / 180
                sparkle(Offset(c.x + a * cos(t1).toFloat(), c.y + b * sin(t1).toFloat()), 17.dp.toPx() * pulse, FBlue)
                val t2 = -22 * PI / 180
                sparkle(Offset(c.x + a * cos(t2).toFloat(), c.y + b * sin(t2).toFloat()), 15.dp.toPx() * (1.8f - pulse), FBlue)
            }
            sparkle(Offset(c.x - 70.dp.toPx(), c.y - 42.dp.toPx()), 17.dp.toPx() * pulse, Color.White)
            drawCircle(Color.White, 3.dp.toPx(), Offset(c.x - 52.dp.toPx(), c.y - 66.dp.toPx()))
        }
    }
}

@Composable
private fun ColumnScope.Sheet(
    expanded: Boolean, setExpanded: (Boolean) -> Unit, tab: Int, setTab: (Int) -> Unit,
    label: String, total: Long, store: Store, refresh: Int
) {
    Column(
        Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(36.dp)).background(FCard)
            .then(if (expanded) Modifier.weight(1f) else Modifier)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var acc by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.fillMaxWidth().height(28.dp)
                .pointerInput(expanded) {
                    detectVerticalDragGestures(
                        onDragStart = { acc = 0f },
                        onDragEnd = { if (acc < -30f) setExpanded(true) else if (acc > 30f) setExpanded(false) },
                        onVerticalDrag = { _, d -> acc += d }
                    )
                }
                .tap { setExpanded(!expanded) },
            contentAlignment = Alignment.Center
        ) { Box(Modifier.size(44.dp, 5.dp).clip(CircleShape).background(Color(0xFF5A5A5E))) }

        T(label, 18, 400, Color(0xFFB4B4BA), align = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        T(fmtDur(total), 64, 700, Color.White, align = TextAlign.Center)
        Spacer(Modifier.height(2.dp))
        T("total flip time", 16, 400, FGray, align = TextAlign.Center)
        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Color(0xFF242426)).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("D", "W", "M", "Y").forEachIndexed { i, l ->
                val sel = i == tab
                Box(
                    Modifier.weight(1f).height(54.dp).tap { setTab(i) }.clip(RoundedCornerShape(20.dp))
                        .background(if (sel) Color(0xFF454548) else Color(0xFF2C2C2E)),
                    contentAlignment = Alignment.Center
                ) { T(l, 19, 600, if (sel) Color.White else FDim) }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (expanded) {
            val sessions = remember(refresh) { store.sessions() }
            val f = remember { SimpleDateFormat("EEE, MMM d  h:mm a", Locale.getDefault()) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Recent sessions", 16, 600)
                if (sessions.isEmpty()) T("No sessions yet. Flip your phone face down to begin.", 14, 400, FGray)
                sessions.take(30).forEach { (s, e) ->
                    Row(Modifier.fillMaxWidth()) {
                        T(f.format(Date(s)), 15, 400, Color(0xFFB4B4BA), Modifier.weight(1f))
                        T(fmtDur(e - s).let { if (e - s < 60_000) "${(e - s) / 1000}s" else it }, 15, 600)
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        } else Spacer(Modifier.height(4.dp))
    }
}

// =====================================================================================  help & settings
@Composable
private fun HelpContent() {
    T("How focus works", 22, 700)
    listOf(
        "Put your phone face down for a moment to start a focus session. Flip it back up to finish.",
        "Your session time is added to your total flip time. Switch D, W, M and Y to see each period.",
        "Streak: days in a row you reach your daily minimum.",
        "Trophies: every full hour of focus you have ever flipped.",
        "Pomodoro: your phone buzzes when your goal is reached, then a break timer starts.",
        "No flip? Tap the text under the badge to start by hand."
    ).forEach { T(it, 15, 400, Color(0xFFC4C4C9)) }
}

@Composable
private fun SettingsContent(store: Store, onChange: () -> Unit) {
    var goal by remember { mutableIntStateOf(store.goalMin) }
    var brk by remember { mutableIntStateOf(store.breakMin) }
    var streak by remember { mutableIntStateOf(store.streakMin) }
    T("Focus settings", 22, 700)
    T("Pomodoro goal", 14, 500, FGray)
    Chips(listOf(0 to "Off", 25 to "25m", 50 to "50m", 90 to "90m"), goal) { goal = it; store.goalMin = it; onChange() }
    T("Break length", 14, 500, FGray)
    Chips(listOf(5 to "5m", 10 to "10m", 15 to "15m"), brk) { brk = it; store.breakMin = it; onChange() }
    T("Daily minimum for your streak", 14, 500, FGray)
    Chips(listOf(10 to "10m", 30 to "30m", 60 to "1h"), streak) { streak = it; store.streakMin = it; onChange() }
}

@Composable
private fun Chips(items: List<Pair<Int, String>>, sel: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (v, l) ->
            Box(
                Modifier.weight(1f).tap { onPick(v) }.clip(RoundedCornerShape(18.dp))
                    .background(if (sel == v) Color.White else Color(0xFF2C2C2E)).padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) { T(l, 15, 600, if (sel == v) Color.Black else Color(0xFFC4C4C9)) }
        }
    }
}
