package com.vpegs.ms.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vpegs.ms.BuildConfig
import com.vpegs.ms.ui.components.GlassCard
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp)
    ) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge, color = GlassText)
        Spacer(Modifier.height(12.dp))

        Text("Оформление", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Тема", style = MaterialTheme.typography.bodyLarge, color = GlassText)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppThemeId.entries.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { id ->
                            val selected = vm.themeId == id
                            val p = paletteFor(id)
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) GlassPrimary.copy(0.2f) else GlassSurfaceHigh)
                                    .border(
                                        1.5.dp,
                                        if (selected) GlassPrimary else GlassBorder,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { vm.updateThemeId(id) }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(p.primary)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        id.title,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GlassText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Пинг", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "Средний пинг за ${vm.pingSeconds} с",
                style = MaterialTheme.typography.bodyLarge,
                color = GlassText
            )
            Text(
                "Несколько замеров, показывается среднее",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTextMuted
            )
            Spacer(Modifier.height(8.dp))
            Slider(
                value = vm.pingSeconds.toFloat(),
                onValueChange = { vm.updatePingSeconds(it.toInt()) },
                valueRange = 1f..15f,
                steps = 13,
                colors = SliderDefaults.colors(
                    thumbColor = GlassAccent,
                    activeTrackColor = GlassPrimary
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1 с", color = GlassTextMuted, style = MaterialTheme.typography.labelSmall)
                Text("15 с", color = GlassTextMuted, style = MaterialTheme.typography.labelSmall)
            }
        }


        Spacer(Modifier.height(16.dp))
        Text("Серверы", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Протокол у сервера", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("Бейдж VLESS / Reality и т.д.", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                Switch(
                    checked = vm.showServerProtocol,
                    onCheckedChange = { vm.updateShowServerProtocol(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = GlassAccent, checkedTrackColor = GlassAccent.copy(0.35f))
                )
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("IP у сервера", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("Флаг + адрес, например 🇫🇷 1.2.3.4", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                Switch(
                    checked = vm.showServerIp,
                    onCheckedChange = { vm.updateShowServerIp(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = GlassAccent, checkedTrackColor = GlassAccent.copy(0.35f))
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Подписки", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Update, null, tint = GlassPrimary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Автообновление при запуске", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("Обновлять подписки при открытии", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                Switch(
                    checked = vm.autoUpdateOnLaunch,
                    onCheckedChange = { vm.updateAutoUpdateOnLaunch(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GlassAccent,
                        checkedTrackColor = GlassAccent.copy(0.35f)
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(12.dp))
            Text("Интервал автообновления (ч)", style = MaterialTheme.typography.bodyLarge, color = GlassText)
            Text("Для подписок с включённым авто", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
            Slider(
                value = vm.bgUpdateHours.toFloat(),
                onValueChange = { vm.updateBgUpdateHours(it.toInt()) },
                valueRange = 1f..24f,
                steps = 22,
                colors = SliderDefaults.colors(thumbColor = GlassAccent, activeTrackColor = GlassPrimary)
            )
            Text("${vm.bgUpdateHours} ч", color = GlassPrimary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Фоновое обновление", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("Даже когда приложение закрыто", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                Switch(
                    checked = vm.bgUpdateEnabled,
                    onCheckedChange = { vm.updateBgUpdateEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GlassAccent,
                        checkedTrackColor = GlassAccent.copy(0.35f)
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("О VpEgs", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Info(Icons.Outlined.Info, "Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Info(Icons.Outlined.Security, "Конфиденциальность", "Данные только на устройстве")
            Info(Icons.Outlined.Widgets, "Виджет", "Пинг · Обновить · Лучший")
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/mstrblock"))
                            )
                        } catch (_: Exception) {}
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Send, null, tint = GlassPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Telegram автора", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("@mstrblock", style = MaterialTheme.typography.labelMedium, color = GlassAccent)
                }
            }
        }
    }
}

@Composable
private fun Info(icon: ImageVector, title: String, sub: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = GlassPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = GlassText)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
        }
    }
}
