package com.regiaobs.app.ui.console

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.regiaobs.app.data.AppSettings
import com.regiaobs.app.obs.ObsSceneItem
import com.regiaobs.app.obs.ObsSnapshot
import com.regiaobs.app.obs.PreviewBitmaps
import com.regiaobs.app.ui.theme.BroadcastHaptics
import com.regiaobs.app.ui.theme.StudioColors
import com.regiaobs.app.ui.theme.tabular
import java.util.Locale

@Composable
fun ConsoleScreen(
    snapshot: ObsSnapshot,
    previews: PreviewBitmaps,
    settings: AppSettings,
    haptics: BroadcastHaptics,
    onSelectProgramScene: (String) -> Unit,
    onSelectPreviewScene: (String) -> Unit,
    onToggleStudioMode: () -> Unit,
    onTriggerTransition: () -> Unit,
    onSelectTransition: (String) -> Unit,
    onSetTransitionDuration: (Int) -> Unit,
    onTBarPosition: (Double, Boolean) -> Unit,
    onToggleStream: () -> Unit,
    onToggleRecord: () -> Unit,
    onToggleRecordPause: () -> Unit,
    onCreateChapter: () -> Unit,
    onToggleReplayBuffer: () -> Unit,
    onSaveReplayBuffer: () -> Unit,
    onToggleVirtualCam: () -> Unit,
    onToggleItemEnabled: (String, ObsSceneItem) -> Unit,
    onToggleItemLocked: (String, ObsSceneItem) -> Unit,
    onInspectSource: (String, ObsSceneItem) -> Unit,
    onCyclePreviewFps: () -> Unit
) {
    val config = LocalConfiguration.current
    val isWide = config.orientation == Configuration.ORIENTATION_LANDSCAPE || config.screenWidthDp >= 680

    val activeSceneForSources = remember(snapshot.studioMode, snapshot.previewScene, snapshot.programScene) {
        if (snapshot.studioMode && snapshot.previewScene.isNotBlank()) snapshot.previewScene else snapshot.programScene
    }
    val sceneItems = snapshot.itemsForScene(activeSceneForSources)

    if (isWide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(StudioColors.Canvas)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MonitorSection(
                    snapshot = snapshot,
                    previews = previews,
                    settings = settings,
                    haptics = haptics,
                    onToggleStudioMode = onToggleStudioMode,
                    onTriggerTransition = onTriggerTransition,
                    onSelectTransition = onSelectTransition,
                    onSetTransitionDuration = onSetTransitionDuration,
                    onTBarPosition = onTBarPosition,
                    onCyclePreviewFps = onCyclePreviewFps
                )
                TransportStrip(
                    snapshot = snapshot,
                    haptics = haptics,
                    onToggleStream = onToggleStream,
                    onToggleRecord = onToggleRecord,
                    onToggleRecordPause = onToggleRecordPause,
                    onCreateChapter = onCreateChapter,
                    onToggleReplayBuffer = onToggleReplayBuffer,
                    onSaveReplayBuffer = onSaveReplayBuffer,
                    onToggleVirtualCam = onToggleVirtualCam
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                item {
                    ScenesHeader(studioMode = snapshot.studioMode)
                }
                items(snapshot.scenes.chunked(2)) { pair ->
                    SceneRowPair(
                        pair = pair,
                        snapshot = snapshot,
                        haptics = haptics,
                        onSelectProgramScene = onSelectProgramScene,
                        onSelectPreviewScene = onSelectPreviewScene
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    QuickSourcesHeader(sceneName = activeSceneForSources)
                }
                items(sceneItems, key = { "${activeSceneForSources}_${it.sceneItemId}" }) { item ->
                    QuickSourceRow(
                        sceneName = activeSceneForSources,
                        item = item,
                        haptics = haptics,
                        onToggleEnabled = { onToggleItemEnabled(activeSceneForSources, item) },
                        onToggleLocked = { onToggleItemLocked(activeSceneForSources, item) },
                        onInspect = { onInspectSource(activeSceneForSources, item) }
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(StudioColors.Canvas),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                MonitorSection(
                    snapshot = snapshot,
                    previews = previews,
                    settings = settings,
                    haptics = haptics,
                    onToggleStudioMode = onToggleStudioMode,
                    onTriggerTransition = onTriggerTransition,
                    onSelectTransition = onSelectTransition,
                    onSetTransitionDuration = onSetTransitionDuration,
                    onTBarPosition = onTBarPosition,
                    onCyclePreviewFps = onCyclePreviewFps
                )
            }
            item {
                TransportStrip(
                    snapshot = snapshot,
                    haptics = haptics,
                    onToggleStream = onToggleStream,
                    onToggleRecord = onToggleRecord,
                    onToggleRecordPause = onToggleRecordPause,
                    onCreateChapter = onCreateChapter,
                    onToggleReplayBuffer = onToggleReplayBuffer,
                    onSaveReplayBuffer = onSaveReplayBuffer,
                    onToggleVirtualCam = onToggleVirtualCam
                )
            }
            item {
                ScenesHeader(studioMode = snapshot.studioMode)
            }
            items(snapshot.scenes.chunked(2)) { pair ->
                SceneRowPair(
                    pair = pair,
                    snapshot = snapshot,
                    haptics = haptics,
                    onSelectProgramScene = onSelectProgramScene,
                    onSelectPreviewScene = onSelectPreviewScene
                )
            }
            item {
                QuickSourcesHeader(sceneName = activeSceneForSources)
            }
            if (sceneItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioColors.Panel,
                        border = BorderStroke(1.dp, StudioColors.Border)
                    ) {
                        Text(
                            text = "No sources in this scene.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        )
                    }
                }
            } else {
                items(sceneItems, key = { "${activeSceneForSources}_${it.sceneItemId}" }) { item ->
                    QuickSourceRow(
                        sceneName = activeSceneForSources,
                        item = item,
                        haptics = haptics,
                        onToggleEnabled = { onToggleItemEnabled(activeSceneForSources, item) },
                        onToggleLocked = { onToggleItemLocked(activeSceneForSources, item) },
                        onInspect = { onInspectSource(activeSceneForSources, item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorSection(
    snapshot: ObsSnapshot,
    previews: PreviewBitmaps,
    settings: AppSettings,
    haptics: BroadcastHaptics,
    onToggleStudioMode: () -> Unit,
    onTriggerTransition: () -> Unit,
    onSelectTransition: (String) -> Unit,
    onSetTransitionDuration: (Int) -> Unit,
    onTBarPosition: (Double, Boolean) -> Unit,
    onCyclePreviewFps: () -> Unit
) {
    var transMenuExpanded by remember { mutableStateOf(false) }
    var durationMenuExpanded by remember { mutableStateOf(false) }
    var tBarVal by remember { mutableFloatStateOf(0f) }
    var lastTBarSentMs by remember { mutableLongStateOf(0L) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StudioColors.Panel,
        border = BorderStroke(1.dp, StudioColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Monitor Header Row: Studio Mode + Preview FPS + Quick Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (snapshot.studioMode) StudioColors.StudioPurpleSoft else StudioColors.Elevated,
                        border = BorderStroke(
                            1.dp,
                            if (snapshot.studioMode) StudioColors.StudioPurple else StudioColors.Border
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.tap()
                                onToggleStudioMode()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ViewQuilt,
                                contentDescription = null,
                                tint = if (snapshot.studioMode) StudioColors.StudioPurple else StudioColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = if (snapshot.studioMode) "Studio ON" else "Studio",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (snapshot.studioMode) StudioColors.TextPrimary else StudioColors.TextSecondary
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StudioColors.Elevated,
                        border = BorderStroke(1.dp, StudioColors.Border),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptics.tap()
                                onCyclePreviewFps()
                            }
                    ) {
                        val fpsLabel = if (!settings.previewEnabled || settings.previewFps <= 0) "Preview OFF" else "${settings.previewFps} FPS"
                        Text(
                            text = fpsLabel,
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = if (settings.previewEnabled && settings.previewFps > 0) StudioColors.ReplayCyan else StudioColors.TextMuted,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
                        )
                    }
                }

                // Live Telemetry pill
                Text(
                    text = String.format(
                        Locale.US,
                        "%.0f fps  •  %.1f%% CPU",
                        snapshot.stats.activeFps,
                        snapshot.stats.cpuUsage
                    ),
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = StudioColors.TextSecondary
                )
            }

            // Video Monitor Screens
            if (snapshot.studioMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MonitorFrame(
                        label = "PREVIEW",
                        sceneName = snapshot.previewScene.ifBlank { "—" },
                        tallyColor = StudioColors.TallyGreen,
                        bitmap = if (settings.previewEnabled && settings.previewFps > 0) previews.preview else null,
                        previewEnabled = settings.previewEnabled && settings.previewFps > 0,
                        modifier = Modifier.weight(1f)
                    )
                    MonitorFrame(
                        label = "PROGRAM",
                        sceneName = snapshot.programScene.ifBlank { "—" },
                        tallyColor = StudioColors.TallyRed,
                        bitmap = if (settings.previewEnabled && settings.previewFps > 0) previews.program else null,
                        previewEnabled = settings.previewEnabled && settings.previewFps > 0,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                MonitorFrame(
                    label = "PROGRAM LIVE",
                    sceneName = snapshot.programScene.ifBlank { "—" },
                    tallyColor = StudioColors.TallyRed,
                    bitmap = if (settings.previewEnabled && settings.previewFps > 0) previews.program else null,
                    previewEnabled = settings.previewEnabled && settings.previewFps > 0,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Transition Selector & Studio Take Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StudioColors.Elevated,
                        border = BorderStroke(1.dp, StudioColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { transMenuExpanded = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = snapshot.currentTransition.ifBlank { "Transition" },
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = Icons.Rounded.SwapHoriz,
                                contentDescription = null,
                                tint = StudioColors.TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = transMenuExpanded,
                        onDismissRequest = { transMenuExpanded = false }
                    ) {
                        snapshot.transitions.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(t.name) },
                                onClick = {
                                    transMenuExpanded = false
                                    haptics.tap()
                                    onSelectTransition(t.name)
                                }
                            )
                        }
                    }
                }

                Box {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = StudioColors.Elevated,
                        border = BorderStroke(1.dp, StudioColors.Border),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { durationMenuExpanded = true }
                    ) {
                        Text(
                            text = "${snapshot.transitionDurationMs} ms",
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = StudioColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = durationMenuExpanded,
                        onDismissRequest = { durationMenuExpanded = false }
                    ) {
                        listOf(150, 300, 500, 750, 1000, 1500).forEach { ms ->
                            DropdownMenuItem(
                                text = { Text("$ms ms") },
                                onClick = {
                                    durationMenuExpanded = false
                                    haptics.tap()
                                    onSetTransitionDuration(ms)
                                }
                            )
                        }
                    }
                }

                if (snapshot.studioMode) {
                    Button(
                        onClick = {
                            haptics.strong()
                            onTriggerTransition()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StudioColors.TallyRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("TRANSITION", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (snapshot.studioMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "T-BAR",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextMuted
                    )
                    Slider(
                        value = tBarVal,
                        onValueChange = { next ->
                            tBarVal = next
                            val now = System.currentTimeMillis()
                            if (now - lastTBarSentMs >= 45L) {
                                lastTBarSentMs = now
                                onTBarPosition(next.toDouble(), false)
                            }
                        },
                        onValueChangeFinished = {
                            if (tBarVal >= 0.85f) {
                                haptics.strong()
                                onTBarPosition(1.0, true)
                            } else {
                                onTBarPosition(0.0, true)
                            }
                            tBarVal = 0f
                        },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = StudioColors.TextPrimary,
                            activeTrackColor = StudioColors.TallyRed,
                            inactiveTrackColor = StudioColors.Elevated
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun MonitorFrame(
    label: String,
    sceneName: String,
    tallyColor: Color,
    bitmap: android.graphics.Bitmap?,
    previewEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF06080B))
                .border(1.5.dp, tallyColor.copy(alpha = 0.85f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "$label: $sceneName",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = if (previewEnabled) sceneName else "Preview paused",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Tally corner badge
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = tallyColor.copy(alpha = 0.92f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Text(
            text = sceneName,
            style = MaterialTheme.typography.labelMedium,
            color = StudioColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TransportStrip(
    snapshot: ObsSnapshot,
    haptics: BroadcastHaptics,
    onToggleStream: () -> Unit,
    onToggleRecord: () -> Unit,
    onToggleRecordPause: () -> Unit,
    onCreateChapter: () -> Unit,
    onToggleReplayBuffer: () -> Unit,
    onSaveReplayBuffer: () -> Unit,
    onToggleVirtualCam: () -> Unit
) {
    val out = snapshot.outputStatus
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportKey(
                title = if (out.streamActive) "LIVE" else "Stream",
                subtitle = if (out.streamActive) out.streamTimecode.substringBefore('.') else "Start Stream",
                icon = Icons.Rounded.Cast,
                active = out.streamActive,
                activeColor = StudioColors.TallyRed,
                activeBg = StudioColors.TallyRedSoft,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.strong()
                    onToggleStream()
                }
            )
            TransportKey(
                title = when {
                    out.recActive && out.recPaused -> "PAUSED"
                    out.recActive -> "REC"
                    else -> "Record"
                },
                subtitle = if (out.recActive) out.recTimecode.substringBefore('.') else "Start REC",
                icon = Icons.Rounded.FiberManualRecord,
                active = out.recActive,
                activeColor = StudioColors.RecAmber,
                activeBg = StudioColors.RecAmberSoft,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.strong()
                    onToggleRecord()
                }
            )
        }

        // Secondary Recording controls when recording is active
        if (out.recActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SmallActionChip(
                    icon = if (out.recPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                    label = if (out.recPaused) "Resume REC" else "Pause REC",
                    tint = StudioColors.RecAmber,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.tap()
                        onToggleRecordPause()
                    }
                )
                SmallActionChip(
                    icon = Icons.Rounded.BookmarkAdd,
                    label = "Add Chapter",
                    tint = StudioColors.ReplayCyan,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.tap()
                        onCreateChapter()
                    }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportKey(
                title = "Replay Buffer",
                subtitle = if (out.replayActive) "Active" else "Off",
                icon = Icons.Rounded.Replay,
                active = out.replayActive,
                activeColor = StudioColors.ReplayCyan,
                activeBg = StudioColors.ReplayCyanSoft,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.tap()
                    onToggleReplayBuffer()
                }
            )
            if (out.replayActive) {
                TransportKey(
                    title = "Save Clip",
                    subtitle = "Instant",
                    icon = Icons.Rounded.SaveAlt,
                    active = true,
                    activeColor = StudioColors.TallyGreen,
                    activeBg = StudioColors.TallyGreenSoft,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.strong()
                        onSaveReplayBuffer()
                    }
                )
            } else {
                TransportKey(
                    title = "Virtual Cam",
                    subtitle = if (out.virtualCamActive) "Active" else "Off",
                    icon = if (out.virtualCamActive) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                    active = out.virtualCamActive,
                    activeColor = StudioColors.TallyGreen,
                    activeBg = StudioColors.TallyGreenSoft,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.tap()
                        onToggleVirtualCam()
                    }
                )
            }
        }

        if (out.replayActive) {
            SmallActionChip(
                icon = if (out.virtualCamActive) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff,
                label = if (out.virtualCamActive) "Virtual Cam active (tap to stop)" else "Start Virtual Camera",
                tint = if (out.virtualCamActive) StudioColors.TallyGreen else StudioColors.TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    haptics.tap()
                    onToggleVirtualCam()
                }
            )
        }
    }
}

@Composable
private fun TransportKey(
    title: String,
    subtitle: String,
    icon: ImageVector,
    active: Boolean,
    activeColor: Color,
    activeBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (active) activeBg else StudioColors.Panel,
        border = BorderStroke(1.dp, if (active) activeColor else StudioColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) activeColor else StudioColors.TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = if (active) activeColor else StudioColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SmallActionChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = StudioColors.Panel,
        border = BorderStroke(1.dp, StudioColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = StudioColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ScenesHeader(studioMode: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Scenes",
            style = MaterialTheme.typography.titleMedium,
            color = StudioColors.TextPrimary
        )
        Text(
            text = if (studioMode) "Tap: Preview • Long-press: Program" else "Tap to go live",
            style = MaterialTheme.typography.bodySmall,
            color = StudioColors.TextSecondary
        )
    }
}

@Composable
private fun SceneRowPair(
    pair: List<com.regiaobs.app.obs.ObsScene>,
    snapshot: ObsSnapshot,
    haptics: BroadcastHaptics,
    onSelectProgramScene: (String) -> Unit,
    onSelectPreviewScene: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        for (scene in pair) {
            val isProgram = snapshot.programScene == scene.name
            val isPreview = snapshot.studioMode && snapshot.previewScene == scene.name
            SceneButton(
                name = scene.name,
                isProgram = isProgram,
                isPreview = isPreview,
                modifier = Modifier.weight(1f),
                onClick = {
                    haptics.tap()
                    if (snapshot.studioMode) onSelectPreviewScene(scene.name)
                    else onSelectProgramScene(scene.name)
                },
                onLongClick = {
                    haptics.strong()
                    onSelectProgramScene(scene.name)
                }
            )
        }
        if (pair.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SceneButton(
    name: String,
    isProgram: Boolean,
    isPreview: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val borderColor = when {
        isProgram -> StudioColors.TallyRed
        isPreview -> StudioColors.TallyGreen
        else -> StudioColors.Border
    }
    val bgColor = when {
        isProgram -> StudioColors.TallyRedSoft
        isPreview -> StudioColors.TallyGreenSoft
        else -> StudioColors.Panel
    }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isProgram -> StudioColors.TallyRed
                            isPreview -> StudioColors.TallyGreen
                            else -> StudioColors.BorderStrong
                        }
                    )
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = StudioColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isProgram || isPreview) {
                Text(
                    text = if (isProgram) "PGM" else "PVW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isProgram) StudioColors.TallyRed else StudioColors.TallyGreen
                )
            }
        }
    }
}

@Composable
private fun QuickSourcesHeader(sceneName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Quick Sources",
            style = MaterialTheme.typography.titleMedium,
            color = StudioColors.TextPrimary
        )
        Text(
            text = sceneName.ifBlank { "—" },
            style = MaterialTheme.typography.bodySmall,
            color = StudioColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickSourceRow(
    sceneName: String,
    item: ObsSceneItem,
    haptics: BroadcastHaptics,
    onToggleEnabled: () -> Unit,
    onToggleLocked: () -> Unit,
    onInspect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StudioColors.Panel,
        border = BorderStroke(1.dp, StudioColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onInspect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.sourceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (item.enabled) StudioColors.TextPrimary else StudioColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.inputKind.isNullOrBlank()) {
                    Text(
                        text = item.inputKind,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    haptics.tap()
                    onToggleEnabled()
                }) {
                    Icon(
                        imageVector = if (item.enabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = "Visibility",
                        tint = if (item.enabled) StudioColors.ReplayCyan else StudioColors.TextMuted
                    )
                }
                IconButton(onClick = {
                    haptics.tap()
                    onToggleLocked()
                }) {
                    Icon(
                        imageVector = if (item.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = "Lock",
                        tint = if (item.locked) StudioColors.RecAmber else StudioColors.TextMuted
                    )
                }
                IconButton(onClick = onInspect) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Source controls",
                        tint = StudioColors.TextSecondary
                    )
                }
            }
        }
    }
}
