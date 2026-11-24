package com.regiaobs.app.macro

import com.regiaobs.app.obs.ObsMediaAction
import com.regiaobs.app.obs.ObsScene
import com.regiaobs.app.obs.ObsSnapshot
import com.regiaobs.app.obs.ToggleMode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DeckConfig(
    val pages: List<DeckPage> = emptyList(),
    val columnsPortrait: Int = 3,
    val columnsLandscape: Int = 5
)

@Serializable
data class DeckPage(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val pads: List<DeckPad> = emptyList()
)

@Serializable
data class DeckPad(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val subtitle: String = "",
    val iconKey: String = "bolt",
    val colorHex: Long = 0xFF1D2432L,
    val actions: List<MacroAction> = emptyList(),
    val feedbackBinding: PadFeedback? = null
)

@Serializable
sealed interface PadFeedback {
    @Serializable
    @SerialName("program_scene")
    data class ProgramScene(val sceneName: String) : PadFeedback

    @Serializable
    @SerialName("preview_scene")
    data class PreviewScene(val sceneName: String) : PadFeedback

    @Serializable
    @SerialName("stream_active")
    data object StreamActive : PadFeedback

    @Serializable
    @SerialName("record_active")
    data object RecordActive : PadFeedback

    @Serializable
    @SerialName("replay_active")
    data object ReplayActive : PadFeedback

    @Serializable
    @SerialName("virtual_cam_active")
    data object VirtualCamActive : PadFeedback

    @Serializable
    @SerialName("studio_mode_active")
    data object StudioModeActive : PadFeedback

    @Serializable
    @SerialName("audio_muted")
    data class AudioMuted(val inputName: String) : PadFeedback

    @Serializable
    @SerialName("source_visible")
    data class SourceVisible(val sceneName: String, val sourceName: String) : PadFeedback

    @Serializable
    @SerialName("filter_enabled")
    data class FilterEnabled(val sourceName: String, val filterName: String) : PadFeedback
}

@Serializable
enum class RecordControlMode(val label: String) {
    TOGGLE("Toggle REC"),
    START("Start REC"),
    STOP("Stop REC"),
    PAUSE_TOGGLE("Pause / Resume REC")
}

@Serializable
enum class ReplayControlMode(val label: String) {
    SAVE("Save Clip"),
    TOGGLE("Toggle Replay Buffer"),
    START("Start Replay Buffer"),
    STOP("Stop Replay Buffer")
}

@Serializable
sealed interface MacroAction {
    @Serializable
    @SerialName("switch_program")
    data class SwitchProgramScene(val sceneName: String) : MacroAction

    @Serializable
    @SerialName("switch_preview")
    data class SwitchPreviewScene(val sceneName: String) : MacroAction

    @Serializable
    @SerialName("trigger_transition")
    data object TriggerStudioTransition : MacroAction

    @Serializable
    @SerialName("toggle_studio_mode")
    data object ToggleStudioMode : MacroAction

    @Serializable
    @SerialName("stream_control")
    data class StreamControl(val mode: ToggleMode = ToggleMode.TOGGLE) : MacroAction

    @Serializable
    @SerialName("record_control")
    data class RecordControl(val mode: RecordControlMode = RecordControlMode.TOGGLE) : MacroAction

    @Serializable
    @SerialName("replay_control")
    data class ReplayBufferControl(val mode: ReplayControlMode = ReplayControlMode.SAVE) : MacroAction

    @Serializable
    @SerialName("vcam_control")
    data class VirtualCamControl(val mode: ToggleMode = ToggleMode.TOGGLE) : MacroAction

    @Serializable
    @SerialName("audio_mute")
    data class AudioMute(val inputName: String, val mode: ToggleMode = ToggleMode.TOGGLE) : MacroAction

    @Serializable
    @SerialName("audio_volume_db")
    data class AudioSetVolumeDb(val inputName: String, val volumeDb: Float) : MacroAction

    @Serializable
    @SerialName("source_visibility")
    data class SourceVisibility(
        val sceneName: String,
        val sourceName: String,
        val mode: ToggleMode = ToggleMode.TOGGLE
    ) : MacroAction

    @Serializable
    @SerialName("filter_toggle")
    data class FilterToggle(
        val sourceName: String,
        val filterName: String,
        val mode: ToggleMode = ToggleMode.TOGGLE
    ) : MacroAction

    @Serializable
    @SerialName("media_action")
    data class MediaAction(
        val inputName: String,
        val action: ObsMediaAction = ObsMediaAction.PLAY
    ) : MacroAction

    @Serializable
    @SerialName("set_text")
    data class SetTextSource(val inputName: String, val text: String) : MacroAction

    @Serializable
    @SerialName("refresh_browser")
    data class RefreshBrowserSource(val inputName: String) : MacroAction

    @Serializable
    @SerialName("hotkey")
    data class TriggerHotkey(val hotkeyName: String) : MacroAction

    @Serializable
    @SerialName("set_profile")
    data class SetProfile(val profileName: String) : MacroAction

    @Serializable
    @SerialName("set_scene_collection")
    data class SetSceneCollection(val collectionName: String) : MacroAction

    @Serializable
    @SerialName("delay")
    data class Delay(val millis: Long = 350L) : MacroAction
}

fun MacroAction.describeAction(): String = when (this) {
    is MacroAction.SwitchProgramScene -> "Program Scene: $sceneName"
    is MacroAction.SwitchPreviewScene -> "Preview Scene: $sceneName"
    is MacroAction.TriggerStudioTransition -> "Trigger Studio Transition"
    is MacroAction.ToggleStudioMode -> "Toggle Studio Mode"
    is MacroAction.StreamControl -> "Stream: ${mode.label}"
    is MacroAction.RecordControl -> mode.label
    is MacroAction.ReplayBufferControl -> mode.label
    is MacroAction.VirtualCamControl -> "Virtual Cam: ${mode.label}"
    is MacroAction.AudioMute -> "Audio '$inputName': ${mode.label}"
    is MacroAction.AudioSetVolumeDb -> "Volume '$inputName' to ${"%.1f".format(volumeDb)} dB"
    is MacroAction.SourceVisibility -> "Source '$sourceName': ${mode.label}"
    is MacroAction.FilterToggle -> "Filter '$filterName': ${mode.label}"
    is MacroAction.MediaAction -> "Media '$inputName': ${action.label}"
    is MacroAction.SetTextSource -> "Text '$inputName': \"$text\""
    is MacroAction.RefreshBrowserSource -> "Refresh Browser '$inputName'"
    is MacroAction.TriggerHotkey -> "Hotkey: $hotkeyName"
    is MacroAction.SetProfile -> "Profile: $profileName"
    is MacroAction.SetSceneCollection -> "Scene Collection: $collectionName"
    is MacroAction.Delay -> "Wait ${millis} ms"
}

object DeckPresets {
    val swatchPalette: List<Pair<String, Long>> = listOf(
        "Graphite" to 0xFF1A2230L,
        "Live Red" to 0xFF5A1D22L,
        "Studio Green" to 0xFF17402AL,
        "Rec Amber" to 0xFF4F3412L,
        "Replay Cyan" to 0xFF14384EL,
        "Indigo" to 0xFF282452L,
        "Magenta" to 0xFF4A1D42L,
        "Slate" to 0xFF2A3241L
    )

    val availableIconKeys: List<String> = listOf(
        "bolt", "live", "record", "replay", "cam", "mic", "mic_off",
        "music", "scene", "transition", "studio", "play", "pause",
        "refresh", "text", "filter", "eye", "sparkles", "timer", "settings"
    )

    fun defaultDeckConfig(): DeckConfig = DeckConfig(
        columnsPortrait = 3,
        columnsLandscape = 5,
        pages = listOf(
            DeckPage(
                id = "page_regia",
                title = "Quick Control",
                pads = listOf(
                    DeckPad(
                        id = "pad_live",
                        label = "LIVE",
                        subtitle = "Start / Stop",
                        iconKey = "live",
                        colorHex = 0xFF5A1D22L,
                        actions = listOf(MacroAction.StreamControl(ToggleMode.TOGGLE)),
                        feedbackBinding = PadFeedback.StreamActive
                    ),
                    DeckPad(
                        id = "pad_rec",
                        label = "REC",
                        subtitle = "Record",
                        iconKey = "record",
                        colorHex = 0xFF4F3412L,
                        actions = listOf(MacroAction.RecordControl(RecordControlMode.TOGGLE)),
                        feedbackBinding = PadFeedback.RecordActive
                    ),
                    DeckPad(
                        id = "pad_replay",
                        label = "Save Clip",
                        subtitle = "Replay Buffer",
                        iconKey = "replay",
                        colorHex = 0xFF14384EL,
                        actions = listOf(MacroAction.ReplayBufferControl(ReplayControlMode.SAVE)),
                        feedbackBinding = PadFeedback.ReplayActive
                    ),
                    DeckPad(
                        id = "pad_trans",
                        label = "Transition",
                        subtitle = "Studio Take",
                        iconKey = "transition",
                        colorHex = 0xFF17402AL,
                        actions = listOf(MacroAction.TriggerStudioTransition)
                    ),
                    DeckPad(
                        id = "pad_studio",
                        label = "Studio Mode",
                        subtitle = "Dual Screen",
                        iconKey = "studio",
                        colorHex = 0xFF282452L,
                        actions = listOf(MacroAction.ToggleStudioMode),
                        feedbackBinding = PadFeedback.StudioModeActive
                    ),
                    DeckPad(
                        id = "pad_vcam",
                        label = "Virtual Cam",
                        subtitle = "OBS Camera",
                        iconKey = "cam",
                        colorHex = 0xFF1A2230L,
                        actions = listOf(MacroAction.VirtualCamControl(ToggleMode.TOGGLE)),
                        feedbackBinding = PadFeedback.VirtualCamActive
                    )
                )
            ),
            DeckPage(
                id = "page_audio",
                title = "Audio & Macros",
                pads = listOf(
                    DeckPad(
                        id = "pad_mute_mic",
                        label = "Mute Mic",
                        subtitle = "Microphone",
                        iconKey = "mic_off",
                        colorHex = 0xFF5A1D22L,
                        actions = listOf(MacroAction.AudioMute("Microphone", ToggleMode.TOGGLE)),
                        feedbackBinding = PadFeedback.AudioMuted("Microphone")
                    ),
                    DeckPad(
                        id = "pad_mute_desk",
                        label = "Mute Desktop",
                        subtitle = "Desktop Audio",
                        iconKey = "music",
                        colorHex = 0xFF2A3241L,
                        actions = listOf(MacroAction.AudioMute("Desktop Audio", ToggleMode.TOGGLE)),
                        feedbackBinding = PadFeedback.AudioMuted("Desktop Audio")
                    ),
                    DeckPad(
                        id = "pad_rec_pause",
                        label = "Pause REC",
                        subtitle = "Pause / Resume",
                        iconKey = "pause",
                        colorHex = 0xFF4F3412L,
                        actions = listOf(MacroAction.RecordControl(RecordControlMode.PAUSE_TOGGLE))
                    ),
                    DeckPad(
                        id = "pad_brb",
                        label = "BRB Macro",
                        subtitle = "BRB + Mute Mic",
                        iconKey = "bolt",
                        colorHex = 0xFF4A1D42L,
                        actions = listOf(
                            MacroAction.AudioMute("Microphone", ToggleMode.ENABLE),
                            MacroAction.Delay(150L),
                            MacroAction.SwitchProgramScene("BRB")
                        ),
                        feedbackBinding = PadFeedback.ProgramScene("BRB")
                    ),
                    DeckPad(
                        id = "pad_back_live",
                        label = "Back Live",
                        subtitle = "Scene + Mic ON",
                        iconKey = "sparkles",
                        colorHex = 0xFF17402AL,
                        actions = listOf(
                            MacroAction.SwitchProgramScene("Just Chatting"),
                            MacroAction.Delay(150L),
                            MacroAction.AudioMute("Microphone", ToggleMode.DISABLE)
                        )
                    ),
                    DeckPad(
                        id = "pad_refresh_chat",
                        label = "Refresh Chat",
                        subtitle = "Browser Cache",
                        iconKey = "refresh",
                        colorHex = 0xFF14384EL,
                        actions = listOf(MacroAction.RefreshBrowserSource("Chat Overlay"))
                    )
                )
            )
        )
    )

    fun buildPageFromScenes(scenes: List<ObsScene>): DeckPage {
        val pads = scenes.mapIndexed { idx, scene ->
            val swatch = swatchPalette[idx % swatchPalette.size].second
            DeckPad(
                label = scene.name,
                subtitle = "OBS Scene",
                iconKey = "scene",
                colorHex = swatch,
                actions = listOf(MacroAction.SwitchProgramScene(scene.name)),
                feedbackBinding = PadFeedback.ProgramScene(scene.name)
            )
        }
        return DeckPage(
            title = "Live Scenes",
            pads = pads
        )
    }

    fun isPadActive(pad: DeckPad, snap: ObsSnapshot): Boolean {
        val binding = pad.feedbackBinding ?: return false
        return when (binding) {
            is PadFeedback.ProgramScene -> snap.programScene == binding.sceneName
            is PadFeedback.PreviewScene -> snap.studioMode && snap.previewScene == binding.sceneName
            is PadFeedback.StreamActive -> snap.outputStatus.streamActive
            is PadFeedback.RecordActive -> snap.outputStatus.recActive
            is PadFeedback.ReplayActive -> snap.outputStatus.replayActive
            is PadFeedback.VirtualCamActive -> snap.outputStatus.virtualCamActive
            is PadFeedback.StudioModeActive -> snap.studioMode
            is PadFeedback.AudioMuted -> snap.audioInputs.firstOrNull { it.name == binding.inputName }?.muted == true
            is PadFeedback.SourceVisible -> {
                val targetScene = binding.sceneName.ifBlank { snap.programScene }
                snap.itemsForScene(targetScene).any { it.sourceName == binding.sourceName && it.enabled }
            }
            is PadFeedback.FilterEnabled -> {
                snap.filtersBySource[binding.sourceName]?.any { it.name == binding.filterName && it.enabled } == true
            }
        }
    }

    fun findPadMissingTarget(pad: DeckPad, snap: ObsSnapshot): String? {
        if (snap.scenes.isEmpty() && snap.audioInputs.isEmpty()) return null
        val audioNames = snap.audioInputs.map { it.name }.toSet()
        val sceneNames = snap.scenes.map { it.name }.toSet()
        val inputNames = snap.inputKindsByName.keys + audioNames

        for (action in pad.actions) {
            when (action) {
                is MacroAction.SwitchProgramScene ->
                    if (action.sceneName.isNotBlank() && action.sceneName !in sceneNames) return action.sceneName
                is MacroAction.SwitchPreviewScene ->
                    if (action.sceneName.isNotBlank() && action.sceneName !in sceneNames) return action.sceneName
                is MacroAction.AudioMute ->
                    if (action.inputName !in audioNames) return action.inputName
                is MacroAction.AudioSetVolumeDb ->
                    if (action.inputName !in audioNames) return action.inputName
                is MacroAction.RefreshBrowserSource ->
                    if (action.inputName !in inputNames) return action.inputName
                is MacroAction.SetTextSource ->
                    if (action.inputName !in inputNames) return action.inputName
                is MacroAction.MediaAction ->
                    if (action.inputName !in snap.mediaStatuses.keys && action.inputName !in inputNames) {
                        return action.inputName
                    }
                is MacroAction.SourceVisibility -> {
                    if (action.sceneName.isNotBlank() && action.sceneName !in sceneNames) return action.sceneName
                    val targetScene = action.sceneName.ifBlank { snap.programScene }
                    val items = snap.itemsForScene(targetScene)
                    if (items.isNotEmpty() && items.none { it.sourceName == action.sourceName }) {
                        return action.sourceName
                    }
                }
                else -> Unit
            }
        }
        return null
    }

    fun adaptDeckToObs(config: DeckConfig, snap: ObsSnapshot): Pair<DeckConfig, Boolean> {
        if (snap.scenes.isEmpty() && snap.audioInputs.isEmpty()) return config to false

        val audioNames = snap.audioInputs.map { it.name }.toSet()
        val sceneNames = snap.scenes.map { it.name }.toSet()
        val inputNames = snap.inputKindsByName.keys + audioNames

        val micName = snap.specialInputs.primaryMic()?.takeIf { it in audioNames }
            ?: snap.audioInputs.firstOrNull { it.isMicInput() }?.name
            ?: snap.audioInputs.firstOrNull { it.name.contains("mic", ignoreCase = true) }?.name
            ?: snap.audioInputs.firstOrNull()?.name

        val desktopName = snap.specialInputs.primaryDesktop()?.takeIf { it in audioNames }
            ?: snap.audioInputs.firstOrNull { it.isOutputDeviceInput() }?.name
            ?: snap.audioInputs.firstOrNull {
                it.name.contains("desktop", ignoreCase = true) ||
                    it.name.contains("altoparlant", ignoreCase = true) ||
                    it.name.contains("speaker", ignoreCase = true)
            }?.name

        val browserName = snap.inputKindsByName.entries
            .firstOrNull { (name, kind) ->
                kind.contains("browser", ignoreCase = true) && name.contains("chat", ignoreCase = true)
            }?.key
            ?: snap.inputKindsByName.entries
                .firstOrNull { it.value.contains("browser", ignoreCase = true) }?.key

        val pauseSceneName = snap.scenes.firstOrNull {
            it.name.contains("pausa", ignoreCase = true) ||
                it.name.contains("pause", ignoreCase = true) ||
                it.name.contains("brb", ignoreCase = true) ||
                it.name.contains("afk", ignoreCase = true) ||
                it.name.contains("blur", ignoreCase = true)
        }?.name ?: snap.scenes.lastOrNull()?.name

        val liveSceneName = snap.programScene.takeIf { it.isNotBlank() && it in sceneNames }
            ?: snap.scenes.firstOrNull {
                it.name.contains("cam", ignoreCase = true) ||
                    it.name.contains("main", ignoreCase = true) ||
                    it.name.contains("chat", ignoreCase = true)
            }?.name
            ?: snap.scenes.firstOrNull()?.name

        var changed = false
        val adaptedPages = config.pages.map { page ->
            val adaptedPads = page.pads.map { pad ->
                val newActions = pad.actions.map { action ->
                    when (action) {
                        is MacroAction.AudioMute -> {
                            if (action.inputName in audioNames) action
                            else {
                                val looksDesktop = pad.id == "pad_mute_desk" ||
                                    action.inputName.contains("desktop", ignoreCase = true)
                                val replacement = if (looksDesktop) desktopName else micName
                                if (replacement != null && replacement != action.inputName) {
                                    changed = true
                                    action.copy(inputName = replacement)
                                } else action
                            }
                        }

                        is MacroAction.AudioSetVolumeDb -> {
                            if (action.inputName in audioNames) action
                            else {
                                val looksDesktop = action.inputName.contains("desktop", ignoreCase = true)
                                val replacement = if (looksDesktop) desktopName else micName
                                if (replacement != null && replacement != action.inputName) {
                                    changed = true
                                    action.copy(inputName = replacement)
                                } else action
                            }
                        }

                        is MacroAction.RefreshBrowserSource -> {
                            if (action.inputName in inputNames) action
                            else if (browserName != null && browserName != action.inputName) {
                                changed = true
                                action.copy(inputName = browserName)
                            } else action
                        }

                    is MacroAction.SwitchProgramScene -> {
                        if (action.sceneName in sceneNames) action
                        else {
                            val replacement = if (
                                pad.id == "pad_brb" ||
                                action.sceneName.contains("pausa", ignoreCase = true) ||
                                action.sceneName.contains("pause", ignoreCase = true) ||
                                action.sceneName.contains("brb", ignoreCase = true)
                            ) {
                                pauseSceneName
                            } else {
                                liveSceneName
                            }
                            if (replacement != null && replacement != action.sceneName) {
                                changed = true
                                action.copy(sceneName = replacement)
                            } else action
                        }
                    }

                        else -> action
                    }
                }

                val newFeedback = when (val fb = pad.feedbackBinding) {
                    is PadFeedback.AudioMuted -> {
                        if (fb.inputName in audioNames) fb
                        else {
                            val looksDesktop = pad.id == "pad_mute_desk" ||
                                fb.inputName.contains("desktop", ignoreCase = true)
                            val replacement = if (looksDesktop) desktopName else micName
                            if (replacement != null && replacement != fb.inputName) {
                                changed = true
                                fb.copy(inputName = replacement)
                            } else fb
                        }
                    }

                    is PadFeedback.ProgramScene -> {
                        if (fb.sceneName in sceneNames) fb
                        else {
                            val replacement = if (
                                pad.id == "pad_brb" ||
                                fb.sceneName.contains("pausa", ignoreCase = true) ||
                                fb.sceneName.contains("pause", ignoreCase = true) ||
                                fb.sceneName.contains("brb", ignoreCase = true)
                            ) pauseSceneName else liveSceneName
                            if (replacement != null && replacement != fb.sceneName) {
                                changed = true
                                fb.copy(sceneName = replacement)
                            } else fb
                        }
                    }

                    else -> fb
                }

                val newSubtitle = when (pad.id) {
                    "pad_mute_mic" -> micName ?: pad.subtitle
                    "pad_mute_desk" -> desktopName ?: pad.subtitle
                    "pad_refresh_chat" -> browserName ?: pad.subtitle
                    "pad_brb" -> if (pauseSceneName != null) "$pauseSceneName + Mute Mic" else pad.subtitle
                    "pad_back_live" -> if (liveSceneName != null) "$liveSceneName + Mic ON" else pad.subtitle
                    else -> pad.subtitle
                }
                if (newSubtitle != pad.subtitle) changed = true

                pad.copy(
                    subtitle = newSubtitle,
                    actions = newActions,
                    feedbackBinding = newFeedback
                )
            }
            page.copy(pads = adaptedPads)
        }

        return if (changed) config.copy(pages = adaptedPages) to true else config to false
    }
}
