package com.zhisheng.weather.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.zhisheng.weather.data.CityRepository
import kotlinx.coroutines.*

class WidgetLaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            when (intent.action) {
                "none" -> Unit
                "notification_settings" -> open(Intent(this@WidgetLaunchActivity, WidgetConfigActivity::class.java).putExtra("notification_settings", true))
                "calendar" -> openSystemCalendar()
                "clock" -> if (!open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), showFailure = false)) {
                    open(Intent(this@WidgetLaunchActivity, com.zhisheng.weather.MainActivity::class.java)
                        .setAction("com.zhisheng.weather.action.WIDGET_WEATHER").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
                "settings" -> open(Intent(this@WidgetLaunchActivity, WidgetConfigActivity::class.java)
                    .putExtra("widget_style", intent.getStringExtra("widget_style")).apply { if (id >= 0) putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id) })
                else -> {
                    // The launcher tap must still open weather if saved-city data is
                    // temporarily unavailable during an upgrade or cold start.
                    try {
                        val cfg = if (id >= 0) WidgetStore.config(this@WidgetLaunchActivity, id) else WidgetStore.notification(this@WidgetLaunchActivity).config
                        val key = intent.getStringExtra("city")
                        val city = withContext(Dispatchers.IO) { WidgetRuntime.city(this@WidgetLaunchActivity, cfg) }
                        if (key != null && city?.locationKey == key) withContext(Dispatchers.IO) {
                            if (CityRepository.savedCities().any { it.locationKey == key }) CityRepository.selectCity(key) else CityRepository.addCity(city)
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        android.util.Log.w("DesktopWeather", "Cannot select widget city before opening weather", error)
                    }
                    open(Intent(this@WidgetLaunchActivity, com.zhisheng.weather.MainActivity::class.java)
                        .setAction("com.zhisheng.weather.action.WIDGET_WEATHER").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }
            }
            finish()
        }
    }
    private fun openSystemCalendar() {
        // A generic content://calendar/time VIEW can resolve to document viewers
        // while the phone's own calendar only exposes its APP_CALENDAR entry.
        val entry = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
        val candidates = runCatching { packageManager.queryIntentActivities(entry, 0) }.getOrDefault(emptyList())
            .sortedBy { result ->
                val app = result.activityInfo.applicationInfo
                when {
                    app.packageName == "com.google.android.calendar" -> 2
                    app.packageName == "com.android.calendar" ||
                        app.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 -> 0
                    else -> 1
                }
            }
        for (result in candidates) {
            val target = Intent(entry).setComponent(ComponentName(result.activityInfo.packageName, result.activityInfo.name))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            if (open(target, showFailure = false)) return
        }
        open(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun open(target: Intent, showFailure: Boolean = true): Boolean {
        return runCatching { startActivity(target) }.fold(onSuccess = { true }, onFailure = {
            if (showFailure) Toast.makeText(this, "未找到对应应用，请检查系统时钟或日历", Toast.LENGTH_SHORT).show()
            false
        })
    }
}

class WidgetConfigActivity : ComponentActivity() {
    private var notification by mutableStateOf(NotificationSettings())
    private var pinState by mutableStateOf(WidgetPinState.IDLE)
    private var showAddHelp by mutableStateOf(false)
    private var showAdded by mutableStateOf(false)
    private var pendingPinToken: String? = null
    private var pinStartedAt = 0L
    private var pinKind = WidgetKind.WEEK
    private var pinStyle = WidgetStyle.VISTA
    private var pinMonitor: Job? = null
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // The chosen value is persisted before opening the system dialog, so
        // activity recreation cannot silently discard the user's switch.
        if (granted) WidgetRuntime.scope.launch { WidgetNotification.refresh(applicationContext) }
        if (!granted) Toast.makeText(this, "通知未获授权，可在系统通知设置中开启", Toast.LENGTH_LONG).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { saved ->
            pendingPinToken = saved.getString("pending_pin_token")
            pinStartedAt = saved.getLong("pending_pin_started")
            pinKind = WidgetKind.entries.firstOrNull { it.name == saved.getString("pending_pin_kind") } ?: WidgetKind.WEEK
            pinStyle = WidgetStyle.from(saved.getString("pending_pin_style"))
            pinState = WidgetPinState.entries.firstOrNull { it.name == saved.getString("pending_pin_state") } ?: WidgetPinState.IDLE
            showAddHelp = saved.getBoolean("show_add_help")
            showAdded = saved.getBoolean("show_added")
            // A destroyed activity's preparation coroutine was cancelled before
            // dispatch. Never leave its recreated button disabled indefinitely.
            if (pinState == WidgetPinState.PREPARING) pinState = WidgetPinState.BACKGROUND
        }
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val instance = if (id != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetRuntime.instance(this, id) else null
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && instance == null) { finish(); return }
        val style = instance?.style ?: WidgetStyle.from(intent.getStringExtra("widget_style"))
        val initial = instance?.let { WidgetStore.config(this, it.id, style) } ?: WidgetConfig.defaults(style)
        notification = WidgetStore.notification(this)
        val statusBar = if (style == WidgetStyle.VISTA) android.graphics.Color.rgb(237,244,245) else android.graphics.Color.rgb(20,27,27)
        setContent {
            val cities by CityRepository.cities.collectAsState(initial = emptyList())
            val selected by CityRepository.selectedCity.collectAsState(initial = null)
            val located by CityRepository.locatedCityKey.collectAsState(initial = null)
            if (intent.getBooleanExtra("notification_settings", false)) NotificationStudio(notification, cities, selected, located,
                onChange = { requestNotification(it) }, onClose = { finish() })
            else WidgetStudio(style, instance?.id, instance?.kind ?: WidgetKind.WEEK, initial, cities, selected, located,
                notification, onNotification = { requestNotification(it) }, onClose = { finish() },
                onApply = { kind, config -> applyWidget(style, kind, config, id) },
                pinState = pinState, onAddHelp = { kind -> pinKind = kind; pinStyle = style; showAddHelp = true })
            WidgetFeedbackTheme(pinStyle) {
            if (showAddHelp) WidgetAddHelp(pinState, pinKind, pinStyle, onDismiss = { showAddHelp = false },
                onHome = { showAddHelp = false; openAddSettings(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) },
                onHomeSettings = { openAddSettings(Intent(android.provider.Settings.ACTION_HOME_SETTINGS)) },
                onAppSettings = { openAddSettings(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))) })
            if (showAdded) WidgetAddedDialog(pinKind, pinStyle, onDismiss = { showAdded = false }, onHome = {
                    showAdded = false; openAddSettings(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                })
            }
        }
        if (instance == null && pinState == WidgetPinState.IDLE) { pinStyle = style; pinKind = WidgetKind.WEEK }
        // MIUI can create the DecorView lazily; apply system-bar styling only after content exists.
        window.decorView.post {
            window.statusBarColor = statusBar
            window.navigationBarColor = statusBar
            if (Build.VERSION.SDK_INT >= 30) window.decorView.windowInsetsController?.setSystemBarsAppearance(
                if (style == WidgetStyle.VISTA) android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
            )
        }
        // Install the restored request's observer once; repeatOnLifecycle owns
        // pausing/resuming it when returning from the launcher's confirmation.
        monitorPin()
    }
    private fun requestNotification(value: NotificationSettings) {
        // Editing a place or icon must not repeatedly reopen permission settings.
        if (value.enabled == notification.enabled && value.alertsEnabled == notification.alertsEnabled) {
            saveNotification(value)
            return
        }
        val manager = getSystemService(android.app.NotificationManager::class.java)
        WeatherAlertNotification.ensureChannel(this)
        val channels = listOfNotNull(WidgetNotification.CHANNEL.takeIf { value.enabled }, WeatherAlertNotification.CHANNEL.takeIf { value.alertsEnabled })
        val allowed = channels.all { manager?.getNotificationChannel(it)?.importance != android.app.NotificationManager.IMPORTANCE_NONE }
        val granted = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val p = getSharedPreferences("notification_permission", MODE_PRIVATE)
        when (notificationPermissionAction(value.enabled || value.alertsEnabled, Build.VERSION.SDK_INT >= 33, granted,
            p.getBoolean("asked", false), shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS),
            androidx.core.app.NotificationManagerCompat.from(this).areNotificationsEnabled(), allowed)) {
            NotificationPermissionAction.REQUEST -> {
                saveNotification(value)
                p.edit().putBoolean("asked", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            NotificationPermissionAction.SETTINGS -> {
                saveNotification(value)
                Toast.makeText(this, "请在系统设置中允许通知，返回后自动生效", Toast.LENGTH_LONG).show()
                startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
            }
            NotificationPermissionAction.SAVE -> saveNotification(value)
        }
    }
    override fun onResume() {
        super.onResume()
        WidgetRuntime.scope.launch { WidgetNotification.refresh(applicationContext) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_pin_token", pendingPinToken)
        outState.putLong("pending_pin_started", pinStartedAt)
        outState.putString("pending_pin_kind", pinKind.name)
        outState.putString("pending_pin_style", pinStyle.key)
        outState.putString("pending_pin_state", pinState.name)
        outState.putBoolean("show_add_help", showAddHelp)
        outState.putBoolean("show_added", showAdded)
        super.onSaveInstanceState(outState)
    }
    private fun openAddSettings(target: Intent) {
        try { startActivity(target) }
        catch (error: Exception) {
            android.util.Log.w("DesktopWeather", "Widget help settings unavailable", error)
            Toast.makeText(this, "无法直接打开，请回到桌面长按空白处，或从系统设置进入", Toast.LENGTH_LONG).show()
        }
    }
    private fun pinFeedback(state: WidgetPinState) { pinState = state; showAddHelp = true }
    private fun monitorPin() {
        pinMonitor?.cancel()
        val token = pendingPinToken ?: return
        if (pinState in setOf(WidgetPinState.PREPARING, WidgetPinState.ADDED, WidgetPinState.CONFIG_FAILED)) return
        pinMonitor = lifecycleScope.launch {
            // Wait for RESUMED after creation and when returning from the launcher.
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Completed requests must not reopen their feedback on every resume.
            if (pinState in setOf(WidgetPinState.ADDED, WidgetPinState.CONFIG_FAILED)) return@repeatOnLifecycle
            while (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                val result = pinAwaitResult((SystemClock.elapsedRealtime() - pinStartedAt).coerceAtLeast(0), WidgetStore.pinCompletion(this@WidgetConfigActivity, token))
                when {
                    result == WidgetPinState.ADDED -> {
                        pinState = result; showAddHelp = false; showAdded = true; return@repeatOnLifecycle
                    }
                    result == WidgetPinState.CONFIG_FAILED -> { pinFeedback(result); return@repeatOnLifecycle }
                    result == WidgetPinState.UNCONFIRMED && pinState == WidgetPinState.WAITING -> pinFeedback(result)
                }
                delay(750)
            }
            }
        }
    }
    private fun saveNotification(value: NotificationSettings) {
        notification = value
        WidgetStore.saveNotification(this, value)
        WidgetRefresh.schedule(this); WidgetRefresh.request(this)
        WidgetRuntime.scope.launch { WidgetNotification.refresh(applicationContext) }
    }
    private fun applyWidget(style: WidgetStyle, kind: WidgetKind, config: WidgetConfig, id: Int) {
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            WidgetStore.save(this, id, config)
            lifecycleScope.launch { withContext(Dispatchers.IO) { WidgetRuntime.renderOne(this@WidgetConfigActivity, id) }; WidgetRefresh.schedule(this@WidgetConfigActivity); WidgetRefresh.request(this@WidgetConfigActivity)
                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish() }
        } else {
            if (pinState in setOf(WidgetPinState.PREPARING, WidgetPinState.WAITING)) return
            pinStyle = style; pinKind = kind
            val manager = AppWidgetManager.getInstance(this)
            pinMonitor?.cancel(); pendingPinToken = null
            pinState = WidgetPinState.PREPARING
            showAddHelp = false; showAdded = false
            lifecycleScope.launch {
                try {
                    if (!manager.isRequestPinAppWidgetSupported) { pinFeedback(WidgetPinState.UNSUPPORTED); return@launch }
                    val snapshot = try {
                        withTimeoutOrNull(1_500) { withContext(Dispatchers.IO) { WidgetRuntime.snapshot(this@WidgetConfigActivity, config) } }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { android.util.Log.w("DesktopWeather", "Weather unavailable for pin preview", error); null }
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) { pinFeedback(WidgetPinState.BACKGROUND); return@launch }
                    val token = WidgetStore.preparePin(this@WidgetConfigActivity, style, kind, config)
                    pendingPinToken = token
                    pinStartedAt = SystemClock.elapsedRealtime()
                    val intent = Intent(this@WidgetConfigActivity, WidgetPinReceiver::class.java)
                        .setData(Uri.parse("zhisheng://pin/$token")).putExtra("token", token)
                    val callback = PendingIntent.getBroadcast(this@WidgetConfigActivity, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                    // Some launchers reject a custom preview. Keep that optional:
                    // a default provider preview can still allow the request.
                    val extras = runCatching {
                        val preview = WidgetRuntime.views(this@WidgetConfigActivity, -1, style, kind, config,
                            snapshot?.takeIf { it.weather.current != null } ?: widgetPreviewSample())
                        Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview) }
                    }.getOrElse { android.util.Log.w("DesktopWeather", "Use default pin preview", it); null }
                    val accepted = manager.requestPinAppWidget(ComponentName(this@WidgetConfigActivity, WidgetRuntime.providerFor(style, kind)), extras, callback)
                    pinState = pinRequestResult(accepted)
                    if (!accepted) pinFeedback(pinState)
                    monitorPin()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    android.util.Log.w("DesktopWeather", "Widget pin request unavailable", error)
                    pinFeedback(WidgetPinState.ERROR)
                    monitorPin()
                }
            }
        }
    }
}

class WidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val instance = WidgetRuntime.instance(context, id) ?: return
        val token = intent.getStringExtra("token") ?: return
        if (!WidgetStore.applyPin(context, token, instance)) return
        val pending = goAsync()
        WidgetRuntime.scope.launch {
            try { withTimeout(8_000) { WidgetRuntime.renderOne(context, id); WidgetRefresh.schedule(context); WidgetRefresh.request(context) } }
            finally { pending.finish() }
        }
    }
}
