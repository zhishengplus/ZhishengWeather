package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.zhisheng.weather.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File

data class QwRuntimeCreds(
    val host: String = "",
    val projectId: String = "",
    val kid: String = "",
    val privateKey: String = "",
    val apiKey: String = "",
) {
    val jwtReady: Boolean
        get() = host.isNotBlank() && projectId.isNotBlank() && kid.isNotBlank() && privateKey.isNotBlank()
    val keyReady: Boolean
        get() = host.isNotBlank() && apiKey.isNotBlank()
    val ready: Boolean get() = jwtReady || keyReady
}

data class CaiyunRuntimeCreds(val token: String = "") {
    val ready: Boolean get() = token.isNotBlank()
}

data class AmapRuntimeCreds(val webServiceKey: String = "") {
    val ready: Boolean get() = webServiceKey.isNotBlank()
}

data class BaiduRuntimeCreds(val webServiceAk: String = "") {
    val ready: Boolean get() = webServiceAk.isNotBlank()
}

data class RainviewerRuntimeCreds(val apiKey: String = "") {
    val ready: Boolean get() = apiKey.isNotBlank()
}

internal data class SharedPlaceConfiguration(val consentVersion: Int = 0,
    val installationId: String = "", val installationToken: String = "") {
    val enabled: Boolean get() = consentVersion == 2 && installationId.isNotBlank() && installationToken.isNotBlank()
    override fun toString() = "SharedPlaceConfiguration(redacted)"
}

data class QwResolved(
    val host: String,
    val projectId: String,
    val kid: String,
    val privateKey: String,
    val apiKey: String,
) {
    val jwtReady: Boolean
        get() = host.isNotBlank() && projectId.isNotBlank() && kid.isNotBlank() && privateKey.isNotBlank()
    val keyReady: Boolean get() = host.isNotBlank() && apiKey.isNotBlank()
    val ready: Boolean get() = jwtReady || keyReady
}

// 运行时凭据：只存本机 secrets DataStore，不进 APK、不打日志。
object SecretStore {

    private lateinit var store: DataStore<Preferences>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val KEY_QW_HOST = stringPreferencesKey("qw_host")
    private val KEY_QW_PROJECT = stringPreferencesKey("qw_project_id")
    private val KEY_QW_KID = stringPreferencesKey("qw_kid")
    private val KEY_QW_PRIV = stringPreferencesKey("qw_private_key")
    private val KEY_QW_API = stringPreferencesKey("qw_api_key")
    private val KEY_CAIYUN = stringPreferencesKey("caiyun_token")
    private val KEY_AMAP = stringPreferencesKey("amap_web_service_key")
    private val KEY_PLACE_CONSENT = stringPreferencesKey("shared_place_consent")
    private val KEY_PLACE_INSTALLATION = stringPreferencesKey("shared_place_installation")
    private val KEY_PLACE_TOKEN = stringPreferencesKey("shared_place_installation_token")
    private val KEY_BAIDU = stringPreferencesKey("baidu_web_service_ak")
    private val KEY_RAINVIEWER = stringPreferencesKey("rainviewer_api_key")
    private val KEY_SENIVERSE = stringPreferencesKey("seniverse_api_key")
    private val KEY_MOJI_APP = stringPreferencesKey("moji_appcode")
    private val KEY_MOJI_NOW_URL = stringPreferencesKey("moji_current_endpoint")
    private val KEY_MOJI_NOW_TOKEN = stringPreferencesKey("moji_current_token")
    private val KEY_MOJI_DAY_URL = stringPreferencesKey("moji_daily_endpoint")
    private val KEY_MOJI_DAY_TOKEN = stringPreferencesKey("moji_daily_token")

    @Volatile var qwRuntime: QwRuntimeCreds = QwRuntimeCreds()
        private set
    @Volatile var caiyunRuntime: CaiyunRuntimeCreds = CaiyunRuntimeCreds()
        private set
    @Volatile var amapRuntime: AmapRuntimeCreds = AmapRuntimeCreds()
        private set
    @Volatile internal var sharedPlaceRuntime = SharedPlaceConfiguration()
        private set
    @Volatile var baiduRuntime: BaiduRuntimeCreds = BaiduRuntimeCreds()
        private set
    @Volatile var rainviewerRuntime: RainviewerRuntimeCreds = RainviewerRuntimeCreds()
        private set
    @Volatile var seniverseRuntime: SeniverseRuntimeCreds = SeniverseRuntimeCreds()
        private set
    @Volatile var mojiRuntime: MojiRuntimeCreds = MojiRuntimeCreds()
        private set

    private suspend fun editSecretContext(transform: suspend (MutablePreferences) -> Unit,
        applyRuntime: () -> Unit) {
        reminderDeliveryGate.changeContext {
            store.edit(transform)
            applyRuntime()
        }
    }

    private fun <T> updateRuntime(next: T, current: () -> T, apply: (T) -> Unit) {
        reminderDeliveryGate.updateSettings {
            if (current() != next) reminderDeliveryGate.changeContextNow {
                if (next is AmapRuntimeCreds || next is BaiduRuntimeCreds || next is SharedPlaceConfiguration) PlaceConfigurationRevision.changed()
                apply(next)
            }
        }
    }

    fun init(context: Context) {
        val app = context.applicationContext
        // 放 no_backup：系统备份和设备迁移都带不走凭据。
        store = PreferenceDataStoreFactory.create(
            produceFile = {
                val dir = File(app.noBackupFilesDir, "datastore")
                if (!dir.exists()) dir.mkdirs()
                File(dir, "secrets.preferences_pb")
            },
        )
        scope.launch {
            store.data.collect { prefs ->
                applyQw(
                    QwRuntimeCreds(
                        host = prefs[KEY_QW_HOST].orEmpty().trim(),
                        projectId = prefs[KEY_QW_PROJECT].orEmpty().trim(),
                        kid = prefs[KEY_QW_KID].orEmpty().trim(),
                        privateKey = prefs[KEY_QW_PRIV].orEmpty().trim(),
                        apiKey = prefs[KEY_QW_API].orEmpty().trim(),
                    ),
                )
                updateRuntime(CaiyunRuntimeCreds(prefs[KEY_CAIYUN].orEmpty().trim()), { caiyunRuntime }) { caiyunRuntime = it }
                updateRuntime(AmapRuntimeCreds(prefs[KEY_AMAP].orEmpty().trim()), { amapRuntime }) { amapRuntime = it }
                updateRuntime(sharedPlaceFrom(prefs), { sharedPlaceRuntime }) { sharedPlaceRuntime = it }
                updateRuntime(BaiduRuntimeCreds(prefs[KEY_BAIDU].orEmpty().trim()), { baiduRuntime }) { baiduRuntime = it }
                updateRuntime(RainviewerRuntimeCreds(prefs[KEY_RAINVIEWER].orEmpty().trim()), { rainviewerRuntime }) { rainviewerRuntime = it }
                updateRuntime(SeniverseRuntimeCreds(prefs[KEY_SENIVERSE].orEmpty().trim()), { seniverseRuntime }) { seniverseRuntime = it }
                updateRuntime(mojiFrom(prefs), { mojiRuntime }) { mojiRuntime = it }
            }
        }
    }

    private fun applyQw(creds: QwRuntimeCreds) {
        reminderDeliveryGate.updateSettings {
            if (qwRuntime != creds) reminderDeliveryGate.changeContextNow { PlaceConfigurationRevision.changed(); qwRuntime = creds }
            QwAuth.invalidate()
            QWeatherApi.invalidateClient()
        }
    }

    val qwRuntimeFlow: Flow<QwRuntimeCreds> by lazy {
        store.data.map { prefs ->
            QwRuntimeCreds(
                host = prefs[KEY_QW_HOST].orEmpty().trim(),
                projectId = prefs[KEY_QW_PROJECT].orEmpty().trim(),
                kid = prefs[KEY_QW_KID].orEmpty().trim(),
                privateKey = prefs[KEY_QW_PRIV].orEmpty().trim(),
                apiKey = prefs[KEY_QW_API].orEmpty().trim(),
            )
        }.distinctUntilChanged()
    }

    val caiyunRuntimeFlow: Flow<CaiyunRuntimeCreds> by lazy {
        store.data.map { CaiyunRuntimeCreds(it[KEY_CAIYUN].orEmpty().trim()) }.distinctUntilChanged()
    }

    val amapRuntimeFlow: Flow<AmapRuntimeCreds> by lazy {
        store.data.map { AmapRuntimeCreds(it[KEY_AMAP].orEmpty().trim()) }.distinctUntilChanged()
    }

    private fun sharedPlaceFrom(prefs: Preferences) = SharedPlaceConfiguration(
        prefs[KEY_PLACE_CONSENT]?.toIntOrNull() ?: 0, prefs[KEY_PLACE_INSTALLATION].orEmpty(), prefs[KEY_PLACE_TOKEN].orEmpty())
    internal val sharedPlaceFlow: Flow<SharedPlaceConfiguration> by lazy {
        store.data.map(::sharedPlaceFrom).distinctUntilChanged()
    }
    internal suspend fun setSharedPlaceConsent(enabled: Boolean) {
        var next = SharedPlaceConfiguration()
        editSecretContext({ prefs ->
            val old = sharedPlaceFrom(prefs)
            next = if (!enabled) old.copy(consentVersion = 0) else SharedPlaceConfiguration(2,
                old.installationId.ifBlank { java.util.UUID.randomUUID().toString() },
                old.installationToken.ifBlank { ByteArray(32).also(java.security.SecureRandom()::nextBytes)
                    .joinToString("") { "%02x".format(it) } })
            prefs[KEY_PLACE_CONSENT] = next.consentVersion.toString()
            prefs[KEY_PLACE_INSTALLATION] = next.installationId
            prefs[KEY_PLACE_TOKEN] = next.installationToken
        }) { updateRuntime(next, { sharedPlaceRuntime }) { sharedPlaceRuntime = it } }
    }
    internal suspend fun forgetDeletedSharedPlaceIdentity(expected: SharedPlaceConfiguration) {
        var next = SharedPlaceConfiguration()
        editSecretContext({ prefs ->
            val current = sharedPlaceFrom(prefs)
            if (!current.enabled && current.installationId == expected.installationId && current.installationToken == expected.installationToken) {
                prefs.remove(KEY_PLACE_INSTALLATION); prefs.remove(KEY_PLACE_TOKEN); prefs.remove(KEY_PLACE_CONSENT)
            }
            next = sharedPlaceFrom(prefs)
        }) { updateRuntime(next, { sharedPlaceRuntime }) { sharedPlaceRuntime = it } }
    }

    val baiduRuntimeFlow: Flow<BaiduRuntimeCreds> by lazy {
        store.data.map { BaiduRuntimeCreds(it[KEY_BAIDU].orEmpty().trim()) }.distinctUntilChanged()
    }

    val rainviewerRuntimeFlow: Flow<RainviewerRuntimeCreds> by lazy {
        store.data.map { RainviewerRuntimeCreds(it[KEY_RAINVIEWER].orEmpty().trim()) }.distinctUntilChanged()
    }

    val seniverseRuntimeFlow: Flow<SeniverseRuntimeCreds> by lazy {
        store.data.map { SeniverseRuntimeCreds(it[KEY_SENIVERSE].orEmpty().trim()) }.distinctUntilChanged()
    }
    private fun mojiFrom(prefs: Preferences): MojiRuntimeCreds = MojiRuntimeCreds(
        prefs[KEY_MOJI_APP].orEmpty().trim(), prefs[KEY_MOJI_NOW_URL].orEmpty().trim(),
        prefs[KEY_MOJI_NOW_TOKEN].orEmpty().trim(), prefs[KEY_MOJI_DAY_URL].orEmpty().trim(), prefs[KEY_MOJI_DAY_TOKEN].orEmpty().trim())
    val mojiRuntimeFlow: Flow<MojiRuntimeCreds> by lazy { store.data.map(::mojiFrom).distinctUntilChanged() }

    suspend fun saveSeniverse(key: String) {
        val next = SeniverseRuntimeCreds(key.trim())
        editSecretContext({ it[KEY_SENIVERSE] = next.apiKey }) { updateRuntime(next, { seniverseRuntime }) { seniverseRuntime = it } }
    }
    suspend fun clearSeniverse() = saveSeniverse("")
    suspend fun currentSeniverse(): SeniverseRuntimeCreds = seniverseRuntimeFlow.first().also { loaded ->
        updateRuntime(loaded, { seniverseRuntime }) { seniverseRuntime = it }
    }
    suspend fun saveMoji(candidate: MojiRuntimeCreds) {
        val next = normalizedMojiCredentials(candidate)
        editSecretContext({
            it[KEY_MOJI_APP] = next.appCode; it[KEY_MOJI_NOW_URL] = next.currentEndpoint; it[KEY_MOJI_NOW_TOKEN] = next.currentToken
            it[KEY_MOJI_DAY_URL] = next.dailyEndpoint; it[KEY_MOJI_DAY_TOKEN] = next.dailyToken
        }) { updateRuntime(next, { mojiRuntime }) { mojiRuntime = it } }
    }
    suspend fun clearMoji() = saveMoji(MojiRuntimeCreds())
    suspend fun currentMoji(): MojiRuntimeCreds = mojiRuntimeFlow.first().also { loaded ->
        updateRuntime(loaded, { mojiRuntime }) { mojiRuntime = it }
    }

    fun resolvedQw(): QwResolved {
        val rt = qwRuntime
        if (rt.ready) {
            return QwResolved(rt.host, rt.projectId, rt.kid, rt.privateKey, rt.apiKey)
        }
        return QwResolved(
            host = BuildConfig.QW_HOST.trim(),
            projectId = BuildConfig.QW_PROJECT_ID.trim(),
            kid = BuildConfig.QW_KID.trim(),
            privateKey = BuildConfig.QW_PRIVATE_KEY.trim(),
            apiKey = "",
        )
    }

    val caiyunReady: Boolean get() = caiyunRuntime.ready
    val amapReady: Boolean get() = amapRuntime.ready
    val baiduReady: Boolean get() = baiduRuntime.ready
    val rainviewerReady: Boolean get() = rainviewerRuntime.ready

    suspend fun saveQw(creds: QwRuntimeCreds) {
        val next = QwRuntimeCreds(
            host = creds.host.trim(),
            projectId = creds.projectId.trim(),
            kid = creds.kid.trim(),
            privateKey = creds.privateKey.trim(),
            apiKey = creds.apiKey.trim(),
        )
        editSecretContext({
            it[KEY_QW_HOST] = next.host
            it[KEY_QW_PROJECT] = next.projectId
            it[KEY_QW_KID] = next.kid
            it[KEY_QW_PRIV] = next.privateKey
            it[KEY_QW_API] = next.apiKey
        }) { applyQw(next) }
    }

    suspend fun saveCaiyun(token: String) {
        val next = CaiyunRuntimeCreds(token.trim())
        editSecretContext({ it[KEY_CAIYUN] = next.token }) {
            updateRuntime(next, { caiyunRuntime }) { caiyunRuntime = it }
        }
    }

    suspend fun saveAmap(webServiceKey: String) {
        val next = AmapRuntimeCreds(webServiceKey.trim())
        editSecretContext({ it[KEY_AMAP] = next.webServiceKey }) {
            updateRuntime(next, { amapRuntime }) { amapRuntime = it }
        }
    }

    suspend fun saveBaidu(webServiceAk: String) {
        val next = BaiduRuntimeCreds(webServiceAk.trim())
        editSecretContext({ it[KEY_BAIDU] = next.webServiceAk }) {
            updateRuntime(next, { baiduRuntime }) { baiduRuntime = it }
        }
    }

    suspend fun saveRainviewer(apiKey: String) {
        val next = RainviewerRuntimeCreds(apiKey.trim())
        editSecretContext({ it[KEY_RAINVIEWER] = next.apiKey }) {
            updateRuntime(next, { rainviewerRuntime }) { rainviewerRuntime = it }
        }
    }

    suspend fun clearQw() {
        editSecretContext({
            it.remove(KEY_QW_HOST)
            it.remove(KEY_QW_PROJECT)
            it.remove(KEY_QW_KID)
            it.remove(KEY_QW_PRIV)
            it.remove(KEY_QW_API)
        }) { applyQw(QwRuntimeCreds()) }
    }

    suspend fun clearCaiyun() {
        editSecretContext({ it.remove(KEY_CAIYUN) }) {
            updateRuntime(CaiyunRuntimeCreds(), { caiyunRuntime }) { caiyunRuntime = it }
        }
    }

    suspend fun clearAmap() {
        editSecretContext({ it.remove(KEY_AMAP) }) {
            updateRuntime(AmapRuntimeCreds(), { amapRuntime }) { amapRuntime = it }
        }
    }

    suspend fun clearBaidu() {
        editSecretContext({ it.remove(KEY_BAIDU) }) {
            updateRuntime(BaiduRuntimeCreds(), { baiduRuntime }) { baiduRuntime = it }
        }
    }

    suspend fun clearRainviewer() {
        editSecretContext({ it.remove(KEY_RAINVIEWER) }) {
            updateRuntime(RainviewerRuntimeCreds(), { rainviewerRuntime }) { rainviewerRuntime = it }
        }
    }

    suspend fun currentQw(): QwRuntimeCreds = qwRuntimeFlow.first().also { loaded ->
        // DataStore 的首帧在 IO 协程异步到达。天气页可能先于 init() 中的 collector 发起抓取，
        // 此时必须把磁盘凭据同步进运行态，否则冷启动会误报“未配置”，手动刷新后才恢复。
        if (loaded != qwRuntime) applyQw(loaded)
    }

    suspend fun currentCaiyun(): CaiyunRuntimeCreds = caiyunRuntimeFlow.first().also { loaded ->
        updateRuntime(loaded, { caiyunRuntime }) { caiyunRuntime = it }
    }

    suspend fun currentAmap(): AmapRuntimeCreds = amapRuntimeFlow.first().also { loaded ->
        updateRuntime(loaded, { amapRuntime }) { amapRuntime = it }
    }

    suspend fun currentBaidu(): BaiduRuntimeCreds = baiduRuntimeFlow.first().also { loaded ->
        updateRuntime(loaded, { baiduRuntime }) { baiduRuntime = it }
    }

    suspend fun currentRainviewer(): RainviewerRuntimeCreds = rainviewerRuntimeFlow.first().also { loaded ->
        updateRuntime(loaded, { rainviewerRuntime }) { rainviewerRuntime = it }
    }
}
