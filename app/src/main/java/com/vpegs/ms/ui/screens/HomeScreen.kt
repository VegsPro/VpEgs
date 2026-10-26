package com.vpegs.ms.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vpegs.ms.data.ConnState
import com.vpegs.ms.ui.components.*
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel

@Composable
fun HomeScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val connected = vm.connState == ConnState.CONNECTED
    val connecting = vm.connState == ConnState.CONNECTING || vm.connState == ConnState.DISCONNECTING
    val server = vm.selectedServer
    LaunchedEffect(vm.connState) { vm.refreshPublicIp() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!vm.lastError.isNullOrBlank()) {
            Text(vm.lastError!!, color = GlassDanger, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            when (vm.connState) {
                ConnState.CONNECTED -> "Подключено"
                ConnState.CONNECTING -> "Подключение…"
                ConnState.DISCONNECTING -> "Отключение…"
                else -> "Отключено"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (connected) GlassAccent else GlassTextMuted
        )

        Spacer(Modifier.height(16.dp))
        GlassPowerButton(connected, connecting) {
            activity?.let { vm.prepareAndToggle(it) }
        }
        Spacer(Modifier.height(20.dp))

        if (server != null) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(server.flag, style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(server.name, style = MaterialTheme.typography.titleMedium, color = GlassText)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ProtocolBadge(server.protocol)
                            Spacer(Modifier.width(8.dp))
                            LatencyText(server.latencyMs ?: vm.lastPingMs)
                        }
                    }
                    if (connected) Icon(Icons.Outlined.Speed, null, tint = GlassAccent)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Quick actions: Ping / Refresh / Best
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // IP card above chips is injected separately
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Текущий IP", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                        Text(
                            when {
                                vm.ipLoading -> "определение…"
                                !vm.publicIp.isNullOrBlank() -> vm.publicIp!!
                                else -> "н/д"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = GlassText
                        )
                    }
                    TextButton(onClick = { vm.refreshPublicIp() }) {
                        Text("Обновить", color = GlassPrimary)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
                        ActionChip("Пинг", vm.isPinging) { vm.pingCurrent() }
            ActionChip("Обновить", false) { vm.refreshCurrentServer() }
            ActionChip("Лучший", vm.isScanningBest, accent = true) { vm.selectBestServer(activity) }
        }

        Spacer(Modifier.height(18.dp))

        if (connected) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("↑", vm.fmtBytes(vm.stats.uploadBytes), Modifier.weight(1f))
                StatPill("↓", vm.fmtBytes(vm.stats.downloadBytes), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatPill("Скор. ↑", vm.stats.speedUp, Modifier.weight(1f))
                StatPill("Скор. ↓", vm.stats.speedDown, Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            StatPill("Время", vm.fmtDuration(vm.stats.durationSec), Modifier.fillMaxWidth())
        } else {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Нажмите кнопку для подключения", style = MaterialTheme.typography.bodyMedium, color = GlassTextMuted)
                Spacer(Modifier.height(6.dp))
                Text("«Лучший» выберет сервер с минимальным пингом", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted.copy(0.7f))
            }
        }
    }
}
