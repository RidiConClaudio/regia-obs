package com.regiaobs.app

import com.regiaobs.app.macro.DeckConfig
import com.regiaobs.app.macro.DeckPad
import com.regiaobs.app.macro.DeckPage
import com.regiaobs.app.macro.DeckPresets
import com.regiaobs.app.macro.MacroAction
import com.regiaobs.app.macro.MacroEngine
import com.regiaobs.app.macro.PadFeedback
import com.regiaobs.app.obs.ConnectionState
import com.regiaobs.app.obs.ObsAudioInput
import com.regiaobs.app.obs.ObsController
import com.regiaobs.app.obs.ObsMediaAction
import com.regiaobs.app.obs.ObsMonitorType
import com.regiaobs.app.obs.ObsPropertyOption
import com.regiaobs.app.obs.ObsScene
import com.regiaobs.app.obs.ObsServerProfile
import com.regiaobs.app.obs.ObsSnapshot
import com.regiaobs.app.obs.ObsSpecialInputs
import com.regiaobs.app.obs.PreviewBitmaps
import com.regiaobs.app.obs.ToggleMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroEngineTest {

    private class RecordingFakeController(
        private val failOnScene: String? = null
    ) : ObsController {
        val log = mutableListOf<String>()

        override val connectionState: StateFlow<ConnectionState> =
            MutableStateFlow(ConnectionState.Connected("Fake"))
        override val snapshot: StateFlow<ObsSnapshot> = MutableStateFlow(ObsSnapshot(studioMode = false))
        override val volumeMeters: StateFlow<Map<String, List<Float>>> = MutableStateFlow(emptyMap())
        override val previewBitmaps: StateFlow<PreviewBitmaps> = MutableStateFlow(PreviewBitmaps())

        override fun connect(profile: ObsServerProfile) = Unit
        override fun disconnect() = Unit
        override fun updatePreviewConfig(enabled: Boolean, fps: Int, quality: Int, isAppActive: Boolean) = Unit
        override suspend fun refreshSceneItems(sceneName: String) = Unit
        override suspend fun refreshInputDetails(inputName: String) = Unit

        override suspend fun setProgramScene(sceneName: String): Result<Unit> {
            log += "program:$sceneName"
            return if (sceneName == failOnScene) Result.failure(IllegalStateException("Scena mancante"))
            else Result.success(Unit)
        }

        override suspend fun setPreviewScene(sceneName: String) = Result.success(Unit).also { log += "preview:$sceneName" }
        override suspend fun setStudioMode(enabled: Boolean) = Result.success(Unit).also { log += "studio:$enabled" }
        override suspend fun triggerTransition() = Result.success(Unit).also { log += "transition" }
        override suspend fun setTransition(transitionName: String) = Result.success(Unit)
        override suspend fun setTransitionDuration(durationMs: Int) = Result.success(Unit)
        override suspend fun setTBarPosition(position: Double, release: Boolean) = Result.success(Unit)
        override suspend fun toggleStream() = Result.success(Unit).also { log += "stream:toggle" }
        override suspend fun startStream() = Result.success(Unit)
        override suspend fun stopStream() = Result.success(Unit)
        override suspend fun toggleRecord() = Result.success(Unit)
        override suspend fun startRecord() = Result.success(Unit)
        override suspend fun stopRecord() = Result.success(Unit)
        override suspend fun toggleRecordPause() = Result.success(Unit)
        override suspend fun splitRecordFile() = Result.success(Unit)
        override suspend fun createRecordChapter(chapterName: String?) = Result.success(Unit)
        override suspend fun toggleReplayBuffer() = Result.success(Unit)
        override suspend fun startReplayBuffer() = Result.success(Unit)
        override suspend fun stopReplayBuffer() = Result.success(Unit)
        override suspend fun saveReplayBuffer() = Result.success(Unit).also { log += "replay:save" }
        override suspend fun toggleVirtualCam() = Result.success(Unit)
        override suspend fun startVirtualCam() = Result.success(Unit)
        override suspend fun stopVirtualCam() = Result.success(Unit)
        override suspend fun toggleMute(inputName: String) = Result.success(Unit)
        override suspend fun setMute(inputName: String, muted: Boolean) =
            Result.success(Unit).also { log += "mute:$inputName=$muted" }
        override suspend fun setVolumeMul(inputName: String, volumeMul: Float) = Result.success(Unit)
        override suspend fun setVolumeDb(inputName: String, volumeDb: Float) = Result.success(Unit)
        override suspend fun setMonitorType(inputName: String, monitorType: ObsMonitorType) = Result.success(Unit)
        override suspend fun setSyncOffset(inputName: String, offsetMs: Long) = Result.success(Unit)
        override suspend fun setBalance(inputName: String, balance: Float) = Result.success(Unit)
        override suspend fun getInputPropertyListItems(inputName: String, propertyName: String) =
            Result.success(emptyList<ObsPropertyOption>())
        override suspend fun setInputAudioDevice(inputName: String, propertyName: String, deviceValue: String) =
            Result.success(Unit)
        override suspend fun setSceneItemEnabled(sceneName: String, sceneItemId: Int, enabled: Boolean) = Result.success(Unit)
        override suspend fun setSceneItemEnabledByName(sceneName: String, sourceName: String, mode: ToggleMode) = Result.success(Unit)
        override suspend fun setSceneItemLocked(sceneName: String, sceneItemId: Int, locked: Boolean) = Result.success(Unit)
        override suspend fun setSceneItemIndex(sceneName: String, sceneItemId: Int, index: Int) = Result.success(Unit)
        override suspend fun setSceneItemTransform(sceneName: String, sceneItemId: Int, transformPatch: JsonObject) = Result.success(Unit)
        override suspend fun fitSceneItemToScreen(sceneName: String, sceneItemId: Int) = Result.success(Unit)
        override suspend fun resetSceneItemTransform(sceneName: String, sceneItemId: Int) = Result.success(Unit)
        override suspend fun setFilterEnabled(sourceName: String, filterName: String, enabled: Boolean) = Result.success(Unit)
        override suspend fun toggleFilterByName(sourceName: String, filterName: String, mode: ToggleMode) = Result.success(Unit)
        override suspend fun getInputSettings(inputName: String) = Result.success(JsonObject(emptyMap()))
        override suspend fun setInputSettings(inputName: String, settings: JsonObject, overlay: Boolean) =
            Result.success(Unit).also { log += "settings:$inputName:$settings" }
        override suspend fun pressInputPropertiesButton(inputName: String, propertyName: String) =
            Result.success(Unit).also { log += "propBtn:$inputName:$propertyName" }
        override suspend fun triggerMediaAction(inputName: String, action: ObsMediaAction) = Result.success(Unit)
        override suspend fun setMediaCursor(inputName: String, cursorMs: Long) = Result.success(Unit)
        override suspend fun setProfile(profileName: String) = Result.success(Unit)
        override suspend fun setSceneCollection(collectionName: String) = Result.success(Unit)
        override suspend fun triggerHotkey(hotkeyName: String) = Result.success(Unit)
        override suspend fun callVendorRequest(vendorName: String, requestType: String, requestData: JsonObject?) =
            Result.success(JsonObject(emptyMap()))
        override suspend fun sendRawRequest(requestType: String, requestData: JsonObject?) =
            Result.success(JsonObject(emptyMap()))
    }

    @Test
    fun `execute runs multi-step macro in exact order and clears state`() = runTest {
        val engine = MacroEngine()
        val fake = RecordingFakeController()
        val pad = DeckPad(
            id = "p1",
            label = "Pausa",
            actions = listOf(
                MacroAction.AudioMute("Microfono", ToggleMode.ENABLE),
                MacroAction.Delay(10L),
                MacroAction.SwitchProgramScene("Pausa"),
                MacroAction.SetTextSource("Ticker", "Torno subito"),
                MacroAction.RefreshBrowserSource("Chat")
            )
        )

        val result = engine.execute(pad, fake)
        assertTrue(result.isSuccess)
        assertNull(engine.executionState.value)
        assertEquals(
            listOf(
                "mute:Microfono=true",
                "program:Pausa",
                "settings:Ticker:{\"text\":\"Torno subito\"}",
                "propBtn:Chat:refreshnocache"
            ),
            fake.log
        )
    }

    @Test
    fun `execute aborts remaining steps on error`() = runTest {
        val engine = MacroEngine()
        val fake = RecordingFakeController(failOnScene = "Mancante")
        val pad = DeckPad(
            id = "p2",
            label = "Fail",
            actions = listOf(
                MacroAction.SwitchProgramScene("Mancante"),
                MacroAction.StreamControl(ToggleMode.TOGGLE)
            )
        )

        val result = engine.execute(pad, fake)
        assertTrue(result.isFailure)
        assertEquals(listOf("program:Mancante"), fake.log)
    }

    @Test
    fun `exportJson and importJson roundtrip full DeckConfig`() {
        val engine = MacroEngine()
        val original = DeckPresets.defaultDeckConfig().let {
            it.copy(
                pages = it.pages + DeckPage(
                    id = "custom",
                    title = "Custom",
                    pads = listOf(
                        DeckPad(
                            id = "c1",
                            label = "Clip",
                            actions = listOf(MacroAction.ReplayBufferControl()),
                            feedbackBinding = PadFeedback.ReplayActive
                        )
                    )
                )
            )
        }
        val json = engine.exportJson(original)
        val restored = engine.importJson(json)
        assertTrue(restored.isSuccess)
        assertEquals(original, restored.getOrThrow())
    }

    @Test
    fun `adaptDeckToObs maps starter deck to real Italian OBS sources and scenes`() {
        val snap = ObsSnapshot(
            scenes = listOf(
                ObsScene("Scena 3"),
                ObsScene("CAM/RM"),
                ObsScene("BLUR")
            ),
            programScene = "Scena 3",
            audioInputs = listOf(
                ObsAudioInput(name = "Microfono/disp. ausiliario", kind = "wasapi_input_capture"),
                ObsAudioInput(name = "Audio del desktop", kind = "wasapi_output_capture"),
                ObsAudioInput(name = "Alerts", kind = "browser_source")
            ),
            inputKindsByName = mapOf(
                "ChatBox" to "browser_source",
                "Alerts" to "browser_source"
            ),
            specialInputs = ObsSpecialInputs(
                desktop1 = "Audio del desktop",
                mic1 = "Microfono/disp. ausiliario"
            )
        )

        val (adapted, changed) = DeckPresets.adaptDeckToObs(DeckPresets.defaultDeckConfig(), snap)
        assertTrue(changed)

        val audioPage = adapted.pages.first { it.id == "page_audio" }
        val muteMic = audioPage.pads.first { it.id == "pad_mute_mic" }
        val muteDesk = audioPage.pads.first { it.id == "pad_mute_desk" }
        val brb = audioPage.pads.first { it.id == "pad_brb" }
        val backLive = audioPage.pads.first { it.id == "pad_back_live" }
        val refreshChat = audioPage.pads.first { it.id == "pad_refresh_chat" }

        assertEquals(
            MacroAction.AudioMute("Microfono/disp. ausiliario", ToggleMode.TOGGLE),
            muteMic.actions.first()
        )
        assertEquals(
            MacroAction.AudioMute("Audio del desktop", ToggleMode.TOGGLE),
            muteDesk.actions.first()
        )
        assertEquals(MacroAction.SwitchProgramScene("BLUR"), brb.actions.last())
        assertEquals(MacroAction.SwitchProgramScene("Scena 3"), backLive.actions.first())
        assertEquals(MacroAction.RefreshBrowserSource("ChatBox"), refreshChat.actions.first())

        for (page in adapted.pages) {
            for (pad in page.pads) {
                assertNull(
                    "Pad ${pad.id} should not have missing target",
                    DeckPresets.findPadMissingTarget(pad, snap)
                )
            }
        }
    }

    @Test
    fun `adaptDeckToObs maps starter deck to real English OBS sources and scenes`() {
        val snap = ObsSnapshot(
            scenes = listOf(
                ObsScene("Main Cam"),
                ObsScene("Gaming"),
                ObsScene("BRB")
            ),
            programScene = "Main Cam",
            audioInputs = listOf(
                ObsAudioInput(name = "Mic/Aux", kind = "wasapi_input_capture"),
                ObsAudioInput(name = "Desktop Audio", kind = "wasapi_output_capture")
            ),
            inputKindsByName = mapOf(
                "Stream Chat" to "browser_source"
            ),
            specialInputs = ObsSpecialInputs(
                desktop1 = "Desktop Audio",
                mic1 = "Mic/Aux"
            )
        )

        val (adapted, changed) = DeckPresets.adaptDeckToObs(DeckPresets.defaultDeckConfig(), snap)
        assertTrue(changed)

        val audioPage = adapted.pages.first { it.id == "page_audio" }
        assertEquals(
            MacroAction.AudioMute("Mic/Aux", ToggleMode.TOGGLE),
            audioPage.pads.first { it.id == "pad_mute_mic" }.actions.first()
        )
        assertEquals(
            MacroAction.AudioMute("Desktop Audio", ToggleMode.TOGGLE),
            audioPage.pads.first { it.id == "pad_mute_desk" }.actions.first()
        )
        assertEquals(
            MacroAction.SwitchProgramScene("BRB"),
            audioPage.pads.first { it.id == "pad_brb" }.actions.last()
        )
        assertEquals(
            MacroAction.SwitchProgramScene("Main Cam"),
            audioPage.pads.first { it.id == "pad_back_live" }.actions.first()
        )
        assertEquals(
            MacroAction.RefreshBrowserSource("Stream Chat"),
            audioPage.pads.first { it.id == "pad_refresh_chat" }.actions.first()
        )

        for (page in adapted.pages) {
            for (pad in page.pads) {
                assertNull(
                    "Pad ${pad.id} should not have missing target",
                    DeckPresets.findPadMissingTarget(pad, snap)
                )
            }
        }
    }
}
