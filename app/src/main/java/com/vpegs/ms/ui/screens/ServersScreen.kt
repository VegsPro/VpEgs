package com.vpegs.ms.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vpegs.ms.ui.components.ServerRow
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel

@Composable
fun ServersScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Серверы (${vm.filteredServers.size})", style = MaterialTheme.typography.titleLarge, color = GlassText)
            IconButton(onClick = { vm.toggleFavoritesFilter() }) {
                Icon(Icons.Outlined.Star, null, tint = if (vm.showOnlyFavorites) GlassWarning else GlassTextMuted)
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = { vm.updateSearch(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск…") },
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(vm.filteredServers, key = { it.id }) { s ->
                ServerRow(s, onClick = { vm.selectServer(s) }, onFav = { vm.toggleFavorite(s.id) })
            }
        }
    }
}
