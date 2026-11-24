package com.regiaobs.app.ui.deck

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import com.regiaobs.app.macro.DeckPad
import com.regiaobs.app.macro.DeckPresets
import com.regiaobs.app.macro.MacroAction
import com.regiaobs.app.macro.PadFeedback
import com.regiaobs.app.macro.RecordControlMode
import com.regiaobs.app.macro.ReplayControlMode
import com.regiaobs.app.macro.describeAction
import com.regiaobs.app.obs.ObsMediaAction
import com.regiaobs.app.obs.ObsSnapshot
import com.regiaobs.app.obs.ToggleMode
import com.regiaobs.app.ui.theme.StudioColors
import com.regiaobs.app.ui.theme.padIconForKey
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PadEditorSheet(
    initialPad: DeckPad?,
    snapshot: ObsSnapshot,
    onSave: (DeckPad) -> Unit,
    onTestPad: (DeckPad) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val padId = remember(initialPad) { initialPad?.id ?: UUID.randomUUID().toString() }
    var label by remember(initialPad) { mutableStateOf(initialPad?.label ?: "New Pad") }
    var subtitle by remember(initialPad) { mutableStateOf(initialPad?.subtitle ?: "") }
    var iconKey by remember(initialPad) { mutableStateOf(initialPad?.iconKey ?: "bolt") }
    var colorHex by remember(initialPad) { mutableStateOf(initialPad?.colorHex ?: 0xFF1A2230L) }
    var feedback by remember(initialPad) { mutableStateOf(initialPad?.feedbackBinding) }
    val actions = remember(initialPad) {
        mutableStateListOf<MacroAction>().apply {
            addAll(initialPad?.actions.orEmpty())
        }
    }

    var showActionPicker by remember { mutableStateOf(false) }
    var showFeedbackMenu by remember { mutableStateOf(false) }

    fun currentPad() = DeckPad(
        id = padId,
        label = label.trim().ifBlank { "Pad" },
        subtitle = subtitle.trim(),
        iconKey = iconKey,
        colorHex = colorHex,
        actions = actions.toList(),
        feedbackBinding = feedback
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioColors.Panel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialPad == null) "Create Macro Pad" else "Edit Pad",
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioColors.TextPrimary
                )
                Row {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete pad",
                                tint = StudioColors.TallyRed
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = StudioColors.TextSecondary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.weight(1.3f)
                )
                OutlinedTextField(
                    value = subtitle,
                    onValueChange = { subtitle = it },
                    label = { Text("Subtitle") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Color Swatches
            Text(
                text = "Pad color",
                style = MaterialTheme.typography.labelMedium,
                color = StudioColors.TextSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DeckPresets.swatchPalette.forEach { (name, hex) ->
                    val selected = colorHex == hex
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(hex))
                            .border(
                                width = if (selected) 2.5.dp else 1.dp,
                                color = if (selected) StudioColors.ReplayCyan else StudioColors.Border,
                                shape = CircleShape
                            )
                            .clickable { colorHex = hex }
                    )
                }
            }

            // Icon Picker
            Text(
                text = "Icon",
                style = MaterialTheme.typography.labelMedium,
                color = StudioColors.TextSecondary
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeckPresets.availableIconKeys.forEach { key ->
                    val selected = iconKey == key
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) StudioColors.ReplayCyanSoft else StudioColors.Elevated,
                        border = BorderStroke(1.dp, if (selected) StudioColors.ReplayCyan else StudioColors.Border),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { iconKey = key }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = padIconForKey(key),
                                contentDescription = key,
                                tint = if (selected) StudioColors.ReplayCyan else StudioColors.TextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Feedback Binding
            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Elevated,
                    border = BorderStroke(1.dp, StudioColors.Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showFeedbackMenu = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status LED: ${describeFeedback(feedback)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioColors.ReplayCyan
                        )
                    }
                }
                DropdownMenu(
                    expanded = showFeedbackMenu,
                    onDismissRequest = { showFeedbackMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("No indicator") },
                        onClick = { feedback = null; showFeedbackMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Stream LIVE active") },
                        onClick = { feedback = PadFeedback.StreamActive; showFeedbackMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Recording active") },
                        onClick = { feedback = PadFeedback.RecordActive; showFeedbackMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Replay Buffer active") },
                        onClick = { feedback = PadFeedback.ReplayActive; showFeedbackMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Virtual Cam active") },
                        onClick = { feedback = PadFeedback.VirtualCamActive; showFeedbackMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Studio Mode active") },
                        onClick = { feedback = PadFeedback.StudioModeActive; showFeedbackMenu = false }
                    )
                    snapshot.scenes.forEach { scene ->
                        DropdownMenuItem(
                            text = { Text("Program Scene: ${scene.name}") },
                            onClick = {
                                feedback = PadFeedback.ProgramScene(scene.name)
                                showFeedbackMenu = false
                            }
                        )
                    }
                    snapshot.audioInputs.forEach { audio ->
                        DropdownMenuItem(
                            text = { Text("Audio muted: ${audio.name}") },
                            onClick = {
                                feedback = PadFeedback.AudioMuted(audio.name)
                                showFeedbackMenu = false
                            }
                        )
                    }
                }
            }

            // Action Steps Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Action sequence (${actions.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioColors.TextPrimary
                )
                TextButton(onClick = { showActionPicker = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add step")
                }
            }

            if (actions.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Elevated,
                    border = BorderStroke(1.dp, StudioColors.Border)
                ) {
                    Text(
                        text = "No actions added yet. Tap \"Add step\" to build a macro or single action.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    actions.forEachIndexed { idx, action ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = StudioColors.Elevated,
                            border = BorderStroke(1.dp, StudioColors.Border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${idx + 1}. ${action.describeAction()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = StudioColors.TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row {
                                    if (idx > 0) {
                                        IconButton(
                                            onClick = {
                                                val item = actions.removeAt(idx)
                                                actions.add(idx - 1, item)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowUpward,
                                                contentDescription = "Move up",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (idx < actions.lastIndex) {
                                        IconButton(
                                            onClick = {
                                                val item = actions.removeAt(idx)
                                                actions.add(idx + 1, item)
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ArrowDownward,
                                                contentDescription = "Move down",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { actions.removeAt(idx) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Remove",
                                            tint = StudioColors.TallyRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { onTestPad(currentPad()) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Test")
                }
                Button(
                    onClick = { onSave(currentPad()) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioColors.ReplayCyan,
                        contentColor = StudioColors.Canvas
                    ),
                    modifier = Modifier.weight(1.4f)
                ) {
                    Text("Save Pad", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showActionPicker) {
        ActionBuilderDialog(
            snapshot = snapshot,
            onActionAdded = { newAction ->
                actions.add(newAction)
                showActionPicker = false
            },
            onDismiss = { showActionPicker = false }
        )
    }
}

private fun describeFeedback(f: PadFeedback?): String = when (f) {
    null -> "None"
    is PadFeedback.ProgramScene -> "Program Scene: ${f.sceneName}"
    is PadFeedback.PreviewScene -> "Preview Scene: ${f.sceneName}"
    is PadFeedback.StreamActive -> "Stream LIVE"
    is PadFeedback.RecordActive -> "Recording REC"
    is PadFeedback.ReplayActive -> "Replay Buffer"
    is PadFeedback.VirtualCamActive -> "Virtual Cam"
    is PadFeedback.StudioModeActive -> "Studio Mode"
    is PadFeedback.AudioMuted -> "Audio muted: ${f.inputName}"
    is PadFeedback.SourceVisible -> "Source visible: ${f.sourceName}"
    is PadFeedback.FilterEnabled -> "Filter enabled: ${f.filterName}"
}

private enum class ActionCategory(val label: String) {
    SCENE_PROGRAM("Switch Scene (Program)"),
    SCENE_PREVIEW("Switch Scene (Preview)"),
    TRANSITION("Trigger Studio Transition"),
    STUDIO_MODE("Toggle Studio Mode"),
    STREAM("Stream Control (LIVE)"),
    RECORD("Record Control (REC)"),
    REPLAY("Replay Buffer Control"),
    VCAM("Virtual Camera Control"),
    AUDIO_MUTE("Mute / Unmute Audio"),
    AUDIO_VOL("Set Volume dB"),
    SOURCE_VIS("Source Visibility"),
    FILTER("Toggle Filter"),
    MEDIA("Media Player Control"),
    TEXT("Edit Text Source"),
    BROWSER_REFRESH("Refresh Browser Source"),
    HOTKEY("Trigger OBS Hotkey"),
    PROFILE("Switch OBS Profile"),
    COLLECTION("Switch Scene Collection"),
    DELAY("Delay (ms)")
}

@Composable
private fun ActionBuilderDialog(
    snapshot: ObsSnapshot,
    onActionAdded: (MacroAction) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<ActionCategory?>(null) }

    val defaultScene = snapshot.scenes.firstOrNull()?.name ?: snapshot.programScene
    val defaultInput = snapshot.audioInputs.firstOrNull()?.name
        ?: snapshot.inputKindsByName.keys.firstOrNull().orEmpty()

    var arg1 by remember(selectedCategory) {
        mutableStateOf(
            when (selectedCategory) {
                ActionCategory.SCENE_PROGRAM,
                ActionCategory.SCENE_PREVIEW,
                ActionCategory.SOURCE_VIS -> defaultScene
                ActionCategory.AUDIO_MUTE,
                ActionCategory.AUDIO_VOL,
                ActionCategory.MEDIA,
                ActionCategory.TEXT,
                ActionCategory.BROWSER_REFRESH -> defaultInput
                ActionCategory.FILTER -> snapshot.filtersBySource.keys.firstOrNull() ?: defaultInput
                ActionCategory.HOTKEY -> snapshot.hotkeys.firstOrNull().orEmpty()
                ActionCategory.PROFILE -> snapshot.profiles.firstOrNull().orEmpty()
                ActionCategory.COLLECTION -> snapshot.sceneCollections.firstOrNull().orEmpty()
                ActionCategory.DELAY -> "350"
                else -> ""
            }
        )
    }
    var arg2 by remember(selectedCategory) {
        mutableStateOf(
            when (selectedCategory) {
                ActionCategory.AUDIO_VOL -> "-6.0"
                ActionCategory.SOURCE_VIS -> snapshot.itemsForScene(defaultScene).firstOrNull()?.sourceName.orEmpty()
                ActionCategory.FILTER -> snapshot.filtersBySource.values.firstOrNull()?.firstOrNull()?.name.orEmpty()
                else -> ""
            }
        )
    }
    var toggleMode by remember { mutableStateOf(ToggleMode.TOGGLE) }
    var recMode by remember { mutableStateOf(RecordControlMode.TOGGLE) }
    var replayMode by remember { mutableStateOf(ReplayControlMode.SAVE) }
    var mediaAction by remember { mutableStateOf(ObsMediaAction.PLAY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioColors.Panel,
        title = {
            Text(
                text = selectedCategory?.label ?: "Choose OBS Action",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            if (selectedCategory == null) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(ActionCategory.entries) { _, cat ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = StudioColors.Elevated,
                            border = BorderStroke(1.dp, StudioColors.Border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    when (cat) {
                                        ActionCategory.TRANSITION -> onActionAdded(MacroAction.TriggerStudioTransition)
                                        ActionCategory.STUDIO_MODE -> onActionAdded(MacroAction.ToggleStudioMode)
                                        else -> selectedCategory = cat
                                    }
                                }
                        ) {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioColors.TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (selectedCategory!!) {
                        ActionCategory.SCENE_PROGRAM, ActionCategory.SCENE_PREVIEW -> {
                            QuickOptionPills(
                                options = snapshot.scenes.map { it.name },
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Scene name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ActionCategory.STREAM, ActionCategory.VCAM -> {
                            QuickOptionPills(
                                options = ToggleMode.entries.map { it.label },
                                selected = toggleMode.label,
                                onSelect = { lbl ->
                                    toggleMode = ToggleMode.entries.first { it.label == lbl }
                                }
                            )
                        }

                        ActionCategory.RECORD -> {
                            QuickOptionPills(
                                options = RecordControlMode.entries.map { it.label },
                                selected = recMode.label,
                                onSelect = { lbl ->
                                    recMode = RecordControlMode.entries.first { it.label == lbl }
                                }
                            )
                        }

                        ActionCategory.REPLAY -> {
                            QuickOptionPills(
                                options = ReplayControlMode.entries.map { it.label },
                                selected = replayMode.label,
                                onSelect = { lbl ->
                                    replayMode = ReplayControlMode.entries.first { it.label == lbl }
                                }
                            )
                        }

                        ActionCategory.AUDIO_MUTE -> {
                            QuickOptionPills(
                                options = snapshot.audioInputs.map { it.name },
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Audio input name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            QuickOptionPills(
                                options = ToggleMode.entries.map { it.label },
                                selected = toggleMode.label,
                                onSelect = { lbl ->
                                    toggleMode = ToggleMode.entries.first { it.label == lbl }
                                }
                            )
                        }

                        ActionCategory.AUDIO_VOL -> {
                            QuickOptionPills(
                                options = snapshot.audioInputs.map { it.name },
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Audio input name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = arg2,
                                onValueChange = { arg2 = it },
                                label = { Text("Target volume in dB (e.g. -6.0)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ActionCategory.SOURCE_VIS -> {
                            QuickOptionPills(
                                options = snapshot.scenes.map { it.name },
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Scene (empty = active Program)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            QuickOptionPills(
                                options = snapshot.itemsForScene(arg1.ifBlank { snapshot.programScene }).map { it.sourceName },
                                selected = arg2,
                                onSelect = { arg2 = it }
                            )
                            OutlinedTextField(
                                value = arg2,
                                onValueChange = { arg2 = it },
                                label = { Text("Source name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            QuickOptionPills(
                                options = ToggleMode.entries.map { it.label },
                                selected = toggleMode.label,
                                onSelect = { lbl ->
                                    toggleMode = ToggleMode.entries.first { it.label == lbl }
                                }
                            )
                        }

                        ActionCategory.FILTER -> {
                            QuickOptionPills(
                                options = snapshot.filtersBySource.keys.toList(),
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Source") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            QuickOptionPills(
                                options = snapshot.filtersBySource[arg1].orEmpty().map { it.name },
                                selected = arg2,
                                onSelect = { arg2 = it }
                            )
                            OutlinedTextField(
                                value = arg2,
                                onValueChange = { arg2 = it },
                                label = { Text("Filter name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            QuickOptionPills(
                                options = ToggleMode.entries.map { it.label },
                                selected = toggleMode.label,
                                onSelect = { lbl ->
                                    toggleMode = ToggleMode.entries.first { it.label == lbl }
                                }
                            )
                        }

                        ActionCategory.MEDIA -> {
                            QuickOptionPills(
                                options = snapshot.mediaStatuses.keys.toList(),
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Media source name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            QuickOptionPills(
                                options = ObsMediaAction.entries.map { it.label },
                                selected = mediaAction.label,
                                onSelect = { lbl ->
                                    mediaAction = ObsMediaAction.entries.first { it.label == lbl }
                                }
                            )
                        }

                        ActionCategory.TEXT -> {
                            QuickOptionPills(
                                options = snapshot.inputKindsByName
                                    .filterValues { it.contains("text", ignoreCase = true) }
                                    .keys.toList(),
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Text source name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = arg2,
                                onValueChange = { arg2 = it },
                                label = { Text("New text content") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ActionCategory.BROWSER_REFRESH -> {
                            QuickOptionPills(
                                options = snapshot.inputKindsByName
                                    .filterValues { it.contains("browser", ignoreCase = true) }
                                    .keys.toList(),
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Browser Source name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ActionCategory.HOTKEY -> {
                            QuickOptionPills(
                                options = snapshot.hotkeys.take(16),
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("OBS Hotkey name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ActionCategory.PROFILE -> {
                            QuickOptionPills(
                                options = snapshot.profiles,
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Profile name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ActionCategory.COLLECTION -> {
                            QuickOptionPills(
                                options = snapshot.sceneCollections,
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it },
                                label = { Text("Scene Collection name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ActionCategory.DELAY -> {
                            QuickOptionPills(
                                options = listOf("150", "350", "500", "1000", "2000"),
                                selected = arg1,
                                onSelect = { arg1 = it }
                            )
                            OutlinedTextField(
                                value = arg1,
                                onValueChange = { arg1 = it.filter { c -> c.isDigit() } },
                                label = { Text("Milliseconds (ms)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ActionCategory.TRANSITION, ActionCategory.STUDIO_MODE -> Unit
                    }
                }
            }
        },
        confirmButton = {
            if (selectedCategory != null) {
                Button(onClick = {
                    val created: MacroAction = when (selectedCategory!!) {
                        ActionCategory.SCENE_PROGRAM -> MacroAction.SwitchProgramScene(arg1.trim())
                        ActionCategory.SCENE_PREVIEW -> MacroAction.SwitchPreviewScene(arg1.trim())
                        ActionCategory.TRANSITION -> MacroAction.TriggerStudioTransition
                        ActionCategory.STUDIO_MODE -> MacroAction.ToggleStudioMode
                        ActionCategory.STREAM -> MacroAction.StreamControl(toggleMode)
                        ActionCategory.RECORD -> MacroAction.RecordControl(recMode)
                        ActionCategory.REPLAY -> MacroAction.ReplayBufferControl(replayMode)
                        ActionCategory.VCAM -> MacroAction.VirtualCamControl(toggleMode)
                        ActionCategory.AUDIO_MUTE -> MacroAction.AudioMute(arg1.trim(), toggleMode)
                        ActionCategory.AUDIO_VOL -> MacroAction.AudioSetVolumeDb(arg1.trim(), arg2.toFloatOrNull() ?: -6f)
                        ActionCategory.SOURCE_VIS -> MacroAction.SourceVisibility(arg1.trim(), arg2.trim(), toggleMode)
                        ActionCategory.FILTER -> MacroAction.FilterToggle(arg1.trim(), arg2.trim(), toggleMode)
                        ActionCategory.MEDIA -> MacroAction.MediaAction(arg1.trim(), mediaAction)
                        ActionCategory.TEXT -> MacroAction.SetTextSource(arg1.trim(), arg2)
                        ActionCategory.BROWSER_REFRESH -> MacroAction.RefreshBrowserSource(arg1.trim())
                        ActionCategory.HOTKEY -> MacroAction.TriggerHotkey(arg1.trim())
                        ActionCategory.PROFILE -> MacroAction.SetProfile(arg1.trim())
                        ActionCategory.COLLECTION -> MacroAction.SetSceneCollection(arg1.trim())
                        ActionCategory.DELAY -> MacroAction.Delay(arg1.toLongOrNull() ?: 350L)
                    }
                    onActionAdded(created)
                }) {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (selectedCategory != null) selectedCategory = null else onDismiss()
            }) {
                Text(if (selectedCategory != null) "Back" else "Cancel")
            }
        }
    )
}

@Composable
private fun QuickOptionPills(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    if (options.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { opt ->
            FilterChip(
                selected = opt == selected,
                onClick = { onSelect(opt) },
                label = {
                    Text(
                        text = opt,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}
