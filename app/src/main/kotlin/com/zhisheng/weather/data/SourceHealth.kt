package com.zhisheng.weather.data

// 数据源熔断（v0.0.4）：会话级健康状态，只服务于 AUTO 降级链。
// 某源连续 2 次失败（网络错误/超时/4xx 拒绝）后标记 down 5 分钟，
// AUTO 链在此期间直接跳过该源——避免源被限流后每次刷新白等两轮超时。
// 手动锁定的源不受熔断影响：用户主动选择就该看到那个源的真实状态。
object SourceHealth {

    private const val FAILURE_THRESHOLD = 2
    private const val COOLDOWN_MS = 5 * 60_000L

    const val QWEATHER = "qweather"
    const val XIAOMI = "xiaomi"
    const val NMC = "nmc"
    const val OPEN_METEO = "openmeteo"
    const val CAIYUN = "caiyun"

    private val failures = mutableMapOf<String, Int>()
    private val downUntil = mutableMapOf<String, Long>()

    // 时间源可注入（测试用），resetForTest 恢复
    internal var nowProvider: () -> Long = System::currentTimeMillis

    internal fun resetForTest() {
        failures.clear()
        downUntil.clear()
        nowProvider = System::currentTimeMillis
    }

    @Synchronized
    fun isDown(source: String): Boolean {
        val until = downUntil[source] ?: return false
        return nowProvider() < until
    }

    @Synchronized
    fun recordFailure(source: String) {
        val n = (failures[source] ?: 0) + 1
        failures[source] = n
        if (n >= FAILURE_THRESHOLD) {
            downUntil[source] = nowProvider() + COOLDOWN_MS
            failures[source] = 0 // 冷却期结束后重新累计
            android.util.Log.w("ZhishengWeather", "数据源 $source 连续失败，AUTO 链熔断 5 分钟")
        }
    }

    @Synchronized
    fun recordSuccess(source: String) {
        failures[source] = 0
        downUntil.remove(source)
    }
}
