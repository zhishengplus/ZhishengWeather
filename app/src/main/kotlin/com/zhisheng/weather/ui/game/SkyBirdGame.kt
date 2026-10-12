package com.zhisheng.weather.ui.game

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.R
import com.zhisheng.weather.ui.Text
import com.zhisheng.weather.ui.components.PhosphorIcon
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable
internal fun SkyBirdGameDialog(onWeatherDetails: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val store = remember(context) { context.getSharedPreferences("sky_bird", Context.MODE_PRIVATE) }
    var haptics by remember { mutableStateOf(store.getBoolean("flap_haptics", true)) }
    var best by remember { mutableIntStateOf(store.getInt("best", 0).coerceAtLeast(0)) }
    var state by remember { mutableStateOf(SkyBirdEngine.start()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!foreground) state = SkyBirdEngine.pause(state)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val inspection = LocalInspectionMode.current
    LaunchedEffect(state.phase, foreground, inspection) {
        if (inspection || !foreground || state.phase != SkyBirdPhase.FLYING) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive && foreground && state.phase == SkyBirdPhase.FLYING) {
            withFrameNanos { frame ->
                state = SkyBirdEngine.step(state, (frame - previous) / 1_000_000_000f)
                previous = frame
            }
        }
    }
    LaunchedEffect(state.phase, state.score) {
        if (state.phase == SkyBirdPhase.LANDED && state.score > best) {
            best = state.score
            store.edit().putInt("best", best).apply()
        }
    }
    WeatherOverlayDialog(onClose, maxWidth = 480.dp) { modifier ->
        SkyBirdPanel(state, best, haptics, modifier.fillMaxHeight(),
            onAction = {
                if (foreground) {
                    val next = when (state.phase) {
                        SkyBirdPhase.LANDED -> SkyBirdEngine.flap(SkyBirdEngine.start(state.height))
                        SkyBirdPhase.PAUSED -> SkyBirdEngine.resume(state)
                        else -> SkyBirdEngine.flap(state)
                    }
                    if (haptics && state.phase != SkyBirdPhase.PAUSED)
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    state = next
                }
            }, onPause = { state = SkyBirdEngine.pause(state) }, onClose = onClose,
            onWeatherDetails = onWeatherDetails, onHaptics = { enabled ->
                haptics = enabled; store.edit().putBoolean("flap_haptics", enabled).apply()
            })
    }
}

/** Real production panel also used by native measured-layout checks. */
@Composable
internal fun SkyBirdPanel(state: SkyBirdState, best: Int, haptics: Boolean, modifier: Modifier = Modifier,
    onAction: () -> Unit, onPause: () -> Unit, onClose: () -> Unit, onHaptics: (Boolean) -> Unit,
    onWeatherDetails: () -> Unit = {}) {
    val p = LocalZhishengPalette.current
    val shape = RoundedCornerShape(28.dp)
    Column(modifier.clip(shape).background(p.surface).border(1.dp, p.cardBorder, shape)) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("云间小鸟", color = p.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text("${SkyBirdJourney.scenery(state.score).title} · 最好 $best", color = p.textSecondary, fontSize = 11.sp)
            }
            if (state.phase == SkyBirdPhase.FLYING) IconButton(onClick = onPause,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "暂停游戏" }) {
                Canvas(Modifier.size(18.dp)) {
                    drawRoundRect(p.textSecondary, size = Size(size.width * .28f, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                    drawRoundRect(p.textSecondary, topLeft = Offset(size.width * .72f, 0f),
                        size = Size(size.width * .28f, size.height), cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                PhosphorIcon(R.drawable.ph_x, "关闭游戏", Modifier.size(20.dp), p.textSecondary)
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp)
            .clip(RoundedCornerShape(22.dp))) {
            val compact = maxHeight < 320.dp
            val actionLabel = when (state.phase) {
                SkyBirdPhase.READY -> "开始飞行"
                SkyBirdPhase.PAUSED -> "继续飞行"
                SkyBirdPhase.LANDED -> "再飞一次"
                SkyBirdPhase.FLYING -> "振翅"
            }
            SkyBirdScene(state, p.isLight, Modifier.fillMaxSize()
                .clickable(role = Role.Button, onClickLabel = actionLabel, onClick = onAction)
                .semantics { contentDescription = "云间小鸟飞行区域" })
            if (state.phase == SkyBirdPhase.FLYING) Column(
                Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 12.dp, end = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${state.score}", color = if (p.isLight && SkyBirdJourney.scenery(state.score) != SkyBirdScenery.COSMOS)
                    Color(0xFF244A54) else Color(0xFFECF8FA), fontSize = 38.sp, fontWeight = FontWeight.Light)
                state.celebration?.let { reward ->
                    Column(Modifier.widthIn(max = 270.dp).clip(RoundedCornerShape(16.dp))
                        .background(p.surface.copy(alpha = .93f))
                        .semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${reward.threshold} 根 · ${SkyBirdJourney.medalTitle(reward.threshold)}徽章",
                            Modifier.fillMaxWidth(), color = p.text, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                        Text("抵达${SkyBirdJourney.scenery(reward.threshold).title}", Modifier.fillMaxWidth(),
                            color = p.textSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    }
                }
            }
            else {
                Column(Modifier.align(Alignment.Center).padding(if (compact) 12.dp else 20.dp).widthIn(max = 290.dp)
                    .heightIn(max = maxHeight).clip(RoundedCornerShape(22.dp))
                    .background(if (p.isLight) Color(0xEEF4FAFC) else Color(0xEE172C3D))
                    .padding(if (compact) 12.dp else 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                    Text(when (state.phase) {
                        SkyBirdPhase.READY -> "趁云间放晴"
                        SkyBirdPhase.PAUSED -> "歇一会儿"
                        else -> "这次飞过 ${state.score} 片云"
                    }, Modifier.fillMaxWidth(), color = p.text, fontSize = 23.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Text(if (state.phase == SkyBirdPhase.READY) "轻点屏幕振翅\n每10根换景，奖励升级" else
                        if (state.phase == SkyBirdPhase.PAUSED) "准备好再继续" else
                            if (state.score >= 10) "收获${SkyBirdJourney.medalTitle(state.score)}徽章\n已完成 ${state.score / 10} 次换景" else "再试一次，飞得更远一点",
                        Modifier.fillMaxWidth(), color = p.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                    Button(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = p.cyan,
                        contentColor = if (p.isLight) Color.White else Color(0xFF102634))) { Text(actionLabel) }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("振翅震动", Modifier.weight(1f), color = p.textSecondary, fontSize = 13.sp)
                Switch(checked = haptics, onCheckedChange = onHaptics,
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = "振翅震动" },
                    colors = SwitchDefaults.colors(checkedThumbColor = p.surface, checkedTrackColor = p.cyan))
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("每10根换景 · 奖励逐级升级", Modifier.weight(1f), color = p.textTertiary, fontSize = 10.sp)
                TextButton(onClick = onWeatherDetails, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("降水详情", color = p.textSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
internal fun SkyBirdBadge(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val scale = min(size.width, size.height) / 42f
        withTransform({ translate(size.width / 2f, size.height / 2f); scale(scale, scale, Offset.Zero) }) {
            drawSkyBird(0f, 0f, -.12f)
        }
    }
}

@Composable
internal fun SkyBirdScene(state: SkyBirdState, light: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawSkyBirdLandscape(state, light)
        val viewport = SkyBirdViewport.fit(size.width, size.height, state.height)
        withTransform({ translate(viewport.left, viewport.top); scale(viewport.scale, viewport.scale, Offset.Zero) }) {
            val cloudColor = when (SkyBirdJourney.scenery(state.score)) {
                SkyBirdScenery.VALLEY -> if (light) Color(0xFFA6D1CC) else Color(0xFF598D98)
                SkyBirdScenery.PRAIRIE -> if (light) Color(0xFFE0C590) else Color(0xFFB49B76)
                SkyBirdScenery.PEAKS -> if (light) Color(0xFFB6D1E8) else Color(0xFF849FC0)
                SkyBirdScenery.COAST -> if (light) Color(0xFFCEBCCA) else Color(0xFFB69CAF)
                SkyBirdScenery.COSMOS -> Color(0xFF9B9CCB)
            }
            state.gates.forEach { gate ->
                val gap = gate.gap / 2f
                val start = viewport.minY - 20f
                drawCloudPillar(gate.x, start, gate.centerY - gap - start, cloudColor, false)
                drawCloudPillar(gate.x, gate.centerY + gap, viewport.maxY + 20f - gate.centerY - gap,
                    cloudColor, true)
            }
            val ground = state.height - SkyBirdEngine.FLOOR
            drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .35f), Color.White.copy(alpha = .1f)),
                startY = ground, endY = state.height), Offset(0f, ground), Size(SkyBirdEngine.WIDTH, SkyBirdEngine.FLOOR))
            val cloudBank = Path().apply {
                moveTo(0f, ground)
                repeat(49) { i ->
                    val x = i * SkyBirdEngine.WIDTH / 48f
                    val wave = sin(x * .033f) * 4f + sin(x * .071f) * 2f
                    lineTo(x, ground + 8f + wave)
                }
                lineTo(SkyBirdEngine.WIDTH, state.height); lineTo(0f, state.height); close()
            }
            drawPath(cloudBank, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (light) .58f else .23f),
                Color.White.copy(alpha = .06f)), ground, state.height))
            drawLine(Color.White.copy(alpha = .36f), Offset(0f, ground), Offset(SkyBirdEngine.WIDTH, ground), 1f)
            // The colored body is the collision core; the wing and tail are decorative.
            drawSkyBirdRewards(state)
            drawSkyBird(SkyBirdEngine.BIRD_X, state.birdY, (state.velocity / 500f).coerceIn(-.45f, .9f), state.distance)
        }
    }
}

private fun DrawScope.drawCloudPillar(x: Float, y: Float, height: Float, color: Color, bottom: Boolean) {
    if (height <= 0f) return
    val w = SkyBirdEngine.GATE_WIDTH
    drawRoundRect(Brush.horizontalGradient(listOf(color.copy(alpha = .95f), color, color,
        color.copy(red = color.red * .70f, green = color.green * .83f, blue = color.blue * .87f))),
        Offset(x, y), Size(w, height), CornerRadius(12f))
    drawRoundRect(Color(0xFF224C58).copy(alpha = .25f), Offset(x + 1f, y + 1f),
        Size(w - 2f, height - 2f), CornerRadius(11f), style = Stroke(2f))
    // Every highlight and cloud seam stays inside the solid silhouette.
    val edge = if (bottom) y else y + height - 17f
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF3F1D8).copy(alpha = .8f), color)),
        Offset(x + 2f, edge + 2f), Size(w - 4f, 13f), CornerRadius(6f))
    repeat((height / 48f).toInt()) { i ->
        val seamY = y + 26f + i * 48f
        if (seamY + 8f < y + height - 18f) {
            val seam = Path().apply {
                moveTo(x + 10f, seamY + 4f)
                cubicTo(x + 18f, seamY - 5f, x + 24f, seamY - 5f, x + 31f, seamY + 2f)
                cubicTo(x + 37f, seamY - 2f, x + 44f, seamY + 1f, x + 50f, seamY + 5f)
            }
            drawPath(seam, Color.White.copy(alpha = .23f), style = Stroke(2f))
        }
    }
    drawRoundRect(Color.White.copy(alpha = .33f), Offset(x + 7f, y + 18f),
        Size(4f, (height - 36f).coerceAtLeast(0f)), CornerRadius(2f))
}

private fun DrawScope.drawSkyBird(x: Float, y: Float, tilt: Float, distance: Float = 0f) {
    withTransform({ translate(x, y); rotate(tilt * 57.3f, Offset.Zero) }) {
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9A5).copy(alpha = .45f), Color.Transparent), Offset.Zero, 28f), 28f, Offset.Zero)
        val tail = Path().apply { moveTo(-9f, 1f); lineTo(-21f, -4f); lineTo(-17f, 7f); close() }
        drawPath(tail, Color(0xFF27777F))
        drawCircle(Color(0xFF318A90), 10f, Offset.Zero)
        drawOval(Color(0xFFF4E9C4), Offset(-5f, 1f), Size(14f, 9f))
        val wing = sin(distance * .09f) * 3f
        drawOval(Color(0xFF73B8B2), Offset(-12f, -2f + wing), Size(13f, 9f))
        val beak = Path().apply { moveTo(9f, -1f); lineTo(17f, 2f); lineTo(9f, 5f); close() }
        drawPath(beak, Color(0xFFE9B775))
        drawCircle(Color.White, 3.3f, Offset(5f, -3f))
        drawCircle(Color(0xFF173B46), 1.7f, Offset(6f, -3f))
        drawCircle(Color(0xFFE8AF83).copy(alpha = .75f), 2f, Offset(7f, 3f))
    }
}
