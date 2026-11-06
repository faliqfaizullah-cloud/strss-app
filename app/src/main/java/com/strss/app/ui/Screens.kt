package com.strss.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.strss.app.*
import com.strss.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

fun fmtDur(ms: Long): String { val m = ms / 60000; return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }
fun fmtMin(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
fun fmtClock(min: Int): String {
    val h = (min / 60) % 24; val m = min % 60
    return "%d:%02d %s".format(if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")
}

private val W70 = Color.White.copy(alpha = 0.72f)

// =====================================================================================
@Composable
fun StrssApp() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var screen by remember { mutableStateOf("home") }
    var premium by remember { mutableStateOf(store.premium) }
    var limits by remember { mutableStateOf(store.limits()) }
    var sheet by remember { mutableStateOf<Pair<String, String>?>(null) }
    var snap by remember { mutableStateOf(Snap(false, 0, emptyList(), LongArray(24), 0, 0, 0)) }
    var tick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) { while (true) { delay(30_000); tick++ } }
    LaunchedEffect(Refresh.resume.intValue, tick, limits) {
        snap = withContext(Dispatchers.IO) { Usage.snapshot(ctx) }
        StrssWidget.refresh(ctx)
    }
    var iconSheet by remember { mutableStateOf(false) }
    var boxIcon by remember { mutableStateOf(AppIcon.isBox(ctx)) }
    BackHandler(screen != "home" || sheet != null || iconSheet) { if (iconSheet) iconSheet = false else if (sheet != null) sheet = null else screen = "home" }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val root = IntSize(constraints.maxWidth, constraints.maxHeight)
        CompositionLocalProvider(LocalRoot provides root) {
            Backdrop()
            val go: (String) -> Unit = { screen = it }
            val openSheet: (String, String) -> Unit = { p, l -> sheet = p to l }
            when (screen) {
                "home" -> HomeScreen(snap, premium, { premium = it; store.premium = it }, go) { iconSheet = true }
                "stress" -> StressScreen(snap, go)
                "time" -> TimeScreen(snap, limits, go, openSheet)
                "timers" -> TimersScreen(snap, limits, go, openSheet)
                "bed" -> BedScreen(store, go)
                "breathe" -> BreatheScreen(go)
                "insights" -> InsightsScreen(snap, go)
                "focus" -> FocusScreen(go)
            }
            if (screen != "focus") BottomNav(screen, go, Modifier.align(Alignment.BottomCenter))

            sheet?.let { (pkg, label) ->
                LimitSheet(label, limits[pkg] ?: 0, onSet = { m ->
                    store.setLimit(pkg, m); limits = store.limits(); LimitService.start(ctx); sheet = null
                }, onDismiss = { sheet = null })
            }

            if (iconSheet) IconSheet(boxIcon, { useBox ->
                AppIcon.set(ctx, useBox); boxIcon = useBox; Haptics.double(ctx)
            }, { iconSheet = false })
        }
    }
}

// =====================================================================================
@Composable
fun BottomNav(current: String, go: (String) -> Unit, modifier: Modifier) {
    Glass(modifier.navigationBarsPadding().padding(bottom = 14.dp), CircleShape, 0.14f) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            NavItem("insights", "Insights", current == "insights") { go("insights") }
            NavItem("home", "Home", current == "home") { go("home") }
        }
    }
}

@Composable
private fun NavItem(icon: String, label: String, sel: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.tap(onClick).clip(CircleShape).background(if (sel) Color.White else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Ico(icon, 20.dp, if (sel) Ink else Color.White)
        Tx(label, 14, 600, if (sel) Ink else Color.White)
    }
}

@Composable
private fun Page(title: String?, go: (String) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
    ) {
        if (title != null) {
            Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassCircle("back") { go("home") }
                Tx(title, 26, 600)
            }
        }
        content()
        Spacer(Modifier.height(120.dp))
    }
}

@Composable
private fun AccessCard(snap: Snap) {
    if (snap.access) return
    val ctx = LocalContext.current
    Glass(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Tx("Allow usage access", 18, 600)
            Tx("Strss needs Usage Access to measure your screen time and app timers. Everything stays on your phone.", 13, 400, W70)
            GlassButton("Open settings", filled = true, modifier = Modifier.fillMaxWidth()) {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

// =====================================================================================  HOME
@Composable
fun HomeScreen(snap: Snap, premium: Boolean, onPremium: (Boolean) -> Unit, go: (String) -> Unit, onIcon: () -> Unit) {
    Page(null, go) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Ico("breathe", 40.dp, modifier = Modifier.tap(onIcon))
            Spacer(Modifier.weight(1f))
            Glass(shape = CircleShape, tint = 0.10f) {
                Row(Modifier.padding(4.dp)) {
                    Seg("Basic", !premium) { onPremium(false) }
                    Seg("Premium", premium) { onPremium(true) }
                }
            }
        }
        AccessCard(snap)

        BoxWithConstraints(Modifier.fillMaxWidth().height(410.dp)) {
            val w = maxWidth
            val glow by rememberInfiniteTransition(label = "g").animateFloat(0.5f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "ga")
            Pebble("Bed Schedule", "bed", Modifier.offset(w * 0.04f, 10.dp), -10f, RoundedCornerShape(46.dp, 60.dp, 44.dp, 58.dp), false) { go("bed") }
            Pebble("Stress", "stress", Modifier.offset(w * 0.52f, 40.dp), 12f, RoundedCornerShape(58.dp, 44.dp, 62.dp, 42.dp), true) { go("stress") }
            Pebble("Screen Time", "screen", Modifier.offset(w * 0.0f, 150.dp), -6f, RoundedCornerShape(60.dp, 48.dp, 46.dp, 62.dp), false) { go("time") }
            Pebble("App Timers", "timer", Modifier.offset(w * 0.56f, 175.dp), 8f, RoundedCornerShape(44.dp, 62.dp, 58.dp, 46.dp), false) { go("timers") }
            Pebble("Breathe", "breathe", Modifier.offset(w * 0.10f, 285.dp), -4f, RoundedCornerShape(52.dp, 44.dp, 60.dp, 56.dp), false) { go("breathe") }
            Pebble("Focus", "focus", Modifier.offset(w * 0.50f, 300.dp), 9f, RoundedCornerShape(46.dp, 58.dp, 48.dp, 60.dp), false) { go("focus") }
            Canvas(Modifier.offset(w * 0.40f, 130.dp).size(80.dp)) {
                drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = glow), Color.Transparent)), size.minDimension / 2)
                drawCircle(Color.White, 6.dp.toPx())
            }
        }

        BasicText(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Start Your ") }
                withStyle(SpanStyle(fontWeight = FontWeight.Light, color = Color.White.copy(alpha = 0.78f))) { append("Stress Relief Mode") }
            },
            style = TextStyle(fontFamily = UFont, fontSize = 38.sp, lineHeight = 44.sp, color = Color.White)
        )
        Spacer(Modifier.height(8.dp))
        Tx("Understand your screen habits, calm your nervous system and reduce digital tension.", 14, 400, W70)
        Spacer(Modifier.height(18.dp))

        if (premium) {
            StressCard(snap) { go("stress") }
            Spacer(Modifier.height(14.dp))
            CalmCard(snap) { go("breathe") }
            Spacer(Modifier.height(18.dp))
        }
        GlassButton("Start Calm Session", filled = true, modifier = Modifier.fillMaxWidth()) { go("breathe") }
    }
}

@Composable
private fun Seg(label: String, sel: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.tap(onClick).clip(CircleShape).background(if (sel) Color.White else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 11.dp)
    ) { Tx(label, 13, 600, if (sel) Ink else Color.White) }
}

@Composable
private fun Pebble(label: String, icon: String, modifier: Modifier, rot: Float, shape: androidx.compose.ui.graphics.Shape, bright: Boolean, onClick: () -> Unit) {
    Glass(modifier.size(118.dp).graphicsLayer { rotationZ = rot }.tap(onClick), shape, if (bright) 0.34f else 0.12f) {
        Column(
            Modifier.fillMaxSize().graphicsLayer { rotationZ = -rot },
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Ico(icon, 24.dp)
            Spacer(Modifier.height(6.dp))
            Tx(label, 12, 500)
        }
    }
}

// =====================================================================================  STRESS
@Composable
fun StressCard(snap: Snap, onClick: () -> Unit) {
    Glass(Modifier.fillMaxWidth().tap(onClick), RoundedCornerShape(28.dp), 0.16f) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Tx("Daily Stress Index", 13, 500, W70)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Tx("${snap.stress}", 64, 300)
                Tx(" LVL", 14, 500, W70, Modifier.padding(bottom = 14.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Ico("screen", 14.dp); Tx(fmtDur(snap.totalMs), 13, 500) }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { Ico("bed", 14.dp); Tx(fmtDur(snap.nightMs), 13, 500) }
                }
            }
            Dial(snap.hourly)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("12AM", "6AM", "12PM", "6PM", "12AM").forEach { Tx(it, 10, 500, W70) }
            }
            Spacer(Modifier.height(12.dp))
            Tx("Smart Stress Insight", 13, 700, Color.White, Modifier.fillMaxWidth(), TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Tx(Insight.text(snap.stress), 12, 400, W70, Modifier.fillMaxWidth(), TextAlign.Center)
        }
    }
}

@Composable
private fun Dial(hourly: LongArray) {
    val nowFrac = remember { val c = Calendar.getInstance(); (c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f) / 24f }
    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
        val w = size.width
        val r = w * 0.58f
        val m = 34.dp.toPx()
        val cx = w / 2f
        val cy = m + r
        fun pt(deg: Float, rad: Float) = Offset(cx + rad * cos(deg * PI / 180).toFloat(), cy + rad * sin(deg * PI / 180).toFloat())
        drawArc(Color.White.copy(alpha = 0.85f), -145f, 110f, false, Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
        for (i in 0..47) {
            val deg = -145f + 110f * i / 47f
            val len = (3 + min(1f, hourly[i / 2] / (20 * 60000f)) * 20).dp.toPx()
            drawLine(Color.White.copy(alpha = 0.6f), pt(deg, r + 8.dp.toPx()), pt(deg, r + 8.dp.toPx() + len), 1.5.dp.toPx(), StrokeCap.Round)
        }
        val k = pt(-145f + 110f * nowFrac, r)
        drawCircle(Color.White.copy(alpha = 0.3f), 10.dp.toPx(), k)
        drawCircle(Color.White, 5.dp.toPx(), k)
    }
}

@Composable
fun CalmCard(snap: Snap, onClick: () -> Unit) {
    Glass(Modifier.fillMaxWidth(), RoundedCornerShape(28.dp), 0.16f) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Tx("Calm Score", 13, 500, W70)
                Row(verticalAlignment = Alignment.Bottom) {
                    Tx("${snap.calm}", 52, 300)
                    Tx(" SCR", 14, 500, W70, Modifier.padding(bottom = 10.dp))
                }
                Tx("Timers exceeded: ${snap.overruns}", 12, 400, W70)
            }
            Box(Modifier.size(64.dp).tap(onClick).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Ico("heart", 28.dp, Ink)
            }
        }
    }
}

@Composable
fun StressScreen(snap: Snap, go: (String) -> Unit) {
    Page("Stress", go) {
        AccessCard(snap)
        StressCard(snap) { go("breathe") }
        Spacer(Modifier.height(16.dp))
        CalmCard(snap) { go("breathe") }
        Spacer(Modifier.height(16.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Tx("What shapes your index", 17, 600)
                Row { Tx("Screen time today", 14, 400, W70, Modifier.weight(1f)); Tx(fmtDur(snap.totalMs), 14, 600) }
                Row { Tx("Use during bed hours", 14, 400, W70, Modifier.weight(1f)); Tx(fmtDur(snap.nightMs), 14, 600) }
                Row { Tx("App timers exceeded", 14, 400, W70, Modifier.weight(1f)); Tx("${snap.overruns}", 14, 600) }
                Tx("An estimate from your device usage. It is not a medical measurement.", 11, 400, W70)
            }
        }
    }
}

// =====================================================================================  SCREEN TIME
@Composable
fun AppIcon(pkg: String, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { try { ctx.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() } catch (e: Exception) { null } }
    if (bmp != null) Image(bmp, null, Modifier.size(size).clip(RoundedCornerShape(10.dp)))
    else Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
}

@Composable
fun TimeScreen(snap: Snap, limits: Map<String, Int>, go: (String) -> Unit, openSheet: (String, String) -> Unit) {
    Page("Screen Time", go) {
        AccessCard(snap)
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Tx("Today", 13, 500, W70)
                Tx(fmtDur(snap.totalMs), 52, 300)
                Spacer(Modifier.height(10.dp))
                val mx = max(1L, snap.hourly.max())
                Row(Modifier.fillMaxWidth().height(80.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    snap.hourly.forEach {
                        Box(Modifier.weight(1f).fillMaxHeight(max(0.04f, it.toFloat() / mx)).clip(RoundedCornerShape(3.dp)).background(Color.White.copy(alpha = 0.8f)))
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("12AM", "6AM", "12PM", "6PM").forEach { Tx(it, 10, 500, W70) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Tx("Most used apps", 17, 600)
                if (snap.apps.isEmpty()) Tx("No app usage yet today.", 13, 400, W70)
                val top = snap.apps.firstOrNull()?.ms ?: 1L
                snap.apps.take(12).forEach { a ->
                    Column(Modifier.tap { openSheet(a.pkg, a.label) }) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppIcon(a.pkg, 36.dp)
                            Column(Modifier.weight(1f)) {
                                Tx(a.label, 14, 600)
                                limits[a.pkg]?.let { Tx("Timer ${fmtMin(it)}", 11, 400, W70) }
                            }
                            Tx(fmtDur(a.ms), 14, 500)
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(a.ms.toFloat() / top).clip(CircleShape).background(Color.White))
                        }
                    }
                }
                Tx("Tap an app to set a daily timer.", 11, 400, W70)
            }
        }
    }
}

// =====================================================================================  APP TIMERS
@Composable
fun TimersScreen(snap: Snap, limits: Map<String, Int>, go: (String) -> Unit, openSheet: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var installed by remember { mutableStateOf(emptyList<Pair<String, String>>()) }
    LaunchedEffect(Unit) { installed = withContext(Dispatchers.IO) { Usage.installed(ctx) } }
    val used = snap.apps.associate { it.pkg to it.ms }
    val sorted = installed.sortedWith(compareByDescending<Pair<String, String>> { limits.containsKey(it.first) }.thenByDescending { used[it.first] ?: 0L })
    Page("App Timers", go) {
        AccessCard(snap)
        Tx("Set a daily limit. Strss nudges you with a notification and haptics when time is up.", 13, 400, W70, Modifier.padding(bottom = 14.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                sorted.take(40).forEach { (pkg, label) ->
                    Row(Modifier.tap { openSheet(pkg, label) }.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppIcon(pkg, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Tx(label, 14, 600)
                            Tx("Today ${fmtDur(used[pkg] ?: 0L)}", 11, 400, W70)
                        }
                        val l = limits[pkg]
                        Box(Modifier.clip(CircleShape).background(if (l != null) Color.White else Color.White.copy(alpha = 0.18f)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Tx(if (l != null) fmtMin(l) else "Set", 12, 600, if (l != null) Ink else Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LimitSheet(label: String, current: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    var m by remember { mutableIntStateOf(if (current > 0) current else 30) }
    val src = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxSize().background(Color(0x66101820)).clickable(interactionSource = src, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Glass(Modifier.padding(24.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }, RoundedCornerShape(28.dp), 0.28f, 26.dp) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Tx("Daily timer for $label", 17, 600, align = TextAlign.Center)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(Modifier.size(46.dp).tap { m = max(5, m - 5) }.clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), Alignment.Center) { Tx("−", 24, 500) }
                    Tx(fmtMin(m), 36, 300, modifier = Modifier.width(120.dp), align = TextAlign.Center)
                    Box(Modifier.size(46.dp).tap { m = min(720, m + 5) }.clip(CircleShape).background(Color.White.copy(alpha = 0.25f)), Alignment.Center) { Tx("+", 24, 500) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach {
                        Box(Modifier.tap { m = it }.clip(CircleShape).background(if (m == it) Color.White else Color.White.copy(alpha = 0.2f)).padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Tx(fmtMin(it), 12, 600, if (m == it) Ink else Color.White)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (current > 0) GlassButton("Remove", modifier = Modifier.weight(1f)) { onSet(0) }
                    GlassButton("Set timer", filled = true, modifier = Modifier.weight(1f)) { onSet(m) }
                }
            }
        }
    }
}

// =====================================================================================  BED SCHEDULE
@Composable
fun BedScreen(store: Store, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var on by remember { mutableStateOf(store.bedEnabled) }
    var bed by remember { mutableIntStateOf(store.bedMin) }
    var wake by remember { mutableIntStateOf(store.wakeMin) }
    var wind by remember { mutableIntStateOf(store.windDown) }
    fun save() { store.bedEnabled = on; store.bedMin = bed; store.wakeMin = wake; store.windDown = wind; Bed.schedule(ctx) }
    fun pick(cur: Int, set: (Int) -> Unit) {
        TimePickerDialog(ctx, { _, h, mi -> set(h * 60 + mi); Haptics.double(ctx); save() }, cur / 60, cur % 60, false).show()
    }
    val goal = ((wake - bed) % 1440 + 1440) % 1440
    Page("Bed Schedule", go) {
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Tx("Bedtime reminders", 17, 600); Tx("Wind-down and bedtime notifications", 12, 400, W70) }
                    GlassSwitch(on) { on = it; save() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeTile("Bedtime", fmtClock(bed), "bed", Modifier.weight(1f)) { pick(bed) { bed = it } }
                    TimeTile("Wake up", fmtClock(wake), "breathe", Modifier.weight(1f)) { pick(wake) { wake = it } }
                }
                Tx("Sleep goal  ${fmtMin(goal)}", 15, 600)
            }
        }
        Spacer(Modifier.height(16.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Tx("Wind-down reminder", 17, 600)
                Tx("Get a nudge before bedtime to put your phone away. Screen use during bed hours also feeds your stress index.", 12, 400, W70)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 45, 60).forEach {
                        Box(Modifier.tap { wind = it; save() }.clip(CircleShape).background(if (wind == it) Color.White else Color.White.copy(alpha = 0.2f)).padding(horizontal = 16.dp, vertical = 9.dp)) {
                            Tx("$it min", 13, 600, if (wind == it) Ink else Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeTile(label: String, value: String, icon: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.tap(onClick).clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = 0.18f)).padding(16.dp)) {
        Column {
            Ico(icon, 20.dp)
            Spacer(Modifier.height(8.dp))
            Tx(label, 12, 400, W70)
            Tx(value, 20, 600)
        }
    }
}

// =====================================================================================  BREATHE
@Composable
fun BreatheScreen(go: (String) -> Unit) {
    val ctx = LocalContext.current
    var running by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf("Ready") }
    var count by remember { mutableIntStateOf(0) }
    var secs by remember { mutableIntStateOf(0) }
    val scale = remember { Animatable(0.62f) }

    LaunchedEffect(running) {
        if (!running) { phase = "Ready"; scale.animateTo(0.62f, tween(600)); return@LaunchedEffect }
        while (true) {
            phase = "Inhale"; Haptics.heavy(ctx)
            launch { scale.animateTo(1f, tween(4000, easing = FastOutSlowInEasing)) }
            for (i in 4 downTo 1) { count = i; delay(1000); Haptics.tick(ctx) }
            phase = "Hold"; Haptics.click(ctx)
            for (i in 4 downTo 1) { count = i; delay(1000); Haptics.tick(ctx) }
            phase = "Exhale"; Haptics.heavy(ctx)
            launch { scale.animateTo(0.62f, tween(6000, easing = FastOutSlowInEasing)) }
            for (i in 6 downTo 1) { count = i; delay(1000); Haptics.tick(ctx) }
        }
    }
    LaunchedEffect(running) { secs = 0; while (running) { delay(1000); secs++ } }

    Page("Breathe", go) {
        Spacer(Modifier.height(30.dp))
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            Glass(Modifier.size(260.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value }, CircleShape, 0.28f, 24.dp) {
                Column(
                    Modifier.fillMaxSize().graphicsLayer { scaleX = 1f / scale.value; scaleY = 1f / scale.value },
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Tx(phase, 26, 500)
                    if (running) Tx("$count", 44, 300)
                }
            }
        }
        Tx("Inhale 4 · Hold 4 · Exhale 6", 14, 500, W70, Modifier.fillMaxWidth(), TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Tx("Session %02d:%02d".format(secs / 60, secs % 60), 14, 400, W70, Modifier.fillMaxWidth(), TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        GlassButton(if (running) "Stop" else "Start Therapy Session", filled = true, modifier = Modifier.fillMaxWidth()) { running = !running }
    }
}

// =====================================================================================  INSIGHTS
@Composable
fun InsightsScreen(snap: Snap, go: (String) -> Unit) {
    val ctx = LocalContext.current
    var week by remember { mutableStateOf(List(7) { 0L }) }
    LaunchedEffect(snap.access, snap.totalMs / 600000) {
        if (snap.access) week = withContext(Dispatchers.IO) { Usage.weekly(ctx) }
    }
    val labels = remember {
        val f = SimpleDateFormat("EEE", Locale.getDefault())
        (6 downTo 0).map { d -> f.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -d) }.time) }
    }
    val avg = week.sum() / 7
    Page("Insights", go) {
        AccessCard(snap)
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Tx("Last 7 days", 13, 500, W70)
                Tx("Avg ${fmtDur(avg)} / day", 30, 300)
                Spacer(Modifier.height(14.dp))
                val mx = max(1L, week.max())
                Row(Modifier.fillMaxWidth().height(150.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    week.forEachIndexed { i, v ->
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.fillMaxWidth().fillMaxHeight(0.78f * max(0.03f, v.toFloat() / mx)).clip(RoundedCornerShape(8.dp)).background(if (i == 6) Color.White else Color.White.copy(alpha = 0.55f)))
                            Spacer(Modifier.height(6.dp))
                            Tx(labels[i], 10, 500, W70)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Glass(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Tx("Smart Stress Insight", 17, 600)
                Tx(Insight.text(snap.stress), 13, 400, W70)
                val diff = week.last() - (week.dropLast(1).sum() / 6)
                if (week.any { it > 0 }) Tx(
                    if (diff > 0) "Today is ${fmtDur(diff)} above your recent average." else "Today is ${fmtDur(-diff)} below your recent average.",
                    13, 500
                )
            }
        }
    }
}


// =====================================================================================  APP ICON PICKER
@Composable
fun IconSheet(box: Boolean, onPick: (Boolean) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x66101820))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Glass(
            Modifier.padding(24.dp).fillMaxWidth().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            RoundedCornerShape(28.dp), 0.28f, 26.dp
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Tx("App icon", 20, 600)
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    IconChoice("Default", R.drawable.ic_default_preview, !box) { onPick(false) }
                    IconChoice("Shoe box", R.drawable.ic_box_preview, box) { onPick(true) }
                }
                Tx("Your launcher can take a few seconds to show the new icon.", 12, 400, W70, align = TextAlign.Center)
                GlassButton("Done", filled = true, modifier = Modifier.fillMaxWidth()) { onDismiss() }
            }
        }
    }
}

@Composable
private fun IconChoice(label: String, res: Int, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.tap(onClick), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(26.dp))
                .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.3f), RoundedCornerShape(26.dp))
                .padding(if (selected) 3.dp else 1.dp)
        ) {
            Image(painterResource(res), null, Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp)))
        }
        Tx(label, 14, if (selected) 700 else 500)
    }
}
