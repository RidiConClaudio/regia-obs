package com.regiaobs.app.macro

import com.regiaobs.app.obs.ObsController
import com.regiaobs.app.obs.ObsJson
import com.regiaobs.app.obs.ToggleMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class MacroExecutionState(
    val padId: String,
    val stepIndex: Int,
    val totalSteps: Int
)

class MacroEngine {
    private val _executionState = MutableStateFlow<MacroExecutionState?>(null)
    val executionState: StateFlow<MacroExecutionState?> = _executionState.asStateFlow()

    suspend fun execute(pad: DeckPad, controller: ObsController): Result<Unit> {
        if (pad.actions.isEmpty()) {
            return Result.failure(IllegalStateException("Pad '${pad.label}' has no configured actions."))
        }

        val total = pad.actions.size
        return try {
            for ((index, action) in pad.actions.withIndex()) {
                _executionState.value = MacroExecutionState(
                    padId = pad.id,
                    stepIndex = index,
                    totalSteps = total
                )
                val stepResult = runStep(action, controller)
                if (stepResult.isFailure) {
                    return stepResult
                }
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            _executionState.value = null
        }
    }

    private suspend fun runStep(action: MacroAction, controller: ObsController): Result<Unit> = when (action) {
        is MacroAction.SwitchProgramScene -> controller.setProgramScene(action.sceneName)
        is MacroAction.SwitchPreviewScene -> controller.setPreviewScene(action.sceneName)
        is MacroAction.TriggerStudioTransition -> controller.triggerTransition()
        is MacroAction.ToggleStudioMode -> controller.setStudioMode(!controller.snapshot.value.studioMode)
        is MacroAction.StreamControl -> when (action.mode) {
            ToggleMode.TOGGLE -> controller.toggleStream()
            ToggleMode.ENABLE -> controller.startStream()
            ToggleMode.DISABLE -> controller.stopStream()
        }
        is MacroAction.RecordControl -> when (action.mode) {
            RecordControlMode.TOGGLE -> controller.toggleRecord()
            RecordControlMode.START -> controller.startRecord()
            RecordControlMode.STOP -> controller.stopRecord()
            RecordControlMode.PAUSE_TOGGLE -> controller.toggleRecordPause()
        }
        is MacroAction.ReplayBufferControl -> when (action.mode) {
            ReplayControlMode.SAVE -> controller.saveReplayBuffer()
            ReplayControlMode.TOGGLE -> controller.toggleReplayBuffer()
            ReplayControlMode.START -> controller.startReplayBuffer()
            ReplayControlMode.STOP -> controller.stopReplayBuffer()
        }
        is MacroAction.VirtualCamControl -> when (action.mode) {
            ToggleMode.TOGGLE -> controller.toggleVirtualCam()
            ToggleMode.ENABLE -> controller.startVirtualCam()
            ToggleMode.DISABLE -> controller.stopVirtualCam()
        }
        is MacroAction.AudioMute -> when (action.mode) {
            ToggleMode.TOGGLE -> controller.toggleMute(action.inputName)
            ToggleMode.ENABLE -> controller.setMute(action.inputName, true)
            ToggleMode.DISABLE -> controller.setMute(action.inputName, false)
        }
        is MacroAction.AudioSetVolumeDb -> controller.setVolumeDb(action.inputName, action.volumeDb)
        is MacroAction.SourceVisibility -> controller.setSceneItemEnabledByName(
            sceneName = action.sceneName,
            sourceName = action.sourceName,
            mode = action.mode
        )
        is MacroAction.FilterToggle -> controller.toggleFilterByName(
            sourceName = action.sourceName,
            filterName = action.filterName,
            mode = action.mode
        )
        is MacroAction.MediaAction -> controller.triggerMediaAction(action.inputName, action.action)
        is MacroAction.SetTextSource -> controller.setInputSettings(
            inputName = action.inputName,
            settings = buildJsonObject { put("text", action.text) },
            overlay = true
        )
        is MacroAction.RefreshBrowserSource ->
            controller.pressInputPropertiesButton(action.inputName, "refreshnocache")
        is MacroAction.TriggerHotkey -> controller.triggerHotkey(action.hotkeyName)
        is MacroAction.SetProfile -> controller.setProfile(action.profileName)
        is MacroAction.SetSceneCollection -> controller.setSceneCollection(action.collectionName)
        is MacroAction.Delay -> {
            delay(action.millis.coerceIn(0L, 60_000L))
            Result.success(Unit)
        }
    }

    fun exportJson(config: DeckConfig): String = ObsJson.codec.encodeToString(config)

    fun importJson(raw: String): Result<DeckConfig> = runCatching {
        val parsed = ObsJson.codec.decodeFromString<DeckConfig>(raw.trim())
        require(parsed.pages.isNotEmpty()) { "JSON file does not contain any Macro Deck pages." }
        parsed
    }
}
