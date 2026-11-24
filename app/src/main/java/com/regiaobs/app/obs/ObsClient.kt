package com.regiaobs.app.obs

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
enum class ToggleMode(val label: String) {
    TOGGLE("Toggle"),
    ENABLE("Enable"),
    DISABLE("Disable")
}

interface ObsController {
    val connectionState: StateFlow<ConnectionState>
    val snapshot: StateFlow<ObsSnapshot>
    val volumeMeters: StateFlow<Map<String, List<Float>>>
    val previewBitmaps: StateFlow<PreviewBitmaps>

    fun connect(profile: ObsServerProfile)
    fun disconnect()
    fun updatePreviewConfig(enabled: Boolean, fps: Int, quality: Int, isAppActive: Boolean)

    suspend fun refreshSceneItems(sceneName: String)
    suspend fun refreshInputDetails(inputName: String)

    // Scenes, Studio Mode, Transitions
    suspend fun setProgramScene(sceneName: String): Result<Unit>
    suspend fun setPreviewScene(sceneName: String): Result<Unit>
    suspend fun setStudioMode(enabled: Boolean): Result<Unit>
    suspend fun triggerTransition(): Result<Unit>
    suspend fun setTransition(transitionName: String): Result<Unit>
    suspend fun setTransitionDuration(durationMs: Int): Result<Unit>
    suspend fun setTBarPosition(position: Double, release: Boolean = true): Result<Unit>

    // Outputs
    suspend fun toggleStream(): Result<Unit>
    suspend fun startStream(): Result<Unit>
    suspend fun stopStream(): Result<Unit>
    suspend fun toggleRecord(): Result<Unit>
    suspend fun startRecord(): Result<Unit>
    suspend fun stopRecord(): Result<Unit>
    suspend fun toggleRecordPause(): Result<Unit>
    suspend fun splitRecordFile(): Result<Unit>
    suspend fun createRecordChapter(chapterName: String? = null): Result<Unit>
    suspend fun toggleReplayBuffer(): Result<Unit>
    suspend fun startReplayBuffer(): Result<Unit>
    suspend fun stopReplayBuffer(): Result<Unit>
    suspend fun saveReplayBuffer(): Result<Unit>
    suspend fun toggleVirtualCam(): Result<Unit>
    suspend fun startVirtualCam(): Result<Unit>
    suspend fun stopVirtualCam(): Result<Unit>

    // Audio
    suspend fun toggleMute(inputName: String): Result<Unit>
    suspend fun setMute(inputName: String, muted: Boolean): Result<Unit>
    suspend fun setVolumeMul(inputName: String, volumeMul: Float): Result<Unit>
    suspend fun setVolumeDb(inputName: String, volumeDb: Float): Result<Unit>
    suspend fun setMonitorType(inputName: String, monitorType: ObsMonitorType): Result<Unit>
    suspend fun setSyncOffset(inputName: String, offsetMs: Long): Result<Unit>
    suspend fun setBalance(inputName: String, balance: Float): Result<Unit>
    suspend fun getInputPropertyListItems(inputName: String, propertyName: String): Result<List<ObsPropertyOption>>
    suspend fun setInputAudioDevice(inputName: String, propertyName: String, deviceValue: String): Result<Unit>

    // Sources, Scene Items, Filters, Settings, Transform, Media
    suspend fun setSceneItemEnabled(sceneName: String, sceneItemId: Int, enabled: Boolean): Result<Unit>
    suspend fun setSceneItemEnabledByName(sceneName: String, sourceName: String, mode: ToggleMode): Result<Unit>
    suspend fun setSceneItemLocked(sceneName: String, sceneItemId: Int, locked: Boolean): Result<Unit>
    suspend fun setSceneItemIndex(sceneName: String, sceneItemId: Int, index: Int): Result<Unit>
    suspend fun setSceneItemTransform(sceneName: String, sceneItemId: Int, transformPatch: JsonObject): Result<Unit>
    suspend fun fitSceneItemToScreen(sceneName: String, sceneItemId: Int): Result<Unit>
    suspend fun resetSceneItemTransform(sceneName: String, sceneItemId: Int): Result<Unit>
    suspend fun setFilterEnabled(sourceName: String, filterName: String, enabled: Boolean): Result<Unit>
    suspend fun toggleFilterByName(sourceName: String, filterName: String, mode: ToggleMode): Result<Unit>
    suspend fun getInputSettings(inputName: String): Result<JsonObject>
    suspend fun setInputSettings(inputName: String, settings: JsonObject, overlay: Boolean = true): Result<Unit>
    suspend fun pressInputPropertiesButton(inputName: String, propertyName: String): Result<Unit>
    suspend fun triggerMediaAction(inputName: String, action: ObsMediaAction): Result<Unit>
    suspend fun setMediaCursor(inputName: String, cursorMs: Long): Result<Unit>

    // System & Raw
    suspend fun setProfile(profileName: String): Result<Unit>
    suspend fun setSceneCollection(collectionName: String): Result<Unit>
    suspend fun triggerHotkey(hotkeyName: String): Result<Unit>
    suspend fun callVendorRequest(vendorName: String, requestType: String, requestData: JsonObject? = null): Result<JsonObject>
    suspend fun sendRawRequest(requestType: String, requestData: JsonObject? = null): Result<JsonObject>
}
