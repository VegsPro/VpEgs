package com.vpegs.ms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.vpegs.ms.data.ServerNode
import com.vpegs.ms.data.Subscription
import com.vpegs.ms.ui.components.GlassProgressBar
import com.vpegs.ms.ui.components.ProtocolBadge
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel
import com.vpegs.ms.viewmodel.ServerSort
import kotlin.math.roundToInt

private sealed class ServerListItem {
    data class Header(val sub: Subscription, val count: Int, val expanded: Boolean) : ServerListItem()
    data class Row(val server: ServerNode) : ServerListItem()
}

@Composable
fun ServersScreen(vm: MainViewModel) {
    val groups = remember(
        vm.servers,
        vm.subscriptions,
        vm.serverSort,
        vm.showOnlyFavorites,
        vm.showBuiltinServers,
        vm.searchQuery
    ) { vm.serversBySubscription() }

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
                "Зажмите ☰ и перетащите подписку",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTextMuted
            )
            Spacer(Modifier.height(8.dp))
            DragReorderSubsList(vm)
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

        // Flat virtualized list: only visible headers/rows are composed
        val flat = remember(groups, vm.expandedSubIds) {
            buildList {
                for ((sub, nodes) in groups) {
                    val exp = sub.id in vm.expandedSubIds
                    add(ServerListItem.Header(sub, nodes.size, exp))
                    if (exp) nodes.forEach { add(ServerListItem.Row(it)) }
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            items(
                items = flat,
                key = {
                    when (it) {
                        is ServerListItem.Header -> "h-${it.sub.id}"
                        is ServerListItem.Row -> "s-${it.server.id}"
                    }
                },
                contentType = {
                    when (it) {
                        is ServerListItem.Header -> "header"
                        is ServerListItem.Row -> "row"
                    }
                }
            ) { item ->
                when (item) {
                    is ServerListItem.Header -> SubHeader(
                        sub = item.sub,
                        count = item.count,
                        expanded = item.expanded,
                        isPinging = vm.pingingIds.isNotEmpty() &&
                            vm.servers.any { it.subscriptionId == item.sub.id && it.id in vm.pingingIds },
                        onToggle = { vm.toggleSubExpanded(item.sub.id) },
                        onRefresh = { vm.refreshSubscription(item.sub.id) },
                        onPing = { vm.pingSubscriptionServers(item.sub.id) }
                    )
                    is ServerListItem.Row -> RegionRow(
                        server = item.server,
                        selected = item.server.id == vm.selectedServer?.id || item.server.isSelected,
                        showProtocol = vm.showServerProtocol,
                        showIp = vm.showServerIp,
                        isPinging = item.server.id in vm.pingingIds,
                        onSelect = { vm.selectServer(item.server) },
                        onFav = { vm.toggleFavorite(item.server.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DragReorderSubsList(vm: MainViewModel) {
    val density = LocalDensity.current
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(density) { 64.dp.toPx() }
    val list = vm.subscriptions

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        itemsIndexed(list, key = { _, s -> s.id }) { index, sub ->
            val isDrag = draggingId == sub.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDrag) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDrag) dragOffsetY else 0f
                        shadowElevation = if (isDrag) 12f else 0f
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(GlassSurfaceHigh.copy(alpha = if (isDrag) 0.85f else 0.55f))
                    .border(1.dp, if (isDrag) GlassPrimary else GlassBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "☰",
                    color = GlassTextMuted,
                    fontSize = 20.sp,
                    modifier = Modifier.pointerInput(sub.id, index, list.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingId = sub.id
                                dragOffsetY = 0f
                            },
                            onDragEnd = {
                                val from = list.indexOfFirst { it.id == draggingId }
                                if (from >= 0) {
                                    val delta = (dragOffsetY / itemHeightPx).roundToInt()
                                    val to = (from + delta).coerceIn(0, list.lastIndex)
                                    if (from != to) vm.moveSubscription(from, to)
                                }
                                draggingId = null
                                dragOffsetY = 0f
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffsetY = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                            }
                        )
                    }
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(sub.name, color = GlassText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${sub.serversCount} серверов", color = GlassTextMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SubHeader(
    sub: Subscription,
    count: Int,
    expanded: Boolean,
    isPinging: Boolean = false,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onPing: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurfaceHigh.copy(alpha = 0.5f))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                sub.name,
                color = GlassText,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text("$count серверов", color = GlassTextMuted, style = MaterialTheme.typography.labelMedium)
            if (sub.announce.isNotBlank()) {
                Text(
                    sub.announce,
                    color = GlassAccent.copy(0.9f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onPing, enabled = !isPinging) {
            if (isPinging) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = GlassAccent
                )
            } else {
                Icon(Icons.Outlined.Speed, "Пинг", tint = GlassAccent, modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Outlined.Update, "Обновить", tint = GlassPrimary, modifier = Modifier.size(20.dp))
        }
        Icon(
            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            null,
            tint = GlassTextMuted
        )
    }
}

@Composable
private fun RegionRow(
    server: ServerNode,
    selected: Boolean = server.isSelected,
    showProtocol: Boolean,
    showIp: Boolean,
    isPinging: Boolean,
    onSelect: () -> Unit,
    onFav: () -> Unit
) {
    val dead = server.latencyMs != null && server.latencyMs < 0
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                if (selected) GlassPrimary.copy(alpha = 0.22f)
                else Color.Transparent
            )
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) GlassPrimary.copy(alpha = 0.85f) else Color.Transparent,
                shape = shape
            )
            .clickable(enabled = !dead, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .alpha(if (dead) 0.45f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(server.flag.ifBlank { "🌐" }, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    server.country.ifBlank { server.name },
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        dead -> GlassTextMuted
                        selected -> GlassPrimary
                        else -> GlassText
                    },
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (selected) {
                    Spacer(Modifier.width(6.dp))
                    Text("✓", color = GlassPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            if (showIp) {
                val ip = server.exitIp.ifBlank { server.address }
                if (ip.isNotBlank()) {
                    Text(ip, style = MaterialTheme.typography.labelSmall, color = GlassTextMuted, maxLines = 1)
                }
            }
            if (showProtocol) {
                Spacer(Modifier.height(4.dp))
                ProtocolBadge(server.protocol)
            }
        }
        when {
            isPinging -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = GlassPrimary)
            dead -> Text("не отвечает", style = MaterialTheme.typography.labelSmall, color = GlassDanger)
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
