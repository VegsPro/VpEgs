package com.vpegs.ms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpegs.ms.data.Subscription
import com.vpegs.ms.ui.components.GlassCard
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SubscriptionsScreen(vm: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Подписки", style = MaterialTheme.typography.titleLarge, color = GlassText, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Default.Refresh, null, tint = GlassPrimary) }
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null, tint = GlassAccent) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(vm.subscriptions, key = { it.id }) { sub ->
                SubCard(sub, onToggle = { vm.toggleSubAutoUpdate(sub.id) }, onDelete = { vm.removeSubscription(sub.id) })
            }
        }
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Новая подписка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(url, { url = it }, label = { Text("URL / vless://…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { if (url.isNotBlank()) { vm.addSubscription(name, url); showAdd = false } }, enabled = url.isNotBlank()) { Text("Добавить") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } },
            containerColor = GlassSurface
        )
    }
}

@Composable
private fun SubCard(sub: Subscription, onToggle: () -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sub.name, style = MaterialTheme.typography.titleMedium, color = GlassText)
                Text("${sub.serversCount} серверов · ${fmt.format(Date(sub.lastUpdated))}", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = GlassDanger.copy(0.8f)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(sub.url.take(50) + if (sub.url.length > 50) "…" else "", style = MaterialTheme.typography.bodyMedium, color = GlassTextMuted.copy(0.7f), maxLines = 1)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Update, null, tint = if (sub.autoUpdate) GlassAccent else GlassTextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Авто · ${sub.updateIntervalHours} ч", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted, modifier = Modifier.weight(1f))
            Switch(checked = sub.autoUpdate, onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = GlassAccent, checkedTrackColor = GlassAccent.copy(0.35f)))
        }
    }
}
