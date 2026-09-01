package com.zhisheng.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhisheng.weather.data.CityRepository
import com.zhisheng.weather.model.City
import com.zhisheng.weather.ui.theme.ZhishengBg
import com.zhisheng.weather.ui.theme.ZhishengMint
import com.zhisheng.weather.ui.theme.ZhishengOrange
import com.zhisheng.weather.ui.theme.ZhishengSurface
import com.zhisheng.weather.ui.theme.ZhishengText
import com.zhisheng.weather.ui.theme.ZhishengTextSecondary
import com.zhisheng.weather.ui.theme.ZhishengTextTertiary
import com.zhisheng.weather.i18n.uiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    onCityPicked: (City) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    // rememberCoroutineScope：离开搜索页自动取消挂起的搜索（v0.0.1）
    val scope = rememberCoroutineScope()
    var debounceJob by remember { mutableStateOf<Job?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(ZhishengBg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .height(56.dp)
                    .border(1.dp, ZhishengTextTertiary.copy(alpha = 0.34f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = uiText("返回"),
                        onClick = onBack,
                    )
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = ZhishengOrange,
                )
                Spacer(Modifier.width(5.dp))
                Text(uiText("返回"), color = ZhishengText, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { q ->
                    query = q
                    debounceJob?.cancel()
                    debounceJob = scope.launch {
                        delay(350)
                        searching = true
                        // 透传 CancellationException：被新输入取消的旧搜索不再写空结果覆盖新结果（v0.0.1）
                        results = try {
                            CityRepository.search(q)
                        } catch (ce: CancellationException) {
                            throw ce
                        } catch (_: Exception) {
                            emptyList()
                        }
                        searching = false
                    }
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("> 输入城市名_", color = ZhishengTextTertiary) },
                singleLine = true,
                shape = androidx.compose.ui.graphics.RectangleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZhishengMint,
                    unfocusedBorderColor = ZhishengTextTertiary.copy(alpha = 0.4f),
                    focusedTextColor = ZhishengText,
                    unfocusedTextColor = ZhishengText,
                    cursorColor = ZhishengMint,
                ),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SEARCH//", style = MaterialTheme.typography.titleSmall, color = ZhishengOrange)
            Spacer(Modifier.width(6.dp))
            Text("城市检索", style = MaterialTheme.typography.titleSmall, color = ZhishengTextSecondary, letterSpacing = 2.sp)
            Spacer(Modifier.weight(1f))
            if (query.isNotBlank() && !searching) {
                Text("HITS ${results.size}", style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
            }
        }

        when {
            searching -> Text(
                "> QUERYING ...",
                modifier = Modifier.padding(top = 24.dp, start = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = ZhishengMint,
                letterSpacing = 1.sp,
            )
            query.isNotBlank() && results.isEmpty() -> Text(
                "> 没有找到「$query」",
                modifier = Modifier.padding(top = 24.dp, start = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = ZhishengTextSecondary,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(results.size) { i ->
                    val city = results[i]
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(androidx.compose.ui.graphics.RectangleShape)
                            .background(ZhishengSurface)
                            .clickable { onCityPicked(city) }
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "%02d".format(i + 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZhishengOrange,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(city.name, style = MaterialTheme.typography.titleSmall, color = ZhishengText)
                            if (city.affiliation.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(city.affiliation, style = MaterialTheme.typography.labelSmall, color = ZhishengTextTertiary)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "[+]",
                            style = MaterialTheme.typography.labelMedium,
                            color = ZhishengMint,
                        )
                    }
                }
            }
        }
    }
}
