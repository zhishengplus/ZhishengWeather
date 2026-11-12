package com.zhisheng.weather.ui.badge

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zhisheng.weather.data.badge.BadgeViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Acquire quietly in the foreground; only presentation waits for the startup prompt queue. */
@Composable
internal fun BadgeHomeAwardHost(lifecycle: Lifecycle, canPresent: Boolean,
    canCheck: Boolean = canPresent,
    onVisible: (Boolean) -> Unit = {}, vm: BadgeViewModel = viewModel(key = "home_badge_award")) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    var launchingGrantId by rememberSaveable { mutableStateOf<String?>(null) }
    var attemptedGrantId by remember { mutableStateOf<String?>(null) }
    val latestCanPresent by rememberUpdatedState(canPresent)
    val launch = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        launchingGrantId = null
        vm.consumeReward()
        onVisible(false)
        // A real presentation is acknowledged by BadgeActivity. Closing before
        // it is shown leaves the durable pending award for a later app session.
    }
    LaunchedEffect(lifecycle, canCheck) {
        if (canCheck) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                while (isActive) {
                    vm.checkAutomaticAward()
                    delay(30_000)
                }
            } finally { vm.stop() }
        }
    }
    LaunchedEffect(state.reward, canPresent) {
        val grant = state.collection.firstOrNull { it.grantId == state.reward }
        val reward = state.reward
        if (latestCanPresent && reward != null && grant?.grantId == reward && launchingGrantId == null && attemptedGrantId != reward) {
            attemptedGrantId = reward
            launchingGrantId = reward
            onVisible(true)
            try { launch.launch(BadgeActivity.intent(context, reward)) }
            catch (_: Exception) {
                launchingGrantId = null
                vm.consumeReward()
                onVisible(false)
            }
        }
    }
    SideEffect { onVisible(launchingGrantId != null) }
    DisposableEffect(Unit) { onDispose { vm.stop(); onVisible(false) } }
}
