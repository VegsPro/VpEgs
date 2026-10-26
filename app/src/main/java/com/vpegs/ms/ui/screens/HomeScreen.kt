package com.vpegs.ms.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val sub = server?.let { s -> vm.subscriptions.find { it.id == s.subscriptionId } }

    LaunchedEffect(vm.connState) { vm.refreshPublicIp() }
    // Sync ON/OFF with real system VPN every second
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            vm.refreshVpnPresence(ctx)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 28.dp),
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

        // Server card
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
            Spacer(Modifier.height(12.dp))
        }

        // Subscription traffic bar
        if (sub != null && sub.totalBytes > 0) {
            GlassCard(Modifier.fillMaxWidth()) {
                val used = sub.uploadBytes + sub.downloadBytes
                val progress = (used.toFloat() / sub.totalBytes).coerceIn(0f, 1f)
                Text(sub.name, style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        formatHomeTraffic(used) + " / " + formatHomeTraffic(sub.totalBytes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassText
                    )
                    if (sub.expireAt > 0) {
                        val days = ((sub.expireAt * 1000 - System.currentTimeMillis()) / 86400000L).coerceAtLeast(0)
                        Text("ещё ${days}д", style = MaterialTheme.typography.labelMedium, color = GlassAccent)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(GlassBorder)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (progress > 0.9f) GlassDanger else GlassPrimary)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Public IP (fetched via SOCKS when VPN on)
        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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

        Spacer(Modifier.height(16.dp))

        // Buttons row — separate from IP card
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
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
                Text(
                    "Нажмите кнопку для подключения\n«Лучший» выберет сервер с минимальным пингом",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTextMuted
                )
            }
        }
    }
}

private fun formatHomeTraffic(b: Long): String = when {
    b >= 1_099_511_627_776 -> "%.1f TB".format(b / 1_099_511_627_776.0)
    b >= 1_073_741_824 -> "%.1f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576 -> "%.0f MB".format(b / 1_048_576.0)
    else -> "$b B"
}
