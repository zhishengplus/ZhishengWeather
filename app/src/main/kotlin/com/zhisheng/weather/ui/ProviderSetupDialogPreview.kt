/* Hallmark · component demo: provider setup controls · eight states */
package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.ui.theme.ZhishengWeatherTheme

@Preview(name = "Provider controls · 320", widthDp = 320, heightDp = 720)
@Preview(name = "Provider controls · 375", widthDp = 375, heightDp = 720)
@Preview(name = "Provider controls · 414", widthDp = 414, heightDp = 720)
@Preview(name = "Provider controls · 768", widthDp = 768, heightDp = 720)
@Composable
private fun ProviderSetupControlsPreview() {
    ZhishengWeatherTheme {
        Column(
            modifier = Modifier.fillMaxSize().background(ZhishengBg).padding(20.dp),
        ) {
            Text(
                "TERMINAL BUTTON · 8 STATES",
                color = ZhishengTextTertiary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(16.dp))
            ButtonVisualState.entries.forEach { state ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        state.name.lowercase(),
                        color = ZhishengTextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(72.dp),
                    )
                    TerminalButton(
                        label = when (state) {
                            ButtonVisualState.ERROR -> "重新验证"
                            ButtonVisualState.SUCCESS -> "已保存"
                            else -> "验证并保存"
                        },
                        onClick = {},
                        enabled = state != ButtonVisualState.DISABLED,
                        loading = state == ButtonVisualState.LOADING,
                        previewState = state,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
