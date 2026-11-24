package com.regiaobs.app.obs

import android.graphics.Bitmap
import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ObsServerProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Studio PC",
    val host: String = "192.168.1.100",
    val port: Int = 4455,
    val password: String = "",
    val useTls: Boolean = false
) {
    val webSocketUrl: String
        get() {
            val scheme = if (useTls) "wss" else "ws"
            val formattedHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
            return "$scheme://$formattedHost:$port"
        }
}

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data object Connecting : ConnectionState
    data object Authenticating : ConnectionState
    data class Connected(val serverName: String, val isDemo: Boolean = false) : ConnectionState
    data class Reconnecting(val attempt: Int) : ConnectionState
    data class Error(val message: String, val isAuthError: Boolean = false) : ConnectionState
}

@Immutable
data class ObsScene(
    val name: String,
    val uuid: String = "",
    val index: Int = 0
)

@Immutable
data class SceneItemTransform(
    val positionX: Double = 0.0,
    val positionY: Double = 0.0,
    val scaleX: Double = 1.0,
    val scaleY: Double = 1.0,
    val rotation: Double = 0.0,
    val sourceWidth: Double = 1920.0,
    val sourceHeight: Double = 1080.0,
    val width: Double = 1920.0,
    val height: Double = 1080.0,
    val boundsType: String = "OBS_BOUNDS_NONE",
    val boundsWidth: Double = 0.0,
    val boundsHeight: Double = 0.0,
    val cropTop: Int = 0,
    val cropBottom: Int = 0,
    val cropLeft: Int = 0,
    val cropRight: Int = 0
)

@Immutable
data class ObsSceneItem(
    val sceneItemId: Int,
    val sourceName: String,
    val sourceUuid: String = "",
    val inputKind: String? = null,
    val isGroup: Boolean = false,
    val enabled: Boolean = true,
    val locked: Boolean = false,
    val index: Int = 0,
    val transform: SceneItemTransform? = null,
    val groupChildren: List<ObsSceneItem> = emptyList()
)

enum class ObsMonitorType(val obsValue: String, val shortLabel: String, val displayName: String) {
    NONE("OBS_MONITORING_TYPE_NONE", "Off", "Monitor Off"),
    MONITOR_ONLY("OBS_MONITORING_TYPE_MONITOR_ONLY", "Mon", "Monitor Only (mute output)"),
    MONITOR_AND_OUTPUT("OBS_MONITORING_TYPE_MONITOR_AND_OUTPUT", "M+O", "Monitor and Output");

    companion object {
        fun fromObs(value: String?): ObsMonitorType =
            entries.firstOrNull { it.obsValue == value } ?: NONE
    }
}

@Immutable
data class ObsFilter(
    val name: String,
    val kind: String = "",
    val enabled: Boolean = true,
    val index: Int = 0
)

@Immutable
data class ObsPropertyOption(
    val name: String,
    val value: String,
    val enabled: Boolean = true
)

@Immutable
data class ObsSpecialInputs(
    val desktop1: String? = null,
    val desktop2: String? = null,
    val mic1: String? = null,
    val mic2: String? = null,
    val mic3: String? = null,
    val mic4: String? = null
) {
    fun allNames(): Set<String> =
        listOfNotNull(desktop1, desktop2, mic1, mic2, mic3, mic4)
            .filter { it.isNotBlank() }
            .toSet()

    fun primaryMic(): String? =
        listOfNotNull(mic1, mic2, mic3, mic4).firstOrNull { it.isNotBlank() }

    fun primaryDesktop(): String? =
        listOfNotNull(desktop1, desktop2).firstOrNull { it.isNotBlank() }
}

@Immutable
data class ObsAudioInput(
    val name: String,
    val uuid: String = "",
    val kind: String = "",
    val muted: Boolean = false,
    val volumeMul: Float = 1f,
    val volumeDb: Float = 0f,
    val monitorType: ObsMonitorType = ObsMonitorType.NONE,
    val syncOffsetMs: Long = 0L,
    val balance: Float = 0.5f,
    val active: Boolean = true,
    val filters: List<ObsFilter> = emptyList()
) {
    fun devicePropertyName(): String? = when (kind) {
        "wasapi_input_capture",
        "wasapi_output_capture",
        "pulse_input_capture",
        "pulse_output_capture",
        "coreaudio_input_capture",
        "coreaudio_output_capture",
        "alsa_input_capture" -> "device_id"
        "wasapi_process_output_capture" -> "window"
        else -> null
    }

    fun isMicInput(): Boolean = kind.contains("input_capture", ignoreCase = true)

    fun isOutputDeviceInput(): Boolean =
        kind == "wasapi_output_capture" ||
            kind == "pulse_output_capture" ||
            kind == "coreaudio_output_capture"
}

enum class ObsMediaState(val obsValue: String, val label: String) {
    NONE("OBS_MEDIA_STATE_NONE", "Idle"),
    PLAYING("OBS_MEDIA_STATE_PLAYING", "Playing"),
    OPENING("OBS_MEDIA_STATE_OPENING", "Opening"),
    BUFFERING("OBS_MEDIA_STATE_BUFFERING", "Buffering"),
    PAUSED("OBS_MEDIA_STATE_PAUSED", "Paused"),
    STOPPED("OBS_MEDIA_STATE_STOPPED", "Stopped"),
    ENDED("OBS_MEDIA_STATE_ENDED", "Ended"),
    ERROR("OBS_MEDIA_STATE_ERROR", "Error");

    companion object {
        fun fromObs(value: String?): ObsMediaState =
            entries.firstOrNull { it.obsValue == value } ?: NONE
    }
}

enum class ObsMediaAction(val obsInputAction: String, val label: String) {
    PLAY("OBS_WEBSOCKET_MEDIA_INPUT_ACTION_PLAY", "Play"),
    PAUSE("OBS_WEBSOCKET_MEDIA_INPUT_ACTION_PAUSE", "Pause"),
    RESTART("OBS_WEBSOCKET_MEDIA_INPUT_ACTION_RESTART", "Restart"),
    STOP("OBS_WEBSOCKET_MEDIA_INPUT_ACTION_STOP", "Stop"),
    NEXT("OBS_WEBSOCKET_MEDIA_INPUT_ACTION_NEXT", "Next"),
    PREVIOUS("OBS_WEBSOCKET_MEDIA_INPUT_ACTION_PREVIOUS", "Previous")
}

@Immutable
data class ObsMediaStatus(
    val state: ObsMediaState = ObsMediaState.NONE,
    val cursorMs: Long = 0L,
    val durationMs: Long = 0L
)

@Immutable
data class ObsTransition(
    val name: String,
    val kind: String = "",
    val configurable: Boolean = false
)

@Immutable
data class ObsOutputStatus(
    val streamActive: Boolean = false,
    val streamReconnecting: Boolean = false,
    val streamTimecode: String = "00:00:00.000",
    val streamDurationMs: Long = 0L,
    val streamCongestion: Double = 0.0,
    val streamSkippedFrames: Int = 0,
    val streamTotalFrames: Int = 0,
    val recActive: Boolean = false,
    val recPaused: Boolean = false,
    val recTimecode: String = "00:00:00.000",
    val recDurationMs: Long = 0L,
    val recBytes: Long = 0L,
    val replayActive: Boolean = false,
    val virtualCamActive: Boolean = false
)

@Immutable
data class ObsStats(
    val cpuUsage: Double = 0.0,
    val memoryUsageMb: Double = 0.0,
    val activeFps: Double = 0.0,
    val averageFrameRenderTimeMs: Double = 0.0,
    val renderSkippedFrames: Int = 0,
    val renderTotalFrames: Int = 0,
    val outputSkippedFrames: Int = 0,
    val outputTotalFrames: Int = 0,
    val availableDiskSpaceMb: Double = 0.0
)

@Immutable
data class ObsSnapshot(
    val scenes: List<ObsScene> = emptyList(),
    val programScene: String = "",
    val previewScene: String = "",
    val studioMode: Boolean = false,
    val transitions: List<ObsTransition> = emptyList(),
    val currentTransition: String = "",
    val transitionDurationMs: Int = 300,
    val sceneItemsByScene: Map<String, List<ObsSceneItem>> = emptyMap(),
    val filtersBySource: Map<String, List<ObsFilter>> = emptyMap(),
    val audioInputs: List<ObsAudioInput> = emptyList(),
    val mediaStatuses: Map<String, ObsMediaStatus> = emptyMap(),
    val inputKindsByName: Map<String, String> = emptyMap(),
    val outputStatus: ObsOutputStatus = ObsOutputStatus(),
    val stats: ObsStats = ObsStats(),
    val profiles: List<String> = emptyList(),
    val currentProfile: String = "",
    val sceneCollections: List<String> = emptyList(),
    val currentSceneCollection: String = "",
    val hotkeys: List<String> = emptyList(),
    val obsVersion: String = "",
    val wsVersion: String = "",
    val baseCanvasWidth: Int = 1920,
    val baseCanvasHeight: Int = 1080,
    val specialInputs: ObsSpecialInputs = ObsSpecialInputs()
) {
    fun itemsForScene(sceneName: String): List<ObsSceneItem> =
        sceneItemsByScene[sceneName].orEmpty()

    fun activeSceneItems(): List<ObsSceneItem> =
        itemsForScene(if (studioMode && previewScene.isNotBlank()) previewScene else programScene)

    fun activeSourceNames(): Set<String> {
        val activeScene = if (programScene.isNotBlank()) programScene else return specialInputs.allNames()
        val names = specialInputs.allNames().toMutableSet()
        fun collect(items: List<ObsSceneItem>) {
            for (item in items) {
                if (item.enabled) {
                    names += item.sourceName
                    if (item.groupChildren.isNotEmpty()) collect(item.groupChildren)
                }
            }
        }
        collect(itemsForScene(activeScene))
        return names
    }
}

@Immutable
data class PreviewBitmaps(
    val program: Bitmap? = null,
    val preview: Bitmap? = null,
    val updatedAtMs: Long = 0L
)
