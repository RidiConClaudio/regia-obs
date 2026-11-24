package com.regiaobs.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.regiaobs.app.obs.ConnectionState
import com.regiaobs.app.obs.ObsSceneItem
import com.regiaobs.app.ui.connect.ConnectScreen
import com.regiaobs.app.ui.console.ConsoleScreen
import com.regiaobs.app.ui.deck.DeckScreen
import com.regiaobs.app.ui.mixer.MixerScreen
import com.regiaobs.app.ui.sources.SourceInspectorSheet
import com.regiaobs.app.ui.sources.SourcesScreen
import com.regiaobs.app.ui.system.SystemScreen
import com.regiaobs.app.ui.theme.StudioColors
import com.regiaobs.app.ui.theme.rememberBroadcastHaptics
import com.regiaobs.app.ui.theme.tabular

private enum class RegiaTab(val label: String, val icon: ImageVector) {
    REGIA("Console", Icons.Rounded.Tv),
    DECK("Deck", Icons.Rounded.GridView),
    MIXER("Mixer", Icons.Rounded.GraphicEq),
    SORGENTI("Sources", Icons.Rounded.Layers),
    SISTEMA("System", Icons.Rounded.Tune)
}

@Composable
fun RegiaApp(viewModel: RegiaViewModel) {
    val storedState by viewModel.storedState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val previewBitmaps by viewModel.previewBitmaps.collectAsStateWithLifecycle()
    val macroExec by viewModel.macroExecutionState.collectAsStateWithLifecycle()
    val pendingConfirm by viewModel.pendingConfirm.collectAsStateWithLifecycle()

    val settings = storedState.settings
    val haptics = rememberBroadcastHaptics(settings.hapticsEnabled)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val context = LocalContext.current
    var askedNotifOnce by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(connectionState) {
        val conn = connectionState
        if (conn is ConnectionState.Connected &&
            !conn.isDemo &&
            settings.foregroundServiceEnabled &&
            !askedNotifOnce &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                askedNotifOnce = true
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var activeTab by remember { mutableStateOf(RegiaTab.REGIA) }
    var inspectingTarget by remember { mutableStateOf<Pair<String, Int>?>(null) }

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE || config.screenWidthDp >= 680

    val connected = connectionState as? ConnectionState.Connected

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = StudioColors.Canvas,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (connected != null && !isLandscape) {
                NavigationBar(
                    containerColor = StudioColors.Panel,
                    tonalElevation = 0.dp
                ) {
                    RegiaTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = activeTab == tab,
                            onClick = {
                                haptics.tap()
                                activeTab = tab
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = StudioColors.ReplayCyan,
                                selectedTextColor = StudioColors.ReplayCyan,
                                indicatorColor = StudioColors.ReplayCyanSoft,
                                unselectedIconColor = StudioColors.TextSecondary,
                                unselectedTextColor = StudioColors.TextSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        if (connected == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ConnectScreen(
                    profiles = storedState.profiles,
                    connectionState = connectionState,
                    onConnect = { viewModel.connectToProfile(it) },
                    onSaveProfile = { viewModel.saveProfile(it) },
                    onDeleteProfile = { viewModel.deleteProfile(it) },
                    onStartDemo = { viewModel.connectDemo() },
                    onCancelReconnect = { viewModel.disconnect() }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (isLandscape) {
                    NavigationRail(
                        containerColor = StudioColors.Panel,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        RegiaTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = activeTab == tab,
                                onClick = {
                                    haptics.tap()
                                    activeTab = tab
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = StudioColors.ReplayCyan,
                                    selectedTextColor = StudioColors.ReplayCyan,
                                    indicatorColor = StudioColors.ReplayCyanSoft,
                                    unselectedIconColor = StudioColors.TextSecondary,
                                    unselectedTextColor = StudioColors.TextSecondary
                                )
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    TopTallyBar(
                        serverName = connected.serverName,
                        isDemo = connected.isDemo,
                        snapshot = snapshot
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (activeTab) {
                            RegiaTab.REGIA -> ConsoleScreen(
                                snapshot = snapshot,
                                previews = previewBitmaps,
                                settings = settings,
                                haptics = haptics,
                                onSelectProgramScene = { scene ->
                                    viewModel.launchObsAction { setProgramScene(scene) }
                                },
                                onSelectPreviewScene = { scene ->
                                    viewModel.launchObsAction { setPreviewScene(scene) }
                                },
                                onToggleStudioMode = {
                                    viewModel.launchObsAction { setStudioMode(!snapshot.studioMode) }
                                },
                                onTriggerTransition = {
                                    viewModel.launchObsAction { triggerTransition() }
                                },
                                onSelectTransition = { name ->
                                    viewModel.launchObsAction { setTransition(name) }
                                },
                                onSetTransitionDuration = { ms ->
                                    viewModel.launchObsAction { setTransitionDuration(ms) }
                                },
                                onTBarPosition = { pos, release ->
                                    viewModel.launchObsAction { setTBarPosition(pos, release) }
                                },
                                onToggleStream = { viewModel.toggleStreamSafely() },
                                onToggleRecord = { viewModel.toggleRecordSafely() },
                                onToggleRecordPause = {
                                    viewModel.launchObsAction { toggleRecordPause() }
                                },
                                onCreateChapter = {
                                    viewModel.launchObsAction("Recording chapter created") {
                                        createRecordChapter()
                                    }
                                },
                                onToggleReplayBuffer = {
                                    viewModel.launchObsAction { toggleReplayBuffer() }
                                },
                                onSaveReplayBuffer = {
                                    viewModel.launchObsAction("Replay clip saved!") {
                                        saveReplayBuffer()
                                    }
                                },
                                onToggleVirtualCam = {
                                    viewModel.launchObsAction { toggleVirtualCam() }
                                },
                                onToggleItemEnabled = { sceneName, item ->
                                    viewModel.launchObsAction {
                                        setSceneItemEnabled(sceneName, item.sceneItemId, !item.enabled)
                                    }
                                },
                                onToggleItemLocked = { sceneName, item ->
                                    viewModel.launchObsAction {
                                        setSceneItemLocked(sceneName, item.sceneItemId, !item.locked)
                                    }
                                },
                                onInspectSource = { sceneName, item ->
                                    inspectingTarget = sceneName to item.sceneItemId
                                },
                                onCyclePreviewFps = {
                                    viewModel.updateSettings { cur ->
                                        when {
                                            !cur.previewEnabled || cur.previewFps <= 0 -> cur.copy(previewEnabled = true, previewFps = 1)
                                            cur.previewFps == 1 -> cur.copy(previewFps = 2)
                                            cur.previewFps == 2 -> cur.copy(previewFps = 4)
                                            cur.previewFps == 4 -> cur.copy(previewFps = 8)
                                            else -> cur.copy(previewEnabled = false)
                                        }
                                    }
                                }
                            )

                            RegiaTab.DECK -> DeckScreen(
                                deckConfig = storedState.deckConfig,
                                snapshot = snapshot,
                                executionState = macroExec,
                                haptics = haptics,
                                onExecutePad = { viewModel.executePad(it) },
                                onUpsertPad = { pageId, pad -> viewModel.upsertPad(pageId, pad) },
                                onDuplicatePad = { pageId, pad -> viewModel.duplicatePad(pageId, pad) },
                                onDeletePad = { pageId, padId -> viewModel.deletePad(pageId, padId) },
                                onMovePad = { pageId, idx, delta -> viewModel.movePad(pageId, idx, delta) },
                                onAddPage = { title -> viewModel.addDeckPage(title) },
                                onDeletePage = { pageId -> viewModel.deleteDeckPage(pageId) },
                                onGenerateFromScenes = { viewModel.generateDeckFromScenes() },
                                onSyncWithObs = { viewModel.syncDeckWithObs() },
                                onExportJson = { viewModel.macroEngine.exportJson(storedState.deckConfig) },
                                onImportJson = { raw -> viewModel.importDeckJson(raw) },
                                onChangeColumns = { portrait, landscape ->
                                    viewModel.saveDeckConfig(
                                        storedState.deckConfig.copy(
                                            columnsPortrait = portrait,
                                            columnsLandscape = landscape
                                        )
                                    )
                                }
                            )

                            RegiaTab.MIXER -> MixerScreen(
                                snapshot = snapshot,
                                volumeMetersFlow = viewModel.currentController.volumeMeters,
                                settings = settings,
                                haptics = haptics,
                                onToggleMute = { name ->
                                    viewModel.launchObsAction { toggleMute(name) }
                                },
                                onSetVolumeMul = { name, mul ->
                                    viewModel.launchObsAction { setVolumeMul(name, mul) }
                                },
                                onSetMonitorType = { name, type ->
                                    viewModel.launchObsAction { setMonitorType(name, type) }
                                },
                                onSetSyncOffset = { name, ms ->
                                    viewModel.launchObsAction { setSyncOffset(name, ms) }
                                },
                                onSetBalance = { name, bal ->
                                    viewModel.launchObsAction { setBalance(name, bal) }
                                },
                                onToggleFilter = { src, filter, enabled ->
                                    viewModel.launchObsAction { setFilterEnabled(src, filter, enabled) }
                                },
                                onFetchDeviceOptions = { inputName, propName ->
                                    viewModel.fetchAudioDeviceOptions(inputName, propName)
                                },
                                onSelectAudioDevice = { inputName, propName, deviceValue, deviceLabel ->
                                    viewModel.launchObsAction("$inputName → $deviceLabel") {
                                        setInputAudioDevice(inputName, propName, deviceValue)
                                    }
                                },
                                onToggleShowActiveOnly = {
                                    viewModel.updateSettings { it.copy(showActiveAudioOnly = !it.showActiveAudioOnly) }
                                },
                                onToggleVerticalFaders = {
                                    viewModel.updateSettings { it.copy(verticalMixerFaders = !it.verticalMixerFaders) }
                                }
                            )

                            RegiaTab.SORGENTI -> SourcesScreen(
                                snapshot = snapshot,
                                haptics = haptics,
                                onEnsureSceneLoaded = { sceneName ->
                                    viewModel.launchObsAction {
                                        refreshSceneItems(sceneName)
                                        Result.success(Unit)
                                    }
                                },
                                onToggleItemEnabled = { sceneName, item ->
                                    viewModel.launchObsAction {
                                        setSceneItemEnabled(sceneName, item.sceneItemId, !item.enabled)
                                    }
                                },
                                onToggleItemLocked = { sceneName, item ->
                                    viewModel.launchObsAction {
                                        setSceneItemLocked(sceneName, item.sceneItemId, !item.locked)
                                    }
                                },
                                onMoveItemIndex = { sceneName, item, newIndex ->
                                    viewModel.launchObsAction {
                                        setSceneItemIndex(sceneName, item.sceneItemId, newIndex)
                                    }
                                },
                                onMediaAction = { inputName, action ->
                                    viewModel.launchObsAction { triggerMediaAction(inputName, action) }
                                },
                                onMediaSeek = { inputName, ms ->
                                    viewModel.launchObsAction { setMediaCursor(inputName, ms) }
                                },
                                onInspectSource = { sceneName, item ->
                                    inspectingTarget = sceneName to item.sceneItemId
                                }
                            )

                            RegiaTab.SISTEMA -> SystemScreen(
                                connectionState = connectionState,
                                snapshot = snapshot,
                                settings = settings,
                                haptics = haptics,
                                onSelectProfile = { profileName ->
                                    viewModel.launchObsAction("Profile: $profileName") { setProfile(profileName) }
                                },
                                onSelectSceneCollection = { colName ->
                                    viewModel.launchObsAction("Collection: $colName") { setSceneCollection(colName) }
                                },
                                onTriggerHotkey = { hk ->
                                    viewModel.launchObsAction("Hotkey triggered: $hk") { triggerHotkey(hk) }
                                },
                                onSendRawRequest = { type, data ->
                                    viewModel.launchObsAction("Sent $type") { sendRawRequest(type, data) }
                                },
                                onUpdateSettings = { transform -> viewModel.updateSettings(transform) },
                                onDisconnect = { viewModel.disconnect() }
                            )
                        }
                    }
                }
            }
        }
    }

    // Global Source Inspector Sheet
    val target = inspectingTarget
    if (target != null) {
        val (sceneName, itemId) = target
        val item = findItemInSnapshot(snapshot.itemsForScene(sceneName), itemId)
        if (item != null) {
            val filters = snapshot.filtersBySource[item.sourceName].orEmpty()
            SourceInspectorSheet(
                sceneName = sceneName,
                item = item,
                filters = filters,
                haptics = haptics,
                onFetchSettings = { sourceName -> viewModel.currentController.getInputSettings(sourceName) },
                onSaveSettings = { sourceName, patch ->
                    viewModel.launchObsAction("Settings saved") {
                        setInputSettings(sourceName, patch, overlay = true)
                    }
                },
                onRefreshBrowser = { sourceName ->
                    viewModel.launchObsAction("Browser source refreshed") {
                        pressInputPropertiesButton(sourceName, "refreshnocache")
                    }
                },
                onToggleFilter = { sourceName, filterName, enabled ->
                    viewModel.launchObsAction { setFilterEnabled(sourceName, filterName, enabled) }
                },
                onUpdateTransform = { sName, id, patch ->
                    viewModel.launchObsAction { setSceneItemTransform(sName, id, patch) }
                },
                onFitToScreen = { sName, id ->
                    viewModel.launchObsAction("Fitted to screen") { fitSceneItemToScreen(sName, id) }
                },
                onResetTransform = { sName, id ->
                    viewModel.launchObsAction("Transform reset") { resetSceneItemTransform(sName, id) }
                },
                onDismiss = { inspectingTarget = null }
            )
        }
    }

    // Safety Confirmation Dialog (Stop LIVE / Stop REC)
    pendingConfirm?.let { req ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissConfirm() },
            containerColor = StudioColors.Panel,
            title = { Text(req.title, color = StudioColors.TextPrimary) },
            text = { Text(req.message, color = StudioColors.TextSecondary) },
            confirmButton = {
                Button(
                    onClick = req.onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StudioColors.TallyRed,
                        contentColor = Color.White
                    )
                ) {
                    Text(req.confirmLabel)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissConfirm() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun findItemInSnapshot(items: List<ObsSceneItem>, itemId: Int): ObsSceneItem? {
    for (item in items) {
        if (item.sceneItemId == itemId) return item
        if (item.groupChildren.isNotEmpty()) {
            val child = findItemInSnapshot(item.groupChildren, itemId)
            if (child != null) return child
        }
    }
    return null
}

@Composable
private fun TopTallyBar(
    serverName: String,
    isDemo: Boolean,
    snapshot: com.regiaobs.app.obs.ObsSnapshot
) {
    val out = snapshot.outputStatus
    Surface(
        color = StudioColors.Panel,
        border = BorderStroke(1.dp, StudioColors.Border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isDemo) StudioColors.RecAmber else StudioColors.TallyGreen)
                )
                Text(
                    text = serverName,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (snapshot.programScene.isNotBlank()) {
                    Text(
                        text = "• ${snapshot.programScene}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (out.streamActive) {
                    TallyBadge(
                        text = "LIVE ${out.streamTimecode.substringBefore('.')}",
                        color = StudioColors.TallyRed
                    )
                }
                if (out.recActive) {
                    TallyBadge(
                        text = if (out.recPaused) "PAUSED" else "REC ${out.recTimecode.substringBefore('.')}",
                        color = StudioColors.RecAmber
                    )
                }
                if (!out.streamActive && !out.recActive && out.replayActive) {
                    TallyBadge(
                        text = "REPLAY",
                        color = StudioColors.ReplayCyan
                    )
                }
                if (isDemo) {
                    TallyBadge(
                        text = "DEMO",
                        color = StudioColors.StudioPurple
                    )
                }
            }
        }
    }
}

@Composable
private fun TallyBadge(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, color)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.tabular(),
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
