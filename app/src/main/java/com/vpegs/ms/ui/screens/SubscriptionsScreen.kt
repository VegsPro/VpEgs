package com.vpegs.ms.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpegs.ms.data.Subscription
import com.vpegs.ms.ui.components.GlassCard
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SubscriptionsScreen(vm: MainViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    var editSub by remember { mutableStateOf<Subscription?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Подписки", style = MaterialTheme.typography.titleLarge, color = GlassText, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.refreshAll() }) { Icon(Icons.Default.Refresh, null, tint = GlassPrimary) }
            IconButton(onClick = { showAdd = true }) { Icon(Icons.Default.Add, null, tint = GlassAccent) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(vm.subscriptions, key = { it.id }) { sub ->
                SubCard(
                    sub = sub,
                    onToggle = { vm.toggleSubAutoUpdate(sub.id) },
                    onDelete = { vm.removeSubscription(sub.id) },
                    onEdit = { editSub = sub }
                )
            }
        }
    }

    if (showAdd) {
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Новая подписка") },
            text = {
                OutlinedTextField(
                    url, { url = it },
                    label = { Text("Ссылка подписки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (url.isNotBlank()) {
                            vm.addSubscription("", url.trim())
                            showAdd = false
                        }
                    }
                ) { Text("Добавить") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
        )
    }

    editSub?.let { sub ->
        EditSubDialog(
            sub = sub,
            onDismiss = { editSub = null },
            onSave = { name, url ->
                vm.updateSubscription(sub.id, name, url)
                editSub = null
            }
        )
    }
}

@Composable
private fun EditSubDialog(
    sub: Subscription,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    val ctx = LocalContext.current
    var name by remember(sub.id) { mutableStateOf(sub.name) }
    var url by remember(sub.id) { mutableStateOf(sub.url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактировать") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text("Имя") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        url, { url = it },
                        label = { Text("Ссылка") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("sub", url))
                    }) {
                        Icon(Icons.Default.ContentCopy, "Копировать", tint = GlassPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), url.trim()) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun SubCard(
    sub: Subscription,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()) }
    val used = sub.uploadBytes + sub.downloadBytes
    val progress = if (sub.totalBytes > 0) (used.toFloat() / sub.totalBytes).coerceIn(0f, 1f) else 0f

    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sub.name, style = MaterialTheme.typography.titleMedium, color = GlassText, fontWeight = FontWeight.SemiBold)
                Text("${sub.serversCount} серверов", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "Изменить", tint = GlassPrimary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Удалить", tint = GlassDanger)
            }
        }

        if (sub.totalBytes > 0) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatBytes(used) + " / " + formatBytes(sub.totalBytes), style = MaterialTheme.typography.labelMedium, color = GlassText)
                if (sub.expireAt > 0) {
                    Text("до ${fmt.format(Date(sub.expireAt * 1000))}", style = MaterialTheme.typography.labelMedium, color = GlassAccent)
                }
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(GlassBorder)
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                        .background(if (progress > 0.9f) GlassDanger else GlassPrimary)
                )
            }
        }

        if (sub.announce.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(sub.announce, style = MaterialTheme.typography.bodyMedium, color = GlassTextMuted, lineHeight = 18.sp)
        }

        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Update, null, tint = if (sub.autoUpdate) GlassAccent else GlassTextMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Автообновление", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted, modifier = Modifier.weight(1f))
            Switch(
                checked = sub.autoUpdate,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedThumbColor = GlassAccent, checkedTrackColor = GlassAccent.copy(0.35f))
            )
        }
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1_099_511_627_776 -> "%.1f TB".format(b / 1_099_511_627_776.0)
    b >= 1_073_741_824 -> "%.1f GB".format(b / 1_073_741_824.0)
    b >= 1_048_576 -> "%.0f MB".format(b / 1_048_576.0)
    else -> "$b B"
}
