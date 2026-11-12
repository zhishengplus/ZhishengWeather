package com.zhisheng.weather.ui.badge

import android.animation.ValueAnimator
import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zhisheng.weather.data.InterfaceStyle
import com.zhisheng.weather.data.badge.*
import com.zhisheng.weather.ui.theme.LocalInterfaceStyle
import com.zhisheng.weather.ui.theme.LocalZhishengPalette
import com.zhisheng.weather.ui.theme.ZhishengMono

private val BADGE_STAGE_FAILURE = setOf("render_failed", "graphics_unavailable", "asset_unavailable")

/** Owned medals in a grid, then a continuous spin into the same canvas's center. */
@Composable
internal fun BadgeCabinetContent(state: BadgeUiState, onBack: () -> Unit,
    onInspect: (BadgeGrant) -> Unit, hapticsEnabled: Boolean = true,
    inspection: BadgeGrant? = null, onReturnToCabinet: () -> Unit = {}, models: Map<String, BadgeModelUiState> = emptyMap(),
    onRetryModel: (String) -> Unit = {}, onPrepareModel: (String) -> Unit = {}, renderModel: Boolean = true,
    onMore: () -> Unit = {}, awardGrantId: String? = null, onAwardPresented: (String) -> Unit = {}, onAwardCollected: () -> Unit = {}, menu: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var awardReady by rememberSaveable(awardGrantId) { mutableStateOf(false) }
    val awarding = awardGrantId != null
    var awardSession by rememberSaveable { mutableStateOf(awardGrantId != null) }
    LaunchedEffect(awardGrantId) { if (awardGrantId != null) awardSession = true }
    val classic = LocalInterfaceStyle.current == InterfaceStyle.CLASSIC_TERMINAL
    val night = !LocalZhishengPalette.current.isLight
    val dark = classic || night
    val background = if (classic) Color(0xFF050807) else if (night) Color(0xFF101F1B) else Color(0xFFE8F3EE)
    val ink = if (dark) Color(0xFFE3EFE9) else Color(0xFF193C33)
    val muted = if (dark) Color(0xFF8AA79A) else Color(0xFF52756A)
    val configuration = LocalConfiguration.current
    var menuOpen by remember { mutableStateOf(false) }
    var renderStatus by remember { mutableStateOf("loading") }
    var renderEpoch by remember { mutableIntStateOf(0) }
    val failed = renderStatus in BADGE_STAGE_FAILURE
    val controller = remember { BadgeViewerController() }
    val gate = remember { BadgeHapticGate() }
    val owner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val designNotes = remember(context) {
        runCatching {
            val json = context.assets.open("badges/design-notes.json").bufferedReader().use { JSONObject(it.readText()) }
            json.keys().asSequence().mapNotNull { key -> json.optString(key).takeIf { it.isNotBlank() && it.length <= 160 }?.let { key to it } }.toMap()
        }.getOrDefault(emptyMap())
    }
    var playing by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val safeTop = WindowInsets.safeDrawing.getTop(density) / density.density
    val safeBottom = WindowInsets.safeDrawing.getBottom(density) / density.density
    val safeLeft = WindowInsets.safeDrawing.getLeft(density, direction) / density.density
    val safeRight = WindowInsets.safeDrawing.getRight(density, direction) / density.density
    var headerBottomPx by remember { mutableFloatStateOf(0f) }
    var footerHeightPx by remember { mutableFloatStateOf(0f) }
    var storyGrant by remember { mutableStateOf<BadgeGrant?>(null) }
    val view = LocalView.current
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reducedMotion by remember { mutableStateOf(true) }
    fun motionPolicy() {
        reducedMotion = !ValueAnimator.areAnimatorsEnabled() ||
            (context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager)?.isTouchExplorationEnabled == true
    }
    DisposableEffect(owner) {
        motionPolicy()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { active = true; motionPolicy() }
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) active = false
        }
        owner.lifecycle.addObserver(observer)
        onDispose { gate.active = false; owner.lifecycle.removeObserver(observer) }
    }
    SideEffect { gate.active = active; gate.enabled = hapticsEnabled; gate.reducedMotion = reducedMotion }
    fun touch(event: String) {
        if (gate.interaction(event, SystemClock.uptimeMillis())) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    fun display(grant: BadgeGrant): BadgeCabinetDisplay = BadgeCabinetDisplay(
        grant.badgeId, grant.serial, state.collection.indexOfFirst { it.grantId == grant.grantId }.coerceAtLeast(0),
        badgeModelKey(grant, models[grant.grantId]?.model) ?: "", models[grant.grantId]?.model?.manifest?.presentation,
        badgeDisplayName(grant), models[grant.grantId]?.status ?: "", designNotes[grant.badgeId].orEmpty(), grant.engravingPublicId ?: models[grant.grantId]?.model?.manifest?.engravingPublicId.orEmpty(), models[grant.grantId]?.attempt ?: 0)
    LaunchedEffect(inspection?.grantId, active) {
        if (active) inspection?.let { if (models[it.grantId]?.status != "error") onPrepareModel(it.grantId) }
    }
    // Prefetch only the first visible rows; other models prepare when opened.
    LaunchedEffect(state.collection.map { it.grantId }, active) {
        if (active) state.collection.take(9).forEach { if (models[it.grantId]?.status != "error") onPrepareModel(it.grantId) }
    }
    val titleAlpha by animateFloatAsState(if (inspection == null || (awarding && !playing)) 1f else 0f,
        tween(if (reducedMotion) 0 else 180), label = "badge-gallery-title")
    val wideAward = configuration.screenWidthDp > configuration.screenHeightDp
    // Reserve this region for the entire award visit, including the flight back to the collection.
    // The ready state and collect action never change the physical canvas dimensions.
    val awardFooter: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (!awarding) "已放入你的收藏" else if (awardReady) "这一枚，属于你" else "一份心意，送给你", color = muted,
                fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 10.dp))
            BadgeAction(enabled = canCollectBadgeAward(awardGrantId, state.collection, state.collectingAwardId), onClick = {
                if (!awarding) onBack() else onAwardCollected()
            }) { Text(if (awarding) "放入我的徽章" else "返回天气", color = ink) }
        }
    }
    Box(Modifier.fillMaxSize().background(background)) {
        BadgeGalleryBackdrop(classic, night, state.collection.size == 1 && inspection == null)
        val stageInsets = BadgeCabinetInsets(
            top = headerBottomPx / density.density,
            right = safeRight + if (awardSession && wideAward) 220f else 0f,
            bottom = safeBottom + footerHeightPx / density.density,
            left = safeLeft, safeTop = safeTop, safeBottom = safeBottom)
        // Keep one full-display WebView beneath the native controls for the whole visit.
        // Collection safe areas are CSS layout insets, not a clipped Android canvas.
        Box(Modifier.fillMaxSize()) {
                when {
                    state.collection.isEmpty() -> Unit // The empty sentence is centered on the whole page below.
                    !renderModel || failed -> Box(Modifier.fillMaxSize().padding(start = stageInsets.left.dp, top = stageInsets.top.dp, end = stageInsets.right.dp, bottom = stageInsets.bottom.dp)) {
                        BadgeGalleryFallback(state.collection, inspection, ink, muted, onInspect, failed, designNotes) { storyGrant = it }
                    }
                    else -> key(renderEpoch) {
                        BadgeModelView(inspection?.serial, classic, null, {}, controller, { renderStatus = it },
                            onFeedback = { touch(it) }, reducedMotion = reducedMotion, cabinet = true,
                            cabinetSlots = state.collection.map { display(it) }, cabinetInspection = inspection?.let { display(it) },
                            remoteModels = models.values.mapNotNull { it.model }.associateBy { it.manifest.sha256 },
                            onCabinetSelect = { index -> state.collection.getOrNull(index)?.let { onInspect(it) } },
                            night = night, fontScale = configuration.fontScale, cabinetInsets = stageInsets, onCabinetPlaying = { playing = it },
                            onCabinetPrepare = { index -> state.collection.getOrNull(index)?.let {
                                if (models[it.grantId]?.status in setOf("ready", "error")) onRetryModel(it.grantId) else onPrepareModel(it.grantId)
                            } },
                            onCabinetStory = { index -> state.collection.getOrNull(index)?.let { storyGrant = it } },
                            awardSlot = state.collection.indexOfFirst { it.grantId == awardGrantId }.takeIf { it >= 0 },
                            awardAcknowledged = awardReady,
                            onAwardPresented = { index ->
                                state.collection.getOrNull(index)?.takeIf { it.grantId == awardGrantId && active && !awardReady }?.let {
                                    awardReady = true; onAwardPresented(it.grantId)
                                }
                            })
                    }
                }
                inspection?.let { grant ->
                    if (models[grant.grantId]?.status == "error") Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(models[grant.grantId]?.error ?: "徽章模型暂未加载", color = muted, textAlign = TextAlign.Center)
                        BadgeAction(onClick = { onRetryModel(grant.grantId) }) { Text("重试") }
                    }
                }
        }
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(Modifier.fillMaxWidth().onGloballyPositioned { headerBottomPx = it.positionInRoot().y + it.size.height }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BadgeIconControl(onClick = { if (inspection != null) onReturnToCabinet() else onBack() },
                    modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, if (inspection != null) "返回我的徽章" else "返回", tint = ink, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.weight(1f))
                Box {
                    BadgeIconControl(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.MoreVert, "更多徽章操作", tint = ink, modifier = Modifier.size(22.dp))
                    }
                    BadgeMenu(menuOpen, { menuOpen = false }) {
                        if (inspection != null) {
                            listOf("回正" to { controller.reset() }, "翻面" to { controller.turn(0, 1, Math.PI) },
                                "放大" to { controller.zoom(1.2) }, "缩小" to { controller.zoom(1.0 / 1.2) }).forEach { (label, action) ->
                                BadgeMenuItem(text = { Text(label) }, enabled = !failed,
                                    onClick = { menuOpen = false; action(); touch("touch") })
                            }
                            HorizontalDivider(color = muted.copy(alpha = .16f), modifier = Modifier.padding(vertical = 8.dp))
                        }
                        menu { menuOpen = false }
                    }
                }
            }
            if (com.zhisheng.weather.BuildConfig.BADGE_EXPERIENCE) Text("体验副本 · 不发放真实徽章",
                color = muted, fontSize = 12.sp, modifier = Modifier.padding(start = 24.dp, bottom = 4.dp).alpha(titleAlpha))
            // The title is an overlay; its measured bounds only inset the collection, never crop the canvas.
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 28.dp, top = 8.dp, bottom = 16.dp).heightIn(min = (42 * configuration.fontScale).dp).alpha(titleAlpha),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (awarding) "收到新的徽章" else "我的徽章", color = ink, fontSize = (if (awarding) minOf(30f, (configuration.screenWidthDp - 48f) / 7.5f / configuration.fontScale) else 30f).sp, maxLines = 1, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (!awarding && state.collection.isNotEmpty()) Text("${state.collection.size}${if (state.nextCursor != null) "+" else ""} 枚",
                    color = muted, fontSize = 12.sp)
            }
            }
            Row(Modifier.fillMaxWidth().weight(1f)) {
                Spacer(Modifier.weight(1f).fillMaxHeight())
                if (awardSession && wideAward) Box(Modifier.width(220.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { awardFooter() }
            }
            Column(Modifier.fillMaxWidth().onSizeChanged { footerHeightPx = it.height.toFloat() }, horizontalAlignment = Alignment.CenterHorizontally) {
                if (awardSession && !wideAward) awardFooter()
                if (inspection == null && state.nextCursor != null) BadgeAction(onClick = onMore, enabled = !state.busy) { Text("加载更多徽章", color = muted) }
                if (failed) BadgeAction(onClick = { renderStatus = "loading"; renderEpoch++ }) { Text("三维暂不可用 · 重新加载", color = muted) }
            }
        }
        // Small status overlay never resizes or replaces the interactive collection canvas.
        Column(Modifier.align(Alignment.BottomCenter).safeDrawingPadding()
            .padding(bottom = if (awardSession && !wideAward) (footerHeightPx / density.density).dp else 0.dp,
                end = if (awardSession && wideAward) 220.dp else 0.dp)
            .padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            state.operationLabel?.let { BadgeOperationProgress(it, active, reducedMotion) }
            state.message?.let { Text(it, color = if (state.messageIsError) MaterialTheme.colorScheme.error else muted,
                fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp)) }
        }
        storyGrant?.let { selected -> designNotes[selected.badgeId]?.let { note ->
            BadgeDesignNoteDialog(badgeDisplayName(selected), note) { storyGrant = null }
        } }
        if (state.collection.isEmpty()) Text(if (awarding) "正在准备你的徽章" else "你还没有拥有任何徽章", color = muted, fontSize = 15.sp,
            maxLines = 1, modifier = Modifier.align(Alignment.Center).padding(horizontal = 12.dp))
    }
}

@Composable
private fun BadgeGalleryBackdrop(classic: Boolean, night: Boolean, single: Boolean) {
    if (classic) {
        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                val green = Color(0xFF62C18B).copy(alpha = .10f)
                if (single && size.height > size.width) {
                    val center = Offset(size.width*.5f, size.height*.47f)
                    val radius = size.width*.44f
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF28513D).copy(alpha=.22f), Color.Transparent), center, radius*1.2f), radius*1.2f, center)
                    drawArc(green, 205f, 112f, false, center-Offset(radius,radius), Size(radius*2,radius*2), style=Stroke(.7.dp.toPx()))
                    drawArc(green.copy(alpha=.06f), 20f, 106f, false, center-Offset(radius,radius), Size(radius*2,radius*2), style=Stroke(.7.dp.toPx()))
                    for (i in 0..3) drawLine(green, Offset(size.width*.5f-12.dp.toPx()+i*8.dp.toPx(), center.y+radius+18.dp.toPx()),
                        Offset(size.width*.5f-12.dp.toPx()+i*8.dp.toPx(), center.y+radius+21.dp.toPx()), .6.dp.toPx())
                }
                drawLine(green, Offset(18.dp.toPx(), size.height * .76f), Offset(18.dp.toPx(), size.height * .86f), 1.dp.toPx())
                drawLine(green, Offset(size.width - 18.dp.toPx(), size.height * .18f), Offset(size.width - 18.dp.toPx(), size.height * .27f), 1.dp.toPx())
                for (i in 0..5) drawLine(green, Offset(18.dp.toPx(), size.height*.86f+i*5.dp.toPx()), Offset((if (i%2==0) 28 else 23).dp.toPx(), size.height*.86f+i*5.dp.toPx()), 1.dp.toPx())
            }
            Text("0x00  /  0x01", color = Color(0xFF62C18B).copy(alpha = .20f), fontFamily = ZhishengMono,
                fontSize = 8.sp, modifier = Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(24.dp))
        }
    } else Canvas(Modifier.fillMaxSize()) {
        val light = if (night) Color(0xFF9EBBAE) else Color.White
        val tint = if (night) Color(0xFF427260) else Color(0xFFA4C9BA)
        // One upper-left softbox lights every surface; no hard tonal panels across the page.
        drawRect(Brush.linearGradient(listOf(
            if(night)Color(0xFF172B24)else Color(0xFFEEF6F1),
            if(night)Color(0xFF10211C)else Color(0xFFE5F0EA),
            if(night)Color(0xFF0D1C18)else Color(0xFFDCEBE3)),
            Offset.Zero,Offset(size.width*.65f,size.height)))
        drawRect(Brush.radialGradient(
            0f to light.copy(alpha=if(night).10f else .50f),
            .5f to light.copy(alpha=if(night).035f else .15f),1f to Color.Transparent,
            center=Offset(size.width*.15f,size.height*.20f),radius=size.maxDimension*.78f))
        if (single && size.height > size.width) {
            val center = Offset(size.width*.5f,size.height*.47f)
            val radius = size.width*.44f
            drawCircle(Brush.radialGradient(listOf(tint.copy(alpha=if(night).09f else .18f),Color.Transparent),center,radius*1.5f),radius*1.5f,center)
            drawCircle(Brush.radialGradient(0f to Color.Transparent,.76f to light.copy(alpha=if(night).018f else .045f),
                .93f to light.copy(alpha=if(night).035f else .09f),1f to Color.Transparent,
                center=center-Offset(radius*.13f,radius*.16f),radius=radius*1.2f),radius*1.2f,center)
            drawArc(Brush.linearGradient(listOf(Color.Transparent,light.copy(alpha=if(night).09f else .32f),Color.Transparent),
                center-Offset(radius,radius),center+Offset(radius*.3f,0f)),198f,112f,false,
                center-Offset(radius,radius),Size(radius*2,radius*2),style=Stroke(.7.dp.toPx()))
        }
        val edge = Path().apply { moveTo(-size.width*.18f,size.height*.72f)
            cubicTo(size.width*.22f,size.height*.69f,size.width*.66f,size.height*.55f,size.width*1.18f,size.height*.51f) }
        val glass = Path().apply { addPath(edge)
            lineTo(size.width*1.18f,size.height*.87f)
            cubicTo(size.width*.65f,size.height*.86f,size.width*.2f,size.height*.99f,-size.width*.18f,size.height*1.03f);close() }
        drawPath(glass,Brush.linearGradient(0f to Color.Transparent,
            .28f to light.copy(alpha=if(night).018f else .12f),.65f to tint.copy(alpha=if(night).025f else .065f),1f to Color.Transparent,
            start=Offset(0f,size.height*.54f),end=Offset(size.width*.5f,size.height)))
        drawPath(edge,Brush.linearGradient(listOf(Color.Transparent,light.copy(alpha=if(night).07f else .28f),Color.Transparent),
            Offset(0f,size.height*.72f),Offset(size.width,size.height*.54f)),style=Stroke(.65.dp.toPx()))
    }
}

/** Native accessible fallback and layout preview; never substitute another type's model. */
@Composable
private fun BadgeGalleryFallback(collection: List<BadgeGrant>, inspection: BadgeGrant?, ink: Color, muted: Color, onInspect: (BadgeGrant) -> Unit, staticOnly: Boolean, designNotes: Map<String, String> = emptyMap(), onStory: (BadgeGrant) -> Unit = {}) {
    val hintText = if (staticOnly) "静态预览 · 轻触查看" else "↔ 拖动旋转 · 轻触放大"
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        if (inspection != null) {
            val fontScale = LocalConfiguration.current.fontScale
            val note = designNotes[inspection.badgeId]
            val fullStory = height >= (420 * fontScale).dp
            val captionSpace = ((64 + (if (inspection.engravingPublicId == null) 0 else 26) + if (note == null) 0 else if (fullStory) 110 else 40) * fontScale).dp
            val side = minOf(width*.72f, (height - captionSpace).coerceAtLeast(0.dp) * .78f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment=Alignment.CenterHorizontally) {
                BadgeGalleryImage(inspection,Modifier.size(side))
                Spacer(Modifier.height(12.dp))
                BadgeGalleryCaption(inspection,ink,muted,18,false)
                if (note != null) {
                    if (fullStory) Column(Modifier.widthIn(max = 310.dp).padding(horizontal = 12.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("设计手记", color = muted.copy(alpha = .75f), fontSize = 10.sp, letterSpacing = 2.sp)
                        Text(note, color = muted, fontSize = 13.sp, lineHeight = 24.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    } else BadgeAction(onClick = { onStory(inspection) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("设计手记  ›", color = muted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            val columns = if (width >= 700.dp) 2 else 1
            val contentWidth = minOf(width, if (columns == 2) 1000.dp else 600.dp)
            val rowWidth = (contentWidth - 48.dp - 24.dp * (columns - 1)) / columns
            val side = (rowWidth - 24.dp) / 2.15f
            LazyVerticalGrid(columns = GridCells.Fixed(columns),
                modifier = Modifier.width(contentWidth).align(Alignment.TopCenter),
                contentPadding = PaddingValues(start=24.dp,end=24.dp,top=if(height<=360.dp)12.dp else 24.dp,bottom=36.dp),
                verticalArrangement = Arrangement.spacedBy(36.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                itemsIndexed(collection,key={_,it->it.grantId}) { _,grant ->
                    Row(Modifier.fillMaxWidth().clickable { onInspect(grant) },verticalAlignment=Alignment.CenterVertically) {
                        BadgeGalleryImage(grant,Modifier.size(side))
                        Spacer(Modifier.width(24.dp))
                        Box(Modifier.weight(1f),contentAlignment=Alignment.Center) {
                            BadgeGalleryCaption(grant,ink,muted,if(collection.size==1)24 else 20,true,hintText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeGalleryImage(grant: BadgeGrant, modifier: Modifier) {
    Box(modifier) { if(grant.badgeId==FIRST_LIGHT_BADGE) BadgeStaticPreview(showControls=false) }
}

@Composable
private fun BadgeGalleryCaption(grant: BadgeGrant, ink: Color, muted: Color, size: Int, hint: Boolean, hintText: String = "") {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text(badgeDisplayName(grant),color=ink,fontSize=size.sp,fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center,
            letterSpacing=if(size==24)2.sp else 0.sp)
        Text(grant.serial.toString().padStart(6,'0'),color=muted,fontSize=(if(size==24)12 else 11).sp,fontFamily=ZhishengMono,
            maxLines=1,letterSpacing=1.sp,modifier=Modifier.padding(top=5.dp))
        grant.engravingPublicId?.let { Text("拥有者：$it",color=muted,fontSize=11.sp,lineHeight=18.sp,textAlign=TextAlign.Center,modifier=Modifier.padding(top=2.dp)) }
        if(hint)Text(hintText,color=muted.copy(alpha=.9f),fontSize=11.sp,textAlign=TextAlign.Center,letterSpacing=.3.sp,modifier=Modifier.padding(top=18.dp))
    }
}

@Composable
internal fun BadgeDesignNoteDialog(name: String, note: String, onClose: () -> Unit) {
    BadgeDialog(onDismissRequest = onClose, title = { Text("设计手记 · $name") },
        text = { Text(note, lineHeight = 26.sp) },
        confirmButton = { BadgeAction(onClick = onClose) { Text("继续把玩") } })
}
