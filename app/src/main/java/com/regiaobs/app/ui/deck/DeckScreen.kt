package com.regiaobs.app.ui.deck

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.regiaobs.app.macro.DeckConfig
import com.regiaobs.app.macro.DeckPad
import com.regiaobs.app.macro.DeckPresets
import com.regiaobs.app.macro.MacroExecutionState
import com.regiaobs.app.obs.ObsSnapshot
import com.regiaobs.app.ui.theme.BroadcastHaptics
import com.regiaobs.app.ui.theme.StudioColors
import com.regiaobs.app.ui.theme.padIconForKey

@Composable
fun DeckScreen(
    deckConfig: DeckConfig,
    snapshot: ObsSnapshot,
    executionState: MacroExecutionState?,
    haptics: BroadcastHaptics,
    onExecutePad: (DeckPad) -> Unit,
    onUpsertPad: (String, DeckPad) -> Unit,
    onDuplicatePad: (String, DeckPad) -> Unit,
    onDeletePad: (String, String) -> Unit,
    onMovePad: (String, Int, Int) -> Unit,
    onAddPage: (String) -> Unit,
    onDeletePage: (String) -> Unit,
    onGenerateFromScenes: () -> Unit,
    onSyncWithObs: () -> Unit,
    onExportJson: () -> String,
    onImportJson: (String) -> Boolean,
    onChangeColumns: (Int, Int) -> Unit
) {
    val pages = deckConfig.pages
    var selectedPageId by remember { mutableStateOf(pages.firstOrNull()?.id.orEmpty()) }
    LaunchedEffect(pages) {
        if (pages.none { it.id == selectedPageId }) {
            selectedPageId = pages.firstOrNull()?.id.orEmpty()
        }
    }
    val activePage = pages.firstOrNull { it.id == selectedPageId } ?: pages.firstOrNull()

    var editMode by remember { mutableStateOf(false) }
    var editingPad by remember { mutableStateOf<DeckPad?>(null) }
    var creatingNewPad by remember { mutableStateOf(false) }
    var showAddPageDialog by remember { mutableStateOf(false) }
    var showJsonDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE || config.screenWidthDp >= 680
    val columns = if (isLandscape) deckConfig.columnsLandscape.coerceIn(3, 6) else deckConfig.columnsPortrait.coerceIn(2, 4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Canvas)
    ) {
        // Header + Page Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEach { page ->
                    val selected = page.id == activePage?.id
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) StudioColors.ReplayCyanSoft else StudioColors.Panel,
                        border = BorderStroke(
                            1.dp,
                            if (selected) StudioColors.ReplayCyan else StudioColors.Border
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.tap()
                                selectedPageId = page.id
                            }
                    ) {
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) StudioColors.TextPrimary else StudioColors.TextSecondary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StudioColors.Panel,
                    border = BorderStroke(1.dp, StudioColors.Border),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showAddPageDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = "New page",
                        tint = StudioColors.ReplayCyan,
                        modifier = Modifier.padding(7.dp).size(18.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (editMode) StudioColors.RecAmberSoft else StudioColors.Panel,
                    border = BorderStroke(1.dp, if (editMode) StudioColors.RecAmber else StudioColors.Border),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { editMode = !editMode }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (editMode) Icons.Rounded.Done else Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = if (editMode) StudioColors.RecAmber else StudioColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (editMode) "Done" else "Edit",
                            style = MaterialTheme.typography.labelMedium,
                            color = StudioColors.TextPrimary
                        )
                    }
                }

                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Deck Options",
                            tint = StudioColors.TextSecondary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sync Deck with OBS") },
                            leadingIcon = { Icon(Icons.Rounded.Sync, null) },
                            onClick = {
                                menuExpanded = false
                                onSyncWithObs()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Generate page from OBS Scenes") },
                            leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null) },
                            onClick = {
                                menuExpanded = false
                                onGenerateFromScenes()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Export / Import JSON") },
                            onClick = {
                                menuExpanded = false
                                showJsonDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Grid: 3 columns (portrait)") },
                            onClick = {
                                menuExpanded = false
                                onChangeColumns(3, deckConfig.columnsLandscape)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Grid: 4 columns (portrait)") },
                            onClick = {
                                menuExpanded = false
                                onChangeColumns(4, deckConfig.columnsLandscape)
                            }
                        )
                        if (activePage != null && pages.size > 1) {
                            DropdownMenuItem(
                                text = { Text("Delete page '${activePage.title}'", color = StudioColors.TallyRed) },
                                onClick = {
                                    menuExpanded = false
                                    onDeletePage(activePage.id)
                                }
                            )
                        }
                    }
                }
            }
        }

        if (activePage != null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(activePage.pads, key = { _, p -> p.id }) { idx, pad ->
                    val isActive = DeckPresets.isPadActive(pad, snapshot)
                    val missingTarget = remember(pad, snapshot.scenes, snapshot.audioInputs, snapshot.inputKindsByName) {
                        DeckPresets.findPadMissingTarget(pad, snapshot)
                    }
                    val isRunning = executionState?.padId == pad.id
                    val progress = if (isRunning && executionState != null && executionState.totalSteps > 0) {
                        (executionState.stepIndex + 1).toFloat() / executionState.totalSteps.toFloat()
                    } else 0f

                    DeckPadKey(
                        pad = pad,
                        isActive = isActive,
                        hasMissingTarget = missingTarget != null,
                        isRunning = isRunning,
                        progress = progress,
                        editMode = editMode,
                        canMoveLeft = idx > 0,
                        canMoveRight = idx < activePage.pads.lastIndex,
                        onClick = {
                            if (editMode) {
                                editingPad = pad
                            } else {
                                haptics.strong()
                                onExecutePad(pad)
                            }
                        },
                        onMoveLeft = { onMovePad(activePage.id, idx, -1) },
                        onMoveRight = { onMovePad(activePage.id, idx, +1) },
                        onDuplicate = { onDuplicatePad(activePage.id, pad) }
                    )
                }

                if (editMode || activePage.pads.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = StudioColors.Panel,
                            border = BorderStroke(1.dp, StudioColors.BorderStrong),
                            modifier = Modifier
                                .aspectRatio(1.02f)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { creatingNewPad = true }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = null,
                                    tint = StudioColors.ReplayCyan,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "New Pad",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = StudioColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if ((editingPad != null || creatingNewPad) && activePage != null) {
        PadEditorSheet(
            initialPad = editingPad,
            snapshot = snapshot,
            onSave = { saved ->
                onUpsertPad(activePage.id, saved)
                editingPad = null
                creatingNewPad = false
            },
            onTestPad = { pad -> onExecutePad(pad) },
            onDelete = editingPad?.let { padToDelete ->
                {
                    onDeletePad(activePage.id, padToDelete.id)
                    editingPad = null
                }
            },
            onDismiss = {
                editingPad = null
                creatingNewPad = false
            }
        )
    }

    if (showAddPageDialog) {
        var newTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddPageDialog = false },
            containerColor = StudioColors.Panel,
            title = { Text("New Macro Deck page") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Page name") },
                    placeholder = { Text("e.g. Effects, Guests, Audio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTitle.isNotBlank()) {
                            onAddPage(newTitle)
                            showAddPageDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPageDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showJsonDialog) {
        val clipboard = LocalClipboardManager.current
        var jsonText by remember { mutableStateOf(onExportJson()) }
        AlertDialog(
            onDismissRequest = { showJsonDialog = false },
            containerColor = StudioColors.Panel,
            title = { Text("Deck Backup & Import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Copy the JSON to back it up or paste an existing configuration:",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextSecondary
                    )
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp),
                        textStyle = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(jsonText)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy to clipboard")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (onImportJson(jsonText)) {
                            showJsonDialog = false
                        }
                    }
                ) {
                    Text("Import JSON")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJsonDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun DeckPadKey(
    pad: DeckPad,
    isActive: Boolean,
    hasMissingTarget: Boolean,
    isRunning: Boolean,
    progress: Float,
    editMode: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onClick: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDuplicate: () -> Unit
) {
    val baseColor = Color(pad.colorHex)
    val borderColor = when {
        isRunning -> StudioColors.ReplayCyan
        isActive -> StudioColors.TallyGreen
        editMode -> StudioColors.RecAmber.copy(alpha = 0.7f)
        hasMissingTarget -> StudioColors.RecAmber.copy(alpha = 0.55f)
        else -> StudioColors.BorderStrong
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) baseColor.copy(alpha = 1f) else baseColor.copy(alpha = 0.85f),
        border = BorderStroke(if (isActive || isRunning) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .aspectRatio(1.02f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            if (isActive && !editMode) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(StudioColors.TallyGreen)
                )
            } else if (hasMissingTarget && !editMode) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = "Missing OBS target",
                    tint = StudioColors.RecAmber,
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.TopEnd)
                )
            }

            if (pad.actions.size > 1 && !editMode) {
                Text(
                    text = "${pad.actions.size}x",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioColors.TextSecondary,
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = padIconForKey(pad.iconKey),
                    contentDescription = pad.label,
                    tint = if (isActive) Color.White else StudioColors.TextPrimary,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = pad.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (pad.subtitle.isNotBlank()) {
                    Text(
                        text = pad.subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextPrimary.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (editMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (canMoveLeft) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(StudioColors.Canvas.copy(alpha = 0.75f))
                                .clickable(onClick = onMoveLeft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.ArrowBack, null, modifier = Modifier.size(14.dp), tint = Color.White)
                        }
                    } else Spacer(modifier = Modifier.size(24.dp))

                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(StudioColors.Canvas.copy(alpha = 0.75f))
                            .clickable(onClick = onDuplicate),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(13.dp), tint = StudioColors.ReplayCyan)
                    }

                    if (canMoveRight) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(StudioColors.Canvas.copy(alpha = 0.75f))
                                .clickable(onClick = onMoveRight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.ArrowForward, null, modifier = Modifier.size(14.dp), tint = Color.White)
                        }
                    } else Spacer(modifier = Modifier.size(24.dp))
                }
            }

            if (isRunning) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = StudioColors.ReplayCyan
                )
            }
        }
    }
}
