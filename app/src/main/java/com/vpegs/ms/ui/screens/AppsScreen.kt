package com.vpegs.ms.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Public
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

/** Packages/prefixes that are never "Russian apps" even if label is in Russian. */
private val NON_RU_PACKAGE_BLOCK = listOf(
    "com.google.", "com.android.", "com.samsung.", "com.sec.", "com.miui.",
    "com.xiaomi.", "com.huawei.", "com.oppo.", "com.realme.", "com.oneplus.",
    "com.coloros.", "com.oplus.", "com.transsion.", "com.nothing.",
    "com.microsoft.", "com.facebook.", "com.instagram.", "com.whatsapp",
    "com.telegram.", "org.telegram.", "com.spotify.", "com.netflix.",
    "com.discord.", "com.twitter.", "com.zhiliaoapp.", "com.ss.android.",
    "com.apple.", "com.chrome.", "com.brave.", "org.mozilla.",
    "com.android.chrome", "com.google.android", "com.android.vending",
    "com.android.settings", "com.android.calculator", "com.android.calendar",
    "com.android.deskclock", "com.android.contacts", "com.android.camera",
    "com.mi.android", "com.samsung.android", "com.sec.android"
)

private fun isBlockedForeign(pkg: String): Boolean {
    val p = pkg.lowercase()
    return NON_RU_PACKAGE_BLOCK.any { p.startsWith(it) || p.contains(it) }
}

private val RU_GROUPS = listOf(
    RuGroup(
        "Яндекс",
        listOf(
            "yandex", "ru.yandex", "com.yandex", "zen.yandex", "ya.android",
            "yango", "auto.ru", "com.yango", "ru.kinopoisk"
        ),
        listOf("яндекс", "yandex", "алиса", "авто.ру")
    ),
    RuGroup(
        "VK / Mail",
        listOf(
            "vkontakte", "com.vk.", "ru.vk.", "vk.im", "vk.clips", "vk.video",
            "vk.admin", "vk.calls", "vk.music", "ru.mail.", "com.mail.ru",
            "my.mail", "ok.android", "odnoklassniki", "ru.ok."
        ),
        listOf("вконтакте", "одноклассники", "mail.ru", "мейл.ру")
    ),
    RuGroup(
        "Макс",
        listOf("ru.oneme.app", "oneme.app", "ru.ok.max", "max.ru"),
        listOf("мессенджер макс", "max —")
    ),
    RuGroup(
        "Госуслуги / Гос",
        listOf(
            "gosuslugi", "goskey", "mincifry", "nalog", "mos.ru", "dit.mos",
            "gu.spb", "ru.rts.gosuslugi", "ru.rostel"
        ),
        listOf("госуслуги", "госключ", "мой налог", "налог ру")
    ),
    RuGroup(
        "Образование",
        listOf(
            "myschool", "мояшкола", "mes.mos", "mos.school", "dnevnik",
            "eljur", "netschool", "edu.mos", "school.mos", "ya.class",
            "uchiru", "foxford", "skyeng", "maximum.edu", "resh.edu",
            "yaklass", "ru.mobile.edu", "ru.mos.edusschool"
        ),
        listOf("моя школа", "электронный дневник", "дневник.ру", "фоксфорд", "учу.ру", "якласс")
    ),
    RuGroup(
        "Маркетплейсы",
        listOf(
            "wildberries", "wb.ru", "wb.android", "ozon", "aliexpress.ru",
            "sbermegamarket", "megamarket", "lamoda", "detmir", "mvideo",
            "eldorado", "citilink", "dns.shop", "leroy", "flowwow", "kazanexpress",
            "ru.ozon", "com.ozon", "ru.wildberries", "com.wildberries"
        ),
        listOf("wildberries", "вайлдберриз", "ozon", "озон", "ламода", "мегамаркет", "dns", "м.видео", "kazanexpress")
    ),
    RuGroup(
        "Банки",
        listOf(
            "sberbank", "sber.", "tinkoff", "idamob.tinkoff", "alfabank", "vtb24",
            "vtb.", "openbank", "gazprombank", "rshb", "rosbank", "homecredit",
            "sovcombank", "raiffeisen", "yoomoney", "yookassa", "qiwi",
            "tochka", "modulbank", "rocketbank", "psbank", "bspb"
        ),
        listOf("сбербанк", "сбер ", "тинькофф", "т-банк", "альфа-банк", "втб", "юmoney", "qiwi")
    ),
    RuGroup(
        "Операторы",
        listOf("mts.", "megafon", "tele2", "beeline", "yota", "t2.ru", "rostel", "ru.mts", "ru.beeline"),
        listOf("мтс", "мегафон", "теле2", "билайн", "йота")
    ),
    RuGroup(
        "Карты / Транспорт",
        listOf(
            "dublgis", "2gis", "moscow.transport", "troika", "citymobil",
            "citi.mobil", "gett", "indriver", "ru.taxi", "maxim.taxi",
            "ru.yandex.yandexnavi", "yandex.maps"
        ),
        listOf("2гис", "2gis", "ситимобил", "тройка", "яндекс.go", "яндекс про")
    ),
    RuGroup(
        "Объявления / Работа",
        listOf("avito", "youla", "hh.android", "headhunter", "superjob", "drom.", "farpost", "cian"),
        listOf("авито", "юла", "хедхантер", "дром", "циан")
    ),
    RuGroup(
        "Медиа",
        listOf(
            "kinopoisk", "ivi.client", "rutube", "more.tv", "start.android",
            "premier.android", "wink.", "zvuk", "boom.android", "zvooq",
            "okko", "kion", "wink.rt", "tv1000"
        ),
        listOf("кинопоиск", "ivi", "rutube", "wink", "звук", "premier", "окко", "kion")
    ),
    RuGroup(
        "Доставка / Ритейл",
        listOf(
            "samokat", "magnit", "pyaterochka", "x5.", "lenta.", "deliveryclub",
            "dc.client", "chibbis", "vkusvill", "perekrestok", "kuper", "sbermarket"
        ),
        listOf("самокат", "магнит", "пятёрочка", "лента", "delivery club", "вкусвилл", "перекрёсток", "сбермаркет", "купер")
    ),
    RuGroup(
        "Прочее RU",
        listOf(
            "rustore", "vk.store", "sberdevices", "salute", "beru.",
            "rzd.passengers", "russianpost", "pochta", "gosuslugi",
            "sovcombank", "tinkoff", "2gis"
        ),
        listOf("rustore", "сбердевайс", "ржд", "почта россии")
    ),
)

private fun matchGroup(app: AppProxyItem, group: RuGroup): Boolean {
    if (isBlockedForeign(app.packageName)) return false
    val p = app.packageName.lowercase()
    val n = app.appName.lowercase()
    if (group.packageNeedles.any { needle ->
            val k = needle.lowercase()
            p == k || p.startsWith("$k.") || p.contains(k)
        }) return true
    // Name match only if package looks local (ru./or known) — avoids "Калькулятор" etc.
    if (group.nameNeedles.any { n.contains(it.lowercase()) }) {
        if (p.startsWith("ru.") || p.contains(".ru.") || !isBlockedForeign(p)) {
            // still require at least one package needle-ish or ru. prefix for pure name hits
            if (p.startsWith("ru.") || p.startsWith("com.yandex") || p.startsWith("com.vk") ||
                p.contains("yandex") || p.contains("ozon") || p.contains("wildberries") ||
                p.contains("sber") || p.contains("tinkoff") || p.contains("gosuslugi") ||
                p.contains("avito") || p.contains("mail") || p.contains("vkontakte")
            ) return true
        }
    }
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
    // Strict catch-all: only ru.* packages not foreign-blocked and not already listed
    val rest = installed.filter { app ->
        app.packageName !in used &&
            !isBlockedForeign(app.packageName) &&
            app.packageName.lowercase().startsWith("ru.") &&
            // skip pure system-ish short tools under ru.android if any
            !app.packageName.lowercase().startsWith("ru.android.")
    }.sortedBy { it.appName.lowercase() }
    if (rest.isNotEmpty()) out += "Другие RU" to rest.take(100)
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

        Spacer(Modifier.height(12.dp))
        DomainBypassSection(vm)

        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Text("Блокировка рекламы", style = MaterialTheme.typography.titleMedium, color = GlassText)
            Text(
                "Галочка — блокировать известные рекламные домены в трафике через VPN. Нужен реконнект.",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTextMuted
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.setAllAdBlock(true) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Блокировать всю", color = GlassText, maxLines = 1) }
                OutlinedButton(
                    onClick = { vm.setAllAdBlock(false) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Разблокировать всю", color = GlassText, maxLines = 1) }
            }
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
                    AppRow(app, onToggle = { vm.toggleAppProxy(app.packageName) }, onAdToggle = { vm.toggleAppAdBlock(app.packageName) })
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
private fun AppRow(app: AppProxyItem, onToggle: () -> Unit, onAdToggle: () -> Unit) {
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
            Checkbox(
                checked = app.adBlockEnabled,
                onCheckedChange = { onAdToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = GlassPrimary,
                    uncheckedColor = GlassTextMuted
                )
            )
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
private fun DomainBypassSection(vm: MainViewModel) {
    var input by remember { mutableStateOf("") }
    GlassCard(Modifier.fillMaxWidth()) {
        Text("Обход доменов", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Text(
            "domen.ru, https://domen.ru/ — мимо VPN. Иконка 🌐 WEB. Нужен реконнект.",
            style = MaterialTheme.typography.labelMedium,
            color = GlassTextMuted
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("example.com", color = GlassTextMuted) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GlassPrimary,
                    unfocusedBorderColor = GlassBorder,
                    focusedTextColor = GlassText,
                    unfocusedTextColor = GlassText,
                    cursorColor = GlassPrimary
                )
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (vm.addDomainBypass(input)) input = ""
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GlassPrimary)
            ) { Text("Добавить") }
        }
        AnimatedVisibility(
            visible = vm.domainBypassList.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
            Spacer(Modifier.height(10.dp))
            vm.domainBypassList.forEach { domain ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(GlassPrimary.copy(0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Public, contentDescription = null, tint = GlassPrimary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(domain, color = GlassText, maxLines = 1)
                        Text("WEB · напрямую", style = MaterialTheme.typography.labelSmall, color = GlassTextMuted)
                    }
                    IconButton(onClick = { vm.removeDomainBypass(domain) }) {
                        Icon(Icons.Outlined.Delete, null, tint = GlassTextMuted)
                    }
                }
            }
            } // Column
        } // AnimatedVisibility
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
