package com.zhisheng.weather.widget

import android.content.Context
import android.os.SystemClock
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.data.SettingsRepository
import com.zhisheng.weather.data.WeatherRepository
import com.zhisheng.weather.ui.WidgetSnapshotBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit

// 小组件后台刷新（v0.0.4）：周期性拉取当前选中城市，更新 WidgetCache 并刷新桌面。
// 此前小组件数据完全依赖「打开 App」才会更新——几天不开 App 桌面就停留在旧天气。
// 走主 App 同一条数据链路：默认小米/Open-Meteo；和风仅开发者锁定时才会打。
class WidgetSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val feedbackStartedAt = SystemClock.elapsedRealtime()
        return try {
            val city = CityRepository.selectedCity.first() ?: return Result.success()
            val pref = SettingsRepository.sourcePref.first()
            val data = try {
                withTimeout(FETCH_TIMEOUT_MS) {
                    WeatherRepository.fetchWeather(city, pref)
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                null
            }
            if (data?.current != null) {
                WidgetSnapshotBuilder.save(applicationContext, city, data)
                android.util.Log.i(TAG, "后台刷新成功 ${city.name} 源=${data.dataSource}")
                Result.success()
            } else {
                android.util.Log.w(TAG, "后台刷新失败 ${city.name}，稍后重试")
                Result.retry()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            android.util.Log.w(TAG, "后台刷新异常", e)
            Result.retry()
        } finally {
            // 给桌面宿主留出绘制“…”与“正在刷新…”的时间，避免快速返回时用户看不到反馈。
            val remainingFeedback = MIN_REFRESH_FEEDBACK_MS -
                (SystemClock.elapsedRealtime() - feedbackStartedAt)
            if (remainingFeedback > 0) delay(remainingFeedback)
            // 无论成功、失败或无选中城市，都恢复按钮与更新时间。
            ZhishengWidgetProvider.refreshAll(applicationContext)
        }
    }

    companion object {
        private const val TAG = "ZhishengWidgetSync"
        private const val FETCH_TIMEOUT_MS = 20_000L
        private const val MIN_REFRESH_FEEDBACK_MS = 1_200L
        private const val UNIQUE_NAME = "zhisheng_widget_sync"
        private const val UNIQUE_REFRESH_NAME = "zhisheng_widget_refresh_now"

        // 每小时拉一次（系统下限 15 分钟；配合 widget_info 的 30 分钟重绘，
        // 桌面最多约 1 小时滞后，远好于此前「永远停在最后一次开 App」）。
        // KEEP：已存在任务则保留原计划，不因每次启动重置周期。
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        // 用户从桌面点“刷新”时立即走主 App 的同一数据链路；KEEP 防止连点产生并发请求。
        fun refreshNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<WidgetSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_REFRESH_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
