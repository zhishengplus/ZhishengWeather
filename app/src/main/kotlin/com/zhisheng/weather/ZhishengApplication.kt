package com.zhisheng.weather

import android.app.Application
import kotlinx.coroutines.launch
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.HistoricalWeatherRepository
import com.zhisheng.weather.data.KnownPlaceStore
import com.zhisheng.weather.data.RadarRepository
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import android.content.ComponentCallbacks2
import com.zhisheng.weather.widget.WidgetIcons
import com.zhisheng.weather.widget.WidgetSkyBitmap

class ZhishengApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Compose controls app palettes. Keep platform resources on the system
        // configuration, including widgets and notification RemoteViews.
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            getSystemService(android.app.UiModeManager::class.java)
                ?.setApplicationNightMode(android.app.UiModeManager.MODE_NIGHT_AUTO)
        }
        SecretStore.init(this)
        SettingsRepository.init(this)
        com.zhisheng.weather.data.AutomationSettingsRepository.init(this)
        com.zhisheng.weather.data.SceneWeatherRepository.init(this)
        CityRepository.init(this)
        HistoricalWeatherRepository.init(this)
        RadarRepository.init(this)
        KnownPlaceStore.init(this)
        com.zhisheng.weather.data.WeatherComCnApi.init(this)
        com.zhisheng.weather.data.FusionScoreboard.init(this)
        com.zhisheng.weather.widget.WidgetRuntime.start(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Android 17 enforces per-process memory limits. These are rebuildable
        // images only; keep city weather snapshots so paging stays populated.
        if (level == ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            WidgetIcons.clearMemoryCache()
            WidgetSkyBitmap.clearMemoryCache()
        }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        com.zhisheng.weather.widget.WidgetRuntime.scope.launch {
            com.zhisheng.weather.widget.WidgetRuntime.renderAll(this@ZhishengApplication)
            com.zhisheng.weather.widget.WidgetNotification.refresh(this@ZhishengApplication)
        }
    }
}
