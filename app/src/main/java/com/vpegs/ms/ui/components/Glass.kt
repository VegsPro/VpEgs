package com.vpegs.ms.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vpegs.ms.data.Protocol
import com.vpegs.ms.data.ServerNode
import com.vpegs.ms.ui.theme.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val mod = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(GlassSurfaceHigh)
        .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Column(mod.padding(16.dp), content = content)
}

@Composable
fun ProtocolBadge(p: Protocol) {
    val c = Color(p.color)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = c.copy(0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.copy(0.35f))
    ) {
        Text(p.display, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium, color = c, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
    }
}

@Composable
fun LatencyText(ms: Int?) {
    if (ms == null) return
    val c = when { ms < 50 -> GlassAccent; ms < 100 -> GlassWarning; else -> GlassDanger }
    Text("${ms} ms", style = MaterialTheme.typography.labelMedium, color = c, fontWeight = FontWeight.Medium)
}

@Composable
fun ServerRow(server: ServerNode, onClick: () -> Unit, onFav: () -> Unit) {
    val selected = server.isSelected
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) GlassPrimary.copy(0.12f) else GlassSurfaceHigh)
            .border(1.dp, if (selected) GlassPrimary.copy(0.5f) else GlassBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(server.flag, fontSize = 26.sp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(server.name, style = MaterialTheme.typography.titleMedium, color = GlassText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProtocolBadge(server.protocol)
                Spacer(Modifier.width(8.dp))
                LatencyText(server.latencyMs)
            }
        }
        IconButton(onClick = onFav) {
            Icon(if (server.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder, null,
                tint = if (server.isFavorite) GlassWarning else GlassTextMuted)
        }
    }
}

@Composable
fun GlassPowerButton(connected: Boolean, connecting: Boolean, onClick: () -> Unit) {
    val inf = rememberInfiniteTransition(label = "p")
    val scale by inf.animateFloat(1f, if (connected || connecting) 1.05f else 1f,
        infiniteRepeatable(tween(1000, easing = EaseInOut), RepeatMode.Reverse), label = "s")
    val glow by inf.animateFloat(0.2f, if (connected) 0.55f else 0.12f,
        infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "g")
    val ring = when {
        connected -> GlassAccent
        connecting -> GlassPrimarySoft
        else -> GlassTextMuted
    }
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(170.dp).scale(scale).background(
            Brush.radialGradient(listOf(ring.copy(glow), Color.Transparent)), CircleShape))
        Surface(
            onClick = onClick,
            Modifier.size(128.dp),
            shape = CircleShape,
            color = if (connected) GlassAccent.copy(0.12f) else GlassSurfaceHigh,
            border = androidx.compose.foundation.BorderStroke(2.5.dp, Brush.linearGradient(listOf(ring, ring.copy(0.3f)))),
            shadowElevation = 10.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (connecting) CircularProgressIndicator(Modifier.size(44.dp), color = GlassPrimary, strokeWidth = 3.dp)
                else Text(if (connected) "ON" else "OFF", style = MaterialTheme.typography.displayLarge,
                    color = if (connected) GlassAccent else GlassTextMuted, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            }
        }
    }
}

@Composable
fun ActionChip(label: String, loading: Boolean = false, accent: Boolean = false, onClick: () -> Unit) {
    val bg = if (accent) GlassPrimary.copy(0.2f) else GlassSurfaceHigh
    val border = if (accent) GlassPrimary.copy(0.5f) else GlassBorder
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = androidx.compose.foundation.BorderStroke(1.dp, border),
        modifier = Modifier.height(44.dp)
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = GlassPrimarySoft)
            else Text(label, style = MaterialTheme.typography.labelLarge, color = GlassText)
        }
    }
}

@Composable
fun StatPill(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(GlassSurfaceHigh)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = GlassTextMuted)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = GlassText, fontWeight = FontWeight.SemiBold)
    }
}
