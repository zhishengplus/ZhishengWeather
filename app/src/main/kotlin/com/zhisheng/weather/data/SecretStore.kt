package com.zhisheng.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

data class RainviewerRuntimeCreds(val apiKey: String = "") {
    val ready: Boolean get() = apiKey.isNotBlank()
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
    private val KEY_RAINVIEWER = stringPreferencesKey("rainviewer_api_key")

    @Volatile var qwRuntime: QwRuntimeCreds = QwRuntimeCreds()
        private set
    @Volatile var caiyunRuntime: CaiyunRuntimeCreds = CaiyunRuntimeCreds()
        private set
    @Volatile var amapRuntime: AmapRuntimeCreds = AmapRuntimeCreds()
        private set
    @Volatile var rainviewerRuntime: RainviewerRuntimeCreds = RainviewerRuntimeCreds()
        private set

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
                caiyunRuntime = CaiyunRuntimeCreds(prefs[KEY_CAIYUN].orEmpty().trim())
                amapRuntime = AmapRuntimeCreds(prefs[KEY_AMAP].orEmpty().trim())
                rainviewerRuntime = RainviewerRuntimeCreds(prefs[KEY_RAINVIEWER].orEmpty().trim())
            }
        }
    }

    private fun applyQw(creds: QwRuntimeCreds) {
        qwRuntime = creds
        QwAuth.invalidate()
        QWeatherApi.invalidateClient()
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

    val rainviewerRuntimeFlow: Flow<RainviewerRuntimeCreds> by lazy {
        store.data.map { RainviewerRuntimeCreds(it[KEY_RAINVIEWER].orEmpty().trim()) }.distinctUntilChanged()
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
    val rainviewerReady: Boolean get() = rainviewerRuntime.ready

    suspend fun saveQw(creds: QwRuntimeCreds) {
        val next = QwRuntimeCreds(
            host = creds.host.trim(),
            projectId = creds.projectId.trim(),
            kid = creds.kid.trim(),
            privateKey = creds.privateKey.trim(),
            apiKey = creds.apiKey.trim(),
        )
        store.edit {
            it[KEY_QW_HOST] = next.host
            it[KEY_QW_PROJECT] = next.projectId
            it[KEY_QW_KID] = next.kid
            it[KEY_QW_PRIV] = next.privateKey
            it[KEY_QW_API] = next.apiKey
        }
        applyQw(next)
    }

    suspend fun saveCaiyun(token: String) {
        val next = CaiyunRuntimeCreds(token.trim())
        store.edit { it[KEY_CAIYUN] = next.token }
        caiyunRuntime = next
    }

    suspend fun saveAmap(webServiceKey: String) {
        val next = AmapRuntimeCreds(webServiceKey.trim())
        store.edit { it[KEY_AMAP] = next.webServiceKey }
        amapRuntime = next
    }

    suspend fun saveRainviewer(apiKey: String) {
        val next = RainviewerRuntimeCreds(apiKey.trim())
        store.edit { it[KEY_RAINVIEWER] = next.apiKey }
        rainviewerRuntime = next
    }

    suspend fun clearQw() {
        store.edit {
            it.remove(KEY_QW_HOST)
            it.remove(KEY_QW_PROJECT)
            it.remove(KEY_QW_KID)
            it.remove(KEY_QW_PRIV)
            it.remove(KEY_QW_API)
        }
        applyQw(QwRuntimeCreds())
    }

    suspend fun clearCaiyun() {
        store.edit { it.remove(KEY_CAIYUN) }
        caiyunRuntime = CaiyunRuntimeCreds()
    }

    suspend fun clearAmap() {
        store.edit { it.remove(KEY_AMAP) }
        amapRuntime = AmapRuntimeCreds()
    }

    suspend fun clearRainviewer() {
        store.edit { it.remove(KEY_RAINVIEWER) }
        rainviewerRuntime = RainviewerRuntimeCreds()
    }

    suspend fun currentQw(): QwRuntimeCreds = qwRuntimeFlow.first().also { loaded ->
        // DataStore 的首帧在 IO 协程异步到达。天气页可能先于 init() 中的 collector 发起抓取，
        // 此时必须把磁盘凭据同步进运行态，否则冷启动会误报“未配置”，手动刷新后才恢复。
        if (loaded != qwRuntime) applyQw(loaded)
    }

    suspend fun currentCaiyun(): CaiyunRuntimeCreds = caiyunRuntimeFlow.first().also { loaded ->
        if (loaded != caiyunRuntime) caiyunRuntime = loaded
    }

    suspend fun currentAmap(): AmapRuntimeCreds = amapRuntimeFlow.first().also { loaded ->
        if (loaded != amapRuntime) amapRuntime = loaded
    }

    suspend fun currentRainviewer(): RainviewerRuntimeCreds = rainviewerRuntimeFlow.first().also { loaded ->
        if (loaded != rainviewerRuntime) rainviewerRuntime = loaded
    }
}
