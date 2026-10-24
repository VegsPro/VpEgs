package com.vpegs.ms.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpegs.ms.data.ServerNode
import com.vpegs.ms.data.Subscription
import com.vpegs.ms.ui.components.LatencyText
import com.vpegs.ms.ui.components.ProtocolBadge
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ServersScreen(vm: MainViewModel) {
    val groups = remember(vm.servers, vm.subscriptions, vm.searchQuery, vm.showOnlyFavorites) {
        vm.serversBySubscription()
    }
    val expandedIds = remember { mutableStateOf(vm.subscriptions.map { it.id }.toSet()) }

    // Expand newly added subscriptions
    LaunchedEffect(vm.subscriptions.map { it.id }) {
        expandedIds.value = expandedIds.value + vm.subscriptions.map { it.id }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Text("Серверы", style = MaterialTheme.typography.titleLarge, color = GlassText)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = { vm.updateSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск по региону / протоколу…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GlassPrimary,
                unfocusedBorderColor = GlassBorder,
                focusedContainerColor = GlassSurfaceHigh,
                unfocusedContainerColor = GlassSurfaceHigh
            )
        )

        Spacer(Modifier.height(12.dp))

        if (vm.subscriptions.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Нет подписок", style = MaterialTheme.typography.titleMedium, color = GlassText)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Добавьте подписку на вкладке «Подписки».\nДемо-серверов нет — только ваши.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTextMuted
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(groups, key = { it.first.id }) { (sub, nodes) ->
                    SubscriptionGroup(
                        sub = sub,
                        nodes = nodes,
                        expanded = expandedIds.value.contains(sub.id),
                        onToggleExpand = {
                            expandedIds.value = if (expandedIds.value.contains(sub.id))
                                expandedIds.value - sub.id
                            else
                                expandedIds.value + sub.id
                        },
                        onSelect = { vm.selectServer(it) },
                        onFavorite = { vm.toggleFavorite(it) },
                        onRefresh = { vm.refreshAll() }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubscriptionGroup(
    sub: Subscription,
    nodes: List<ServerNode>,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: (ServerNode) -> Unit,
    onFavorite: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GlassSurfaceHigh)
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    sub.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = GlassText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${nodes.size} серверов · ${fmt.format(Date(sub.lastUpdated))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTextMuted
                )
                if (sub.autoUpdate) {
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Update, null, tint = GlassAccent, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Авто · ${sub.updateIntervalHours} ч",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassAccent
                        )
                    }
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Update, "Обновить", tint = GlassPrimarySoft)
            }
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                null,
                tint = GlassTextMuted
            )
        }

        if (expanded) {
            HorizontalDivider(color = GlassBorder)
            if (nodes.isEmpty()) {
                Text(
                    "Нет серверов в этой подписке",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTextMuted
                )
            } else {
                nodes.forEachIndexed { index, server ->
                    RegionRow(
                        server = server,
                        onSelect = { onSelect(server) },
                        onFav = { onFavorite(server.id) }
                    )
                    if (index < nodes.lastIndex) {
                        HorizontalDivider(
                            color = GlassBorder.copy(alpha = 0.5f),
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
    onSelect: () -> Unit,
    onFav: () -> Unit
) {
    val selected = server.isSelected
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) GlassPrimary.copy(0.12f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(server.flag, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                server.country.ifBlank { server.name },
                style = MaterialTheme.typography.bodyLarge,
                color = GlassText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProtocolBadge(server.protocol)
                Spacer(Modifier.width(8.dp))
                LatencyText(server.latencyMs)
            }
        }
        IconButton(onClick = onFav) {
            Icon(
                if (server.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                null,
                tint = if (server.isFavorite) GlassWarning else GlassTextMuted
            )
        }
    }
}
