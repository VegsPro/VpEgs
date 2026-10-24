package com.vpegs.ms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vpegs.ms.ui.components.GlassCard
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel

@Composable
fun SettingsScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 32.dp)) {
        Text("Настройки", style = MaterialTheme.typography.titleLarge, color = GlassText)
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Update, null, tint = GlassPrimary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Автообновление подписок", style = MaterialTheme.typography.bodyLarge, color = GlassText)
                    Text("При каждом запуске", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                Switch(checked = vm.autoUpdateOnLaunch, onCheckedChange = { vm.updateAutoUpdateOnLaunch(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = GlassAccent, checkedTrackColor = GlassAccent.copy(0.35f)))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Протоколы", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Proto("VLESS / Reality", "Xray · Reality · Vision")
            Proto("VMess", "V2Ray")
            Proto("Trojan", "TLS + Trojan")
            Proto("Shadowsocks", "AEAD")
            Proto("Hysteria2", "UDP · высокая скорость")
            Proto("TUIC", "QUIC")
        }
        Spacer(Modifier.height(16.dp))
        Text("О VpEgs", style = MaterialTheme.typography.titleMedium, color = GlassText)
        Spacer(Modifier.height(8.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Info(Icons.Outlined.Info, "VpEgs", "v1.0.0 · Glass Black-Blue")
            Info(Icons.Outlined.Security, "Конфиденциальность", "Данные только на устройстве")
            Info(Icons.Outlined.Widgets, "Виджет", "Пинг · Обновить · Лучший сервер")
        }
        Spacer(Modifier.height(20.dp))
        Text("VpEgs — личный клиент для ваших подписок и конфигов.\nGlass-стиль · виджет · выбор лучшего сервера по пингу.",
            style = MaterialTheme.typography.bodyMedium, color = GlassTextMuted)
    }
}

@Composable
private fun Proto(name: String, desc: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, color = GlassText)
            Text(desc, style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
        }
        Text("✓", color = GlassAccent)
    }
}

@Composable
private fun Info(icon: ImageVector, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = GlassPrimary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = GlassText)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
        }
    }
}
