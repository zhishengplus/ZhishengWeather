package com.zhisheng.weather.ui.badge

import android.animation.ValueAnimator
import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.badge.BadgeGrant
import com.zhisheng.weather.data.badge.FIRST_LIGHT_EDITION
import com.zhisheng.weather.data.badge.badgeSerialText
import com.zhisheng.weather.ui.theme.LocalInterfaceStyle
import com.zhisheng.weather.ui.theme.ZhishengMono
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Shared by automatic awards and About. The object owns the space; tools stay in a menu. */
@Composable
internal fun BadgeImmersiveContent(grant: BadgeGrant?, onBack: () -> Unit, celebration: Boolean = false,
    status: String? = null, onPresented: () -> Unit = {}, renderModel: Boolean = true,
    hapticsEnabled: Boolean = true, menu: (@Composable ColumnScope.(close: () -> Unit) -> Unit)? = null) {
    val classic = LocalInterfaceStyle.current == InterfaceStyle.CLASSIC_TERMINAL
    // The collection is a dim studio in either appearance, while each theme
    // keeps its own typography and reflected accent. No light page surrounds it.
    val ink = if (classic) Color(0xFFE2EDE8) else Color(0xFF183A3D)
    val secondary = if (classic) Color(0xFF9CB1AD) else Color(0xFF486565)
    val accent = if (classic) Color(0xFF8AD5A2) else Color(0xFF327C76)
    val gold = Color(0xFFE5C591)
    val background = if (classic) Color(0xFF080F0E) else Color(0xFFBCDCD8)
    val scope = rememberCoroutineScope()
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reducedMotion by remember { mutableStateOf(true) }
    var entered by rememberSaveable(grant?.grantId) { mutableStateOf(!renderModel) }
    var presented by rememberSaveable(grant?.grantId) { mutableStateOf(false) }
    var renderer by remember { mutableStateOf(if (renderModel) "loading" else "ready") }
    var pose by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val reveal = remember { Animatable(if (entered) 1f else 0f) }
    val controller = remember { BadgeViewerController() }
    val haptics = remember { BadgeHapticGate() }
    val ready = renderer != "loading"
    val canManipulate = renderModel && renderer in setOf("ready", "label_uncalibrated")
    // Keep navigation available, but don't place labels across the enlarged medal.
    val inspectionAlpha = if (!classic && runCatching { JSONObject(pose ?: "{}").optDouble("zoom", 1.0) > 1.55 }.getOrDefault(false)) 0f else 1f
    fun refreshMotionPolicy() {
        reducedMotion = !ValueAnimator.areAnimatorsEnabled() ||
            (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
    }
    DisposableEffect(owner) {
        refreshMotionPolicy()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { active = true; refreshMotionPolicy() }
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) active = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { haptics.active = false; owner.lifecycle.removeObserver(observer) }
    }
    SideEffect { haptics.active = active; haptics.reducedMotion = reducedMotion; haptics.enabled = hapticsEnabled }
    val actualModelFrame = renderer in setOf("ready", "label_uncalibrated")
    LaunchedEffect(actualModelFrame, active, reducedMotion, grant?.grantId) {
        if (!active || !actualModelFrame) return@LaunchedEffect
        if (entered || reducedMotion) { entered = true; reveal.snapTo(1f) }
        else {
            if (celebration && grant != null && haptics.success(grant.grantId, SystemClock.uptimeMillis()))
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            withFrameNanos { }
            controller.reveal()
            reveal.animateTo(1f, tween(if (celebration) 1_800 else 1_100, easing = FastOutSlowInEasing))
            entered = true
        }
        // Interrupted entrances remain pending; only a completed, visible reveal is acknowledged.
        withFrameNanos { }
        if (grant != null && !presented) { presented = true; onPresented() }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(background)) {
        val compact = maxHeight < 520.dp || configuration.fontScale > 1.5f
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                BadgeGlassButton(onBack, ink) { Icon(Icons.Default.Close, contentDescription = "返回") }
                Text(if (compact || !celebration) "我的徽章" else "一份属于你的初光", color = secondary,
                    fontFamily = if (classic) ZhishengMono else FontFamily.SansSerif, fontSize = 12.sp,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Box {
                    if (menu != null) BadgeGlassButton({ menuOpen = true }, ink) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多徽章操作")
                    } else Spacer(Modifier.size(48.dp))
                    BadgeMenu(menuOpen, { menuOpen = false }) { menu?.invoke(this) { menuOpen = false } }
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                BadgeStudioBackground(reveal.value, accent, gold, Modifier.fillMaxSize())
                // WebView owns its native hardware layer. The object entrance is
                // animated inside the renderer; the native panel stays outside it.
                val stage = Modifier.fillMaxSize()
                if (renderModel && renderer !in setOf("asset_unavailable", "render_failed", "graphics_unavailable")) {
                    BadgeModelView(grant?.serial, true, pose, { pose = it }, controller, { renderer = it }, modifier = stage,
                        reducedMotion = reducedMotion, clearsky = !classic, onFeedback = { event ->
                            if (event == "touch") scope.launch { reveal.snapTo(1f) }
                            if (haptics.interaction(event, SystemClock.uptimeMillis()))
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        })
                } else Box(stage, contentAlignment = Alignment.Center) { BadgeStaticPreview(showControls = renderModel) }
                if (!ready) BadgePreparing()
            }
            Column(Modifier.fillMaxWidth().safeDrawingPadding().padding(start = 28.dp, end = 28.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("初光", color = ink, fontFamily = if (classic) ZhishengMono else FontFamily.SansSerif,
                    fontWeight = FontWeight.Light, fontSize = if (compact) 20.sp else 22.sp, letterSpacing = 4.sp,
                    modifier = Modifier.graphicsLayer { alpha = inspectionAlpha })
                Text(grant?.let { badgeSerialText(it.serial) } ?: "尚未珍藏", color = ink,
                    fontFamily = ZhishengMono, fontSize = if (compact) 15.sp else 16.sp, letterSpacing = 2.sp,
                    modifier = Modifier.padding(top = if (compact) 0.dp else 2.dp).graphicsLayer { alpha = inspectionAlpha }
                        .semantics { liveRegion = LiveRegionMode.Polite })
                if (!compact) Text(FIRST_LIGHT_EDITION, color = secondary.copy(alpha = .72f), fontSize = 10.sp,
                    letterSpacing = 1.sp, modifier = Modifier.padding(top = 2.dp).graphicsLayer { alpha = inspectionAlpha })
                if (status != null && !compact) Text(status, color = secondary.copy(alpha = .8f), fontSize = 11.sp,
                    maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).graphicsLayer { alpha = inspectionAlpha })
                if (!compact) Text(if (renderer in setOf("graphics_unavailable", "render_failed", "asset_unavailable")) "当前设备使用静态展示"
                    else "拖动翻转 · 双指缩放", color = secondary.copy(alpha = .65f),
                    fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp).graphicsLayer { alpha = inspectionAlpha })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    BadgeAction(onClick = {
                        if (haptics.interaction("touch", SystemClock.uptimeMillis())) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        controller.zoom(1.0 / 1.2)
                    }, enabled = canManipulate, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp)) { Text("缩小", color = accent, fontSize = 12.sp, maxLines = 1) }
                    BadgeAction(onClick = {
                        if (haptics.interaction("touch", SystemClock.uptimeMillis())) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        controller.zoom(1.2)
                    }, enabled = canManipulate, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp)) { Text("放大", color = accent, fontSize = 12.sp, maxLines = 1) }
                    BadgeAction(onClick = controller::reset, enabled = canManipulate, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp)) { Text("回正", color = accent, fontSize = 12.sp, maxLines = 1) }
                    BadgeAction(onClick = { controller.turn(0, 1, Math.PI) }, enabled = canManipulate, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 2.dp)) { Text("翻面", color = accent, fontSize = 12.sp, maxLines = 1) }
                }
                if (celebration) BadgeAction(onClick = onBack, primary = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("收好这束初光") }
            }
        }
    }
}

@Composable
private fun BadgeGlassButton(onClick: () -> Unit, color: Color,
    content: @Composable () -> Unit) {
    BadgeIconControl(onClick = onClick, content = content)
}

@Composable
private fun BadgeStudioBackground(progress: Float, accent: Color, gold: Color,
    modifier: Modifier) {
    Canvas(modifier) {
        val origin = Offset(size.width * .5f, size.height * .42f)
        val radius = size.minDimension * (.72f + progress * .06f)
        drawRect(Brush.radialGradient(listOf(accent.copy(alpha = .10f), Color.Transparent), origin, radius))
        val warm = Offset(size.width * .72f, size.height * .24f)
        drawCircle(Brush.radialGradient(listOf(gold.copy(alpha = .055f), Color.Transparent), warm, radius * .7f), radius * .7f, warm)
        val shadow = Offset(size.width * .5f, size.height * .68f)
        drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = .38f), Color.Transparent), shadow, radius * .42f),
            Offset(shadow.x - radius * .42f, shadow.y - radius * .075f), Size(radius * .84f, radius * .15f))
    }
}
