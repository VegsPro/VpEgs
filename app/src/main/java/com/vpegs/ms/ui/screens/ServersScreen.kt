package com.vpegs.ms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpegs.ms.data.ServerNode
import com.vpegs.ms.data.Subscription
import com.vpegs.ms.ui.components.GlassProgressBar
import com.vpegs.ms.ui.components.ProtocolBadge
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel
import com.vpegs.ms.viewmodel.ServerSort
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ServersScreen(vm: MainViewModel) {
    // Cheap keys only — avoid recompute on every latency tick of unrelated fields
    val groups by remember {
        derivedStateOf { vm.serversBySubscription() }
    }
    // Collapse all by default for performance; expand on demand
    var expandedIds by remember { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Серверы", style = MaterialTheme.typography.titleLarge, color = GlassText, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.reorderSubsMode = !vm.reorderSubsMode }) {
                Icon(
                    Icons.Default.Reorder,
                    contentDescription = "Порядок",
                    tint = if (vm.reorderSubsMode) GlassPrimary else GlassTextMuted
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (vm.reorderSubsMode) {
            Text(
                "Порядок подписок — стрелки вверх/вниз",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTextMuted
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                itemsIndexed(vm.subscriptions, key = { _, s -> s.id }) { index, sub ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassSurfaceHigh.copy(alpha = 0.55f))
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("☰", color = GlassTextMuted, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, color = GlassText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${sub.serversCount} серверов", color = GlassTextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(
                            onClick = { vm.reorderSubscription(sub.id, -1) },
                            enabled = index > 0
                        ) { Icon(Icons.Default.KeyboardArrowUp, null, tint = GlassPrimary) }
                        IconButton(
                            onClick = { vm.reorderSubscription(sub.id, 1) },
                            enabled = index < vm.subscriptions.lastIndex
                        ) { Icon(Icons.Default.KeyboardArrowDown, null, tint = GlassPrimary) }
                    }
                }
            }
            return@Column
        }

        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = { vm.updateSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск по региону / протоколу…", color = GlassTextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = GlassTextMuted) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GlassPrimary,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = GlassText,
                unfocusedTextColor = GlassText
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Сортировка", color = GlassTextMuted, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Box {
                OutlinedButton(onClick = { sortMenu = true }) {
                    Text(vm.serverSort.title, color = GlassText)
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    ServerSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.title) },
                            onClick = {
                                vm.updateServerSort(s)
                                sortMenu = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            FilterChip(
                selected = vm.showOnlyFavorites,
                onClick = { vm.toggleFavoritesFilter() },
                label = { Text("★") }
            )
        }

        if (vm.workProgress > 0f || vm.workStatus.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            GlassProgressBar(vm.workProgress, label = vm.workStatus.ifBlank { null })
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(groups, key = { it.first.id }) { (sub, nodes) ->
                val open = sub.id in expandedIds
                SubGroupCard(
                    sub = sub,
                    nodes = nodes,
                    expanded = open,
                    showProtocol = vm.showServerProtocol,
                    showIp = vm.showServerIp,
                    pingingIds = vm.pingingIds,
                    onToggle = {
                        expandedIds = if (open) expandedIds - sub.id else expandedIds + sub.id
                    },
                    onSelect = { vm.selectServer(it) },
                    onFavorite = { vm.toggleFavorite(it) },
                    onRefresh = { vm.refreshSubscription(sub.id) }
                )
            }
        }
    }
}

@Composable
private fun SubGroupCard(
    sub: Subscription,
    nodes: List<ServerNode>,
    expanded: Boolean,
    showProtocol: Boolean,
    showIp: Boolean,
    pingingIds: Set<String>,
    onToggle: () -> Unit,
    onSelect: (ServerNode) -> Unit,
    onFavorite: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassSurfaceHigh.copy(alpha = 0.45f))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(sub.name, color = GlassText, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    "${nodes.size} серверов",
                    color = GlassTextMuted,
                    style = MaterialTheme.typography.labelMedium
                )
                if (sub.announce.isNotBlank()) {
                    Text(sub.announce, color = GlassAccent.copy(0.9f), style = MaterialTheme.typography.labelSmall, maxLines = 2)
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Update, null, tint = GlassPrimary, modifier = Modifier.size(20.dp))
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = GlassTextMuted
            )
        }
        if (expanded) {
            HorizontalDivider(color = GlassBorder.copy(alpha = 0.5f))
            // Cap visible rows cost: only compose nodes for expanded group
            nodes.forEachIndexed { index, server ->
                key(server.id) {
                    RegionRow(
                        server = server,
                        showProtocol = showProtocol,
                        showIp = showIp,
                        isPinging = server.id in pingingIds,
                        onSelect = { onSelect(server) },
                        onFav = { onFavorite(server.id) }
                    )
                    if (index < nodes.lastIndex) {
                        HorizontalDivider(
                            color = GlassBorder.copy(alpha = 0.4f),
                            modifier = Modifier.padding(start = 56.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionRow(
    server: ServerNode,
    showProtocol: Boolean,
    showIp: Boolean,
    isPinging: Boolean,
    onSelect: () -> Unit,
    onFav: () -> Unit
) {
    val selected = server.isSelected
    val dead = server.latencyMs != null && server.latencyMs < 0
    val dim = if (dead) 0.45f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) GlassPrimary.copy(0.12f) else Color.Transparent)
            .clickable(enabled = !dead, onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .alpha(dim),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(server.flag.ifBlank { "🌐" }, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                server.country.ifBlank { server.name },
                style = MaterialTheme.typography.bodyLarge,
                color = if (dead) GlassTextMuted else GlassText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showIp) {
                val ipLine = when {
                    server.exitIp.isNotBlank() -> server.exitIp
                    server.address.isNotBlank() -> server.address
                    else -> null
                }
                if (ipLine != null) {
                    Text(ipLine, style = MaterialTheme.typography.labelSmall, color = GlassTextMuted, maxLines = 1)
                }
            }
            if (showProtocol) {
                Spacer(Modifier.height(4.dp))
                ProtocolBadge(server.protocol)
            }
        }
        when {
            isPinging -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = GlassPrimary
                )
            }
            dead -> {
                Text("не отвечает", style = MaterialTheme.typography.labelSmall, color = GlassDanger)
            }
            server.latencyMs != null -> {
                val ms = server.latencyMs
                val c = when {
                    ms < 80 -> GlassAccent
                    ms < 200 -> GlassWarning
                    else -> GlassDanger
                }
                Text("${ms} ms", style = MaterialTheme.typography.labelMedium, color = c, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onFav, modifier = Modifier.size(32.dp)) {
            Icon(
                if (server.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                null,
                tint = if (server.isFavorite) GlassWarning else GlassTextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
