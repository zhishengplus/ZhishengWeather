package com.zhisheng.weather.ui

import com.zhisheng.weather.data.ProviderConnectionResult
import com.zhisheng.weather.data.ProviderConnectionTester
import com.zhisheng.weather.data.QwGeneratedKeys
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderSetupStateTest {

    @Test
    fun `qweather onboarding defaults to the quick api key route`() {
        val state = ProviderSetupUiState(kind = ProviderWizardKind.QWEATHER)

        assertEquals(QweatherAuthMode.API_KEY, state.authMode)
        assertEquals(5, state.lastStep)
    }

    @Test
    fun `amap onboarding validates web service key locally`() {
        val empty = ProviderSetupUiState(kind = ProviderWizardKind.AMAP)
        val ready = empty.copy(amapKey = "test-web-service-key")

        assertEquals(3, empty.lastStep)
        assertTrue(ProviderField.AMAP_KEY in validateProviderCandidate(empty))
        assertTrue(validateProviderCandidate(ready).isEmpty())
    }

    @Test
    fun `provider back behavior is shared by gesture header and footer`() {
        val firstStep = ProviderSetupUiState(kind = ProviderWizardKind.CAIYUN)
        val laterStep = firstStep.copy(step = 2)
        val testing = laterStep.copy(status = ProviderSetupStatus.TESTING)
        val finished = laterStep.copy(status = ProviderSetupStatus.SUCCESS)

        assertEquals(ProviderBackAction.CLOSE, providerBackAction(firstStep))
        assertEquals(ProviderBackAction.PREVIOUS, providerBackAction(laterStep))
        assertEquals(ProviderBackAction.IGNORE, providerBackAction(testing))
        assertEquals(ProviderBackAction.CLOSE, providerBackAction(finished))
    }

    @Test
    fun `qweather host is normalized to official https origin`() {
        val result = ProviderConnectionTester.normalizeQweatherHost("  ABC123.qweatherapi.com/  ")

        assertTrue(result.ok)
        assertEquals("https://abc123.qweatherapi.com", result.value)
        assertNull(result.error)
    }

    @Test
    fun `qweather host rejects insecure foreign and path values`() {
        assertFalse(ProviderConnectionTester.normalizeQweatherHost("http://abc.qweatherapi.com").ok)
        assertFalse(ProviderConnectionTester.normalizeQweatherHost("https://weather.example.com").ok)
        assertFalse(ProviderConnectionTester.normalizeQweatherHost("https://abc.qweatherapi.com/geo/v2").ok)
    }

    @Test
    fun `quick route cannot leave credential step before api key is pasted`() {
        val model = ProviderSetupViewModel(ProviderWizardKind.QWEATHER)
        model.next()
        model.next()

        assertEquals(2, model.state.step)
        model.next()

        assertEquals(2, model.state.step)
        assertEquals(ProviderSetupStatus.ERROR, model.state.status)
        assertTrue(ProviderField.API_KEY in model.state.fieldErrors)
    }

    @Test
    fun `quick route advances after api key is pasted`() {
        val model = ProviderSetupViewModel(ProviderWizardKind.QWEATHER)
        model.next()
        model.next()
        model.setApiKey("test-api-key")

        model.next()

        assertEquals(3, model.state.step)
        assertTrue(model.state.fieldErrors.isEmpty())
    }

    @Test
    fun `jwt candidate reports every missing field`() {
        val errors = validateProviderCandidate(
            ProviderSetupUiState(
                kind = ProviderWizardKind.QWEATHER,
                authMode = QweatherAuthMode.JWT,
            ),
        )

        assertTrue(ProviderField.HOST in errors)
        assertTrue(ProviderField.PROJECT_ID in errors)
        assertTrue(ProviderField.KID in errors)
    }

    @Test
    fun `complete jwt candidate passes local validation`() {
        val errors = validateProviderCandidate(
            ProviderSetupUiState(
                kind = ProviderWizardKind.QWEATHER,
                host = "https://abc.qweatherapi.com",
                projectId = "project-id",
                kid = "credential-id",
                keys = QwGeneratedKeys("public", "private"),
                authMode = QweatherAuthMode.JWT,
            ),
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun `failed verification never persists candidate`() = runBlocking {
        var saves = 0
        val result = verifyThenPersist(
            candidate = "candidate-secret",
            verify = {
                ProviderConnectionResult(false, "鉴权被拒绝", "请核对凭据")
            },
            persist = { _, _ -> saves++ },
        )

        assertFalse(result.ok)
        assertEquals(0, saves)
    }

    @Test
    fun `successful verification persists exactly once`() = runBlocking {
        var saves = 0
        val result = verifyThenPersist(
            candidate = "candidate-secret",
            verify = {
                ProviderConnectionResult(true, "链路已建立", "已返回北京数据")
            },
            persist = { _, _ -> saves++ },
        )

        assertTrue(result.ok)
        assertEquals(1, saves)
        assertTrue(result.detail.contains("本机私密存储"))
    }
}
