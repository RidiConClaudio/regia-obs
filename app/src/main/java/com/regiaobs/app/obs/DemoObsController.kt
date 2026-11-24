package com.regiaobs.app.obs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

class DemoObsController : ObsController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _snapshot = MutableStateFlow(initialDemoSnapshot())
    override val snapshot: StateFlow<ObsSnapshot> = _snapshot.asStateFlow()

    private val _volumeMeters = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    override val volumeMeters: StateFlow<Map<String, List<Float>>> = _volumeMeters.asStateFlow()

    private val _previewBitmaps = MutableStateFlow(PreviewBitmaps())
    override val previewBitmaps: StateFlow<PreviewBitmaps> = _previewBitmaps.asStateFlow()

    private var simJob: Job? = null
    private var previewEnabled = true
    private var previewFps = 2
    private var isAppActive = true

    private val inputSettingsStore = mutableMapOf(
        "News Ticker" to buildJsonObject { put("text", "LIVE: RegiaOBS Demo Mode active — control everything from your phone") },
        "Chat Overlay" to buildJsonObject { put("url", "https://localhost:3000/chat-overlay"); put("fps", 60) },
        "Microphone" to buildJsonObject { put("device_id", "default") },
        "Desktop Audio" to buildJsonObject { put("device_id", "default") }
    )

    override fun connect(profile: ObsServerProfile) {
        _snapshot.value = initialDemoSnapshot()
        _connectionState.value = ConnectionState.Connected(
            serverName = "Demo Studio (Simulator)",
            isDemo = true
        )
        startSimulation()
    }

    override fun disconnect() {
        simJob?.cancel()
        simJob = null
        _connectionState.value = ConnectionState.Disconnected
        _volumeMeters.value = emptyMap()
        _previewBitmaps.value = PreviewBitmaps()
    }

    override fun updatePreviewConfig(enabled: Boolean, fps: Int, quality: Int, isAppActive: Boolean) {
        previewEnabled = enabled
        previewFps = fps
        this.isAppActive = isAppActive
    }

    private fun startSimulation() {
        simJob?.cancel()
        simJob = scope.launch {
            var tick = 0L
            while (isActive) {
                tick++
                val t = tick * 0.14
                val snap = _snapshot.value

                // Update VU meters at ~14 Hz
                val meters = snap.audioInputs.associate { input ->
                    if (input.muted || input.volumeMul <= 0.001f) {
                        input.name to listOf(0f, 0f)
                    } else {
                        val base = when (input.name) {
                            "Microphone" -> (0.42 + 0.32 * abs(sin(t * 1.9 + 0.4))).toFloat()
                            "Desktop Audio" -> (0.28 + 0.25 * abs(sin(t * 1.1))).toFloat()
                            "BGM Music" -> (0.18 + 0.12 * abs(sin(t * 0.7 + 1.2))).toFloat()
                            else -> (0.22 + 0.18 * abs(sin(t * 1.4))).toFloat()
                        } * input.volumeMul
                        val left = base.coerceIn(0f, 1f)
                        val right = (base * (0.92f + 0.12f * sin(t).toFloat())).coerceIn(0f, 1f)
                        input.name to listOf(left, right)
                    }
                }
                _volumeMeters.value = meters

                // Every ~10 ticks (~700ms) advance timers and media cursor
                if (tick % 10 == 0L) {
                    _snapshot.update { cur ->
                        val out = cur.outputStatus
                        val nextStreamMs = if (out.streamActive) out.streamDurationMs + 700L else 0L
                        val nextRecMs = if (out.recActive && !out.recPaused) out.recDurationMs + 700L else if (!out.recActive) 0L else out.recDurationMs
                        val nextMedia = cur.mediaStatuses.mapValues { (_, st) ->
                            if (st.state == ObsMediaState.PLAYING && st.durationMs > 0) {
                                val nextCursor = (st.cursorMs + 700L) % st.durationMs
                                st.copy(cursorMs = nextCursor)
                            } else st
                        }
                        cur.copy(
                            outputStatus = out.copy(
                                streamDurationMs = nextStreamMs,
                                streamTimecode = formatTimecode(nextStreamMs),
                                recDurationMs = nextRecMs,
                                recTimecode = formatTimecode(nextRecMs),
                                recBytes = if (out.recActive) out.recBytes + 850_000L else 0L
                            ),
                            mediaStatuses = nextMedia,
                            stats = cur.stats.copy(
                                cpuUsage = 11.4 + 2.6 * abs(sin(t * 0.2)),
                                activeFps = 59.94 + 0.06 * sin(t * 0.3),
                                averageFrameRenderTimeMs = 2.4 + 0.5 * abs(sin(t * 0.25))
                            )
                        )
                    }
                }

                // Render synthetic preview bitmaps at configured FPS
                if (previewEnabled && isAppActive && previewFps > 0) {
                    val periodTicks = (14 / previewFps.coerceIn(1, 8)).coerceAtLeast(2)
                    if (tick % periodTicks == 0L) {
                        val s = _snapshot.value
                        val progBmp = renderDemoFrame(s.programScene, isProgram = true, s = s, phase = t)
                        val prevBmp = if (s.studioMode) {
                            renderDemoFrame(s.previewScene.ifBlank { s.programScene }, isProgram = false, s = s, phase = t)
                        } else progBmp
                        _previewBitmaps.value = PreviewBitmaps(
                            program = progBmp,
                            preview = prevBmp,
                            updatedAtMs = System.currentTimeMillis()
                        )
                    }
                }

                delay(70L)
            }
        }
    }

    private fun renderDemoFrame(sceneName: String, isProgram: Boolean, s: ObsSnapshot, phase: Double): Bitmap {
        val w = 640
        val h = 360
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgColor = when (sceneName) {
            "Stream Starting" -> Color.rgb(18, 25, 44)
            "Gaming + Cam" -> Color.rgb(15, 32, 34)
            "Just Chatting" -> Color.rgb(34, 20, 42)
            "BRB" -> Color.rgb(36, 26, 16)
            else -> Color.rgb(20, 24, 32)
        }
        canvas.drawColor(bgColor)

        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(26, 255, 255, 255)
            strokeWidth = 1f
        }
        for (x in 0..w step 64) canvas.drawLine(x.toFloat(), 0f, x.toFloat(), h.toFloat(), gridPaint)
        for (y in 0..h step 64) canvas.drawLine(0f, y.toFloat(), w.toFloat(), y.toFloat(), gridPaint)

        val activeItems = s.itemsForScene(sceneName).filter { it.enabled }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(65, 56, 189, 248)
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isProgram) Color.rgb(239, 59, 59) else Color.rgb(34, 197, 94)
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        // Animated pulse bar inside simulated screen
        val pulseX = ((sin(phase * 0.7) * 0.5 + 0.5) * (w - 160)).toFloat()
        canvas.drawRoundRect(RectF(pulseX, 270f, pulseX + 120f, 286f), 8f, 8f, boxPaint)

        // Draw webcam PIP if enabled in this scene
        if (activeItems.any { it.sourceName.contains("Webcam", ignoreCase = true) }) {
            val pipRect = RectF(420f, 190f, 608f, 316f)
            canvas.drawRoundRect(pipRect, 14f, 14f, boxPaint)
            canvas.drawRoundRect(pipRect, 14f, 14f, strokePaint)
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 30f
            isFakeBoldText = true
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 220, 230, 245)
            textSize = 18f
        }
        canvas.drawText(sceneName, 32f, 64f, titlePaint)
        canvas.drawText(
            if (isProgram) "PROGRAM • SIMULATOR" else "PREVIEW • SIMULATOR",
            32f,
            96f,
            subPaint
        )
        canvas.drawText(
            "Active sources: ${activeItems.joinToString(", ") { it.sourceName }.ifBlank { "None" }}",
            32f,
            336f,
            subPaint
        )
        return bmp
    }

    private fun formatTimecode(ms: Long): String {
        val totalSec = ms / 1000L
        val h = totalSec / 3600L
        val m = (totalSec % 3600L) / 60L
        val sec = totalSec % 60L
        val milli = ms % 1000L
        return String.format(Locale.US, "%02d:%02d:%02d.%03d", h, m, sec, milli)
    }

    override suspend fun refreshSceneItems(sceneName: String) = Unit
    override suspend fun refreshInputDetails(inputName: String) = Unit

    override suspend fun setProgramScene(sceneName: String): Result<Unit> {
        _snapshot.update { it.copy(programScene = sceneName) }
        return Result.success(Unit)
    }

    override suspend fun setPreviewScene(sceneName: String): Result<Unit> {
        _snapshot.update { it.copy(previewScene = sceneName) }
        return Result.success(Unit)
    }

    override suspend fun setStudioMode(enabled: Boolean): Result<Unit> {
        _snapshot.update { cur ->
            cur.copy(
                studioMode = enabled,
                previewScene = if (enabled && cur.previewScene.isBlank()) cur.scenes.firstOrNull()?.name ?: cur.programScene else cur.previewScene
            )
        }
        return Result.success(Unit)
    }

    override suspend fun triggerTransition(): Result<Unit> {
        _snapshot.update { cur ->
            if (!cur.studioMode || cur.previewScene.isBlank()) return@update cur
            val prevProg = cur.programScene
            cur.copy(programScene = cur.previewScene, previewScene = prevProg)
        }
        return Result.success(Unit)
    }

    override suspend fun setTransition(transitionName: String): Result<Unit> {
        _snapshot.update { it.copy(currentTransition = transitionName) }
        return Result.success(Unit)
    }

    override suspend fun setTransitionDuration(durationMs: Int): Result<Unit> {
        _snapshot.update { it.copy(transitionDurationMs = durationMs.coerceIn(50, 5000)) }
        return Result.success(Unit)
    }

    override suspend fun setTBarPosition(position: Double, release: Boolean): Result<Unit> {
        if (release && position >= 0.95) {
            triggerTransition()
        }
        return Result.success(Unit)
    }

    override suspend fun toggleStream(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(streamActive = !it.outputStatus.streamActive)) }
        return Result.success(Unit)
    }

    override suspend fun startStream(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(streamActive = true)) }
        return Result.success(Unit)
    }

    override suspend fun stopStream(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(streamActive = false, streamDurationMs = 0L, streamTimecode = "00:00:00.000")) }
        return Result.success(Unit)
    }

    override suspend fun toggleRecord(): Result<Unit> {
        _snapshot.update { cur ->
            val next = !cur.outputStatus.recActive
            cur.copy(outputStatus = cur.outputStatus.copy(recActive = next, recPaused = false))
        }
        return Result.success(Unit)
    }

    override suspend fun startRecord(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(recActive = true, recPaused = false)) }
        return Result.success(Unit)
    }

    override suspend fun stopRecord(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(recActive = false, recPaused = false, recDurationMs = 0L, recTimecode = "00:00:00.000")) }
        return Result.success(Unit)
    }

    override suspend fun toggleRecordPause(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(recPaused = !it.outputStatus.recPaused)) }
        return Result.success(Unit)
    }

    override suspend fun splitRecordFile(): Result<Unit> = Result.success(Unit)
    override suspend fun createRecordChapter(chapterName: String?): Result<Unit> = Result.success(Unit)

    override suspend fun toggleReplayBuffer(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(replayActive = !it.outputStatus.replayActive)) }
        return Result.success(Unit)
    }

    override suspend fun startReplayBuffer(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(replayActive = true)) }
        return Result.success(Unit)
    }

    override suspend fun stopReplayBuffer(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(replayActive = false)) }
        return Result.success(Unit)
    }

    override suspend fun saveReplayBuffer(): Result<Unit> = Result.success(Unit)

    override suspend fun toggleVirtualCam(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(virtualCamActive = !it.outputStatus.virtualCamActive)) }
        return Result.success(Unit)
    }

    override suspend fun startVirtualCam(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(virtualCamActive = true)) }
        return Result.success(Unit)
    }

    override suspend fun stopVirtualCam(): Result<Unit> {
        _snapshot.update { it.copy(outputStatus = it.outputStatus.copy(virtualCamActive = false)) }
        return Result.success(Unit)
    }

    override suspend fun toggleMute(inputName: String): Result<Unit> {
        _snapshot.update { cur ->
            cur.copy(audioInputs = cur.audioInputs.map {
                if (it.name == inputName) it.copy(muted = !it.muted) else it
            })
        }
        return Result.success(Unit)
    }

    override suspend fun setMute(inputName: String, muted: Boolean): Result<Unit> {
        _snapshot.update { cur ->
            cur.copy(audioInputs = cur.audioInputs.map {
                if (it.name == inputName) it.copy(muted = muted) else it
            })
        }
        return Result.success(Unit)
    }

    override suspend fun setVolumeMul(inputName: String, volumeMul: Float): Result<Unit> {
        val clamped = volumeMul.coerceIn(0f, 1f)
        _snapshot.update { cur ->
            cur.copy(audioInputs = cur.audioInputs.map {
                if (it.name == inputName) it.copy(volumeMul = clamped, volumeDb = AudioCurve.mulToDb(clamped)) else it
            })
        }
        return Result.success(Unit)
    }

    override suspend fun setVolumeDb(inputName: String, volumeDb: Float): Result<Unit> {
        val mul = AudioCurve.dbToMul(volumeDb).coerceIn(0f, 1f)
        return setVolumeMul(inputName, mul)
    }

    override suspend fun setMonitorType(inputName: String, monitorType: ObsMonitorType): Result<Unit> {
        _snapshot.update { cur ->
            cur.copy(audioInputs = cur.audioInputs.map {
                if (it.name == inputName) it.copy(monitorType = monitorType) else it
            })
        }
        return Result.success(Unit)
    }

    override suspend fun setSyncOffset(inputName: String, offsetMs: Long): Result<Unit> {
        _snapshot.update { cur ->
            cur.copy(audioInputs = cur.audioInputs.map {
                if (it.name == inputName) it.copy(syncOffsetMs = offsetMs) else it
            })
        }
        return Result.success(Unit)
    }

    override suspend fun setBalance(inputName: String, balance: Float): Result<Unit> {
        _snapshot.update { cur ->
            cur.copy(audioInputs = cur.audioInputs.map {
                if (it.name == inputName) it.copy(balance = balance.coerceIn(0f, 1f)) else it
            })
        }
        return Result.success(Unit)
    }

    override suspend fun getInputPropertyListItems(
        inputName: String,
        propertyName: String
    ): Result<List<ObsPropertyOption>> {
        val kind = _snapshot.value.audioInputs.firstOrNull { it.name == inputName }?.kind.orEmpty()
        val items = when {
            kind.contains("input_capture") -> listOf(
                ObsPropertyOption("Default (Windows)", "default"),
                ObsPropertyOption("Shure MV7 Microphone (USB)", "mic_shure_mv7"),
                ObsPropertyOption("Focusrite Scarlett Solo", "mic_scarlett"),
                ObsPropertyOption("Headset Microphone", "mic_headset")
            )
            kind.contains("output_capture") -> listOf(
                ObsPropertyOption("Default (Windows)", "default"),
                ObsPropertyOption("Realtek Speakers", "spk_realtek"),
                ObsPropertyOption("Studio Headphones (DAC)", "spk_dac"),
                ObsPropertyOption("HDMI Monitor Audio", "spk_hdmi")
            )
            else -> emptyList()
        }
        return Result.success(items)
    }

    override suspend fun setInputAudioDevice(
        inputName: String,
        propertyName: String,
        deviceValue: String
    ): Result<Unit> = setInputSettings(
        inputName = inputName,
        settings = buildJsonObject { put(propertyName, deviceValue) },
        overlay = true
    )

    override suspend fun setSceneItemEnabled(sceneName: String, sceneItemId: Int, enabled: Boolean): Result<Unit> {
        updateDemoItem(sceneName, sceneItemId) { it.copy(enabled = enabled) }
        return Result.success(Unit)
    }

    override suspend fun setSceneItemEnabledByName(sceneName: String, sourceName: String, mode: ToggleMode): Result<Unit> {
        val targetScene = sceneName.ifBlank { _snapshot.value.programScene }
        val item = _snapshot.value.itemsForScene(targetScene).firstOrNull { it.sourceName == sourceName }
            ?: return Result.failure(IllegalArgumentException("Source '$sourceName' not found"))
        val next = when (mode) {
            ToggleMode.TOGGLE -> !item.enabled
            ToggleMode.ENABLE -> true
            ToggleMode.DISABLE -> false
        }
        return setSceneItemEnabled(targetScene, item.sceneItemId, next)
    }

    override suspend fun setSceneItemLocked(sceneName: String, sceneItemId: Int, locked: Boolean): Result<Unit> {
        updateDemoItem(sceneName, sceneItemId) { it.copy(locked = locked) }
        return Result.success(Unit)
    }

    override suspend fun setSceneItemIndex(sceneName: String, sceneItemId: Int, index: Int): Result<Unit> =
        Result.success(Unit)

    override suspend fun setSceneItemTransform(sceneName: String, sceneItemId: Int, transformPatch: JsonObject): Result<Unit> {
        updateDemoItem(sceneName, sceneItemId) { item ->
            val old = item.transform ?: SceneItemTransform()
            item.copy(
                transform = old.copy(
                    positionX = transformPatch["positionX"]?.jsonPrimitive?.doubleOrNull ?: old.positionX,
                    positionY = transformPatch["positionY"]?.jsonPrimitive?.doubleOrNull ?: old.positionY,
                    scaleX = transformPatch["scaleX"]?.jsonPrimitive?.doubleOrNull ?: old.scaleX,
                    scaleY = transformPatch["scaleY"]?.jsonPrimitive?.doubleOrNull ?: old.scaleY,
                    rotation = transformPatch["rotation"]?.jsonPrimitive?.doubleOrNull ?: old.rotation,
                    cropTop = transformPatch["cropTop"]?.jsonPrimitive?.intOrNull ?: old.cropTop,
                    cropBottom = transformPatch["cropBottom"]?.jsonPrimitive?.intOrNull ?: old.cropBottom,
                    cropLeft = transformPatch["cropLeft"]?.jsonPrimitive?.intOrNull ?: old.cropLeft,
                    cropRight = transformPatch["cropRight"]?.jsonPrimitive?.intOrNull ?: old.cropRight
                )
            )
        }
        return Result.success(Unit)
    }

    override suspend fun fitSceneItemToScreen(sceneName: String, sceneItemId: Int): Result<Unit> =
        setSceneItemTransform(
            sceneName,
            sceneItemId,
            buildJsonObject {
                put("positionX", 0.0)
                put("positionY", 0.0)
                put("scaleX", 1.0)
                put("scaleY", 1.0)
                put("rotation", 0.0)
            }
        )

    override suspend fun resetSceneItemTransform(sceneName: String, sceneItemId: Int): Result<Unit> =
        fitSceneItemToScreen(sceneName, sceneItemId)

    override suspend fun setFilterEnabled(sourceName: String, filterName: String, enabled: Boolean): Result<Unit> {
        _snapshot.update { cur ->
            val list = cur.filtersBySource[sourceName].orEmpty().map {
                if (it.name == filterName) it.copy(enabled = enabled) else it
            }
            cur.copy(
                filtersBySource = cur.filtersBySource + (sourceName to list),
                audioInputs = cur.audioInputs.map { inp ->
                    if (inp.name == sourceName) inp.copy(filters = list) else inp
                }
            )
        }
        return Result.success(Unit)
    }

    override suspend fun toggleFilterByName(sourceName: String, filterName: String, mode: ToggleMode): Result<Unit> {
        val current = _snapshot.value.filtersBySource[sourceName].orEmpty().firstOrNull { it.name == filterName }
            ?: return Result.failure(IllegalArgumentException("Filter '$filterName' not found"))
        val next = when (mode) {
            ToggleMode.TOGGLE -> !current.enabled
            ToggleMode.ENABLE -> true
            ToggleMode.DISABLE -> false
        }
        return setFilterEnabled(sourceName, filterName, next)
    }

    override suspend fun getInputSettings(inputName: String): Result<JsonObject> {
        val settings = inputSettingsStore[inputName] ?: buildJsonObject {}
        val kind = _snapshot.value.inputKindsByName[inputName] ?: "unknown"
        return Result.success(
            buildJsonObject {
                put("inputKind", kind)
                put("inputSettings", settings)
            }
        )
    }

    override suspend fun setInputSettings(inputName: String, settings: JsonObject, overlay: Boolean): Result<Unit> {
        val current = inputSettingsStore[inputName] ?: buildJsonObject {}
        inputSettingsStore[inputName] = if (overlay) {
            buildJsonObject {
                current.forEach { (k, v) -> put(k, v) }
                settings.forEach { (k, v) -> put(k, v) }
            }
        } else {
            settings
        }
        return Result.success(Unit)
    }

    override suspend fun pressInputPropertiesButton(inputName: String, propertyName: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun triggerMediaAction(inputName: String, action: ObsMediaAction): Result<Unit> {
        _snapshot.update { cur ->
            val current = cur.mediaStatuses[inputName] ?: ObsMediaStatus()
            val updated = when (action) {
                ObsMediaAction.PLAY -> current.copy(state = ObsMediaState.PLAYING)
                ObsMediaAction.PAUSE -> current.copy(state = ObsMediaState.PAUSED)
                ObsMediaAction.RESTART -> current.copy(state = ObsMediaState.PLAYING, cursorMs = 0L)
                ObsMediaAction.STOP -> current.copy(state = ObsMediaState.STOPPED, cursorMs = 0L)
                ObsMediaAction.NEXT, ObsMediaAction.PREVIOUS -> current.copy(cursorMs = 0L)
            }
            cur.copy(mediaStatuses = cur.mediaStatuses + (inputName to updated))
        }
        return Result.success(Unit)
    }

    override suspend fun setMediaCursor(inputName: String, cursorMs: Long): Result<Unit> {
        _snapshot.update { cur ->
            val current = cur.mediaStatuses[inputName] ?: return@update cur
            cur.copy(mediaStatuses = cur.mediaStatuses + (inputName to current.copy(cursorMs = cursorMs.coerceAtLeast(0L))))
        }
        return Result.success(Unit)
    }

    override suspend fun setProfile(profileName: String): Result<Unit> {
        _snapshot.update { it.copy(currentProfile = profileName) }
        return Result.success(Unit)
    }

    override suspend fun setSceneCollection(collectionName: String): Result<Unit> {
        _snapshot.update { it.copy(currentSceneCollection = collectionName) }
        return Result.success(Unit)
    }

    override suspend fun triggerHotkey(hotkeyName: String): Result<Unit> = Result.success(Unit)

    override suspend fun callVendorRequest(vendorName: String, requestType: String, requestData: JsonObject?): Result<JsonObject> =
        Result.success(buildJsonObject {})

    override suspend fun sendRawRequest(requestType: String, requestData: JsonObject?): Result<JsonObject> =
        Result.success(buildJsonObject { put("demoResponse", "ok") })

    private fun updateDemoItem(sceneName: String, itemId: Int, block: (ObsSceneItem) -> ObsSceneItem) {
        _snapshot.update { cur ->
            val list = cur.sceneItemsByScene[sceneName] ?: return@update cur
            val updated = list.map { if (it.sceneItemId == itemId) block(it) else it }
            cur.copy(sceneItemsByScene = cur.sceneItemsByScene + (sceneName to updated))
        }
    }

    private fun initialDemoSnapshot(): ObsSnapshot {
        val micFilters = listOf(
            ObsFilter("Noise Suppression", "noise_suppress_filter_v2", true, 0),
            ObsFilter("Voice Compressor", "compressor_filter", true, 1),
            ObsFilter("Limiter -1dB", "limiter_filter", false, 2)
        )
        val camFilters = listOf(
            ObsFilter("Color Correction", "color_filter_v2", true, 0),
            ObsFilter("Cinema LUT", "clut_filter", false, 1)
        )
        val scenes = listOf(
            ObsScene("Stream Starting", "s1", 3),
            ObsScene("Just Chatting", "s2", 2),
            ObsScene("Gaming + Cam", "s3", 1),
            ObsScene("BRB", "s4", 0)
        )
        val itemsByScene = mapOf(
            "Stream Starting" to listOf(
                ObsSceneItem(1, "Intro Video", "i1", "ffmpeg_source", enabled = true, index = 2),
                ObsSceneItem(2, "News Ticker", "i2", "text_gdiplus_v2", enabled = true, index = 1),
                ObsSceneItem(3, "BGM Music", "i3", "ffmpeg_source", enabled = true, index = 0)
            ),
            "Just Chatting" to listOf(
                ObsSceneItem(10, "Chat Overlay", "i4", "browser_source", enabled = true, index = 2),
                ObsSceneItem(11, "News Ticker", "i2", "text_gdiplus_v2", enabled = true, index = 1),
                ObsSceneItem(
                    12, "4K Webcam", "i5", "dshow_input", enabled = true, index = 0,
                    transform = SceneItemTransform(0.0, 0.0, 1.0, 1.0, 0.0, 1920.0, 1080.0, 1920.0, 1080.0)
                )
            ),
            "Gaming + Cam" to listOf(
                ObsSceneItem(20, "Chat Overlay", "i4", "browser_source", enabled = true, index = 2),
                ObsSceneItem(
                    21, "4K Webcam", "i5", "dshow_input", enabled = true, index = 1,
                    transform = SceneItemTransform(1380.0, 720.0, 0.25, 0.25, 0.0, 1920.0, 1080.0, 480.0, 270.0)
                ),
                ObsSceneItem(22, "Game Capture", "i6", "game_capture", enabled = true, locked = true, index = 0)
            ),
            "BRB" to listOf(
                ObsSceneItem(30, "BGM Music", "i3", "ffmpeg_source", enabled = true, index = 1),
                ObsSceneItem(31, "BRB Screen", "i7", "image_source", enabled = true, index = 0)
            )
        )

        return ObsSnapshot(
            scenes = scenes,
            programScene = "Gaming + Cam",
            previewScene = "Just Chatting",
            studioMode = true,
            transitions = listOf(
                ObsTransition("Cut", "cut_transition", false),
                ObsTransition("Fade", "fade_transition", false),
                ObsTransition("Logo Stinger", "obs_stinger_transition", true)
            ),
            currentTransition = "Fade",
            transitionDurationMs = 350,
            sceneItemsByScene = itemsByScene,
            filtersBySource = mapOf(
                "Microphone" to micFilters,
                "4K Webcam" to camFilters
            ),
            audioInputs = listOf(
                ObsAudioInput(
                    name = "Microphone",
                    kind = "wasapi_input_capture",
                    muted = false,
                    volumeMul = 0.85f,
                    volumeDb = AudioCurve.mulToDb(0.85f),
                    monitorType = ObsMonitorType.NONE,
                    filters = micFilters
                ),
                ObsAudioInput(
                    name = "Desktop Audio",
                    kind = "wasapi_output_capture",
                    muted = false,
                    volumeMul = 0.68f,
                    volumeDb = AudioCurve.mulToDb(0.68f),
                    monitorType = ObsMonitorType.NONE
                ),
                ObsAudioInput(
                    name = "BGM Music",
                    kind = "ffmpeg_source",
                    muted = false,
                    volumeMul = 0.35f,
                    volumeDb = AudioCurve.mulToDb(0.35f),
                    monitorType = ObsMonitorType.MONITOR_AND_OUTPUT
                ),
                ObsAudioInput(
                    name = "Discord / Guests",
                    kind = "wasapi_process_output_capture",
                    muted = true,
                    volumeMul = 0.72f,
                    volumeDb = AudioCurve.mulToDb(0.72f),
                    monitorType = ObsMonitorType.NONE
                )
            ),
            mediaStatuses = mapOf(
                "Intro Video" to ObsMediaStatus(ObsMediaState.PAUSED, cursorMs = 12_400L, durationMs = 45_000L),
                "BGM Music" to ObsMediaStatus(ObsMediaState.PLAYING, cursorMs = 64_000L, durationMs = 195_000L)
            ),
            inputKindsByName = mapOf(
                "News Ticker" to "text_gdiplus_v2",
                "Chat Overlay" to "browser_source",
                "4K Webcam" to "dshow_input",
                "Intro Video" to "ffmpeg_source",
                "BGM Music" to "ffmpeg_source",
                "Game Capture" to "game_capture"
            ),
            outputStatus = ObsOutputStatus(
                streamActive = false,
                recActive = false,
                replayActive = true,
                virtualCamActive = false
            ),
            stats = ObsStats(
                cpuUsage = 12.2,
                memoryUsageMb = 642.0,
                activeFps = 60.0,
                averageFrameRenderTimeMs = 2.6,
                renderSkippedFrames = 0,
                renderTotalFrames = 18420,
                outputSkippedFrames = 0,
                outputTotalFrames = 0,
                availableDiskSpaceMb = 248_500.0
            ),
            profiles = listOf("Streaming 1080p60", "4K Recording"),
            currentProfile = "Streaming 1080p60",
            sceneCollections = listOf("Main Stream", "Podcast"),
            currentSceneCollection = "Main Stream",
            hotkeys = listOf(
                "OBSBasic.StartStreaming",
                "OBSBasic.StartRecording",
                "OBSBasic.SaveReplay",
                "OBSBasic.Transition",
                "OBSBasic.Screenshot"
            ),
            obsVersion = "33.0.1 (Demo)",
            wsVersion = "5.5.4",
            specialInputs = ObsSpecialInputs(
                desktop1 = "Desktop Audio",
                mic1 = "Microphone"
            )
        )
    }
}
