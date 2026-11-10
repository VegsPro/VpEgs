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
import com.vpegs.ms.ui.components.GlassSwitch
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
            .padding(top = 8.dp, bottom = 120.dp)
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

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Glass-эффект", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text(
                        "Размытие фона (Haze). Выключите на слабых устройствах",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTextMuted
                    )
                }
                GlassSwitch(
                    checked = vm.glassEffect,
                    onCheckedChange = { vm.updateGlassEffect(it) }
                )
            }
        }


        Spacer(Modifier.height(16.dp))
        Text("Кнопка в шторке", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("При включении — лучший сервер", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text(
                        "Как «Лучший»: фильтры РФ / подписки, уведомление «Подбор сервера…»",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTextMuted
                    )
                }
                GlassSwitch(
                    checked = vm.tileUseBest,
                    onCheckedChange = { vm.saveTileSettings(useBest = it) }
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Подзаголовок на плитке", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("Имя сервера / Вкл·Выкл", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                GlassSwitch(
                    checked = vm.tileShowSubtitle,
                    onCheckedChange = { vm.saveTileSettings(showSubtitle = it) }
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Уведомление о подборе", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("Системное «Подбор сервера…»", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                GlassSwitch(
                    checked = vm.tileNotifyPick,
                    onCheckedChange = { vm.saveTileSettings(notifyPick = it) }
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Только избранные", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("Плитка ищет лучший среди ★", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                GlassSwitch(
                    checked = vm.tileOnlyFavorites,
                    onCheckedChange = { vm.saveTileSettings(onlyFavorites = it) }
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(12.dp))
            Text("Макс. пинг для плитки", style = MaterialTheme.typography.bodyLarge, color = GlassText)
            Text(
                if (vm.tileMaxPingMs <= 0) "Без ограничения"
                else "Не брать серверы медленнее ${vm.tileMaxPingMs} ms",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTextMuted
            )
            Slider(
                value = vm.tileMaxPingMs.toFloat().coerceIn(0f, 500f),
                onValueChange = { vm.saveTileSettings(maxPingMs = it.toInt()) },
                valueRange = 0f..500f,
                steps = 9,
                colors = SliderDefaults.colors(
                    thumbColor = GlassAccent,
                    activeTrackColor = GlassPrimary
                )
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("выкл", color = GlassTextMuted, style = MaterialTheme.typography.labelSmall)
                Text("500 ms", color = GlassTextMuted, style = MaterialTheme.typography.labelSmall)
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
                GlassSwitch(checked = vm.showServerProtocol, onCheckedChange = { vm.updateShowServerProtocol(it) })
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("IP у сервера", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("Флаг + адрес, например 🇫🇷 1.2.3.4", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                GlassSwitch(checked = vm.showServerIp, onCheckedChange = { vm.updateShowServerIp(it) })
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Подписки", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Базовые сервера", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("⭐ VpEgs Config с GitHub", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                GlassSwitch(checked = vm.showBuiltinServers, onCheckedChange = { vm.updateShowBuiltin(it) })
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = GlassBorder)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Update, null, tint = GlassPrimary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Автообновление при запуске", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("Обновлять подписки при открытии", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                GlassSwitch(checked = vm.autoUpdateOnLaunch, onCheckedChange = { vm.updateAutoUpdateOnLaunch(it) })
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
                GlassSwitch(checked = vm.bgUpdateEnabled, onCheckedChange = { vm.updateBgUpdateEnabled(it) })
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Обновление", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            val ctx = LocalContext.current
            val act = ctx as? android.app.Activity
            Text(
                "Текущая: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassText
            )

            when {
                vm.updateDownloading -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Скачивание ${vm.updateVersionName.ifBlank { vm.updateVersionCode.toString() }}… ${(vm.updateProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTextMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    com.vpegs.ms.ui.components.GlassProgressBar(vm.updateProgress)
                }
                vm.updateAvailable && vm.updateDownloaded -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Готово: ${vm.updateVersionName.ifBlank { vm.updateVersionCode.toString() }}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GlassText
                    )
                    Text(
                        "APK в кеше · можно установить",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTextMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { act?.let { vm.installUpdate(it) } },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassPrimary)
                        ) { Text("Установить") }
                        OutlinedButton(
                            onClick = { vm.checkForAppUpdate(ctx.applicationContext) },
                            enabled = !vm.updateChecking,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GlassText)
                        ) {
                            if (vm.updateChecking) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = GlassPrimary)
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(if (vm.updateChecking) "Проверка…" else "Проверить снова")
                        }
                    }
                }
                vm.updateAvailable -> {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Доступна ${vm.updateVersionName.ifBlank { vm.updateVersionCode.toString() }}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GlassText
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.checkForAppUpdate(ctx.applicationContext) },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassPrimary)
                    ) { Text("Скачать") }
                }
                else -> {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.checkForAppUpdate(ctx.applicationContext) },
                        enabled = !vm.updateChecking,
                        colors = ButtonDefaults.buttonColors(containerColor = GlassPrimary)
                    ) {
                        if (vm.updateChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = GlassText
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Проверка…")
                        } else {
                            Text("Проверить обновление")
                        }
                    }
                    if (vm.workStatus.isNotBlank() &&
                        (vm.workStatus.contains("актуальн", ignoreCase = true) ||
                            vm.workStatus.contains("обновлен", ignoreCase = true) ||
                            vm.workStatus.contains("Не удалось", ignoreCase = true))
                    ) {
                        Spacer(Modifier.height(6.dp))
                        Text(vm.workStatus, style = MaterialTheme.typography.labelMedium, color = GlassAccent)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Text("О VpEgs", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Info(Icons.Outlined.Info, "Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Info(Icons.Outlined.Security, "Конфиденциальность", "Данные только на устройстве")
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        try {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/VegsPro/VpEgs"))
                            )
                        } catch (_: Exception) {}
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Code, null, tint = GlassPrimary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("GitHub", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("VegsPro/VpEgs", style = MaterialTheme.typography.labelMedium, color = GlassAccent)
                }
            }
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
                    Text("Telegram", style = MaterialTheme.typography.bodyLarge, color = GlassText)
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
