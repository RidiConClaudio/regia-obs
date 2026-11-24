package com.regiaobs.app.ui.system

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.regiaobs.app.data.AppSettings
import com.regiaobs.app.obs.ConnectionState
import com.regiaobs.app.obs.ObsJson
import com.regiaobs.app.obs.ObsSnapshot
import com.regiaobs.app.ui.theme.BroadcastHaptics
import com.regiaobs.app.ui.theme.StudioColors
import com.regiaobs.app.ui.theme.tabular
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.util.Locale

@Composable
fun SystemScreen(
    connectionState: ConnectionState,
    snapshot: ObsSnapshot,
    settings: AppSettings,
    haptics: BroadcastHaptics,
    onSelectProfile: (String) -> Unit,
    onSelectSceneCollection: (String) -> Unit,
    onTriggerHotkey: (String) -> Unit,
    onSendRawRequest: (String, JsonObject?) -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onDisconnect: () -> Unit
) {
    var hotkeyQuery by remember { mutableStateOf("") }
    var profileMenu by remember { mutableStateOf(false) }
    var collectionMenu by remember { mutableStateOf(false) }
    var showRawPanel by remember { mutableStateOf(false) }
    var rawType by remember { mutableStateOf("GetStats") }
    var rawDataText by remember { mutableStateOf("{}") }

    val filteredHotkeys = remember(snapshot.hotkeys, hotkeyQuery) {
        val q = hotkeyQuery.trim()
        if (q.isEmpty()) snapshot.hotkeys.take(25)
        else snapshot.hotkeys.filter { it.contains(q, ignoreCase = true) }.take(40)
    }

    val stats = snapshot.stats
    val out = snapshot.outputStatus

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Canvas),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Server Connection Card
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioColors.Panel,
                border = BorderStroke(1.dp, StudioColors.Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val title = (connectionState as? ConnectionState.Connected)?.serverName ?: "OBS Studio"
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioColors.TextPrimary
                        )
                        Text(
                            text = "OBS ${snapshot.obsVersion.ifBlank { "—" }}  •  WS ${snapshot.wsVersion.ifBlank { "5.x" }}",
                            style = MaterialTheme.typography.bodySmall.tabular(),
                            color = StudioColors.TextSecondary
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.strong()
                            onDisconnect()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PowerSettingsNew,
                            contentDescription = null,
                            tint = StudioColors.TallyRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Disconnect", color = StudioColors.TallyRed)
                    }
                }
            }
        }

        // Live Telemetry Metrics
        item {
            Text(
                text = "Live Telemetry",
                style = MaterialTheme.typography.titleMedium,
                color = StudioColors.TextPrimary
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelemetryTile(
                        label = "OBS CPU",
                        value = String.format(Locale.US, "%.1f%%", stats.cpuUsage),
                        accent = if (stats.cpuUsage > 70) StudioColors.TallyRed else StudioColors.ReplayCyan,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "Active FPS",
                        value = String.format(Locale.US, "%.2f", stats.activeFps),
                        accent = StudioColors.TallyGreen,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "Frame Render",
                        value = String.format(Locale.US, "%.1f ms", stats.averageFrameRenderTimeMs),
                        accent = if (stats.averageFrameRenderTimeMs > 16.0) StudioColors.RecAmber else StudioColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TelemetryTile(
                        label = "Dropped Frames",
                        value = "${stats.renderSkippedFrames + stats.outputSkippedFrames}",
                        accent = if (stats.renderSkippedFrames + stats.outputSkippedFrames > 0) StudioColors.RecAmber else StudioColors.TallyGreen,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "Memory",
                        value = String.format(Locale.US, "%.0f MB", stats.memoryUsageMb),
                        accent = StudioColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryTile(
                        label = "Free Disk",
                        value = String.format(Locale.US, "%.1f GB", stats.availableDiskSpaceMb / 1024.0),
                        accent = if (stats.availableDiskSpaceMb < 5000.0 && stats.availableDiskSpaceMb > 0.0) StudioColors.TallyRed else StudioColors.TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (out.streamActive || out.recActive) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioColors.Panel,
                        border = BorderStroke(1.dp, StudioColors.Border)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Stream skipped: ${out.streamSkippedFrames}/${out.streamTotalFrames}",
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = StudioColors.TextSecondary
                            )
                            Text(
                                text = "REC: ${String.format(Locale.US, "%.1f MB", out.recBytes / (1024.0 * 1024.0))}",
                                style = MaterialTheme.typography.bodySmall.tabular(),
                                color = StudioColors.RecAmber
                            )
                        }
                    }
                }
            }
        }

        // Profiles & Scene Collections
        item {
            Text(
                text = "Profile & Scene Collections",
                style = MaterialTheme.typography.titleMedium,
                color = StudioColors.TextPrimary
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioColors.Panel,
                        border = BorderStroke(1.dp, StudioColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { profileMenu = true }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("OBS Profile", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextSecondary)
                            Text(
                                text = snapshot.currentProfile.ifBlank { "—" },
                                style = MaterialTheme.typography.titleSmall,
                                color = StudioColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                        snapshot.profiles.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    profileMenu = false
                                    haptics.tap()
                                    onSelectProfile(p)
                                }
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioColors.Panel,
                        border = BorderStroke(1.dp, StudioColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { collectionMenu = true }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Scene Collection", style = MaterialTheme.typography.labelSmall, color = StudioColors.TextSecondary)
                            Text(
                                text = snapshot.currentSceneCollection.ifBlank { "—" },
                                style = MaterialTheme.typography.titleSmall,
                                color = StudioColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    DropdownMenu(expanded = collectionMenu, onDismissRequest = { collectionMenu = false }) {
                        snapshot.sceneCollections.forEach { c ->
                            DropdownMenuItem(
                                text = { Text(c) },
                                onClick = {
                                    collectionMenu = false
                                    haptics.tap()
                                    onSelectSceneCollection(c)
                                }
                            )
                        }
                    }
                }
            }
        }

        // Settings
        item {
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.titleMedium,
                color = StudioColors.TextPrimary
            )
        }
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioColors.Panel,
                border = BorderStroke(1.dp, StudioColors.Border)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsSwitchRow(
                        title = "Live video preview",
                        subtitle = "Periodically refresh Program and Preview frames",
                        checked = settings.previewEnabled,
                        onCheckedChange = { onUpdateSettings { s -> s.copy(previewEnabled = it) } }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preview rate:",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        listOf(1, 2, 4, 8).forEach { fps ->
                            FilterChip(
                                selected = settings.previewFps == fps,
                                onClick = { onUpdateSettings { s -> s.copy(previewFps = fps, previewEnabled = true) } },
                                label = { Text("$fps FPS", style = MaterialTheme.typography.labelSmall.tabular()) }
                            )
                        }
                    }

                    SettingsSwitchRow(
                        title = "Confirm before stopping LIVE",
                        subtitle = "Prevent accidental taps while on air",
                        checked = settings.confirmBeforeStopLive,
                        onCheckedChange = { onUpdateSettings { s -> s.copy(confirmBeforeStopLive = it) } }
                    )
                    SettingsSwitchRow(
                        title = "Confirm before stopping REC",
                        subtitle = "Ask before finalizing and saving recording",
                        checked = settings.confirmBeforeStopRec,
                        onCheckedChange = { onUpdateSettings { s -> s.copy(confirmBeforeStopRec = it) } }
                    )
                    SettingsSwitchRow(
                        title = "Keep screen awake",
                        subtitle = "Prevent display sleep during broadcast control",
                        checked = settings.keepScreenOn,
                        onCheckedChange = { onUpdateSettings { s -> s.copy(keepScreenOn = it) } }
                    )
                    SettingsSwitchRow(
                        title = "Haptic feedback",
                        subtitle = "Tactile vibration on scenes, mute, and macros",
                        checked = settings.hapticsEnabled,
                        onCheckedChange = { onUpdateSettings { s -> s.copy(hapticsEnabled = it) } }
                    )
                    SettingsSwitchRow(
                        title = "Persistent background notification",
                        subtitle = "Keep OBS WebSocket connection alive in background",
                        checked = settings.foregroundServiceEnabled,
                        onCheckedChange = { onUpdateSettings { s -> s.copy(foregroundServiceEnabled = it) } }
                    )
                }
            }
        }

        // Hotkeys
        item {
            Text(
                text = "OBS Hotkeys (${snapshot.hotkeys.size})",
                style = MaterialTheme.typography.titleMedium,
                color = StudioColors.TextPrimary
            )
        }
        item {
            OutlinedTextField(
                value = hotkeyQuery,
                onValueChange = { hotkeyQuery = it },
                placeholder = { Text("Search OBS hotkey...") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        items(filteredHotkeys, key = { it }) { hk ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StudioColors.Panel,
                border = BorderStroke(1.dp, StudioColors.Border),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.strong()
                        onTriggerHotkey(hk)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Keyboard, null, tint = StudioColors.TextMuted, modifier = Modifier.size(16.dp))
                        Text(
                            text = hk,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Rounded.Bolt, "Trigger", tint = StudioColors.ReplayCyan, modifier = Modifier.size(18.dp))
                }
            }
        }

        // Power-user Raw Request Console
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioColors.Panel,
                border = BorderStroke(1.dp, StudioColors.Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showRawPanel = !showRawPanel },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Advanced WebSocket v5 Console",
                            style = MaterialTheme.typography.titleSmall,
                            color = StudioColors.TextPrimary
                        )
                        Icon(Icons.Rounded.SwapHoriz, null, tint = StudioColors.TextSecondary)
                    }
                    if (showRawPanel) {
                        OutlinedTextField(
                            value = rawType,
                            onValueChange = { rawType = it },
                            label = { Text("requestType") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = rawDataText,
                            onValueChange = { rawDataText = it },
                            label = { Text("requestData (JSON)") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val parsed = rawDataText.trim().takeIf { it.isNotEmpty() && it != "{}" }?.let {
                                    runCatching { ObsJson.codec.parseToJsonElement(it).jsonObject }.getOrNull()
                                }
                                onSendRawRequest(rawType.trim(), parsed)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioColors.ReplayCyan,
                                contentColor = StudioColors.Canvas
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.Send, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Send OBS Command")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = StudioColors.Panel,
        border = BorderStroke(1.dp, StudioColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = StudioColors.TextSecondary
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.tabular(),
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = StudioColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = StudioColors.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
