package com.zhisheng.weather.ui

import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.zhisheng.weather.BuildConfig
import com.zhisheng.weather.data.AppAnnouncement
import com.zhisheng.weather.data.Announcements
import com.zhisheng.weather.data.eligibleAnnouncements
import com.zhisheng.weather.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun AnnouncementDialog(announcement: AppAnnouncement, onClose: (confirmed: Boolean) -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .55f).coerceAtLeast(100f).dp
    AlertDialog(onDismissRequest = { onClose(false) }, containerColor = zhishengOverlayColor(),
        title = { Text("枳生公告", color = ZhishengMint) },
        text = { Column(Modifier.heightIn(max = maxHeight).verticalScroll(rememberScrollState())) {
            Text(announcement.title, color = ZhishengText, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Text(SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(announcement.publishedAt)), color = ZhishengTextTertiary)
            Spacer(Modifier.height(16.dp))
            SelectionContainer { Text(announcement.body, color = ZhishengTextSecondary) }
        } },
        confirmButton = { TextButton(onClick = { onClose(true) }) { Text("知道了", color = ZhishengMint) } })
}

@Composable
internal fun AnnouncementHost(lifecycle: Lifecycle, canPresent: Boolean) {
    val context = LocalContext.current.applicationContext
    val preview = LocalView.current.isInEditMode
    val snapshot by Announcements.state.collectAsState()
    val read by Announcements.readIds.collectAsState()
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<AppAnnouncement?>(null) }
    var dismissed by remember { mutableStateOf(emptySet<String>()) }
    var nextEligibleAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(lifecycle, preview) {
        if (!preview) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            launch { Announcements.follow(context) }
            launch {
                Announcements.state.collect { Announcements.syncReceipts(context) }
            }
        }
    }
    LaunchedEffect(canPresent, snapshot, read, dismissed) {
        if (!canPresent || selected != null) return@LaunchedEffect
        delay((nextEligibleAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
        // Wait for a validated foreground response; cached bodies never create offline popups.
        if (snapshot.verifiedAt <= 0 || SystemClock.elapsedRealtime() - snapshot.verifiedAt !in 0..60_000L) return@LaunchedEffect
        selected = eligibleAnnouncements(snapshot.feed, System.currentTimeMillis(), BuildConfig.VERSION_CODE)
            .firstOrNull { it.id !in read && it.id !in dismissed }
    }
    val shown = selected?.takeIf { message -> canPresent &&
        eligibleAnnouncements(snapshot.feed, System.currentTimeMillis(), BuildConfig.VERSION_CODE).any { it.id == message.id } }
    LaunchedEffect(shown?.id) {
        shown?.let { message -> withFrameNanos { }; Announcements.mark(context, message.id, 2); Announcements.syncReceipts(context) }
    }
    shown?.let { message ->
        AnnouncementDialog(message) { confirmed ->
            dismissed = dismissed + message.id
            selected = null
            nextEligibleAt = SystemClock.elapsedRealtime() + 60_000L
            scope.launch { Announcements.dismiss(context, message.id, confirmed); Announcements.syncReceipts(context) }
        }
    }
    LaunchedEffect(snapshot.feed) {
        if (selected != null && snapshot.feed?.items?.none { it.id == selected?.id } == true) selected = null
    }
}
