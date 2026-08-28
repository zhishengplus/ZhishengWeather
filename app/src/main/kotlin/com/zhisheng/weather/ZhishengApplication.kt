package com.zhisheng.weather

import android.app.Application
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.HistoricalWeatherRepository
import com.zhisheng.weather.data.RadarRepository
import com.zhisheng.weather.data.SecretStore
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.widget.WidgetSyncWorker
import org.maplibre.android.MapLibre

class ZhishengApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MapLibre.getInstance(this)
        SecretStore.init(this)
        SettingsRepository.init(this)
        CityRepository.init(this)
        HistoricalWeatherRepository.init(this)
        RadarRepository.init(this)
        // 小组件后台刷新周期任务（v0.0.4）：KEEP 策略保证不因每次启动重置周期
        WidgetSyncWorker.schedule(this)
    }
}
