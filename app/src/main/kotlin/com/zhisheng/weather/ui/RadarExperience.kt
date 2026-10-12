package com.zhisheng.weather.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zhisheng.weather.model.City
import com.zhisheng.weather.model.WeatherData
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun RadarScreen(city: City?, utcOffsetSeconds: Int?, onBack: () -> Unit, weather: WeatherData? = null) {
    // Domestic users can read the local forecast without accepting or loading an overseas map.
    var tab by rememberSaveable { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize().zhishengScreen().statusBarsPadding().navigationBarsPadding()) {
        WeatherFeatureNavigation("降水地图", city?.displayName, onBack, listOf("附近回波", "未来2小时"), tab) { tab = it }
        Box(Modifier.weight(1f)) {
            if (tab == 0) RadarMapScreen(city, utcOffsetSeconds, onBack)
            else {
                var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
                val lifecycle = LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(lifecycle) {
                    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        while (true) { now = System.currentTimeMillis(); delay(30_000) }
                    }
                }
                RadarFutureContent(radarPointForecast(weather, now), now, utcOffsetSeconds)
            }
        }
    }
}

@Composable
internal fun RadarFutureContent(forecast: RadarPointForecast, now: Long, utcOffsetSeconds: Int?) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.fillMaxWidth().zhishengPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("这里接下来会下雨吗", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleLarge, color = ZhishengText)
                if (forecast.points.isEmpty()) {
                    Text(if (forecast.expired) "降水预报已过期" else "当前天气源没有分钟级降水预报",
                        Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium, color = ZhishengTextSecondary)
                    Text("回主页刷新，或选择支持分钟降水的已接入来源。也可以打开国内官方降水地图。",
                        Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                } else {
                    Text("${forecast.source} · ${forecast.intervalMinutes}分钟间隔", Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                    Text("已有预报至 ${Fmt.clock(forecast.endTime!!, utcOffsetSeconds)}",
                        Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                    RadarForecastPlot(forecast, now)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("现在", style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                        Text("+1小时", style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                        Text("+2小时", style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
                    }
                    Text("线段代表已有预报，空白处暂无数据。", Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                    forecast.publicationTime?.let {
                        Text("更新于 ${Fmt.clock(it, utcOffsetSeconds)}", Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().zhishengPanel().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("看全国降水走向", Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = ZhishengText)
                Text("中国天气网提供未来两小时降水外推图，在浏览器中查看。", Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
                OfficialFuturePrecipLink()
            }
        }
        item {
            WeatherSourceDisclosure("本地点预报与地图", "本页曲线使用当前地点已有的分钟降水数据，不额外请求个人天气服务。分钟预报是本地点的降水趋势；地图展示区域回波，只有来源真实提供的外推帧才会标为未来。精确定位不代表天气模型有街道级分辨率。")
        }
    }
}

@Composable
private fun RadarForecastPlot(forecast: RadarPointForecast, now: Long) {
    val palette = LocalZhishengPalette.current
    val actualPeak = forecast.points.maxOf { it.precip }
    val peak = actualPeak.coerceAtLeast(1f)
    val description = "本地点降水强度，已有${forecast.points.size}个预报点，最高${String.format(Locale.CHINA, "%.1f", actualPeak)}毫米每小时"
    Text("降水强度 · mm/h", Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = ZhishengTextSecondary)
    Text("本段最高 ${String.format(Locale.CHINA, "%.1f", actualPeak)} mm/h", Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall, color = ZhishengTextSecondary)
    Canvas(Modifier.fillMaxWidth().height(144.dp).semantics { contentDescription = description }) {
        val bottom = size.height - 6.dp.toPx()
        val top = 6.dp.toPx()
        fun x(time: Long) = ((time - now).toFloat() / 7_200_000f * size.width).coerceIn(0f, size.width)
        fun y(value: Float) = bottom - value / peak * (bottom - top)
        (0..2).forEach { i ->
            val xx = size.width * i / 2f
            drawLine(palette.cardBorder, Offset(xx, top), Offset(xx, bottom), 1.dp.toPx())
        }
        drawLine(palette.cardBorder, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())
        forecast.points.forEach { point ->
            val start = x(point.timeMillis)
            val end = x(point.timeMillis + forecast.intervalMinutes * 60_000L)
            // Draw each native bucket independently: gaps must remain empty even when both ends are dry.
            drawLine(palette.cyan, Offset(start, y(point.precip)), Offset(end, y(point.precip)), 3.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
internal fun OfficialFuturePrecipLink() {
    val context = LocalContext.current
    TextButton(onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.weather.com.cn/weatherMap/index.html"))) }
            .onFailure { android.widget.Toast.makeText(context, "没有可打开网页的浏览器", android.widget.Toast.LENGTH_SHORT).show() }
    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("打开国内官方降水地图 ↗", Modifier.fillMaxWidth(), color = ZhishengText,
            style = MaterialTheme.typography.labelLarge)
    }
}
