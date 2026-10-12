package com.zhisheng.weather.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.zhisheng.weather.data.LocationSource
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
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var stage by rememberSaveable { mutableStateOf(InitialLocationSetupStage.INTRO) }
    var failureMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var permissionNeedsSettings by rememberSaveable { mutableStateOf(false) }
    var returningFromSettings by rememberSaveable { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
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
                val located = if (resuming) viewModel.resumeInitialLocation() else viewModel.locateInitialCity()
                if (located) {
                    stage = InitialLocationSetupStage.INTRO
                    onLocated()
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

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted || LocationSource.hasPermission(context)) {
            permissionNeedsSettings = false
            locate()
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
        operation?.cancel()
        operation = null
        viewModel.cancelLocationRequest()
        stage = InitialLocationSetupStage.INTRO
        failureMessage = null
        action()
    }

    if (visible) InitialLocationSetupDialog(
        stage = stage,
        failureMessage = failureMessage,
        permissionNeedsSettings = permissionNeedsSettings,
        onUseLocation = {
            if (LocationSource.hasPermission(context)) locate()
            else {
                stage = InitialLocationSetupStage.REQUESTING_PERMISSION
                // City weather works with approximate foreground location. Precise
                // street permission remains the separate, explicit settings action.
                permission.launch(LocationSource.PERMISSION)
            }
        },
        onChooseCity = { leave(onChooseCity) },
        onOpenAppSettings = {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)))
        },
        onOpenLocationSettings = { openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        onDismiss = { leave(onDismiss) },
    )
}
