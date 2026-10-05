package com.zhisheng.weather.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.work.*
import com.zhisheng.weather.data.*
import com.zhisheng.weather.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

open class FreshWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        async { WidgetRuntime.renderAll(context); WidgetRefresh.schedule(context); WidgetRefresh.request(context) }
    }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        async { WidgetRuntime.renderOne(context, appWidgetId) }
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetStore.delete(context, it) }
        WidgetRefresh.schedule(context)
    }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        oldWidgetIds.zip(newWidgetIds).forEach { (old, new) ->
            WidgetStore.save(context, new, WidgetStore.config(context, old)); WidgetStore.delete(context, old)
        }
        async { WidgetRuntime.renderAll(context); WidgetRefresh.schedule(context) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetRefresh.ACTION_REFRESH) WidgetRefresh.request(context, force = true)
        if (intent.action in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED,
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_WALLPAPER_CHANGED)) {
            async { WidgetRuntime.renderAll(context); WidgetNotification.refresh(context); WidgetRefresh.schedule(context) }
        }
    }
    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try { withTimeout(8_000) { block() } }
            catch (e: Exception) { android.util.Log.w("DesktopWeather", "Redraw deferred", e) }
            finally { pending.finish() }
        }
    }
}
class VistaPulseWidgetProvider : FreshWidgetProvider()
class VistaNowWidgetProvider : FreshWidgetProvider()
class VistaBandWidgetProvider : FreshWidgetProvider()
class VistaWeekWidgetProvider : FreshWidgetProvider()
class VistaSceneWidgetProvider : FreshWidgetProvider()
class ClassicPulseWidgetProvider : FreshWidgetProvider()
class ClassicNowWidgetProvider : FreshWidgetProvider()
class ClassicBandWidgetProvider : FreshWidgetProvider()
class ClassicWeekWidgetProvider : FreshWidgetProvider()
class ClassicSceneWidgetProvider : FreshWidgetProvider()
data class WidgetInstance(val id: Int, val style: WidgetStyle, val kind: WidgetKind)

object WidgetRuntime {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val renderLock = Mutex()
    private var started = false
    private val providers = mapOf(
        WidgetStyle.VISTA to mapOf(WidgetKind.PULSE to VistaPulseWidgetProvider::class.java, WidgetKind.NOW to VistaNowWidgetProvider::class.java,
            WidgetKind.BAND to VistaBandWidgetProvider::class.java, WidgetKind.WEEK to VistaWeekWidgetProvider::class.java, WidgetKind.SCENE to VistaSceneWidgetProvider::class.java),
        WidgetStyle.CLASSIC to mapOf(WidgetKind.PULSE to ClassicPulseWidgetProvider::class.java, WidgetKind.NOW to ClassicNowWidgetProvider::class.java,
            WidgetKind.BAND to ClassicBandWidgetProvider::class.java, WidgetKind.WEEK to ClassicWeekWidgetProvider::class.java, WidgetKind.SCENE to ClassicSceneWidgetProvider::class.java))
    fun providerFor(style: WidgetStyle, kind: WidgetKind): Class<out AppWidgetProvider> = providers.getValue(style).getValue(kind)
    fun instances(c: Context): List<WidgetInstance> = providers.flatMap { (style, kinds) ->
        kinds.flatMap { (kind, provider) -> AppWidgetManager.getInstance(c).getAppWidgetIds(ComponentName(c, provider)).map { WidgetInstance(it, style, kind) } }
    }
    fun instance(c: Context, id: Int): WidgetInstance? {
        val info = AppWidgetManager.getInstance(c).getAppWidgetInfo(id) ?: return null
        return providers.entries.firstNotNullOfOrNull { (style, kinds) ->
            kinds.entries.firstOrNull { ComponentName(c, it.value) == info.provider }?.let { WidgetInstance(id, style, it.key) }
        }
    }
    @Synchronized fun start(c: Context) {
        if (started) return
        started = true
        val ctx = c.applicationContext
        // 升级或开机后配置流可能一个值都不变，distinctUntilChanged 就不会触发渲染，
        // 桌面会一直挂着旧包的 RemoteViews。进程启动先兜底重绘一次。
        scope.launch { renderAll(ctx); WidgetNotification.refresh(ctx); WidgetRefresh.schedule(ctx) }
        scope.launch {
            combine(SettingsRepository.livingSky, SettingsRepository.ambience) { sky, level -> sky to level }
                .distinctUntilChanged().catch { android.util.Log.w("DesktopWeather", "Sky preferences unavailable", it) }
                .collect { (sky, level) -> renderAll(ctx); WidgetRefresh.scheduleSky(ctx, sky, level) }
        }
        scope.launch {
            combine(CityRepository.selectedCity, CityRepository.cities, CityRepository.locatedCityKey,
                SettingsRepository.sourcePref, SettingsRepository.tempUnit) { a, b, d, e, f -> listOf(a, b, d, e, f) }
                .distinctUntilChanged().catch { android.util.Log.w("DesktopWeather", "Preferences unavailable", it) }
                .collect { renderAll(ctx); WidgetNotification.refresh(ctx); WidgetRefresh.schedule(ctx); WidgetRefresh.request(ctx) }
        }
    }
    fun publishAndRender(c: Context, city: City, weather: WeatherData) {
        WidgetStore.publish(c, city, weather)
        scope.launch { renderAll(c); WidgetNotification.refresh(c) }
    }
    suspend fun city(c: Context, config: WidgetConfig): City? = resolveWidgetCity(config,
        CityRepository.selectedCity.first(), CityRepository.savedCities(), CityRepository.locatedCityKey.first())
    suspend fun snapshot(c: Context, config: WidgetConfig): WidgetSnapshot? {
        val city = city(c, config) ?: return null
        val source = SettingsRepository.sourcePref.first()
        val own = WidgetStore.snapshot(c, city.locationKey)?.takeIf { source.matches(it.weather) }
        val cached = WeatherCache.load(c, city.locationKey)?.data?.takeIf { source.matches(it) }
        val data = listOfNotNull(own?.weather, cached).maxByOrNull { it.fetchedAt ?: 0L } ?: return WidgetSnapshot(city, WeatherData())
        return WidgetSnapshot(city, data)
    }
    suspend fun renderAll(c: Context) = renderLock.withLock {
        instances(c).forEach { item ->
            try { renderOneLocked(c, item.id) } catch (e: Exception) { android.util.Log.w("DesktopWeather", "Widget ${item.id} unavailable", e) }
        }
    }
    suspend fun renderOne(c: Context, id: Int) = renderLock.withLock { renderOneLocked(c, id) }

    // Resize callbacks and weather/configuration updates must not publish over
    // one another with dimensions captured by an older concurrent render.
    private suspend fun renderOneLocked(c: Context, id: Int) {
        val instance = instance(c, id) ?: return
        val config = WidgetStore.config(c, id, instance.style)
        val data = snapshot(c, config)
        val manager = AppWidgetManager.getInstance(c)
        val options = manager.getAppWidgetOptions(id)
        val sky = SettingsRepository.livingSky.first()
        val level = SettingsRepository.ambience.first()
        val now = System.currentTimeMillis()
        fun views(size: SizeF) = WidgetBinder.bind(c, id, instance, config, data,
            size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1), now = now,
            livingSkyEnabled = sky, ambienceLevel = level)
        val sizes = widgetExactSizes(options)
        val remote = if (Build.VERSION.SDK_INT >= 31 && sizes.isNotEmpty()) RemoteViews(sizes.associateWith(::views))
        else {
            val (landscape, portrait) = widgetLegacySizes(options, instance.kind)
            RemoteViews(views(landscape), views(portrait))
        }
        manager.updateAppWidget(id, remote)
    }
    suspend fun views(c: Context, id: Int, style: WidgetStyle, kind: WidgetKind, config: WidgetConfig, data: WidgetSnapshot?): RemoteViews {
        val sky = SettingsRepository.livingSky.first()
        val level = SettingsRepository.ambience.first()
        val now = System.currentTimeMillis()
        fun bind(size: SizeF) = WidgetBinder.bind(c, id, WidgetInstance(id, style, kind), config, data,
            size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1), now = now, interactive = false,
            livingSkyEnabled = sky, ambienceLevel = level)
        val reference = SizeF(kind.width.toFloat(), kind.height.toFloat())
        if (Build.VERSION.SDK_INT < 31) return bind(reference)
        // Pin dialogs choose their own grid bounds before an ID/options bundle exists.
        // A single reference-width layout can therefore clip text in a narrower host.
        val provider = AppWidgetManager.getInstance(c).getInstalledProvidersForPackage(c.packageName, null)
            .orEmpty().firstOrNull { it.provider == ComponentName(c, providerFor(style, kind)) }
        val density = c.resources.displayMetrics.density
        val minWidth = provider?.minWidth?.takeIf { it > 0 }?.div(density) ?: reference.width
        val minHeight = provider?.minHeight?.takeIf { it > 0 }?.div(density) ?: reference.height
        val widths = listOf(minWidth, reference.width).distinct()
        val heights = listOf(minHeight, reference.height).distinct()
        return RemoteViews(widths.flatMap { width -> heights.map { height -> SizeF(width, height) } }
            .associateWith(::bind))
    }
}
object WidgetRefresh {
    const val ACTION_REFRESH = "com.zhisheng.weather.widget.REFRESH"
    private const val PERIODIC = "widget-beta9-refresh"
    internal const val SKY_PERIODIC = "widget-living-sky-render"
    fun scheduleSky(c: Context, enabled: Boolean, level: AmbienceLevel) {
        val work = WorkManager.getInstance(c)
        if (!enabled || level == AmbienceLevel.OFF || WidgetRuntime.instances(c).none { it.style == WidgetStyle.VISTA }) {
            work.cancelUniqueWork(SKY_PERIODIC)
            return
        }
        // Local-only: dawn/dusk colours still advance when the weather service is offline.
        // WorkManager retains the OS's battery limits; there are no exact alarms or minute wakeups.
        work.enqueueUniquePeriodicWork(SKY_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetSkyWorker>(30, TimeUnit.MINUTES).build())
    }
    fun active(c: Context) = WidgetRuntime.instances(c).isNotEmpty() || WidgetStore.notification(c).let { it.enabled || it.alertsEnabled }
    fun schedule(c: Context) {
        val work = WorkManager.getInstance(c)
        WidgetRuntime.scope.launch { scheduleSky(c, SettingsRepository.livingSky.first(), SettingsRepository.ambience.first()) }
        if (!active(c)) { work.cancelUniqueWork(PERIODIC); work.cancelUniqueWork("widget-beta9-now"); return }
        work.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetRefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
    fun request(c: Context, force: Boolean = false) {
        if (!active(c)) return
        WorkManager.getInstance(c).enqueueUniqueWork("widget-beta9-now", if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<WidgetRefreshWorker>().setInputData(workDataOf("force" to force))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}
class WidgetSkyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext
        if (!SettingsRepository.livingSky.first() || SettingsRepository.ambience.first() == AmbienceLevel.OFF ||
            WidgetRuntime.instances(c).none { it.style == WidgetStyle.VISTA }) {
            WorkManager.getInstance(c).cancelUniqueWork(WidgetRefresh.SKY_PERIODIC)
            return Result.success()
        }
        WidgetRuntime.renderAll(c)
        return Result.success()
    }
}
class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = lock.withLock {
        val ctx = applicationContext
        val configs = WidgetRuntime.instances(ctx).map { WidgetStore.config(ctx, it.id, it.style) }.toMutableList()
        WidgetStore.notification(ctx).takeIf { it.enabled || it.alertsEnabled }?.let { configs += it.config }
        val pairs = configs.mapNotNull { cfg -> WidgetRuntime.city(ctx, cfg)?.let { it to cfg.intervalMinutes } }
        val source = SettingsRepository.sourcePref.first()
        var failed = false
        pairs.groupBy { it.first.locationKey }.values.forEach { group ->
            val city = group.first().first
            val interval = group.minOf { it.second }
            val existing = WidgetStore.snapshot(ctx, city.locationKey)?.weather?.takeIf { source.matches(it) }
                ?: WeatherCache.load(ctx, city.locationKey)?.data?.takeIf { source.matches(it) }
            val age = System.currentTimeMillis() - (existing?.fetchedAt ?: 0L)
            if (age in 0..10_000 || (!inputData.getBoolean("force", false) && age in 0 until interval * 60_000L)) return@forEach
            try {
                val data = withTimeout(25_000) { WeatherRepository.fetchWeather(city, source) }
                if (data.current != null && data.error == null) {
                    WeatherCache.save(ctx, city.locationKey, data); WidgetStore.publish(ctx, city, data)
                } else failed = true
            } catch (e: TimeoutCancellationException) { failed = true }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed = true }
        }
        WidgetRuntime.renderAll(ctx); WidgetNotification.refresh(ctx)
        if (failed && runAttemptCount < 2) Result.retry() else Result.success()
    }
    companion object { private val lock = Mutex() }
}
