package com.vpegs.ms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpegs.ms.ui.components.GlassCard
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel

@Composable
fun AppsScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Text("Обход приложений", style = MaterialTheme.typography.titleLarge, color = GlassText)
        Spacer(Modifier.height(12.dp))
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Глобальный режим", style = MaterialTheme.typography.titleMedium, color = GlassText)
                    Text("Все приложения через VPN", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                Switch(checked = vm.globalProxyMode, onCheckedChange = { vm.updateGlobalProxyMode(it) },
                    colors = SwitchDefaults.colors(checkedThumbColor = GlassAccent, checkedTrackColor = GlassAccent.copy(0.35f)))
            }
            if (!vm.globalProxyMode) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Инверсия списка", style = MaterialTheme.typography.titleMedium, color = GlassText)
                        Text(if (vm.invertAppProxy) "Выбранные обходят VPN" else "Только выбранные через VPN",
                            style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                    }
                    Switch(checked = vm.invertAppProxy, onCheckedChange = { vm.updateInvertAppProxy(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = GlassPrimary, checkedTrackColor = GlassPrimary.copy(0.35f)))
                }
            }
        }
        if (!vm.globalProxyMode) {
            Spacer(Modifier.height(14.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(vm.appProxyList, key = { it.packageName }) { app ->
                    Surface(shape = RoundedCornerShape(12.dp), color = GlassSurfaceHigh, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(app.appName, style = MaterialTheme.typography.bodyLarge, color = GlassText)
                                if (app.isSystem) Text("Системное", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                            }
                            Switch(checked = app.proxyEnabled, onCheckedChange = { vm.toggleAppProxy(app.packageName) },
                                colors = SwitchDefaults.colors(checkedThumbColor = GlassAccent, checkedTrackColor = GlassAccent.copy(0.35f)))
                        }
                    }
                }
            }
        } else {
            Spacer(Modifier.height(20.dp))
            Text("Глобальный режим — весь трафик через VPN.\nОтключите, чтобы выбрать приложения.", style = MaterialTheme.typography.bodyMedium, color = GlassTextMuted)
        }
    }
}
