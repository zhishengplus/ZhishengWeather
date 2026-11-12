package com.zhisheng.weather.ui.badge

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.annotation.Keep
import java.io.FileInputStream
import com.zhisheng.weather.data.badge.CachedBadgeModel
import com.zhisheng.weather.data.badge.BadgePresentationSpec
import com.zhisheng.weather.data.badge.badgeJson
import kotlinx.serialization.encodeToString
import com.zhisheng.weather.data.badge.validateCachedModelFile
import java.io.ByteArrayInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

internal data class BadgeCabinetDisplay(val badgeId: String, val serial: Long, val slot: Int, val assetKey: String = "first-light", val presentation: BadgePresentationSpec? = null,
    val name: String = "", val modelStatus: String = "", val designNote: String = "", val engravingPublicId: String = "", val modelAttempt: Long = 0)
internal class BadgeViewerController {
    internal var view: WebView? = null
    fun command(script: String) { view?.evaluateJavascript("window.BadgeView&&window.BadgeView.$script", null) }
    fun reset() = command("reset()")
    fun reveal() = command("reveal()")
    fun detail() = command("detail()")
    fun turn(x: Int, y: Int, radians: Double) = command("rotate([$x,$y,0],$radians)")
    fun zoom(factor: Double) = command("zoom($factor)")
}
internal fun safeBadgePose(raw: String): String? = runCatching {
    require(raw.length < 300)
    val objectValue = JSONObject(raw); val q = objectValue.getJSONArray("q"); require(q.length() == 4)
    val values = (0..3).map { q.getDouble(it) }; val zoom = objectValue.getDouble("zoom")
    require(values.all(Double::isFinite) && zoom.isFinite() && zoom in .65..3.2)
    JSONObject().put("q", JSONArray(values)).put("zoom", zoom).toString()
}.getOrNull()
internal data class BadgeCabinetInsets(val top: Float = 0f, val right: Float = 0f, val bottom: Float = 0f, val left: Float = 0f, val safeTop: Float = 0f, val safeBottom: Float = 0f)

@Keep
internal class BadgeRenderBridge(private val lifetime: BadgeViewLifetime, val status: (String) -> Unit, val pose: (String) -> Unit,
    val feedback: (String) -> Unit, val select: (Int) -> Unit = {}, val prepare: (Int) -> Unit = {}, val award: (Int) -> Unit = {}, val story: (Int) -> Unit = {}, val playing: (Boolean) -> Unit = {}) {
    private val main = Handler(Looper.getMainLooper())
    @JavascriptInterface fun status(value: String) {
        if (value !in setOf("ready", "asset_unavailable", "label_uncalibrated", "graphics_unavailable", "render_failed")) return
        val generation = lifetime.hostGeneration
        main.post { if (lifetime.isCurrentHost(generation)) status.invoke(value) }
    }
    @JavascriptInterface fun selectSlot(value: Int) {
        if (value < 0 || !lifetime.canRender) return // Caller checks the index against the verified collection.
        val generation = lifetime.hostGeneration
        main.post { if (lifetime.isCurrentHost(generation) && lifetime.canRender) select(value) }
    }
    @JavascriptInterface fun playing(value: Boolean) {
        if (!lifetime.canRender) return
        val generation = lifetime.hostGeneration
        main.post { if (lifetime.isCurrentHost(generation) && lifetime.canRender) playing.invoke(value) }
    }
    @JavascriptInterface fun storySlot(value: Int) {
        if (value < 0 || !lifetime.canRender) return
        val generation = lifetime.hostGeneration
        main.post { if (lifetime.isCurrentHost(generation) && lifetime.canRender) story(value) }
    }
    @JavascriptInterface fun awardPresented(value: Int) {
        if (value < 0 || !lifetime.canRender) return
        val generation = lifetime.hostGeneration
        main.post { if (lifetime.isCurrentHost(generation) && lifetime.canRender) award(value) }
    }
    @JavascriptInterface fun prepareSlot(value: Int) {
        if (value < 0 || !lifetime.canRender) return
        val generation = lifetime.hostGeneration
        main.post { if (lifetime.isCurrentHost(generation) && lifetime.canRender) prepare(value) }
    }
    @JavascriptInterface fun pose(value: String) {
        val safe = safeBadgePose(value) ?: return
        val generation = lifetime.hostGeneration
        main.post { if (lifetime.isCurrentHost(generation)) pose.invoke(safe) }
    }
    @JavascriptInterface fun haptic(value: String) {
        if (value !in setOf("touch", "front", "back", "reset", "entry") || !lifetime.canRender) return
        val received = android.os.SystemClock.uptimeMillis()
        val generation = lifetime.hostGeneration
        main.post {
            if (lifetime.isCurrentHost(generation) && lifetime.canRender && android.os.SystemClock.uptimeMillis() - received < 150) feedback(value)
        }
    }
}
@Composable
internal fun BadgeModelView(serial: Long?, dark: Boolean, pose: String?, onPose: (String) -> Unit,
    controller: BadgeViewerController, onStatus: (String) -> Unit, modifier: Modifier = Modifier,
    onFeedback: (String) -> Unit = {}, reducedMotion: Boolean = false, onDiagnostic: (String) -> Unit = {}, clearsky: Boolean = false,
    cabinet: Boolean = false, cabinetSlots: List<BadgeCabinetDisplay> = emptyList(), cabinetInspection: BadgeCabinetDisplay? = null, remoteModels: Map<String, CachedBadgeModel> = emptyMap(), onCabinetSelect: (Int) -> Unit = {},
    night: Boolean = false, fontScale: Float = 1f, onCabinetPrepare: (Int) -> Unit = {}, awardSlot: Int? = null, awardAcknowledged: Boolean = false, onAwardPresented: (Int) -> Unit = {}, onCabinetStory: (Int) -> Unit = {}, cabinetInsets: BadgeCabinetInsets = BadgeCabinetInsets(), onCabinetPlaying: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val session = remember { BadgeRenderSessions.acquire(context, cabinet, clearsky, pose) }
    val configuration = JSONObject().put("serial", serial ?: JSONObject.NULL).put("dark", dark).put("reducedMotion", reducedMotion)
        .put("night", night).put("fontScale", fontScale).put("deferInvitation", cabinet)
        .put("insets", JSONObject().put("top", cabinetInsets.top).put("right", cabinetInsets.right).put("bottom", cabinetInsets.bottom).put("left", cabinetInsets.left).put("safeTop", cabinetInsets.safeTop).put("safeBottom", cabinetInsets.safeBottom))
        .put("awardSlot", awardSlot ?: JSONObject.NULL).put("awardAcknowledged", awardAcknowledged)
        .put("slots", JSONArray().apply { cabinetSlots.forEach { put(JSONObject().put("badgeId", it.badgeId).put("serial", it.serial).put("slot", it.slot).put("name", it.name).put("designNote", it.designNote).put("engravingPublicId", it.engravingPublicId).put("modelStatus", it.modelStatus).put("modelAttempt", it.modelAttempt).put("assetKey", it.assetKey).put("presentation", it.presentation?.let { p -> JSONObject(badgeJson.encodeToString(p)) } ?: JSONObject.NULL)) } })
        .put("inspection", cabinetInspection?.let { JSONObject().put("badgeId", it.badgeId).put("serial", it.serial).put("slot", it.slot).put("name", it.name).put("designNote", it.designNote).put("engravingPublicId", it.engravingPublicId).put("modelStatus", it.modelStatus).put("assetKey", it.assetKey).put("presentation", it.presentation?.let { p -> JSONObject(badgeJson.encodeToString(p)) } ?: JSONObject.NULL) } ?: JSONObject.NULL)
        .toString()
    val expectedCabinetSlots = JSONArray().apply {
        cabinetSlots.filter { it.presentation != null || it.badgeId in setOf("first-light-016", "clear-sky-crown", "together-with-you") }.forEach {
            put(JSONObject().put("slot", it.slot).put("serial", it.serial).put("badgeId", it.badgeId))
        }
    }.toString()
    val callbacks = BadgeRendererCallbacks(onStatus, onPose, onFeedback, onCabinetSelect,
        onCabinetPrepare, onAwardPresented, onCabinetStory, onCabinetPlaying, onDiagnostic)
    SideEffect { session.callbacks = callbacks }
    AndroidView(factory = { session.web }, update = {
        session.configure(configuration, expectedCabinetSlots, remoteModels)
    }, modifier = modifier.fillMaxSize())
    DisposableEffect(session, owner) {
        session.attach(context, callbacks)
        controller.view = session.web
        fun activate(stage: String) {
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) session.resume(stage)
        }
        val attachObserver = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { activate("attached") }
            override fun onViewDetachedFromWindow(view: View) { session.pause() }
        }
        session.web.addOnAttachStateChangeListener(attachObserver)
        if (session.web.isAttachedToWindow) activate("initial-resume")
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> activate("lifecycle-resume")
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> session.pause()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            session.web.removeOnAttachStateChangeListener(attachObserver)
            controller.view = null
            BadgeRenderSessions.release(session)
        }
    }
}

internal data class BadgeRendererCallbacks(
    val status: (String) -> Unit = {}, val pose: (String) -> Unit = {}, val feedback: (String) -> Unit = {},
    val select: (Int) -> Unit = {}, val prepare: (Int) -> Unit = {}, val award: (Int) -> Unit = {},
    val story: (Int) -> Unit = {}, val playing: (Boolean) -> Unit = {}, val diagnostic: (String) -> Unit = {})

/** One idle renderer, never an Activity or a source of badge ownership. All access is on main. */
internal object BadgeRenderSessions {
    private var idle: BadgeRenderSession? = null
    private val main = Handler(Looper.getMainLooper())
    private val expiry = Runnable { clear() }
    private var sequence = 0

    private fun take(): BadgeRenderSession? {
        main.removeCallbacks(expiry)
        return idle.also { idle = null }
    }
    internal fun clear() { main.removeCallbacks(expiry); take()?.destroy() }
    internal fun trimMemory(level: Int) {
        if (level in 10..15 || level >= 40) clear()
    }
    private fun nextId() = ++sequence
    // Private session type never escapes the UI implementation.
    fun acquire(context: Context, cabinet: Boolean, clearsky: Boolean, pose: String?): BadgeRenderSession {
        val candidate = if (cabinet) take() else null
        val retained = candidate?.takeIf { it.reusable }
        if (candidate != null && retained == null) candidate.destroy()
        return (retained ?: BadgeRenderSession(context.applicationContext, cabinet, clearsky, pose, nextId()))
            .also { it.beginVisit() }
    }
    fun release(session: BadgeRenderSession) {
        session.park()
        if (!session.reusable) { session.destroy(); return }
        clear()
        idle = session
        main.postDelayed(expiry, 5 * 60_000L)
    }
}

@SuppressLint("SetJavaScriptEnabled", "LogNotTimber")
internal class BadgeRenderSession(appContext: Context, private val cabinet: Boolean,
    clearsky: Boolean, pose: String?, private val sessionId: Int) {
    private val context = MutableContextWrapper(appContext)
    private val applicationContext = appContext
    private val pageUrl = "https://badge.local.invalid/viewer/" + if (cabinet) "cabinet.html" else if (clearsky) "clearsky.html" else "index.html"
    private val lifetime = BadgeViewLifetime()
    private val renderStatus = AtomicReference("loading")
    private val renderProcessGone = AtomicBoolean(false)
    private val visualCommitRequested = AtomicBoolean(false)
    private val rendererHasFrame = AtomicBoolean(false)
    private val startupDeadline = BadgeStartupDeadline()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val initialPose = safeBadgePose(pose ?: "")
    private var latestConfiguration = "{}"
    private var latestExpectedCabinetSlots = "[]"
    @Volatile private var latestModels: Map<String, CachedBadgeModel> = emptyMap()
    private var hostResumed = false
    private var visit = 0
    private var pageLoads = 0
    private var nativeTouchStarts = 0
    private var nativeTouchMoves = 0
    private var lastConfiguration = ""
    private var healthGeneration = 0
    private var healthInFlight = false
    internal var callbacks = BadgeRendererCallbacks()
    private val timeout = Runnable {
        if (lifetime.canRender && renderStatus.get() == "loading") {
            diagnose(web, "loading-timeout"); reportStatus("render_failed")
        }
    }
    private val unresponsive = Runnable {
        if (lifetime.canRender && renderStatus.get() == "ready") {
            publishDiagnostic(nativeDiagnostic(web, "renderer-unresponsive"))
            reportStatus("render_failed")
        }
    }
    private val healthCheck = Runnable { checkRendererHealth() }

    private fun checkRendererHealth() {
        if (!lifetime.canRender || renderStatus.get() != "ready" || healthInFlight) return
        healthInFlight = true
        val generation = healthGeneration
        mainHandler.postDelayed(unresponsive, 10_000L)
        try {
            web.evaluateJavascript("!!window.BadgeView && (typeof window.BadgeView.healthy !== 'function' || window.BadgeView.healthy())") { result ->
                if (generation != healthGeneration || !lifetime.canRender || renderStatus.get() != "ready") return@evaluateJavascript
                healthInFlight = false
                mainHandler.removeCallbacks(unresponsive)
                if (result != "true") reportStatus("render_failed")
                else mainHandler.postDelayed(healthCheck, 5_000L)
            }
        } catch (_: Exception) { reportStatus("render_failed") }
    }

    private fun requestVisualCommit() {
        if (!cabinet || !lifetime.canRender || !rendererHasFrame.get() || renderStatus.get() != "loading" ||
            !visualCommitRequested.compareAndSet(false, true)) return
        val hostGeneration = lifetime.hostGeneration
        web.postVisualStateCallback(visit.toLong(), object : WebView.VisualStateCallback() {
            override fun onComplete(requestId: Long) {
                if (!lifetime.isCurrentHost(hostGeneration) || renderProcessGone.get() || renderStatus.get() != "loading") return
                if (!lifetime.canRender) { visualCommitRequested.set(false); return }
                web.alpha = 1f
                reportStatus("ready")
                web.evaluateJavascript("window.BadgeView&&window.BadgeView.presented&&window.BadgeView.presented()", null)
            }
        })
    }
    val reusable: Boolean get() = cabinet && lifetime.isAlive && !renderProcessGone.get() &&
        renderStatus.get() == "ready"

    fun beginVisit() {
        lifetime.newHost()
        visit++
        renderStatus.set("loading")
        startupDeadline.reset()
        rendererHasFrame.set(false)
        healthGeneration++
        healthInFlight = false
        visualCommitRequested.set(false)
        lastConfiguration = ""
        if (cabinet) web.alpha = 0f
    }
    internal fun attach(host: Context, callbacks: BadgeRendererCallbacks) {
        context.baseContext = host
        this.callbacks = callbacks
    }
    fun configure(value: String, expected: String, models: Map<String, CachedBadgeModel>) {
        latestConfiguration = value
        latestExpectedCabinetSlots = expected
        latestModels = models
        if (lastConfiguration != value) { lastConfiguration = value; configurePage() }
    }
    private fun configurePage() {
        if (!lifetime.isAlive || renderProcessGone.get()) return
        val restored = JSONObject(latestConfiguration).apply { initialPose?.let { put("pose", JSONObject(it)) } }
        web.evaluateJavascript("""(() => {
            window.__BADGE_CONFIG__=$restored;
            if (!window.BadgeView) return;
            if (window.__BADGE_VISIT__ !== $visit && window.BadgeView.beginVisit) {
                window.__BADGE_VISIT__=$visit; window.BadgeView.beginVisit(window.__BADGE_CONFIG__);
            } else window.BadgeView.configure(window.__BADGE_CONFIG__);
        })()""".trimIndent(), null)
    }
    fun resume(stage: String) {
        hostResumed = true
        resumeRenderer(web, stage)
        mainHandler.removeCallbacks(timeout)
        if (renderStatus.get() == "loading") {
            startupDeadline.resume(android.os.SystemClock.uptimeMillis())?.let { mainHandler.postDelayed(timeout, it) }
            requestVisualCommit()
        } else if (renderStatus.get() == "ready") {
            mainHandler.removeCallbacks(healthCheck)
            mainHandler.postDelayed(healthCheck, 5_000L)
        }
    }
    fun pause() {
        hostResumed = false
        lifetime.pause()
        startupDeadline.pause(android.os.SystemClock.uptimeMillis())
        healthGeneration++
        healthInFlight = false
        if (renderStatus.get() == "loading") visualCommitRequested.set(false)
        mainHandler.removeCallbacksAndMessages(null)
        if (lifetime.isAlive && !renderProcessGone.get()) {
            web.evaluateJavascript("window.BadgeView&&window.BadgeView.pause()", null); web.onPause()
        }
    }
    fun park() {
        pause()
        lifetime.newHost() // Invalidate queued bridge callbacks from the previous Activity.
        callbacks = BadgeRendererCallbacks()
        context.baseContext = applicationContext
        (web.parent as? ViewGroup)?.removeView(web)
    }
    fun destroy() {
        if (!lifetime.destroy()) return
        mainHandler.removeCallbacksAndMessages(null)
        callbacks = BadgeRendererCallbacks()
        latestModels = emptyMap()
        if (!renderProcessGone.get()) runCatching {
            web.evaluateJavascript("window.BadgeView&&window.BadgeView.dispose()", null)
            web.onPause(); web.stopLoading(); web.removeJavascriptInterface("BadgeHost")
        }
        (web.parent as? ViewGroup)?.removeView(web)
        web.removeAllViews(); web.destroy()
    }
    fun reportStatus(value: String) {
        if (!lifetime.isAlive || renderStatus.get() in setOf("asset_unavailable", "graphics_unavailable", "render_failed")) return
        renderStatus.set(value)
        if (value == "ready") {
            startupDeadline.committed()
            mainHandler.removeCallbacks(timeout)
            mainHandler.removeCallbacks(healthCheck)
            if (lifetime.canRender) mainHandler.postDelayed(healthCheck, 5_000L)
        } else if (value in setOf("asset_unavailable", "graphics_unavailable", "render_failed")) {
            healthGeneration++
            healthInFlight = false
            mainHandler.removeCallbacks(timeout)
            mainHandler.removeCallbacks(healthCheck)
            mainHandler.removeCallbacks(unresponsive)
        }
        callbacks.status(value)
    }
    fun nativeDiagnostic(view: WebView, stage: String): JSONObject = JSONObject()
        .put("stage", stage).put("sessionId", sessionId).put("visit", visit).put("pageLoads", pageLoads).put("nativeWidth", view.width).put("nativeHeight", view.height)
        .put("attached", view.isAttachedToWindow).put("nativeProgress", runCatching { view.progress }.getOrDefault(-1))
        .put("lifecycleResumed", hostResumed)
        .put("nativeTouchStarts", nativeTouchStarts).put("nativeTouchMoves", nativeTouchMoves)
        .put("renderStatus", renderStatus.get())
    @SuppressLint("LogNotTimber") // Release renderer health, deliberately limited to bounded flags and dimensions.
    fun publishDiagnostic(value: JSONObject) {
        if (!lifetime.isAlive) return
        // Only bounded dimensions and renderer flags cross this diagnostic path.
        // No URL, page text, pose, serial, identity, receipt, device or location data.
        val message = value.toString()
        Log.i("BadgeRenderer", message)
        callbacks.diagnostic(message)
    }
    fun diagnose(view: WebView, stage: String) {
        if (!lifetime.isAlive) return
        val baseline = nativeDiagnostic(view, stage)
        if (renderProcessGone.get() || renderStatus.get() in setOf("asset_unavailable", "graphics_unavailable", "render_failed")) {
            publishDiagnostic(baseline)
            return
        }
        val script = """(() => {
            const canvas = document.getElementById('view');
            const rect = canvas ? canvas.getBoundingClientRect() : null;
            const state = window.BadgeView && window.BadgeView.diagnostics ? window.BadgeView.diagnostics() : {};
            const expectedSlots = $latestExpectedCabinetSlots;
            const actualSlots = Array.isArray(state.slotSerials) ? state.slotSerials : [];
            return {readyState:document.readyState, visibilityState:document.visibilityState,
                viewportWidth:innerWidth, viewportHeight:innerHeight,
                canvasWidth:canvas ? canvas.width : 0, canvasHeight:canvas ? canvas.height : 0,
                rectWidth:rect ? rect.width : 0, rectHeight:rect ? rect.height : 0,
                modelLoaded:state.modelLoaded === true, labelReady:state.labelReady === true,
                active:state.active === true, disposed:state.disposed === true,
                pendingFrame:state.pendingFrame === true, revealActive:state.revealActive === true,
                firstFrame:state.firstFrame === true, shaderFailed:state.shaderFailed === true,
                contextLost:state.contextLost === true, firstFrameMillis:state.firstFrameMillis,
                userPoseRevision:state.userPoseRevision, sceneMeshes:state.sceneMeshes, sceneStyle:state.sceneStyle,
                motionFrameMedianMillis:state.motionFrameMedianMillis, motionFrameCount:state.motionFrameCount, frameCacheHits:state.frameCacheHits, frameCacheCaptures:state.frameCacheCaptures, gestureStarts:state.gestureCounts?.starts, gestureMoves:state.gestureCounts?.moves, gestureRotations:state.gestureCounts?.rotations, gestureCancels:state.gestureCounts?.cancels, pixelRatio:state.pixelRatio, shadowEnabled:state.shadowEnabled === true,
                phase:state.phase, transitionProgress:state.transitionProgress, drawCalls:state.drawCalls, triangles:state.triangles,
                realMedalCount:state.realMedalCount, geometryCount:state.geometryCount, textureCount:state.textureCount,
                firstMedalWidth:state.firstMedalWidth, firstMedalHeight:state.firstMedalHeight,
                invitationCompleted:state.invitationCompleted, invitationActive:state.invitationActive === true, presented:state.presented === true,
                serialMatchesWallet:${if (cabinet) "null" else "state.serial === ${JSONObject(latestConfiguration).opt("serial") ?: "null"} && state.labelSerial === ${JSONObject(latestConfiguration).opt("serial") ?: "null"}"},
                slotsMatchWallet:${if (cabinet) "actualSlots.every(a => expectedSlots.some(e => a.slot === e.slot && a.serial === e.serial && a.badgeId === e.badgeId))" else "null"}};
        })()""".trimIndent()
        try { view.evaluateJavascript(script) { raw ->
            if (!lifetime.isAlive) return@evaluateJavascript
            val parsed = runCatching {
                if (raw.length > 2048) return@runCatching null
                val decoded = JSONTokener(raw).nextValue()
                when (decoded) { is JSONObject -> decoded; is String -> JSONObject(decoded); else -> null }
            }.getOrNull()
            baseline.put("jsResponded", parsed != null)
            parsed?.let { data ->
                for (key in listOf("motionFrameMedianMillis", "motionFrameCount", "frameCacheHits", "frameCacheCaptures", "gestureStarts", "gestureMoves", "gestureRotations", "gestureCancels", "viewportWidth", "viewportHeight", "canvasWidth", "canvasHeight", "rectWidth", "rectHeight", "firstFrameMillis", "userPoseRevision", "sceneMeshes", "pixelRatio", "transitionProgress", "drawCalls", "triangles", "realMedalCount", "geometryCount", "textureCount", "firstMedalWidth", "firstMedalHeight", "invitationCompleted")) {
                    val number = (data.opt(key) as? Number)?.toDouble()
                    if (number != null && number.isFinite() && number in 0.0..(if (key == "triangles") 2_000_000.0 else 100_000.0)) baseline.put(key, number)
                }
                for (key in listOf("modelLoaded", "labelReady", "active", "disposed", "pendingFrame", "revealActive", "firstFrame", "shaderFailed", "contextLost", "serialMatchesWallet", "slotsMatchWallet", "shadowEnabled", "invitationActive", "presented"))
                    (data.opt(key) as? Boolean)?.let { baseline.put(key, it) }
                data.optString("phase").takeIf { it in setOf("overview", "entering", "inspect", "returning", "disposed") }?.let { baseline.put("phase", it) }
                data.optString("sceneStyle").takeIf { it in setOf("clearsky", "classic", "cabinet") }?.let { baseline.put("sceneStyle", it) }
                data.optString("readyState").takeIf { it in setOf("loading", "interactive", "complete") }?.let { baseline.put("readyState", it) }
                data.optString("visibilityState").takeIf { it in setOf("visible", "hidden", "prerender") }?.let { baseline.put("visibilityState", it) }
            }
            publishDiagnostic(baseline)
        } } catch (_: Exception) {
            publishDiagnostic(baseline.put("jsResponded", false).put("jsEvaluationFailed", true))
        }
    }
    fun resumeRenderer(view: WebView, stage: String) {
        if (!lifetime.isAlive || renderProcessGone.get() || !hostResumed) return
        if (renderStatus.get() in setOf("asset_unavailable", "graphics_unavailable", "render_failed")) return
        lifetime.resume()
        view.onResume()
        view.evaluateJavascript("window.BadgeView&&window.BadgeView.resume()", null)
        view.invalidate()
        view.postOnAnimation {
            if (lifetime.canRender && view.isAttachedToWindow) view.invalidate()
        }
        mainHandler.postDelayed({ if (lifetime.canRender) diagnose(view, stage) }, 1500)
    }
    fun criticalResource(request: WebResourceRequest): Boolean {
        return badgeCriticalResource(request.url.toString(), request.method, request.isForMainFrame, cabinet)
    }
    val web: WebView = run {
        object : WebView(context) {
            override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) nativeTouchStarts = (nativeTouchStarts + 1).coerceAtMost(100_000)
                if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE) nativeTouchMoves = (nativeTouchMoves + 1).coerceAtMost(100_000)
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_POINTER_DOWN ->
                        parent?.requestDisallowInterceptTouchEvent(true)
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                        parent?.requestDisallowInterceptTouchEvent(false)
                }
                val handled = super.dispatchTouchEvent(event)
                if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    mainHandler.postDelayed({ if (lifetime.canRender) diagnose(this, "touch-up") }, 150)
                }
                return handled
            }
        }.apply {
            com.zhisheng.weather.ui.UiRefreshRate.applyViewTree(this)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            // Show the WebView only after Chromium has committed its first painted frame.
            // The Compose glass/terminal background remains visible during loading.
            if (cabinet) alpha = 0f
            contentDescription = if (cabinet) "我的徽章，左右拖动可直接旋转，上下滑动浏览收藏，轻触放大；放大后可双指缩放，更多菜单提供回正和翻面" else "初光三维徽章，拖动自由翻转，双指缩放；下方提供无障碍旋转和回正按钮"
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
            addJavascriptInterface(BadgeRenderBridge(lifetime, { value ->
                if (cabinet && value == "ready") { rendererHasFrame.set(true); requestVisualCommit() }
                else reportStatus(value)
            }, { callbacks.pose(it) }, { callbacks.feedback(it) }, { callbacks.select(it) }, { callbacks.prepare(it) }, { callbacks.award(it) }, { callbacks.story(it) }, { callbacks.playing(it) }), "BadgeHost")
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                    fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(byteArrayOf()))
                    val modelHash = badgeModelCacheHash(request.url.toString(), request.method)
                    if(modelHash!=null){val model=latestModels[modelHash] ?: return denied()
                        return try { require(model.manifest.sha256==modelHash);validateCachedModelFile(model)
                            WebResourceResponse("model/gltf-binary",null,200,"OK",mapOf("Cache-Control" to "no-store","X-Content-Type-Options" to "nosniff"),FileInputStream(model.file))
                        }catch(_:Exception){denied()}
                    }
                    val path = badgeAssetPath(request.url.toString(), request.method) ?: return denied()
                    return try {
                        val input = context.assets.open("badges/$path")
                        val mime = when { path.endsWith(".html") -> "text/html"; path.endsWith(".js") || path.endsWith(".mjs") -> "text/javascript"
                            path.endsWith(".json") -> "application/json"; path.endsWith(".png") -> "image/png"; path.endsWith(".jpg") -> "image/jpeg"
                            path.endsWith(".ttf") -> "font/ttf"; else -> "application/octet-stream" }
                        WebResourceResponse(mime, if (mime.startsWith("text/") || mime == "application/json") "UTF-8" else null,
                            200, "OK", mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"), input)
                    } catch (_: Exception) { denied() }
                }
                override fun onPageFinished(view: WebView, url: String) {
                    if (lifetime.isAlive && !renderProcessGone.get() && url == pageUrl) {
                        pageLoads++
                        configurePage()
                        if (hostResumed) {
                            resumeRenderer(view, "page-finished")
                        } else {
                            view.evaluateJavascript("window.BadgeView&&window.BadgeView.pause()", null); view.onPause()
                        }
                    }
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (!lifetime.isAlive || !criticalResource(request)) return
                    publishDiagnostic(nativeDiagnostic(view, "resource-error").put("errorCode", error.errorCode).put("mainFrame", request.isForMainFrame))
                    reportStatus("render_failed")
                }
                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                    if (!lifetime.isAlive || !criticalResource(request) || response.statusCode < 400) return
                    publishDiagnostic(nativeDiagnostic(view, "http-error").put("httpStatus", response.statusCode).put("mainFrame", request.isForMainFrame))
                    reportStatus("render_failed")
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    renderProcessGone.set(true)
                    if (lifetime.isAlive) {
                        publishDiagnostic(nativeDiagnostic(view, "renderer-gone").put("crashed", detail.didCrash()))
                        reportStatus("graphics_unavailable")
                    }
                    // The failure state removes this AndroidView; failed sessions are never retained.
                    return true
                }
            }
            // No WebChromeClient console logging and no identity/receipt/recovery data crosses the bridge.
            loadUrl(pageUrl)
        }
    }
}
