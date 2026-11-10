package com.vpegs.ms.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vpegs.ms.data.AppProxyItem
import com.vpegs.ms.ui.components.GlassCard
import com.vpegs.ms.ui.components.GlassSwitch
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel

/** Known Russian / CIS apps — matched by package name contains / equals. */
private data class RuPreset(val title: String, val packages: List<String>)

private val RU_PRESETS = listOf(
    RuPreset("Яндекс", listOf(
        "ru.yandex.searchplugin", "com.yandex.browser", "ru.yandex.yandexmaps",
        "ru.yandex.taxi", "ru.yandex.music", "ru.yandex.androidkeyboard",
        "com.yandex.bank", "ru.yandex.disk", "com.yandex.mobile.realty",
        "ru.yandex.mail", "com.yandex.iot"
    )),
    RuPreset("Макс", listOf("ru.oneme.app", "com.max.android", "ru.mail.max")),
    RuPreset("Госуслуги", listOf("ru.gosuslugi.mobile", "ru.rostel.gosuslugi")),
    RuPreset("Wildberries", listOf("com.wildberries.ru")),
    RuPreset("Ozon", listOf("ru.ozon.app.android")),
    RuPreset("VK", listOf("com.vkontakte.android", "com.vk.im", "com.vk.clips")),
    RuPreset("Сбер", listOf("ru.sberbankmobile", "ru.sberbank.sberbankid")),
    RuPreset("Тинькофф", listOf("com.idamob.tinkoff.android")),
    RuPreset("Авито", listOf("com.avito.android")),
    RuPreset("2ГИС", listOf("ru.dublgis.dgismobile")),
    RuPreset("Mail.ru", listOf("ru.mail.mailapp", "ru.mail.cloud")),
    RuPreset("Одноклассники", listOf("ru.ok.android")),
    RuPreset("МТС", listOf("ru.mts.mymts", "ru.mts.money")),
    RuPreset("МегаФон", listOf("ru.megafon.mlk")),
    RuPreset("Теле2", listOf("ru.tele2.mytele2")),
    RuPreset("Альфа-Банк", listOf("ru.alfabank.mobile.android")),
    RuPreset("ВТБ", listOf("ru.vtb24.mobilebanking.android")),
    RuPreset("Кинопоиск", listOf("ru.kinopoisk")),
    RuPreset("IVI", listOf("ru.ivi.client")),
    RuPreset("Rutube", listOf("ru.rutube.app")),
)

@Composable
fun AppsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadInstalledApps(context) }
    var showRuDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 4.dp)) {
        Text("Обход приложений", style = MaterialTheme.typography.titleLarge, color = GlassText)
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Глобальный режим", style = MaterialTheme.typography.titleMedium, color = GlassText)
                    Text("Все приложения через VPN", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                GlassSwitch(checked = vm.globalProxyMode, onCheckedChange = { vm.updateGlobalProxyMode(it) })
            }
            if (!vm.globalProxyMode) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Инверсия списка", style = MaterialTheme.typography.titleMedium, color = GlassText)
                        Text(
                            if (vm.invertAppProxy) "Выбранные обходят VPN"
                            else "Только выбранные через VPN",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTextMuted
                        )
                    }
                    GlassSwitch(checked = vm.invertAppProxy, onCheckedChange = { vm.updateInvertAppProxy(it) })
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { showRuDialog = true },
            enabled = !vm.appsLoading,
            colors = ButtonDefaults.buttonColors(containerColor = GlassPrimary),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Checklist, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Российские приложения")
        }

        Spacer(Modifier.height(8.dp))
        Text(
            if (vm.globalProxyMode)
                "Выключи переключатель у приложения — оно пойдёт мимо VPN (нужен реконнект)"
            else
                "Включи приложения, которые должны идти через VPN (нужен реконнект)",
            style = MaterialTheme.typography.bodyMedium,
            color = GlassText
        )
        Text(
            "Госуслуги и банки часто видят «VPN включён» по системному интерфейсу, даже если трафик приложения идёт напрямую.",
            style = MaterialTheme.typography.labelMedium,
            color = GlassTextMuted
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.appSearch,
            onValueChange = { vm.updateAppSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск приложений…", color = GlassTextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GlassPrimary,
                unfocusedBorderColor = GlassBorder,
                focusedContainerColor = GlassSurfaceHigh,
                unfocusedContainerColor = GlassSurfaceHigh
            )
        )
        Spacer(Modifier.height(10.dp))

        if (vm.appsLoading) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = GlassPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text("Загрузка приложений…", color = GlassTextMuted)
                }
            }
        } else {
            Text(
                "${vm.filteredApps.size} приложений",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTextMuted,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                items(vm.filteredApps, key = { it.packageName }) { app ->
                    AppRow(app, onToggle = { vm.toggleAppProxy(app.packageName) })
                }
            }
        }
    }

    if (showRuDialog) {
        RussianAppsDialog(
            installed = vm.appProxyList,
            onDismiss = { showRuDialog = false },
            onApply = { direct, scope ->
                vm.applyDirectAccessPackages(direct, scope)
                showRuDialog = false
            }
        )
    }
}

@Composable
private fun RussianAppsDialog(
    installed: List<AppProxyItem>,
    onDismiss: () -> Unit,
    onApply: (direct: Set<String>, scope: Set<String>) -> Unit
) {
    // Only presets that match at least one installed package
    val matches = remember(installed) {
        val pkgs = installed.map { it.packageName }.toSet()
        RU_PRESETS.mapNotNull { preset ->
            val found = installed.filter { app ->
                preset.packages.any { p ->
                    app.packageName.equals(p, true) || app.packageName.startsWith("$p.")
                } || preset.title.equals(app.appName, true) ||
                    app.appName.contains(preset.title, ignoreCase = true) ||
                    // broader yandex / vk match
                    (preset.title == "Яндекс" && app.packageName.contains("yandex", true)) ||
                    (preset.title == "VK" && (app.packageName.startsWith("com.vk") || app.packageName.contains("vkontakte")))
            }.map { it.packageName }.distinct()
            if (found.isEmpty()) null else preset.title to found
        }
    }
    // Checked = go direct (bypass VPN). Default: all matched already direct stay checked
    val initial = remember(matches, installed) {
        val direct = installed.filter { !it.proxyEnabled }.map { it.packageName }.toSet()
        matches.flatMap { it.second }.filter { it in direct }.toMutableSet()
    }
    var checked by remember { mutableStateOf(initial.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Российские приложения", color = GlassText) },
        text = {
            if (matches.isEmpty()) {
                Text(
                    "На устройстве не найдено приложений из списка (Яндекс, WB, Госуслуги…).",
                    color = GlassTextMuted
                )
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Галочка = напрямую (минуя VPN). Снимите — через VPN. «Применить» обновит только этот список.",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTextMuted
                    )
                    Spacer(Modifier.height(10.dp))
                    matches.forEach { (title, pkgs) ->
                        pkgs.forEach { pkg ->
                            val app = installed.find { it.packageName == pkg }
                            val label = app?.appName ?: title
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checked = if (pkg in checked) checked - pkg else checked + pkg
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = pkg in checked,
                                    onCheckedChange = {
                                        checked = if (it) checked + pkg else checked - pkg
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = GlassPrimary,
                                        uncheckedColor = GlassTextMuted
                                    )
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(label, color = GlassText, style = MaterialTheme.typography.bodyLarge)
                                    Text(pkg, color = GlassTextMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val allRuPkgs = matches.flatMap { it.second }.toSet()
                    // Pass: which of RU list should be direct; apply handles both on/off
                    val scope = matches.flatMap { it.second }.toSet()
                    onApply(checked, scope)
                },
                enabled = matches.isNotEmpty()
            ) { Text("Применить", color = GlassPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = GlassTextMuted) }
        },
        containerColor = GlassSurfaceHigh
    )
}

@Composable
private fun AppRow(app: AppProxyItem, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GlassSurfaceHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, color = GlassText, maxLines = 1)
                Text(
                    if (app.isSystem) "Системное" else app.packageName,
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTextMuted,
                    maxLines = 1
                )
            }
            Text(
                if (app.proxyEnabled) "VPN" else "Напрямую",
                style = MaterialTheme.typography.labelSmall,
                color = if (app.proxyEnabled) GlassAccent else GlassTextMuted
            )
            Spacer(Modifier.width(6.dp))
            GlassSwitch(checked = app.proxyEnabled, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun AppIcon(drawable: Drawable?) {
    val bmp = remember(drawable) { drawable?.toBitmapCompat(96) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
    } else {
        Box(Modifier.size(40.dp))
    }
}

private fun Drawable.toBitmapCompat(size: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return Bitmap.createScaledBitmap(bitmap, size, size, true)
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bmp
}
