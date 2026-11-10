package com.vpegs.ms.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vpegs.ms.data.AppProxyItem
import com.vpegs.ms.ui.components.GlassCard
import com.vpegs.ms.ui.components.GlassSwitch
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel

/**
 * Broad matchers for RU/CIS apps: package substring or app name keywords.
 * Ordered by company for grouping in the dialog.
 */
private data class RuGroup(
    val title: String,
    val packageNeedles: List<String>,
    val nameNeedles: List<String> = emptyList()
)

private val RU_GROUPS = listOf(
    RuGroup(
        "Яндекс",
        listOf(
            "yandex", "ru.yandex", "com.yandex", "zen.yandex", "ya.android",
            "yango", "eddy.yandex", "auto.ru"
        ),
        listOf("яндекс", "yandex", "алиса", "киса", "авто.ру", "auto.ru")
    ),
    RuGroup(
        "VK / Mail",
        listOf(
            "vkontakte", "com.vk.", "ru.vk.", "vk.im", "vk.clips", "vk.video",
            "vk.admin", "vk.calls", "vk.music", "vk.music.action",
            "ru.mail.", "com.mail.ru", "my.mail", "ok.android", "odnoklassniki"
        ),
        listOf("вконтакте", "вк ", "vk ", "одноклассники", "mail.ru", "мейл")
    ),
    RuGroup(
        "Макс",
        listOf("ru.oneme.app", "oneme.app", "max.messenger", "com.max."),
        listOf("макс", "max ")
    ),
    RuGroup(
        "Госуслуги / Гос",
        listOf("gosuslugi", "goskey", "mincifry", "nalog", "mos.ru", "dit.mos", "gu.spb"),
        listOf("госуслуги", "госключ", "налог", "мой налог")
    ),
    RuGroup(
        "Маркетплейсы",
        listOf(
            "wildberries", "wb.android", "ozon.app", "ru.ozon", "aliexpress",
            "sbermegamarket", "megamarket", "lamoda", "detmir", "mvideo",
            "eldorado", "citilink", "dns.shop", "leroy"
        ),
        listOf("wildberries", "вайлдберриз", "ozon", "озон", "ламода", "мегамаркет", "dns", "м.видео")
    ),
    RuGroup(
        "Банки",
        listOf(
            "sberbank", "sber.", "tinkoff", "idamob.tinkoff", "alfabank", "vtb24",
            "vtb.", "openbank", "gazprombank", "rshb", "rosbank", "homecredit",
            "sovcombank", "raiffeisen", "yoomoney", "yookassa", "qiwi"
        ),
        listOf("сбер", "тинькофф", "т-банк", "альфа", "втб", "юmoney", "qiwi")
    ),
    RuGroup(
        "Операторы",
        listOf("mts.", "megafon", "tele2", "beeline", "yota", "t2.ru", "rostel"),
        listOf("мтс", "мегафон", "теле2", "билайн", "йота")
    ),
    RuGroup(
        "Карты / Транспорт",
        listOf(
            "dublgis", "2gis", "citymapper", "ru.yandex.yandexmaps",
            "moscow.transport", "troika", "citi.mobil", "citymobil",
            "gett", "inDriver", "ru.taxi"
        ),
        listOf("2гис", "2gis", "ситимобил", "тройка")
    ),
    RuGroup(
        "Объявления / Работа",
        listOf("avito", "youla", "hh.android", "headhunter", "superjob", "drom."),
        listOf("авито", "юла", "hh", "хедхантер", "дром")
    ),
    RuGroup(
        "Медиа",
        listOf(
            "kinopoisk", "ivi.client", "rutube", "more.tv", "start.android",
            "premier.android", "wink.", "zvuk", "boom.android", "zvooq"
        ),
        listOf("кинопоиск", "ivi", "rutube", "Wink", "звук", "premier")
    ),
    RuGroup(
        "Прочее RU",
        listOf(
            "rustore", "vk.store", "sberbank.sberdevices", "salute",
            "beru.", "delivery club", "dc.client", "samokat", "magnit",
            "pyaterochka", "x5.", "lenta.", "okko", "kion"
        ),
        listOf("rustore", "сбердевайс", "самокат", "магнит", "пятёрочка", "лента", "окко", "kion")
    ),
)

private fun matchGroup(app: AppProxyItem, group: RuGroup): Boolean {
    val p = app.packageName.lowercase()
    val n = app.appName.lowercase()
    if (group.packageNeedles.any { p.contains(it.lowercase()) }) return true
    if (group.nameNeedles.any { n.contains(it.lowercase()) }) return true
    return false
}

private fun collectRuApps(installed: List<AppProxyItem>): List<Pair<String, List<AppProxyItem>>> {
    val used = mutableSetOf<String>()
    val out = mutableListOf<Pair<String, List<AppProxyItem>>>()
    for (g in RU_GROUPS) {
        val found = installed.filter { matchGroup(it, g) && it.packageName !in used }
            .distinctBy { it.packageName }
            .sortedBy { it.appName.lowercase() }
        if (found.isNotEmpty()) {
            found.forEach { used += it.packageName }
            out += g.title to found
        }
    }
    // Catch-all: remaining with .ru TLD-style packages or cyrillic names
    val rest = installed.filter { app ->
        app.packageName !in used && (
            app.packageName.startsWith("ru.") ||
                app.appName.any { it in '\u0400'..'\u04FF' }
            )
    }.sortedBy { it.appName.lowercase() }
    if (rest.isNotEmpty()) out += "Другие RU" to rest.take(80)
    return out
}

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
            colors = ButtonDefaults.buttonColors(
                containerColor = GlassPrimary,
                contentColor = if (GlassIsLight) Color.White else GlassText
            ),
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
            "Госуслуги и банки часто видят «VPN включён» по системному интерфейсу, даже если трафик идёт напрямую.",
            style = MaterialTheme.typography.labelMedium,
            color = GlassTextMuted
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.appSearch,
            onValueChange = { vm.updateAppSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск приложений…", color = GlassTextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = GlassTextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GlassPrimary,
                unfocusedBorderColor = GlassBorder,
                focusedContainerColor = if (GlassIsLight) Color.White.copy(0.9f) else GlassSurfaceHigh,
                unfocusedContainerColor = if (GlassIsLight) Color.White.copy(0.75f) else GlassSurfaceHigh,
                focusedTextColor = GlassText,
                unfocusedTextColor = GlassText,
                cursorColor = GlassPrimary
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
    val groups = remember(installed) { collectRuApps(installed) }
    val allPkgs = remember(groups) { groups.flatMap { it.second.map { a -> a.packageName } }.toSet() }
    val initial = remember(groups, installed) {
        installed.filter { it.packageName in allPkgs && !it.proxyEnabled }.map { it.packageName }.toSet()
    }
    var checked by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Российские приложения (${allPkgs.size})",
                color = GlassText
            )
        },
        text = {
            if (groups.isEmpty()) {
                Text("Подходящих приложений не найдено.", color = GlassTextMuted)
            } else {
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "Галочка = напрямую (мимо VPN). «Применить» только для этого списка.",
                        style = MaterialTheme.typography.labelMedium,
                        color = GlassTextMuted
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { checked = allPkgs }) {
                            Text("Все", color = GlassPrimary)
                        }
                        TextButton(onClick = { checked = emptySet() }) {
                            Text("Снять", color = GlassTextMuted)
                        }
                    }
                    groups.forEach { (title, apps) ->
                        Spacer(Modifier.height(10.dp))
                        Text(title, color = GlassPrimary, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        apps.forEach { app ->
                            val pkg = app.packageName
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        checked = if (pkg in checked) checked - pkg else checked + pkg
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = pkg in checked,
                                    onCheckedChange = {
                                        checked = if (it) checked + pkg else checked - pkg
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = GlassPrimary,
                                        uncheckedColor = GlassTextMuted,
                                        checkmarkColor = if (GlassIsLight) Color.White else GlassText
                                    )
                                )
                                AppIcon(app.icon)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        app.appName,
                                        color = GlassText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1
                                    )
                                    Text(
                                        pkg,
                                        color = GlassTextMuted,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onApply(checked, allPkgs) },
                enabled = groups.isNotEmpty()
            ) { Text("Применить", color = GlassPrimary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = GlassTextMuted) }
        },
        containerColor = if (GlassIsLight) Color.White else GlassSurfaceHigh,
        titleContentColor = GlassText,
        textContentColor = GlassText
    )
}

@Composable
private fun AppRow(app: AppProxyItem, onToggle: () -> Unit) {
    val rowBg = if (GlassIsLight) Color.White.copy(alpha = 0.88f) else GlassSurfaceHigh
    val rowBorder = if (GlassIsLight) Color.Black.copy(alpha = 0.08f) else GlassBorder
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = rowBg,
        border = androidx.compose.foundation.BorderStroke(1.dp, rowBorder),
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
                color = if (app.proxyEnabled) GlassPrimary else GlassTextMuted
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
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GlassPrimary.copy(0.15f))
        )
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
