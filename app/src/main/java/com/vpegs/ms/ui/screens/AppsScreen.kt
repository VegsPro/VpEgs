package com.vpegs.ms.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vpegs.ms.data.AppProxyItem
import com.vpegs.ms.ui.components.GlassCard
import com.vpegs.ms.ui.theme.*
import com.vpegs.ms.viewmodel.MainViewModel

@Composable
fun AppsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadInstalledApps(context) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 8.dp)) {
        Text("Обход приложений", style = MaterialTheme.typography.titleLarge, color = GlassText)
        Spacer(Modifier.height(12.dp))

        GlassCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Глобальный режим", style = MaterialTheme.typography.titleMedium, color = GlassText)
                    Text("Все приложения через VPN", style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
                }
                Switch(
                    checked = vm.globalProxyMode,
                    onCheckedChange = { vm.updateGlobalProxyMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = GlassAccent,
                        checkedTrackColor = GlassAccent.copy(0.35f)
                    )
                )
            }
            if (!vm.globalProxyMode) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = GlassBorder)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Инверсия списка", style = MaterialTheme.typography.titleMedium, color = GlassText)
                        Text(
                            if (vm.invertAppProxy) "Выбранные обходят VPN"
                            else "Только выбранные через VPN",
                            style = MaterialTheme.typography.labelMedium,
                            color = GlassTextMuted
                        )
                    }
                    Switch(
                        checked = vm.invertAppProxy,
                        onCheckedChange = { vm.updateInvertAppProxy(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GlassPrimary,
                            checkedTrackColor = GlassPrimary.copy(0.35f)
                        )
                    )
                }
            }
        }

        if (!vm.globalProxyMode) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = vm.appSearch,
                onValueChange = { vm.updateAppSearch(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск приложений…") },
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
            Spacer(Modifier.height(10.dp))

            if (vm.appsLoading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = GlassPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text("Загрузка приложений…", color = GlassTextMuted)
                    }
                }
            } else {
                Text(
                    "${vm.filteredApps.size} приложений",
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTextMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(vm.filteredApps, key = { it.packageName }) { app ->
                        AppRow(app, onToggle = { vm.toggleAppProxy(app.packageName) })
                    }
                }
            }
        } else {
            Spacer(Modifier.height(20.dp))
            Text(
                "Глобальный режим — весь трафик через VPN.\nОтключите, чтобы выбрать приложения вручную.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTextMuted
            )
        }
    }
}

@Composable
private fun AppRow(app: AppProxyItem, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = GlassSurfaceHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(app.icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.bodyLarge, color = GlassText, maxLines = 1)
                Text(
                    if (app.isSystem) "Системное" else app.packageName,
                    style = MaterialTheme.typography.labelMedium,
                    color = GlassTextMuted,
                    maxLines = 1
                )
            }
            Switch(
                checked = app.proxyEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = GlassAccent,
                    checkedTrackColor = GlassAccent.copy(0.35f)
                )
            )
        }
    }
}

@Composable
private fun AppIcon(drawable: Drawable?) {
    val bmp = remember(drawable) { drawable?.toBitmapCompat(96) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )
    } else {
        Box(
            Modifier
                .size(36.dp)
                .then(Modifier),
            contentAlignment = Alignment.Center
        ) {
            Text("📦", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun Drawable.toBitmapCompat(size: Int): Bitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return Bitmap.createScaledBitmap(bitmap, size, size, true)
    }
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, size, size)
    draw(canvas)
    return bmp
}
