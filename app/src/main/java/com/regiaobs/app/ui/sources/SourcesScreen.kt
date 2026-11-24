package com.regiaobs.app.ui.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.regiaobs.app.obs.ObsMediaAction
import com.regiaobs.app.obs.ObsMediaState
import com.regiaobs.app.obs.ObsMediaStatus
import com.regiaobs.app.obs.ObsSceneItem
import com.regiaobs.app.obs.ObsSnapshot
import com.regiaobs.app.ui.theme.BroadcastHaptics
import com.regiaobs.app.ui.theme.StudioColors
import com.regiaobs.app.ui.theme.tabular
import java.util.Locale

@Composable
fun SourcesScreen(
    snapshot: ObsSnapshot,
    haptics: BroadcastHaptics,
    onEnsureSceneLoaded: (String) -> Unit,
    onToggleItemEnabled: (String, ObsSceneItem) -> Unit,
    onToggleItemLocked: (String, ObsSceneItem) -> Unit,
    onMoveItemIndex: (String, ObsSceneItem, Int) -> Unit,
    onMediaAction: (String, ObsMediaAction) -> Unit,
    onMediaSeek: (String, Long) -> Unit,
    onInspectSource: (String, ObsSceneItem) -> Unit
) {
    val defaultScene = remember(snapshot.programScene, snapshot.previewScene, snapshot.studioMode, snapshot.scenes) {
        when {
            snapshot.studioMode && snapshot.previewScene.isNotBlank() -> snapshot.previewScene
            snapshot.programScene.isNotBlank() -> snapshot.programScene
            else -> snapshot.scenes.firstOrNull()?.name.orEmpty()
        }
    }
    var selectedScene by remember { mutableStateOf(defaultScene) }
    LaunchedEffect(snapshot.scenes, defaultScene) {
        if (selectedScene.isBlank() || snapshot.scenes.none { it.name == selectedScene }) {
            selectedScene = defaultScene
        }
    }
    LaunchedEffect(selectedScene) {
        if (selectedScene.isNotBlank()) {
            onEnsureSceneLoaded(selectedScene)
        }
    }

    val items = snapshot.itemsForScene(selectedScene)
    val expandedGroups = remember { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Canvas)
    ) {
        // Scene selector pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            snapshot.scenes.forEach { scene ->
                val isSelected = scene.name == selectedScene
                val isProgram = scene.name == snapshot.programScene
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isSelected && isProgram -> StudioColors.TallyRedSoft
                        isSelected -> StudioColors.ReplayCyanSoft
                        else -> StudioColors.Panel
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isSelected && isProgram -> StudioColors.TallyRed
                            isSelected -> StudioColors.ReplayCyan
                            else -> StudioColors.Border
                        }
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            haptics.tap()
                            selectedScene = scene.name
                        }
                ) {
                    Text(
                        text = scene.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioColors.TextPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (items.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioColors.Panel,
                        border = BorderStroke(1.dp, StudioColors.Border)
                    ) {
                        Text(
                            text = "No sources in scene '$selectedScene'.",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        )
                    }
                }
            } else {
                itemsIndexed(items, key = { _, it -> "${selectedScene}_${it.sceneItemId}" }) { idx, item ->
                    val mediaStatus = snapshot.mediaStatuses[item.sourceName]
                    val isExpanded = expandedGroups[item.sceneItemId] ?: true
                    SourceItemCard(
                        sceneName = selectedScene,
                        item = item,
                        mediaStatus = mediaStatus,
                        canMoveUp = idx > 0,
                        canMoveDown = idx < items.lastIndex,
                        isGroupExpanded = isExpanded,
                        haptics = haptics,
                        onToggleGroupExpand = {
                            expandedGroups[item.sceneItemId] = !isExpanded
                        },
                        onToggleEnabled = { onToggleItemEnabled(selectedScene, item) },
                        onToggleLocked = { onToggleItemLocked(selectedScene, item) },
                        onMoveUp = {
                            // Items list is reversed (top layer first), so moving up increases OBS sceneItemIndex
                            val targetObsIdx = items[idx - 1].index
                            onMoveItemIndex(selectedScene, item, targetObsIdx)
                        },
                        onMoveDown = {
                            val targetObsIdx = items[idx + 1].index
                            onMoveItemIndex(selectedScene, item, targetObsIdx)
                        },
                        onMediaAction = { action -> onMediaAction(item.sourceName, action) },
                        onMediaSeek = { ms -> onMediaSeek(item.sourceName, ms) },
                        onInspect = { onInspectSource(selectedScene, item) }
                    )

                    if (item.isGroup && isExpanded && item.groupChildren.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(start = 18.dp, top = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item.groupChildren.forEach { child ->
                                SourceItemCard(
                                    sceneName = item.sourceName,
                                    item = child,
                                    mediaStatus = snapshot.mediaStatuses[child.sourceName],
                                    canMoveUp = false,
                                    canMoveDown = false,
                                    isGroupExpanded = false,
                                    haptics = haptics,
                                    onToggleGroupExpand = {},
                                    onToggleEnabled = { onToggleItemEnabled(item.sourceName, child) },
                                    onToggleLocked = { onToggleItemLocked(item.sourceName, child) },
                                    onMoveUp = {},
                                    onMoveDown = {},
                                    onMediaAction = { action -> onMediaAction(child.sourceName, action) },
                                    onMediaSeek = { ms -> onMediaSeek(child.sourceName, ms) },
                                    onInspect = { onInspectSource(item.sourceName, child) }
                                )
                            }
                        }
                    }
                }
            }

            // Global Media Sources quick deck if user has media inputs outside current scene
            val extraMedia = snapshot.mediaStatuses.filterKeys { name ->
                items.none { it.sourceName == name }
            }
            if (extraMedia.isNotEmpty()) {
                item {
                    Text(
                        text = "Other Media Sources",
                        style = MaterialTheme.typography.titleSmall,
                        color = StudioColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                extraMedia.forEach { (name, status) ->
                    item(key = "extra_media_$name") {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = StudioColors.Panel,
                            border = BorderStroke(1.dp, StudioColors.Border)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StudioColors.TextPrimary
                                )
                                InlineMediaPlayer(
                                    status = status,
                                    haptics = haptics,
                                    onAction = { onMediaAction(name, it) },
                                    onSeek = { onMediaSeek(name, it) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceItemCard(
    sceneName: String,
    item: ObsSceneItem,
    mediaStatus: ObsMediaStatus?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isGroupExpanded: Boolean,
    haptics: BroadcastHaptics,
    onToggleGroupExpand: () -> Unit,
    onToggleEnabled: () -> Unit,
    onToggleLocked: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMediaAction: (ObsMediaAction) -> Unit,
    onMediaSeek: (Long) -> Unit,
    onInspect: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = StudioColors.Panel,
        border = BorderStroke(
            1.dp,
            if (item.enabled) StudioColors.BorderStrong else StudioColors.Border
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onInspect)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (item.isGroup) {
                        IconButton(
                            onClick = onToggleGroupExpand,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isGroupExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = "Toggle group",
                                tint = StudioColors.ReplayCyan
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            tint = StudioColors.RecAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.sourceName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            color = if (item.enabled) StudioColors.TextPrimary else StudioColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val kindLabel = item.inputKind ?: if (item.isGroup) "Group" else "Scene"
                        Text(
                            text = kindLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = StudioColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (canMoveUp) {
                        IconButton(
                            onClick = { haptics.tap(); onMoveUp() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowUpward, "Move up", modifier = Modifier.size(16.dp), tint = StudioColors.TextSecondary)
                        }
                    }
                    if (canMoveDown) {
                        IconButton(
                            onClick = { haptics.tap(); onMoveDown() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowDownward, "Move down", modifier = Modifier.size(16.dp), tint = StudioColors.TextSecondary)
                        }
                    }
                    IconButton(
                        onClick = { haptics.tap(); onToggleEnabled() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (item.enabled) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                            contentDescription = "Show/Hide",
                            tint = if (item.enabled) StudioColors.ReplayCyan else StudioColors.TextMuted
                        )
                    }
                    IconButton(
                        onClick = { haptics.tap(); onToggleLocked() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (item.locked) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                            contentDescription = "Lock/Unlock",
                            tint = if (item.locked) StudioColors.RecAmber else StudioColors.TextMuted
                        )
                    }
                    IconButton(
                        onClick = onInspect,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Edit source",
                            tint = StudioColors.TextSecondary
                        )
                    }
                }
            }

            if (mediaStatus != null) {
                InlineMediaPlayer(
                    status = mediaStatus,
                    haptics = haptics,
                    onAction = onMediaAction,
                    onSeek = onMediaSeek
                )
            }
        }
    }
}

@Composable
private fun InlineMediaPlayer(
    status: ObsMediaStatus,
    haptics: BroadcastHaptics,
    onAction: (ObsMediaAction) -> Unit,
    onSeek: (Long) -> Unit
) {
    val dur = status.durationMs.coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var sliderMs by remember(status.cursorMs) { mutableFloatStateOf(status.cursorMs.toFloat()) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = StudioColors.Elevated,
        border = BorderStroke(1.dp, StudioColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    val isPlaying = status.state == ObsMediaState.PLAYING
                    IconButton(
                        onClick = {
                            haptics.tap()
                            onAction(if (isPlaying) ObsMediaAction.PAUSE else ObsMediaAction.PLAY)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = StudioColors.ReplayCyan
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.tap()
                            onAction(ObsMediaAction.RESTART)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Replay, "Restart", tint = StudioColors.TextPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            haptics.tap()
                            onAction(ObsMediaAction.STOP)
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Rounded.Stop, "Stop", tint = StudioColors.TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Text(
                    text = "${formatMmSs(if (dragging) sliderMs.toLong() else status.cursorMs)} / ${formatMmSs(status.durationMs)}",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = StudioColors.TextSecondary
                )
            }

            if (status.durationMs > 0L) {
                Slider(
                    value = (if (dragging) sliderMs else status.cursorMs.toFloat()).coerceIn(0f, dur.toFloat()),
                    onValueChange = {
                        dragging = true
                        sliderMs = it
                    },
                    onValueChangeFinished = {
                        dragging = false
                        onSeek(sliderMs.toLong())
                    },
                    valueRange = 0f..dur.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = StudioColors.ReplayCyan,
                        activeTrackColor = StudioColors.ReplayCyan,
                        inactiveTrackColor = StudioColors.Panel
                    )
                )
            }
        }
    }
}

private fun formatMmSs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
