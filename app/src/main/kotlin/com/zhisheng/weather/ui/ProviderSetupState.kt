package com.zhisheng.weather.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhisheng.weather.data.ProviderConnectionResult
import com.zhisheng.weather.data.ProviderConnectionTester
import com.zhisheng.weather.data.ProviderTestStage
import com.zhisheng.weather.data.QwGeneratedKeys
import com.zhisheng.weather.data.QwKeygen
import com.zhisheng.weather.data.QwRuntimeCreds
import com.zhisheng.weather.data.SecretStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

enum class ProviderWizardKind { QWEATHER, CAIYUN, AMAP }

enum class QweatherAuthMode { JWT, API_KEY }

enum class ProviderSetupStatus { IDLE, TESTING, ERROR, SUCCESS }

internal enum class ProviderBackAction { IGNORE, PREVIOUS, CLOSE }

internal object ProviderField {
    const val HOST = "host"
    const val PROJECT_ID = "project_id"
    const val KID = "kid"
    const val API_KEY = "api_key"
    const val CAIYUN_TOKEN = "caiyun_token"
    const val AMAP_KEY = "amap_key"
}

data class ProviderSetupUiState(
    val kind: ProviderWizardKind,
    val step: Int = 0,
    val host: String = "",
    val projectId: String = "",
    val kid: String = "",
    val apiKey: String = "",
    val caiyunToken: String = "",
    val amapKey: String = "",
    val authMode: QweatherAuthMode = QweatherAuthMode.API_KEY,
    val keys: QwGeneratedKeys? = null,
    val status: ProviderSetupStatus = ProviderSetupStatus.IDLE,
    val fieldErrors: Map<String, String> = emptyMap(),
    val result: ProviderConnectionResult? = null,
    val activeStage: ProviderTestStage? = null,
    val completedStages: Set<ProviderTestStage> = emptySet(),
) {
    val lastStep: Int get() = when (kind) {
        ProviderWizardKind.QWEATHER -> 5
        ProviderWizardKind.CAIYUN -> 4
        ProviderWizardKind.AMAP -> 3
    }
    val testing: Boolean get() = status == ProviderSetupStatus.TESTING
}

internal fun providerBackAction(state: ProviderSetupUiState): ProviderBackAction = when {
    state.testing -> ProviderBackAction.IGNORE
    state.step > 0 && state.status != ProviderSetupStatus.SUCCESS -> ProviderBackAction.PREVIOUS
    else -> ProviderBackAction.CLOSE
}

internal interface ProviderSetupGateway {
    suspend fun testQweather(
        candidate: QwRuntimeCreds,
        onStage: (ProviderTestStage) -> Unit,
    ): ProviderConnectionResult

    suspend fun testCaiyun(
        token: String,
        onStage: (ProviderTestStage) -> Unit,
    ): ProviderConnectionResult

    suspend fun testAmap(
        key: String,
        onStage: (ProviderTestStage) -> Unit,
    ): ProviderConnectionResult

    suspend fun saveQweather(candidate: QwRuntimeCreds)
    suspend fun saveCaiyun(token: String)
    suspend fun saveAmap(key: String)
}

private object RealProviderSetupGateway : ProviderSetupGateway {
    override suspend fun testQweather(
        candidate: QwRuntimeCreds,
        onStage: (ProviderTestStage) -> Unit,
    ) = ProviderConnectionTester.testQweather(candidate, onStage)

    override suspend fun testCaiyun(
        token: String,
        onStage: (ProviderTestStage) -> Unit,
    ) = ProviderConnectionTester.testCaiyun(token, onStage)

    override suspend fun testAmap(
        key: String,
        onStage: (ProviderTestStage) -> Unit,
    ) = ProviderConnectionTester.testAmap(key, onStage)

    override suspend fun saveQweather(candidate: QwRuntimeCreds) = SecretStore.saveQw(candidate)
    override suspend fun saveCaiyun(token: String) = SecretStore.saveCaiyun(token)
    override suspend fun saveAmap(key: String) = SecretStore.saveAmap(key)
}

internal suspend fun <T> verifyThenPersist(
    candidate: T,
    verify: suspend (T) -> ProviderConnectionResult,
    persist: suspend (T, ProviderConnectionResult) -> Unit,
): ProviderConnectionResult {
    val verified = verify(candidate)
    if (!verified.ok) return verified
    return try {
        persist(candidate, verified)
        verified.copy(detail = "${verified.detail}；凭据已写入本机私密存储")
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        ProviderConnectionResult(
            ok = false,
            title = "连接有效，但保存失败",
            detail = "原配置未被替换，请重试或检查本机存储状态",
        )
    }
}

internal fun validateProviderCandidate(state: ProviderSetupUiState): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    when (state.kind) {
        ProviderWizardKind.QWEATHER -> {
            val host = ProviderConnectionTester.normalizeQweatherHost(state.host)
            if (!host.ok) errors[ProviderField.HOST] = host.error.orEmpty()
            errors.putAll(validateQweatherCredentialStep(state))
        }
        ProviderWizardKind.CAIYUN -> {
            if (state.caiyunToken.isBlank()) {
                errors[ProviderField.CAIYUN_TOKEN] = "请粘贴开放平台生成的 Token"
            }
        }
        ProviderWizardKind.AMAP -> {
            if (state.amapKey.isBlank()) {
                errors[ProviderField.AMAP_KEY] = "请粘贴应用中的 Web 服务 API Key"
            }
        }
    }
    return errors
}

internal fun validateQweatherCredentialStep(state: ProviderSetupUiState): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    if (state.authMode == QweatherAuthMode.JWT) {
        if (state.projectId.isBlank()) errors[ProviderField.PROJECT_ID] = "项目 ID 还没填。回到项目详情页复制后粘贴。"
        if (state.kid.isBlank()) errors[ProviderField.KID] = "凭据 ID 还没填。打开刚创建的 JWT 凭据后复制。"
        if (state.keys == null) errors[ProviderField.KID] = "密钥尚未生成。先生成密钥，再把公钥添加到和风控制台。"
    } else if (state.apiKey.isBlank()) {
        errors[ProviderField.API_KEY] = "API KEY 还没粘贴。打开项目详情中的凭据，复制完整 Key。"
    }
    return errors
}

class ProviderSetupViewModel internal constructor(
    kind: ProviderWizardKind,
    private val gateway: ProviderSetupGateway = RealProviderSetupGateway,
) : ViewModel() {

    private var verificationJob: Job? = null

    var state by mutableStateOf(ProviderSetupUiState(kind = kind))
        private set

    fun reset(kind: ProviderWizardKind = state.kind) {
        verificationJob?.cancel()
        verificationJob = null
        state = ProviderSetupUiState(kind = kind)
    }

    fun cancelVerification() {
        verificationJob?.cancel()
        verificationJob = null
        // 0.0.9-debug 修复：Activity 重建等路径销毁弹窗时调用此方法。
        // TESTING 状态若不落回 IDLE，重开向导后按钮会永远停在「验证中」死锁。
        if (state.testing) {
            state = state.copy(
                status = ProviderSetupStatus.IDLE,
                result = null,
                activeStage = null,
                completedStages = emptySet(),
            )
        }
    }

    fun previous() {
        if (state.testing || state.step == 0) return
        state = state.copy(
            step = state.step - 1,
            status = ProviderSetupStatus.IDLE,
            result = null,
            fieldErrors = emptyMap(),
            activeStage = null,
            completedStages = emptySet(),
        )
    }

    fun next() {
        if (state.testing || state.step >= state.lastStep) return
        if (state.kind == ProviderWizardKind.QWEATHER && state.step == 2) {
            val errors = validateQweatherCredentialStep(state)
            if (errors.isNotEmpty()) {
                state = state.copy(
                    fieldErrors = errors,
                    status = ProviderSetupStatus.ERROR,
                    result = ProviderConnectionResult(
                        ok = false,
                        title = "凭据还没有准备好",
                        detail = "按当前页面的四步路径创建凭据，再把值粘贴到输入框。",
                    ),
                )
                return
            }
        }
        if (state.kind == ProviderWizardKind.QWEATHER && state.step == 3) {
            val host = ProviderConnectionTester.normalizeQweatherHost(state.host)
            if (!host.ok) {
                state = state.copy(fieldErrors = mapOf(ProviderField.HOST to host.error.orEmpty()))
                return
            }
            state = state.copy(host = host.value.orEmpty())
        }
        state = state.copy(
            step = state.step + 1,
            status = ProviderSetupStatus.IDLE,
            result = null,
            fieldErrors = emptyMap(),
            activeStage = null,
            completedStages = emptySet(),
        )
    }

    fun setHost(value: String) = edit(ProviderField.HOST) { copy(host = value) }
    fun setProjectId(value: String) = edit(ProviderField.PROJECT_ID) { copy(projectId = value) }
    fun setKid(value: String) = edit(ProviderField.KID) { copy(kid = value) }
    fun setApiKey(value: String) = edit(ProviderField.API_KEY) { copy(apiKey = value) }
    fun setCaiyunToken(value: String) = edit(ProviderField.CAIYUN_TOKEN) { copy(caiyunToken = value) }
    fun setAmapKey(value: String) = edit(ProviderField.AMAP_KEY) { copy(amapKey = value) }

    fun setAuthMode(mode: QweatherAuthMode) {
        if (state.testing) return
        state = state.copy(
            authMode = mode,
            status = ProviderSetupStatus.IDLE,
            result = null,
            fieldErrors = emptyMap(),
            activeStage = null,
            completedStages = emptySet(),
        )
        if (mode == QweatherAuthMode.JWT) ensureKeys()
    }

    fun regenerateKeys() {
        if (state.testing) return
        state = state.copy(
            keys = QwKeygen.generate(),
            status = ProviderSetupStatus.IDLE,
            result = null,
        )
    }

    fun testAndSave() {
        if (state.testing) return
        val errors = validateProviderCandidate(state)
        if (errors.isNotEmpty()) {
            state = state.copy(
                fieldErrors = errors,
                status = ProviderSetupStatus.ERROR,
                result = ProviderConnectionResult(
                    ok = false,
                    title = "参数还不完整",
                    detail = "按字段提示修正后，再重新验证",
                ),
            )
            return
        }

        state = state.copy(
            status = ProviderSetupStatus.TESTING,
            fieldErrors = emptyMap(),
            result = null,
            activeStage = null,
            completedStages = emptySet(),
        )
        verificationJob = viewModelScope.launch {
            val result = when (state.kind) {
                ProviderWizardKind.QWEATHER -> {
                    val candidate = if (state.authMode == QweatherAuthMode.JWT) {
                        QwRuntimeCreds(
                            host = state.host,
                            projectId = state.projectId,
                            kid = state.kid,
                            privateKey = state.keys?.privateDerB64.orEmpty(),
                        )
                    } else {
                        QwRuntimeCreds(host = state.host, apiKey = state.apiKey)
                    }
                    verifyThenPersist(
                        candidate = candidate,
                        verify = { gateway.testQweather(it, ::enterStage) },
                        persist = { verifiedCandidate, verified ->
                            gateway.saveQweather(
                                verifiedCandidate.copy(host = verified.normalizedHost ?: verifiedCandidate.host),
                            )
                        },
                    )
                }
                ProviderWizardKind.CAIYUN -> verifyThenPersist(
                    candidate = state.caiyunToken.trim(),
                    verify = { gateway.testCaiyun(it, ::enterStage) },
                    persist = { token, _ -> gateway.saveCaiyun(token) },
                )
                ProviderWizardKind.AMAP -> verifyThenPersist(
                    candidate = state.amapKey.trim(),
                    verify = { gateway.testAmap(it, ::enterStage) },
                    persist = { key, _ -> gateway.saveAmap(key) },
                )
            }
            val allCompleted = if (result.ok && state.activeStage != null) {
                state.completedStages + state.activeStage!!
            } else {
                state.completedStages
            }
            state = state.copy(
                step = if (result.ok) state.lastStep else state.step,
                status = if (result.ok) ProviderSetupStatus.SUCCESS else ProviderSetupStatus.ERROR,
                result = result,
                completedStages = allCompleted,
            )
            verificationJob = null
        }
    }

    private fun ensureKeys() {
        if (state.keys == null) state = state.copy(keys = QwKeygen.generate())
    }

    private fun enterStage(stage: ProviderTestStage) {
        val previous = state.activeStage
        state = state.copy(
            activeStage = stage,
            completedStages = if (previous == null) state.completedStages else state.completedStages + previous,
        )
    }

    private inline fun edit(field: String, block: ProviderSetupUiState.() -> ProviderSetupUiState) {
        if (state.testing) return
        state = state.block().copy(
            fieldErrors = state.fieldErrors - field,
            status = ProviderSetupStatus.IDLE,
            result = null,
            activeStage = null,
            completedStages = emptySet(),
        )
    }
}
