package com.regiaobs.app.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.regiaobs.app.data.AppPreferencesRepository
import com.regiaobs.app.data.AppSettings
import com.regiaobs.app.data.StoredAppState
import com.regiaobs.app.macro.DeckConfig
import com.regiaobs.app.macro.DeckPad
import com.regiaobs.app.macro.DeckPage
import com.regiaobs.app.macro.DeckPresets
import com.regiaobs.app.macro.MacroEngine
import com.regiaobs.app.macro.MacroExecutionState
import com.regiaobs.app.obs.ConnectionState
import com.regiaobs.app.obs.DemoObsController
import com.regiaobs.app.obs.ObsController
import com.regiaobs.app.obs.ObsPropertyOption
import com.regiaobs.app.obs.ObsSnapshot
import com.regiaobs.app.obs.ObsServerProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.regiaobs.app.obs.ObsWebSocketClient
import com.regiaobs.app.obs.PreviewBitmaps
import com.regiaobs.app.service.ObsConnectionService
import com.regiaobs.app.service.ObsServiceBridge
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class ConfirmDialogRequest(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val onConfirm: () -> Unit
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class RegiaViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AppPreferencesRepository(application)
    private val realClient = ObsWebSocketClient()
    private val demoClient = DemoObsController()
    val macroEngine = MacroEngine()

    private val _usingDemo = MutableStateFlow(false)
    private val activeControllerFlow: StateFlow<ObsController> = _usingDemo
        .combine(MutableStateFlow(Unit)) { isDemo, _ -> if (isDemo) demoClient else realClient }
        .stateIn(viewModelScope, SharingStarted.Eagerly, realClient)

    val currentController: ObsController
        get() = if (_usingDemo.value) demoClient else realClient

    val storedState: StateFlow<StoredAppState> = repo.stateFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, StoredAppState())

    val connectionState: StateFlow<ConnectionState> = activeControllerFlow
        .flatMapLatest { it.connectionState }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Disconnected)

    val snapshot: StateFlow<ObsSnapshot> = activeControllerFlow
        .flatMapLatest { it.snapshot }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ObsSnapshot())

    val volumeMeters: StateFlow<Map<String, List<Float>>> = activeControllerFlow
        .flatMapLatest { it.volumeMeters }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val previewBitmaps: StateFlow<PreviewBitmaps> = activeControllerFlow
        .flatMapLatest { it.previewBitmaps }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PreviewBitmaps())

    val macroExecutionState: StateFlow<MacroExecutionState?> = macroEngine.executionState

    private val _uiMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val uiMessages = _uiMessages.asSharedFlow()

    private val _pendingConfirm = MutableStateFlow<ConfirmDialogRequest?>(null)
    val pendingConfirm: StateFlow<ConfirmDialogRequest?> = _pendingConfirm.asStateFlow()

    private val _isAppActive = MutableStateFlow(true)
    private var macroJob: Job? = null
    private var lastSyncedSignature: String? = null

    init {
        viewModelScope.launch {
            combine(storedState, _isAppActive) { stored, active ->
                stored.settings to active
            }.collectLatest { (settings, active) ->
                realClient.updatePreviewConfig(
                    enabled = settings.previewEnabled,
                    fps = settings.previewFps,
                    quality = settings.previewQuality,
                    isAppActive = active
                )
                demoClient.updatePreviewConfig(
                    enabled = settings.previewEnabled,
                    fps = settings.previewFps,
                    quality = settings.previewQuality,
                    isAppActive = active
                )
            }
        }

        viewModelScope.launch {
            ObsServiceBridge.disconnectEvents.collect {
                disconnect()
            }
        }

        viewModelScope.launch {
            combine(connectionState, snapshot, storedState) { conn, snap, stored ->
                Triple(conn, snap.outputStatus, stored.settings.foregroundServiceEnabled)
            }.collectLatest { (conn, outputs, fgEnabled) ->
                val app = getApplication<Application>()
                if (!fgEnabled || conn !is ConnectionState.Connected || conn.isDemo) {
                    ObsConnectionService.stop(app)
                } else if (canPostNotifications(app)) {
                    val statusParts = buildList {
                        if (outputs.streamActive) add("LIVE ${outputs.streamTimecode.substringBefore('.')}")
                        if (outputs.recActive) {
                            val prefix = if (outputs.recPaused) "REC PAUSED" else "REC"
                            add("$prefix ${outputs.recTimecode.substringBefore('.')}")
                        }
                        if (isEmpty()) add("Connected • Ready")
                    }
                    ObsConnectionService.updateOrStart(
                        context = app,
                        serverName = conn.serverName,
                        statusText = statusParts.joinToString(" • ")
                    )
                }
            }
        }

        viewModelScope.launch {
            combine(connectionState, snapshot, storedState) { conn, snap, stored ->
                Triple(conn, snap, stored.deckConfig)
            }.collectLatest { (conn, snap, deckConfig) ->
                if (conn !is ConnectionState.Connected || conn.isDemo) {
                    if (conn !is ConnectionState.Connected) lastSyncedSignature = null
                    return@collectLatest
                }
                if (snap.scenes.isEmpty() && snap.audioInputs.isEmpty()) return@collectLatest

                val signature = "${conn.serverName}|${snap.currentSceneCollection}|${snap.scenes.size}|${snap.audioInputs.size}"
                if (signature != lastSyncedSignature && deckConfig.pages.isNotEmpty()) {
                    lastSyncedSignature = signature
                    val (adapted, changed) = DeckPresets.adaptDeckToObs(deckConfig, snap)
                    if (changed) {
                        saveDeckConfig(adapted)
                        _uiMessages.tryEmit("Macro Deck automatically synced with OBS")
                    }
                }
            }
        }
    }

    private fun canPostNotifications(app: Application): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun onAppForegroundChanged(active: Boolean) {
        _isAppActive.value = active
    }

    fun connectToProfile(profile: ObsServerProfile) {
        demoClient.disconnect()
        _usingDemo.value = false
        viewModelScope.launch {
            val current = storedState.value.profiles
            val existingIdx = current.indexOfFirst { it.id == profile.id }
            val updated = if (existingIdx >= 0) {
                current.toMutableList().apply { this[existingIdx] = profile }
            } else {
                listOf(profile) + current
            }
            repo.saveProfiles(updated, profile.id)
        }
        realClient.connect(profile)
    }

    fun connectDemo() {
        realClient.disconnect()
        _usingDemo.value = true
        demoClient.connect(ObsServerProfile(name = "Demo Studio", host = "demo"))
    }

    fun disconnect() {
        realClient.disconnect()
        demoClient.disconnect()
        _usingDemo.value = false
        ObsConnectionService.stop(getApplication())
    }

    fun saveProfile(profile: ObsServerProfile) {
        viewModelScope.launch {
            val current = storedState.value.profiles
            val idx = current.indexOfFirst { it.id == profile.id }
            val updated = if (idx >= 0) {
                current.toMutableList().apply { this[idx] = profile }
            } else {
                listOf(profile) + current
            }
            repo.saveProfiles(updated, storedState.value.lastProfileId)
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            val updated = storedState.value.profiles.filterNot { it.id == profileId }
            val nextLast = storedState.value.lastProfileId?.takeIf { it != profileId }
            repo.saveProfiles(updated, nextLast)
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            val next = transform(storedState.value.settings)
            repo.saveSettings(next)
        }
    }

    fun launchObsAction(
        successMessage: String? = null,
        block: suspend ObsController.() -> Result<*>
    ) {
        viewModelScope.launch {
            val res = currentController.block()
            if (res.isSuccess) {
                if (successMessage != null) _uiMessages.tryEmit(successMessage)
            } else {
                val err = res.exceptionOrNull()?.message ?: "Action failed"
                _uiMessages.tryEmit(err)
            }
        }
    }

    fun toggleStreamSafely() {
        val isLive = snapshot.value.outputStatus.streamActive
        if (isLive && storedState.value.settings.confirmBeforeStopLive) {
            _pendingConfirm.value = ConfirmDialogRequest(
                title = "Stop LIVE stream?",
                message = "The broadcast will stop immediately on OBS Studio.",
                confirmLabel = "Stop Stream",
                onConfirm = {
                    _pendingConfirm.value = null
                    launchObsAction("Stream stopped") { stopStream() }
                }
            )
        } else {
            launchObsAction { toggleStream() }
        }
    }

    fun toggleRecordSafely() {
        val isRec = snapshot.value.outputStatus.recActive
        if (isRec && storedState.value.settings.confirmBeforeStopRec) {
            _pendingConfirm.value = ConfirmDialogRequest(
                title = "Stop recording?",
                message = "The current video file will be finalized and saved to disk.",
                confirmLabel = "Stop REC",
                onConfirm = {
                    _pendingConfirm.value = null
                    launchObsAction("Recording saved") { stopRecord() }
                }
            )
        } else {
            launchObsAction { toggleRecord() }
        }
    }

    fun dismissConfirm() {
        _pendingConfirm.value = null
    }

    // --- Macro Deck Management ---

    fun executePad(pad: DeckPad) {
        macroJob?.cancel()
        macroJob = viewModelScope.launch {
            val res = macroEngine.execute(pad, currentController)
            if (res.isFailure) {
                _uiMessages.tryEmit(res.exceptionOrNull()?.message ?: "Macro execution failed")
            }
        }
    }

    fun saveDeckConfig(config: DeckConfig) {
        viewModelScope.launch {
            repo.saveDeckConfig(config)
        }
    }

    fun addDeckPage(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        val cur = storedState.value.deckConfig
        val next = cur.copy(pages = cur.pages + DeckPage(title = trimmed))
        saveDeckConfig(next)
    }

    fun deleteDeckPage(pageId: String) {
        val cur = storedState.value.deckConfig
        if (cur.pages.size <= 1) {
            _uiMessages.tryEmit("At least one Macro Deck page is required.")
            return
        }
        saveDeckConfig(cur.copy(pages = cur.pages.filterNot { it.id == pageId }))
    }

    fun upsertPad(pageId: String, pad: DeckPad) {
        val cur = storedState.value.deckConfig
        val updatedPages = cur.pages.map { page ->
            if (page.id != pageId) page
            else {
                val idx = page.pads.indexOfFirst { it.id == pad.id }
                val updatedPads = if (idx >= 0) {
                    page.pads.toMutableList().apply { this[idx] = pad }
                } else {
                    page.pads + pad
                }
                page.copy(pads = updatedPads)
            }
        }
        saveDeckConfig(cur.copy(pages = updatedPages))
    }

    fun duplicatePad(pageId: String, pad: DeckPad) {
        val copyPad = pad.copy(
            id = UUID.randomUUID().toString(),
            label = "${pad.label} (copy)"
        )
        upsertPad(pageId, copyPad)
    }

    fun deletePad(pageId: String, padId: String) {
        val cur = storedState.value.deckConfig
        val updatedPages = cur.pages.map { page ->
            if (page.id == pageId) page.copy(pads = page.pads.filterNot { it.id == padId }) else page
        }
        saveDeckConfig(cur.copy(pages = updatedPages))
    }

    fun movePad(pageId: String, index: Int, delta: Int) {
        val cur = storedState.value.deckConfig
        val updatedPages = cur.pages.map { page ->
            if (page.id != pageId) page
            else {
                val target = index + delta
                if (index !in page.pads.indices || target !in page.pads.indices) page
                else {
                    val mutable = page.pads.toMutableList()
                    val item = mutable.removeAt(index)
                    mutable.add(target, item)
                    page.copy(pads = mutable)
                }
            }
        }
        saveDeckConfig(cur.copy(pages = updatedPages))
    }

    fun generateDeckFromScenes() {
        val scenes = snapshot.value.scenes
        if (scenes.isEmpty()) {
            _uiMessages.tryEmit("No scenes loaded from OBS.")
            return
        }
        val newPage = DeckPresets.buildPageFromScenes(scenes)
        val cur = storedState.value.deckConfig
        saveDeckConfig(cur.copy(pages = cur.pages + newPage))
        _uiMessages.tryEmit("Created '${newPage.title}' page with ${scenes.size} scenes.")
    }

    fun syncDeckWithObs() {
        val snap = snapshot.value
        val cur = storedState.value.deckConfig
        val (adapted, changed) = DeckPresets.adaptDeckToObs(cur, snap)
        if (changed) {
            saveDeckConfig(adapted)
            _uiMessages.tryEmit("Macro Deck updated to match your OBS scenes and sources.")
        } else {
            _uiMessages.tryEmit("All Macro Deck pads are already synced with OBS.")
        }
    }

    fun importDeckJson(raw: String): Boolean {
        val parsed = macroEngine.importJson(raw)
        return if (parsed.isSuccess) {
            saveDeckConfig(parsed.getOrThrow())
            _uiMessages.tryEmit("Macro Deck imported successfully.")
            true
        } else {
            _uiMessages.tryEmit("Invalid JSON: ${parsed.exceptionOrNull()?.message}")
            false
        }
    }

    suspend fun fetchAudioDeviceOptions(
        inputName: String,
        propertyName: String
    ): Result<Pair<String, List<ObsPropertyOption>>> {
        val controller = currentController
        val settingsRes = controller.getInputSettings(inputName)
        val itemsRes = controller.getInputPropertyListItems(inputName, propertyName)
        if (itemsRes.isFailure) {
            return Result.failure(
                itemsRes.exceptionOrNull() ?: IllegalStateException("Unable to load audio devices")
            )
        }
        val inputSettings = settingsRes.getOrNull()?.get("inputSettings") as? JsonObject
        val currentValue = (inputSettings?.get(propertyName) as? JsonPrimitive)?.content
            ?.takeIf { it.isNotBlank() } ?: "default"
        return Result.success(currentValue to itemsRes.getOrThrow())
    }

    fun showMessage(message: String) {
        _uiMessages.tryEmit(message)
    }

    override fun onCleared() {
        super.onCleared()
        realClient.disconnect()
        demoClient.disconnect()
    }
}
