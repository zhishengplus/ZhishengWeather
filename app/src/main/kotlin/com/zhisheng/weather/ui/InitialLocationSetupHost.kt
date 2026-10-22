package com.zhisheng.weather.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.zhisheng.weather.data.LocationSource
import com.zhisheng.weather.ui.components.WeatherOverlayDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** The permission launcher stays composed while its system window pauses the app. */
@Composable
internal fun InitialLocationSetupHost(
    viewModel: WeatherViewModel,
    activity: Activity,
    visible: Boolean,
    onLocated: () -> Unit,
    onChooseCity: () -> Unit,
    onDismiss: () -> Unit,
    startImmediately: Boolean = false,
    firstRun: Boolean = false,
    onConfigureProvider: (ProviderWizardKind) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var stage by rememberSaveable { mutableStateOf(InitialLocationSetupStage.INTRO) }
    var failureMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionNeedsSettings by rememberSaveable { mutableStateOf(false) }
    var returningFromSettings by rememberSaveable { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var permissionPreparation by remember { mutableStateOf<Job?>(null) }
    var sharedChoiceInProgress by rememberSaveable { mutableStateOf(false) }
    fun locate(resuming: Boolean = false) {
        if (operation?.isActive == true) return
        if (!LocationSource.hasPermission(context)) {
            stage = InitialLocationSetupStage.PERMISSION_DENIED
            return
        }
        if (!LocationSource.locationEnabledOnDevice(context)) {
            stage = InitialLocationSetupStage.SERVICES_OFF
            return
        }
        stage = InitialLocationSetupStage.LOCATING
        failureMessage = null
        operation = scope.launch {
            try {
                // The permission window may deliver its result before the Activity
                // is foreground again. Single-fix providers can fail in that gap.
                lifecycle.withResumed { Unit }
                val located = if (resuming) viewModel.resumeInitialLocation() else viewModel.locateInitialCity()
                if (located) {
                    onLocated()
                    stage = InitialLocationSetupStage.INTRO
                } else {
                    failureMessage = viewModel.uiState.value.locateMessage
                    stage = when {
                        !LocationSource.hasPermission(context) -> InitialLocationSetupStage.PERMISSION_DENIED
                        !LocationSource.locationEnabledOnDevice(context) -> InitialLocationSetupStage.SERVICES_OFF
                        else -> InitialLocationSetupStage.FAILED
                    }
                }
            } catch (cancelled: CancellationException) {
                // A manual city or widget entry can cancel the ViewModel request
                // while this Host is still alive. Only rethrow our own cancellation.
                currentCoroutineContext().ensureActive()
                stage = InitialLocationSetupStage.INTRO
                failureMessage = null
            } catch (_: Exception) {
                failureMessage = "地点设置暂时未能完成，请重试或手动选择城市"
                stage = InitialLocationSetupStage.FAILED
            }
        }
    }

    LaunchedEffect(Unit) {
        // Reattach to the ViewModel-owned request, including an already completed
        // success. Restoring a progress label alone must not restart GPS work.
        if (stage == InitialLocationSetupStage.LOCATING) locate(resuming = true)
        else if (stage == InitialLocationSetupStage.REQUESTING_PERMISSION) {
            stage = InitialLocationSetupStage.INTRO
        }
    }

    fun locateAfterPermission() {
        if (permissionPreparation?.isActive == true || operation?.isActive == true) return
        stage = InitialLocationSetupStage.LOCATING
        permissionPreparation = scope.launch {
            try {
                if (firstRun && LocationSource.hasPrecisePermission(context))
                    com.zhisheng.weather.data.SettingsRepository.setPreciseLocationEnabled(true)
                currentCoroutineContext().ensureActive()
                locate()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failureMessage = "位置设置未能保存，请重试"; stage = InitialLocationSetupStage.FAILED }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[LocationSource.PERMISSION] == true || LocationSource.hasPermission(context)) {
            permissionNeedsSettings = false
            locateAfterPermission()
        } else {
            permissionNeedsSettings = !ActivityCompat.shouldShowRequestPermissionRationale(activity, LocationSource.PERMISSION)
            stage = InitialLocationSetupStage.PERMISSION_DENIED
        }
    }

    fun openSettings(intent: Intent) {
        try {
            returningFromSettings = true
            context.startActivity(intent)
        } catch (_: Exception) {
            returningFromSettings = false
            failureMessage = "暂时无法打开设置，可手动选择城市"
            stage = InitialLocationSetupStage.FAILED
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (returningFromSettings) {
            returningFromSettings = false
            if (!LocationSource.hasPermission(context)) {
                permissionNeedsSettings = true
                stage = InitialLocationSetupStage.PERMISSION_DENIED
            } else {
                permissionNeedsSettings = false
                locate()
            }
        }
    }

    fun leave(action: () -> Unit) {
        returningFromSettings = false
        permissionPreparation?.cancel()
        permissionPreparation = null
        operation?.cancel()
        operation = null
        sharedChoiceInProgress = false
        viewModel.cancelLocationRequest()
        stage = InitialLocationSetupStage.INTRO
        failureMessage = null
        action()
    }

    fun requestLocation() {
        if (LocationSource.hasPermission(context) && (!firstRun || LocationSource.hasPrecisePermission(context))) {
            locateAfterPermission()
        }
        else {
            stage = InitialLocationSetupStage.REQUESTING_PERMISSION
            permission.launch(LocationSource.requestedPermissions(precise = firstRun))
        }
    }
    LaunchedEffect(visible, startImmediately) {
        if (visible && startImmediately && stage == InitialLocationSetupStage.INTRO) requestLocation()
    }
    val busy = stage == InitialLocationSetupStage.LOCATING || stage == InitialLocationSetupStage.REQUESTING_PERMISSION
    val welcome = !startImmediately && (stage == InitialLocationSetupStage.INTRO || (sharedChoiceInProgress && busy))
    val motionMs = if (rememberReducedMotion()) 0 else 220
    if (visible && !(startImmediately && stage == InitialLocationSetupStage.INTRO)) {
        // Keep one window/scrim through consent, permission and the fix. The first
        // choice stays in place while busy instead of jumping to a smaller dialog.
        WeatherOverlayDialog(onDismiss = { leave(onDismiss) }, maxWidth = 480.dp) { modifier ->
            Crossfade(targetState = welcome, modifier = modifier.animateContentSize(tween(motionMs)),
                animationSpec = tween(motionMs), label = "location-setup-stage") { showWelcome ->
                if (showWelcome) LocationWelcomePanel(
                    onSharedLocation = {
                        if (operation?.isActive != true) {
                            sharedChoiceInProgress = true
                            stage = InitialLocationSetupStage.LOCATING
                            operation = scope.launch {
                                try {
                                    com.zhisheng.weather.data.SecretStore.setSharedPlaceConsent(true)
                                    com.zhisheng.weather.data.SettingsRepository.setLocationEnabled(true)
                                    operation = null
                                    requestLocation()
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) {
                                    failureMessage = "位置设置未能保存，请重试"
                                    stage = InitialLocationSetupStage.FAILED
                                }
                            }
                        }
                    },
                    onConfigure = onConfigureProvider,
                    onManual = { leave(onChooseCity) },
                    modifier = Modifier.fillMaxWidth(),
                    busy = busy,
                ) else InitialLocationSetupPanel(
                    stage = stage,
                    failureMessage = failureMessage,
                    permissionNeedsSettings = permissionNeedsSettings,
                    onUseLocation = { sharedChoiceInProgress = false; requestLocation() },
                    onChooseCity = { leave(onChooseCity) },
                    onOpenAppSettings = {
                        openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)))
                    },
                    onOpenLocationSettings = { openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onDismiss = { leave(onDismiss) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
