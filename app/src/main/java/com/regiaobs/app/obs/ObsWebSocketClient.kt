package com.regiaobs.app.obs

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.min

private data class PreviewConfig(
    val enabled: Boolean = true,
    val fps: Int = 2,
    val quality: Int = 65,
    val isAppActive: Boolean = true
)

class ObsWebSocketClient : ObsController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _snapshot = MutableStateFlow(ObsSnapshot())
    override val snapshot: StateFlow<ObsSnapshot> = _snapshot.asStateFlow()

    private val _volumeMeters = MutableStateFlow<Map<String, List<Float>>>(emptyMap())
    override val volumeMeters: StateFlow<Map<String, List<Float>>> = _volumeMeters.asStateFlow()

    private val _previewBitmaps = MutableStateFlow(PreviewBitmaps())
    override val previewBitmaps: StateFlow<PreviewBitmaps> = _previewBitmaps.asStateFlow()

    private val previewConfig = MutableStateFlow(PreviewConfig())

    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private var activeWebSocket: WebSocket? = null
    private var currentProfile: ObsServerProfile? = null
    private var userInitiatedDisconnect = false
    private var reconnectAttempt = 0

    private var reconnectJob: Job? = null
    private var hydrationJob: Job? = null
    private var statsPollingJob: Job? = null
    private var previewPollingJob: Job? = null

    override fun connect(profile: ObsServerProfile) {
        userInitiatedDisconnect = false
        reconnectAttempt = 0
        currentProfile = profile
        reconnectJob?.cancel()
        openSocket(profile, isReconnect = false)
    }

    override fun disconnect() {
        userInitiatedDisconnect = true
        currentProfile = null
        reconnectJob?.cancel()
        stopSessionLoops()
        failAllPending("Connection closed")
        runCatching { activeWebSocket?.close(1000, "User disconnect") }
        activeWebSocket = null
        _connectionState.value = ConnectionState.Disconnected
        _volumeMeters.value = emptyMap()
        _previewBitmaps.value = PreviewBitmaps()
        _snapshot.value = ObsSnapshot()
    }

    override fun updatePreviewConfig(enabled: Boolean, fps: Int, quality: Int, isAppActive: Boolean) {
        previewConfig.value = PreviewConfig(
            enabled = enabled,
            fps = fps.coerceIn(0, 8),
            quality = quality.coerceIn(25, 95),
            isAppActive = isAppActive
        )
    }

    private fun openSocket(profile: ObsServerProfile, isReconnect: Boolean) {
        stopSessionLoops()
        failAllPending("Reconnecting")
        runCatching { activeWebSocket?.cancel() }
        activeWebSocket = null

        if (!isReconnect) {
            _connectionState.value = ConnectionState.Connecting
        }

        val request = try {
            Request.Builder().url(profile.webSocketUrl).build()
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error(
                message = "Invalid server address: ${e.message ?: profile.host}",
                isAuthError = false
            )
            return
        }

        activeWebSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== activeWebSocket) return
                _connectionState.value = ConnectionState.Authenticating
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket !== activeWebSocket) return
                handleIncomingMessage(webSocket, text, profile)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== activeWebSocket) return
                handleSocketTerminated(code, reason, isFailure = false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== activeWebSocket) return
                val detail = t.message ?: response?.message ?: "Host unreachable"
                handleSocketTerminated(-1, detail, isFailure = true)
            }
        })
    }

    private fun handleSocketTerminated(code: Int, reason: String, isFailure: Boolean) {
        stopSessionLoops()
        failAllPending("Connection lost")
        activeWebSocket = null
        _volumeMeters.value = emptyMap()

        if (userInitiatedDisconnect) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        if (code == ObsCloseCode.AUTHENTICATION_FAILED) {
            _connectionState.value = ConnectionState.Error(
                message = "Authentication failed: check your OBS WebSocket password.",
                isAuthError = true
            )
            return
        }

        val profile = currentProfile
        if (profile == null) {
            _connectionState.value = ConnectionState.Disconnected
            return
        }

        if (reconnectAttempt >= 8) {
            val detail = if (reason.isNotBlank()) " ($reason)" else ""
            _connectionState.value = ConnectionState.Error(
                message = "Unable to connect to ${profile.host}:${profile.port}$detail",
                isAuthError = false
            )
            return
        }

        reconnectAttempt += 1
        val attempt = reconnectAttempt
        val delayMs = min(15_000L, 1_000L shl min(attempt - 1, 4))
        _connectionState.value = ConnectionState.Reconnecting(attempt)
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!userInitiatedDisconnect && isActive) {
                openSocket(profile, isReconnect = true)
            }
        }
    }

    private fun handleIncomingMessage(webSocket: WebSocket, text: String, profile: ObsServerProfile) {
        val root = runCatching { ObsJson.codec.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val op = root.int("op", -1)
        val data = root.obj("d") ?: return

        when (op) {
            ObsOpCode.HELLO -> {
                val rpcVersion = data.int("rpcVersion", 1)
                val authObj = data.obj("authentication")
                val authToken = if (authObj != null) {
                    val challenge = authObj.str("challenge")
                    val salt = authObj.str("salt")
                    ObsAuth.computeAuth(profile.password, salt, challenge)
                } else {
                    null
                }
                val identifyPayload = ObsJson.buildIdentify(
                    rpcVersion = rpcVersion,
                    authentication = authToken,
                    eventSubscriptions = ObsEventSubs.ALL_WITH_METERS
                )
                webSocket.send(identifyPayload)
            }

            ObsOpCode.IDENTIFIED -> {
                reconnectAttempt = 0
                _connectionState.value = ConnectionState.Connected(
                    serverName = profile.name.ifBlank { profile.host },
                    isDemo = false
                )
                hydrationJob?.cancel()
                hydrationJob = scope.launch {
                    hydrateInitialState()
                    startSessionLoops()
                }
            }

            ObsOpCode.REQUEST_RESPONSE -> {
                val requestId = data.str("requestId")
                val deferred = pendingRequests.remove(requestId)
                deferred?.complete(data)
            }

            ObsOpCode.EVENT -> {
                val eventType = data.str("eventType")
                val eventData = data.obj("eventData") ?: buildJsonObject {}
                if (eventType == "InputVolumeMeters") {
                    handleVolumeMetersEvent(eventData)
                } else {
                    scope.launch {
                        handleEvent(eventType, eventData)
                    }
                }
            }
        }
    }

    private fun handleVolumeMetersEvent(eventData: JsonObject) {
        val inputs = eventData.arr("inputs") ?: return
        if (inputs.isEmpty()) return

        val next = HashMap<String, List<Float>>(_volumeMeters.value.size + inputs.size)
        next.putAll(_volumeMeters.value)
        for (elem in inputs) {
            val obj = elem.asObjOrNull() ?: continue
            val name = obj.str("inputName")
            if (name.isEmpty()) continue
            val levelsArr = obj.arr("inputLevelsMul")
            if (levelsArr == null || levelsArr.isEmpty()) {
                next[name] = listOf(0f)
                continue
            }
            val channels = ArrayList<Float>(levelsArr.size)
            for (chElem in levelsArr) {
                val chArr = runCatching { chElem.jsonArray }.getOrNull()
                if (chArr == null || chArr.isEmpty()) {
                    channels += 0f
                } else {
                    // obs-websocket channel array: [magnitude, peak, inputPeak]
                    val idx = if (chArr.size >= 2) 1 else 0
                    val value = chArr[idx].jsonPrimitive.doubleOrNull?.toFloat()?.coerceIn(0f, 1.5f) ?: 0f
                    channels += value
                }
            }
            next[name] = channels
        }
        _volumeMeters.value = next
    }

    private suspend fun request(
        requestType: String,
        requestData: JsonObject? = null,
        timeoutMs: Long = 8_000L
    ): Result<JsonObject> {
        val ws = activeWebSocket ?: return Result.failure(IllegalStateException("Not connected to OBS"))
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[requestId] = deferred

        val sent = ws.send(ObsJson.buildRequest(requestType, requestId, requestData))
        if (!sent) {
            pendingRequests.remove(requestId)
            return Result.failure(IllegalStateException("Failed to send request ($requestType)"))
        }

        return try {
            val response = withTimeout(timeoutMs) { deferred.await() }
            val status = response.obj("requestStatus")
            val ok = status?.bool("result", false) ?: false
            if (ok) {
                Result.success(response.obj("responseData") ?: buildJsonObject {})
            } else {
                val code = status?.int("code", -1) ?: -1
                val comment = status?.strOrNull("comment")
                val friendly = ObsErrorMapper.describe(code, requestType, comment)
                Result.failure(ObsRequestException(requestType, code, friendly))
            }
        } catch (ce: CancellationException) {
            pendingRequests.remove(requestId)
            throw ce
        } catch (e: Exception) {
            pendingRequests.remove(requestId)
            Result.failure(e)
        }
    }

    private suspend fun requestUnit(
        requestType: String,
        requestData: JsonObject? = null
    ): Result<Unit> = request(requestType, requestData).map { Unit }

    private fun failAllPending(reason: String) {
        val ex = IllegalStateException(reason)
        val keys = pendingRequests.keys.toList()
        for (k in keys) {
            pendingRequests.remove(k)?.completeExceptionally(ex)
        }
    }

    private fun stopSessionLoops() {
        hydrationJob?.cancel()
        statsPollingJob?.cancel()
        previewPollingJob?.cancel()
    }

    private fun startSessionLoops() {
        statsPollingJob?.cancel()
        statsPollingJob = scope.launch {
            while (isActive && _connectionState.value is ConnectionState.Connected) {
                delay(1500L)
                if (!isActive) break
                pollStatsAndOutputs()
            }
        }

        previewPollingJob?.cancel()
        previewPollingJob = scope.launch {
            while (isActive && _connectionState.value is ConnectionState.Connected) {
                val cfg = previewConfig.value
                if (!cfg.enabled || !cfg.isAppActive || cfg.fps <= 0) {
                    delay(400L)
                    continue
                }
                val startTime = System.currentTimeMillis()
                pollPreviews(cfg.quality)
                val elapsed = System.currentTimeMillis() - startTime
                val targetPeriod = 1000L / cfg.fps.coerceIn(1, 8)
                val waitMs = (targetPeriod - elapsed).coerceAtLeast(60L)
                delay(waitMs)
            }
        }
    }

    private suspend fun pollPreviews(quality: Int) {
        val snap = _snapshot.value
        val program = snap.programScene
        val preview = snap.previewScene
        if (program.isBlank()) return

        val progBitmap = fetchScreenshot(program, quality)
        val prevBitmap = if (snap.studioMode && preview.isNotBlank() && preview != program) {
            fetchScreenshot(preview, quality)
        } else {
            progBitmap
        }
        if (progBitmap != null || prevBitmap != null) {
            _previewBitmaps.update { current ->
                PreviewBitmaps(
                    program = progBitmap ?: current.program,
                    preview = prevBitmap ?: current.preview,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
        }
    }

    private suspend fun fetchScreenshot(sourceName: String, quality: Int): Bitmap? {
        val data = request(
            "GetSourceScreenshot",
            buildJsonObject {
                put("sourceName", sourceName)
                put("imageFormat", "jpg")
                put("imageWidth", 640)
                put("imageCompressionQuality", quality)
            },
            timeoutMs = 3_500L
        ).getOrNull() ?: return null

        val imageData = data.str("imageData")
        if (imageData.isBlank()) return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val base64Part = imageData.substringAfter(',', imageData)
                val bytes = Base64.getDecoder().decode(base64Part)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }
    }

    private suspend fun pollStatsAndOutputs() {
        coroutineScope {
            val statsDeferred = async { request("GetStats").getOrNull() }
            val streamDeferred = async { request("GetStreamStatus").getOrNull() }
            val recordDeferred = async { request("GetRecordStatus").getOrNull() }

            val mediaInputs = _snapshot.value.mediaStatuses.keys.toList()
            val mediaDeferreds = mediaInputs.associateWith { name ->
                async {
                    request(
                        "GetMediaInputStatus",
                        buildJsonObject { put("inputName", name) }
                    ).getOrNull()
                }
            }

            val statsObj = statsDeferred.await()
            val streamObj = streamDeferred.await()
            val recordObj = recordDeferred.await()
            val mediaUpdates = mediaDeferreds.mapValues { it.value.await() }

            _snapshot.update { cur ->
                val newStats = if (statsObj != null) {
                    ObsStats(
                        cpuUsage = statsObj.double("cpuUsage"),
                        memoryUsageMb = statsObj.double("memoryUsage"),
                        activeFps = statsObj.double("activeFps"),
                        averageFrameRenderTimeMs = statsObj.double("averageFrameRenderTime"),
                        renderSkippedFrames = statsObj.int("renderSkippedFrames"),
                        renderTotalFrames = statsObj.int("renderTotalFrames"),
                        outputSkippedFrames = statsObj.int("outputSkippedFrames"),
                        outputTotalFrames = statsObj.int("outputTotalFrames"),
                        availableDiskSpaceMb = statsObj.double("availableDiskSpace")
                    )
                } else cur.stats

                val newOutputs = cur.outputStatus.copy(
                    streamActive = streamObj?.bool("outputActive") ?: cur.outputStatus.streamActive,
                    streamReconnecting = streamObj?.bool("outputReconnecting") ?: cur.outputStatus.streamReconnecting,
                    streamTimecode = streamObj?.str("outputTimecode", cur.outputStatus.streamTimecode)
                        ?: cur.outputStatus.streamTimecode,
                    streamDurationMs = streamObj?.long("outputDuration") ?: cur.outputStatus.streamDurationMs,
                    streamCongestion = streamObj?.double("outputCongestion") ?: cur.outputStatus.streamCongestion,
                    streamSkippedFrames = streamObj?.int("outputSkippedFrames") ?: cur.outputStatus.streamSkippedFrames,
                    streamTotalFrames = streamObj?.int("outputTotalFrames") ?: cur.outputStatus.streamTotalFrames,
                    recActive = recordObj?.bool("outputActive") ?: cur.outputStatus.recActive,
                    recPaused = recordObj?.bool("outputPaused") ?: cur.outputStatus.recPaused,
                    recTimecode = recordObj?.str("outputTimecode", cur.outputStatus.recTimecode)
                        ?: cur.outputStatus.recTimecode,
                    recDurationMs = recordObj?.long("outputDuration") ?: cur.outputStatus.recDurationMs,
                    recBytes = recordObj?.long("outputBytes") ?: cur.outputStatus.recBytes
                )

                val newMedia = if (mediaUpdates.isNotEmpty()) {
                    cur.mediaStatuses.toMutableMap().apply {
                        for ((name, mObj) in mediaUpdates) {
                            if (mObj != null) {
                                this[name] = ObsMediaStatus(
                                    state = ObsMediaState.fromObs(mObj.strOrNull("mediaState")),
                                    cursorMs = mObj.long("mediaCursor"),
                                    durationMs = mObj.long("mediaDuration")
                                )
                            }
                        }
                    }
                } else cur.mediaStatuses

                cur.copy(
                    stats = newStats,
                    outputStatus = newOutputs,
                    mediaStatuses = newMedia
                )
            }
        }
    }

    private suspend fun hydrateInitialState() = coroutineScope {
        val versionDef = async { request("GetVersion").getOrNull() }
        val videoDef = async { request("GetVideoSettings").getOrNull() }
        val scenesDef = async { request("GetSceneList").getOrNull() }
        val studioDef = async { request("GetStudioModeEnabled").getOrNull() }
        val transListDef = async { request("GetSceneTransitionList").getOrNull() }
        val currTransDef = async { request("GetCurrentSceneTransition").getOrNull() }
        val streamDef = async { request("GetStreamStatus").getOrNull() }
        val recordDef = async { request("GetRecordStatus").getOrNull() }
        val replayDef = async { request("GetReplayBufferStatus").getOrNull() }
        val vcamDef = async { request("GetVirtualCamStatus").getOrNull() }
        val inputsDef = async { request("GetInputList").getOrNull() }
        val specialDef = async { request("GetSpecialInputs").getOrNull() }
        val profilesDef = async { request("GetProfileList").getOrNull() }
        val collectionsDef = async { request("GetSceneCollectionList").getOrNull() }
        val hotkeysDef = async { request("GetHotkeyList").getOrNull() }
        val statsDef = async { request("GetStats").getOrNull() }

        val versionObj = versionDef.await()
        val videoObj = videoDef.await()
        val scenesObj = scenesDef.await()
        val studioObj = studioDef.await()
        val transListObj = transListDef.await()
        val currTransObj = currTransDef.await()
        val streamObj = streamDef.await()
        val recordObj = recordDef.await()
        val replayObj = replayDef.await()
        val vcamObj = vcamDef.await()
        val inputsObj = inputsDef.await()
        val specialObj = specialDef.await()
        val profilesObj = profilesDef.await()
        val collectionsObj = collectionsDef.await()
        val hotkeysObj = hotkeysDef.await()
        val statsObj = statsDef.await()

        val scenes = parseScenes(scenesObj)
        val programScene = scenesObj?.str("currentProgramSceneName").orEmpty()
        val previewScene = scenesObj?.strOrNull("currentPreviewSceneName").orEmpty()
        val studioMode = studioObj?.bool("studioModeEnabled") ?: false

        val transitions = transListObj?.arr("transitions")?.mapNotNull { elem ->
            val obj = elem.asObjOrNull() ?: return@mapNotNull null
            ObsTransition(
                name = obj.str("transitionName"),
                kind = obj.str("transitionKind"),
                configurable = obj.bool("transitionConfigurable")
            )
        }.orEmpty()
        val currentTransition = currTransObj?.str("transitionName")
            ?: transListObj?.str("currentSceneTransitionName").orEmpty()
        val transitionDuration = currTransObj?.int("transitionDuration", 300) ?: 300

        // Hydrate inputs (audio details, filters, media status)
        val inputPairs = inputsObj?.arr("inputs")?.mapNotNull { elem ->
            val obj = elem.asObjOrNull() ?: return@mapNotNull null
            val name = obj.str("inputName")
            if (name.isEmpty()) return@mapNotNull null
            Triple(name, obj.str("inputUuid"), obj.str("inputKind"))
        }.orEmpty()

        val kindsMap = inputPairs.associate { it.first to it.third }
        val audioList = mutableListOf<ObsAudioInput>()
        val filtersMap = mutableMapOf<String, List<ObsFilter>>()
        val mediaMap = mutableMapOf<String, ObsMediaStatus>()

        inputPairs.map { (name, uuid, kind) ->
            async {
                val volRes = request("GetInputVolume", buildJsonObject { put("inputName", name) })
                val filtersRes = request("GetSourceFilterList", buildJsonObject { put("sourceName", name) }).getOrNull()
                val filters = parseFilters(filtersRes)

                var audioInput: ObsAudioInput? = null
                if (volRes.isSuccess) {
                    val volObj = volRes.getOrNull()
                    val muteDef = async { request("GetInputMute", buildJsonObject { put("inputName", name) }).getOrNull() }
                    val monDef = async { request("GetInputAudioMonitorType", buildJsonObject { put("inputName", name) }).getOrNull() }
                    val syncDef = async { request("GetInputAudioSyncOffset", buildJsonObject { put("inputName", name) }).getOrNull() }
                    val balDef = async { request("GetInputAudioBalance", buildJsonObject { put("inputName", name) }).getOrNull() }

                    val muteObj = muteDef.await()
                    val monObj = monDef.await()
                    val syncObj = syncDef.await()
                    val balObj = balDef.await()

                    val mul = volObj?.float("inputVolumeMul", 1f) ?: 1f
                    val db = volObj?.float("inputVolumeDb", AudioCurve.mulToDb(mul)) ?: 0f

                    audioInput = ObsAudioInput(
                        name = name,
                        uuid = uuid,
                        kind = kind,
                        muted = muteObj?.bool("inputMuted") ?: false,
                        volumeMul = mul,
                        volumeDb = db,
                        monitorType = ObsMonitorType.fromObs(monObj?.strOrNull("monitorType")),
                        syncOffsetMs = syncObj?.long("inputAudioSyncOffset") ?: 0L,
                        balance = balObj?.float("inputAudioBalance", 0.5f) ?: 0.5f,
                        active = true,
                        filters = filters
                    )
                }

                var mediaStatus: ObsMediaStatus? = null
                if (isMediaKind(kind)) {
                    val mediaObj = request("GetMediaInputStatus", buildJsonObject { put("inputName", name) }).getOrNull()
                    if (mediaObj != null) {
                        mediaStatus = ObsMediaStatus(
                            state = ObsMediaState.fromObs(mediaObj.strOrNull("mediaState")),
                            cursorMs = mediaObj.long("mediaCursor"),
                            durationMs = mediaObj.long("mediaDuration")
                        )
                    }
                }

                Triple(name, Pair(filters, audioInput), mediaStatus)
            }
        }.awaitAll().forEach { (name, filterAudio, media) ->
            val (filters, audio) = filterAudio
            if (filters.isNotEmpty()) filtersMap[name] = filters
            if (audio != null) audioList += audio
            if (media != null) mediaMap[name] = media
        }

        // Hydrate scene items for program, preview, and first few scenes
        val scenesToLoad = buildList {
            if (programScene.isNotBlank()) add(programScene)
            if (previewScene.isNotBlank() && previewScene != programScene) add(previewScene)
            scenes.take(8).forEach { if (it.name !in this) add(it.name) }
        }
        val sceneItemsMap = scenesToLoad.map { sceneName ->
            async { sceneName to fetchSceneItems(sceneName) }
        }.awaitAll().toMap()

        val outputs = ObsOutputStatus(
            streamActive = streamObj?.bool("outputActive") ?: false,
            streamReconnecting = streamObj?.bool("outputReconnecting") ?: false,
            streamTimecode = streamObj?.str("outputTimecode", "00:00:00.000") ?: "00:00:00.000",
            streamDurationMs = streamObj?.long("outputDuration") ?: 0L,
            streamCongestion = streamObj?.double("outputCongestion") ?: 0.0,
            streamSkippedFrames = streamObj?.int("outputSkippedFrames") ?: 0,
            streamTotalFrames = streamObj?.int("outputTotalFrames") ?: 0,
            recActive = recordObj?.bool("outputActive") ?: false,
            recPaused = recordObj?.bool("outputPaused") ?: false,
            recTimecode = recordObj?.str("outputTimecode", "00:00:00.000") ?: "00:00:00.000",
            recDurationMs = recordObj?.long("outputDuration") ?: 0L,
            recBytes = recordObj?.long("outputBytes") ?: 0L,
            replayActive = replayObj?.bool("outputActive") ?: false,
            virtualCamActive = vcamObj?.bool("outputActive") ?: false
        )

        val stats = if (statsObj != null) {
            ObsStats(
                cpuUsage = statsObj.double("cpuUsage"),
                memoryUsageMb = statsObj.double("memoryUsage"),
                activeFps = statsObj.double("activeFps"),
                averageFrameRenderTimeMs = statsObj.double("averageFrameRenderTime"),
                renderSkippedFrames = statsObj.int("renderSkippedFrames"),
                renderTotalFrames = statsObj.int("renderTotalFrames"),
                outputSkippedFrames = statsObj.int("outputSkippedFrames"),
                outputTotalFrames = statsObj.int("outputTotalFrames"),
                availableDiskSpaceMb = statsObj.double("availableDiskSpace")
            )
        } else ObsStats()

        val specialInputs = ObsSpecialInputs(
            desktop1 = specialObj?.strOrNull("desktop1")?.takeIf { it.isNotBlank() },
            desktop2 = specialObj?.strOrNull("desktop2")?.takeIf { it.isNotBlank() },
            mic1 = specialObj?.strOrNull("mic1")?.takeIf { it.isNotBlank() },
            mic2 = specialObj?.strOrNull("mic2")?.takeIf { it.isNotBlank() },
            mic3 = specialObj?.strOrNull("mic3")?.takeIf { it.isNotBlank() },
            mic4 = specialObj?.strOrNull("mic4")?.takeIf { it.isNotBlank() }
        )
        val specialOrder = listOfNotNull(
            specialInputs.mic1,
            specialInputs.mic2,
            specialInputs.mic3,
            specialInputs.mic4,
            specialInputs.desktop1,
            specialInputs.desktop2
        )
        val sortedAudioList = audioList.sortedWith(
            compareBy<ObsAudioInput> { inp ->
                val idx = specialOrder.indexOf(inp.name)
                if (idx >= 0) idx else Int.MAX_VALUE
            }.thenBy { it.name.lowercase() }
        )

        _snapshot.value = ObsSnapshot(
            scenes = scenes,
            programScene = programScene,
            previewScene = previewScene,
            studioMode = studioMode,
            transitions = transitions,
            currentTransition = currentTransition,
            transitionDurationMs = transitionDuration,
            sceneItemsByScene = sceneItemsMap,
            filtersBySource = filtersMap,
            audioInputs = sortedAudioList,
            mediaStatuses = mediaMap,
            inputKindsByName = kindsMap,
            outputStatus = outputs,
            stats = stats,
            profiles = profilesObj?.arr("profiles")?.mapNotNull {
                (it as? JsonPrimitive)?.content
            }.orEmpty(),
            currentProfile = profilesObj?.str("currentProfileName").orEmpty(),
            sceneCollections = collectionsObj?.arr("sceneCollections")?.mapNotNull {
                (it as? JsonPrimitive)?.content
            }.orEmpty(),
            currentSceneCollection = collectionsObj?.str("currentSceneCollectionName").orEmpty(),
            hotkeys = hotkeysObj?.arr("hotkeys")?.mapNotNull {
                (it as? JsonPrimitive)?.content
            }.orEmpty(),
            obsVersion = versionObj?.str("obsVersion").orEmpty(),
            wsVersion = versionObj?.str("obsWebSocketVersion").orEmpty(),
            baseCanvasWidth = videoObj?.int("baseWidth", 1920) ?: 1920,
            baseCanvasHeight = videoObj?.int("baseHeight", 1080) ?: 1080,
            specialInputs = specialInputs
        )
    }

    private fun parseScenes(scenesObj: JsonObject?): List<ObsScene> {
        val raw = scenesObj?.arr("scenes")?.mapNotNull { elem ->
            val obj = elem.asObjOrNull() ?: return@mapNotNull null
            ObsScene(
                name = obj.str("sceneName"),
                uuid = obj.str("sceneUuid"),
                index = obj.int("sceneIndex")
            )
        }.orEmpty()
        // OBS lists bottom-to-top; reverse so top scene appears first like OBS desktop UI
        return raw.reversed()
    }

    private fun parseFilters(filtersObj: JsonObject?): List<ObsFilter> {
        return filtersObj?.arr("filters")?.mapNotNull { elem ->
            val obj = elem.asObjOrNull() ?: return@mapNotNull null
            ObsFilter(
                name = obj.str("filterName"),
                kind = obj.str("filterKind"),
                enabled = obj.bool("filterEnabled", true),
                index = obj.int("filterIndex")
            )
        }.orEmpty()
    }

    private suspend fun fetchSceneItems(sceneName: String): List<ObsSceneItem> {
        val res = request("GetSceneItemList", buildJsonObject { put("sceneName", sceneName) }).getOrNull()
            ?: return emptyList()
        val rawItems = res.arr("sceneItems") ?: return emptyList()
        val parsed = coroutineScope {
            rawItems.mapNotNull { elem ->
                val obj = elem.asObjOrNull() ?: return@mapNotNull null
                async { parseSceneItem(obj) }
            }.awaitAll()
        }
        // Reverse so top layer in OBS appears first in the list
        return parsed.reversed()
    }

    private suspend fun parseSceneItem(obj: JsonObject): ObsSceneItem {
        val isGroup = obj.bool("isGroup")
        val sourceName = obj.str("sourceName")
        val children = if (isGroup && sourceName.isNotBlank()) {
            val groupRes = request("GetGroupSceneItemList", buildJsonObject { put("sceneName", sourceName) }).getOrNull()
            groupRes?.arr("sceneItems")?.mapNotNull { childElem ->
                childElem.asObjOrNull()?.let { parseSceneItemBasic(it) }
            }?.reversed().orEmpty()
        } else {
            emptyList()
        }
        return parseSceneItemBasic(obj).copy(groupChildren = children)
    }

    private fun parseSceneItemBasic(obj: JsonObject): ObsSceneItem {
        val tObj = obj.obj("sceneItemTransform")
        val transform = tObj?.let { parseTransform(it) }
        return ObsSceneItem(
            sceneItemId = obj.int("sceneItemId"),
            sourceName = obj.str("sourceName"),
            sourceUuid = obj.str("sourceUuid"),
            inputKind = obj.strOrNull("inputKind"),
            isGroup = obj.bool("isGroup"),
            enabled = obj.bool("sceneItemEnabled", true),
            locked = obj.bool("sceneItemLocked"),
            index = obj.int("sceneItemIndex"),
            transform = transform
        )
    }

    private fun parseTransform(t: JsonObject): SceneItemTransform = SceneItemTransform(
        positionX = t.double("positionX"),
        positionY = t.double("positionY"),
        scaleX = t.double("scaleX", 1.0),
        scaleY = t.double("scaleY", 1.0),
        rotation = t.double("rotation"),
        sourceWidth = t.double("sourceWidth", 1920.0),
        sourceHeight = t.double("sourceHeight", 1080.0),
        width = t.double("width", 1920.0),
        height = t.double("height", 1080.0),
        boundsType = t.str("boundsType", "OBS_BOUNDS_NONE"),
        boundsWidth = t.double("boundsWidth"),
        boundsHeight = t.double("boundsHeight"),
        cropTop = t.int("cropTop"),
        cropBottom = t.int("cropBottom"),
        cropLeft = t.int("cropLeft"),
        cropRight = t.int("cropRight")
    )

    private fun isMediaKind(kind: String): Boolean {
        val lower = kind.lowercase()
        return lower.contains("ffmpeg") || lower.contains("vlc") || lower.contains("media")
    }

    private suspend fun handleEvent(eventType: String, d: JsonObject) {
        when (eventType) {
            "CurrentProgramSceneChanged" -> {
                val scene = d.str("sceneName")
                _snapshot.update { it.copy(programScene = scene) }
                if (scene.isNotBlank() && _snapshot.value.sceneItemsByScene[scene] == null) {
                    refreshSceneItems(scene)
                }
            }

            "CurrentPreviewSceneChanged" -> {
                val scene = d.str("sceneName")
                _snapshot.update { it.copy(previewScene = scene) }
                if (scene.isNotBlank() && _snapshot.value.sceneItemsByScene[scene] == null) {
                    refreshSceneItems(scene)
                }
            }

            "StudioModeStateChanged" -> {
                val enabled = d.bool("studioModeEnabled")
                _snapshot.update { it.copy(studioMode = enabled) }
            }

            "SceneListChanged" -> {
                val scenes = parseScenes(d)
                _snapshot.update { it.copy(scenes = scenes) }
            }

            "SceneCreated", "SceneRemoved", "SceneNameChanged" -> {
                val scenesObj = request("GetSceneList").getOrNull()
                if (scenesObj != null) {
                    _snapshot.update {
                        it.copy(
                            scenes = parseScenes(scenesObj),
                            programScene = scenesObj.str("currentProgramSceneName", it.programScene),
                            previewScene = scenesObj.strOrNull("currentPreviewSceneName") ?: it.previewScene
                        )
                    }
                }
            }

            "CurrentSceneTransitionChanged" -> {
                val name = d.str("transitionName")
                val curr = request("GetCurrentSceneTransition").getOrNull()
                _snapshot.update {
                    it.copy(
                        currentTransition = name,
                        transitionDurationMs = curr?.int("transitionDuration", it.transitionDurationMs)
                            ?: it.transitionDurationMs
                    )
                }
            }

            "CurrentSceneTransitionDurationChanged" -> {
                val dur = d.int("transitionDuration", 300)
                _snapshot.update { it.copy(transitionDurationMs = dur) }
            }

            "StreamStateChanged" -> {
                val active = d.bool("outputActive")
                val state = d.str("outputState")
                _snapshot.update {
                    it.copy(
                        outputStatus = it.outputStatus.copy(
                            streamActive = active,
                            streamReconnecting = state.contains("RECONNECT", ignoreCase = true),
                            streamTimecode = if (!active) "00:00:00.000" else it.outputStatus.streamTimecode
                        )
                    )
                }
            }

            "RecordStateChanged" -> {
                val active = d.bool("outputActive")
                val state = d.str("outputState")
                val paused = when {
                    state.contains("PAUSED", ignoreCase = true) -> true
                    state.contains("RESUMED", ignoreCase = true) || !active -> false
                    else -> _snapshot.value.outputStatus.recPaused
                }
                _snapshot.update {
                    it.copy(
                        outputStatus = it.outputStatus.copy(
                            recActive = active,
                            recPaused = paused,
                            recTimecode = if (!active) "00:00:00.000" else it.outputStatus.recTimecode
                        )
                    )
                }
            }

            "ReplayBufferStateChanged" -> {
                val active = d.bool("outputActive")
                _snapshot.update {
                    it.copy(outputStatus = it.outputStatus.copy(replayActive = active))
                }
            }

            "VirtualcamStateChanged" -> {
                val active = d.bool("outputActive")
                _snapshot.update {
                    it.copy(outputStatus = it.outputStatus.copy(virtualCamActive = active))
                }
            }

            "InputMuteStateChanged" -> {
                val name = d.str("inputName")
                val muted = d.bool("inputMuted")
                _snapshot.update { snap ->
                    snap.copy(audioInputs = snap.audioInputs.map {
                        if (it.name == name) it.copy(muted = muted) else it
                    })
                }
            }

            "InputVolumeChanged" -> {
                val name = d.str("inputName")
                val mul = d.float("inputVolumeMul", 1f)
                val db = d.float("inputVolumeDb", AudioCurve.mulToDb(mul))
                _snapshot.update { snap ->
                    snap.copy(audioInputs = snap.audioInputs.map {
                        if (it.name == name) it.copy(volumeMul = mul, volumeDb = db) else it
                    })
                }
            }

            "InputAudioMonitorTypeChanged" -> {
                val name = d.str("inputName")
                val type = ObsMonitorType.fromObs(d.strOrNull("monitorType"))
                _snapshot.update { snap ->
                    snap.copy(audioInputs = snap.audioInputs.map {
                        if (it.name == name) it.copy(monitorType = type) else it
                    })
                }
            }

            "InputAudioSyncOffsetChanged" -> {
                val name = d.str("inputName")
                val offset = d.long("inputAudioSyncOffset")
                _snapshot.update { snap ->
                    snap.copy(audioInputs = snap.audioInputs.map {
                        if (it.name == name) it.copy(syncOffsetMs = offset) else it
                    })
                }
            }

            "InputAudioBalanceChanged" -> {
                val name = d.str("inputName")
                val balance = d.float("inputAudioBalance", 0.5f)
                _snapshot.update { snap ->
                    snap.copy(audioInputs = snap.audioInputs.map {
                        if (it.name == name) it.copy(balance = balance) else it
                    })
                }
            }

            "InputActiveStateChanged" -> {
                val name = d.str("inputName")
                val active = d.bool("videoActive", true)
                _snapshot.update { snap ->
                    snap.copy(audioInputs = snap.audioInputs.map {
                        if (it.name == name) it.copy(active = active) else it
                    })
                }
            }

            "InputCreated", "InputRemoved", "InputNameChanged" -> {
                hydrateInitialState()
            }

            "SceneItemCreated", "SceneItemRemoved", "SceneItemListReindexed" -> {
                val sceneName = d.str("sceneName")
                if (sceneName.isNotBlank()) refreshSceneItems(sceneName)
            }

            "SceneItemEnableStateChanged" -> {
                val sceneName = d.str("sceneName")
                val itemId = d.int("sceneItemId")
                val enabled = d.bool("sceneItemEnabled")
                updateSceneItemInCache(sceneName, itemId) { it.copy(enabled = enabled) }
            }

            "SceneItemLockStateChanged" -> {
                val sceneName = d.str("sceneName")
                val itemId = d.int("sceneItemId")
                val locked = d.bool("sceneItemLocked")
                updateSceneItemInCache(sceneName, itemId) { it.copy(locked = locked) }
            }

            "SceneItemTransformChanged" -> {
                val sceneName = d.str("sceneName")
                val itemId = d.int("sceneItemId")
                val tObj = d.obj("sceneItemTransform")
                if (tObj != null) {
                    val transform = parseTransform(tObj)
                    updateSceneItemInCache(sceneName, itemId) { it.copy(transform = transform) }
                }
            }

            "SourceFilterCreated", "SourceFilterRemoved", "SourceFilterListReindexed" -> {
                val sourceName = d.str("sourceName")
                if (sourceName.isNotBlank()) refreshFiltersForSource(sourceName)
            }

            "SourceFilterEnableStateChanged" -> {
                val sourceName = d.str("sourceName")
                val filterName = d.str("filterName")
                val enabled = d.bool("filterEnabled")
                _snapshot.update { snap ->
                    val updatedList = snap.filtersBySource[sourceName].orEmpty().map {
                        if (it.name == filterName) it.copy(enabled = enabled) else it
                    }
                    val updatedMap = snap.filtersBySource + (sourceName to updatedList)
                    val updatedAudio = snap.audioInputs.map { input ->
                        if (input.name == sourceName) {
                            input.copy(filters = input.filters.map {
                                if (it.name == filterName) it.copy(enabled = enabled) else it
                            })
                        } else input
                    }
                    snap.copy(filtersBySource = updatedMap, audioInputs = updatedAudio)
                }
            }

            "MediaInputPlaybackStarted", "MediaInputPlaybackEnded", "MediaInputActionTriggered" -> {
                val inputName = d.str("inputName")
                if (inputName.isNotBlank()) refreshMediaStatus(inputName)
            }

            "CurrentProfileChanged", "ProfileListChanged",
            "CurrentSceneCollectionChanged", "SceneCollectionListChanged" -> {
                hydrateInitialState()
            }

            "ExitStarted" -> {
                userInitiatedDisconnect = true
                _connectionState.value = ConnectionState.Error(
                    message = "OBS Studio exited on the host PC.",
                    isAuthError = false
                )
            }
        }
    }

    private fun updateSceneItemInCache(
        sceneName: String,
        itemId: Int,
        transformItem: (ObsSceneItem) -> ObsSceneItem
    ) {
        _snapshot.update { snap ->
            val currentList = snap.sceneItemsByScene[sceneName] ?: return@update snap
            fun mapItems(items: List<ObsSceneItem>): List<ObsSceneItem> = items.map { item ->
                val updatedSelf = if (item.sceneItemId == itemId) transformItem(item) else item
                if (updatedSelf.groupChildren.isNotEmpty()) {
                    updatedSelf.copy(groupChildren = mapItems(updatedSelf.groupChildren))
                } else {
                    updatedSelf
                }
            }
            snap.copy(sceneItemsByScene = snap.sceneItemsByScene + (sceneName to mapItems(currentList)))
        }
    }

    private suspend fun refreshFiltersForSource(sourceName: String) {
        val filtersObj = request("GetSourceFilterList", buildJsonObject { put("sourceName", sourceName) }).getOrNull()
        val filters = parseFilters(filtersObj)
        _snapshot.update { snap ->
            snap.copy(
                filtersBySource = snap.filtersBySource + (sourceName to filters),
                audioInputs = snap.audioInputs.map {
                    if (it.name == sourceName) it.copy(filters = filters) else it
                }
            )
        }
    }

    private suspend fun refreshMediaStatus(inputName: String) {
        val mediaObj = request("GetMediaInputStatus", buildJsonObject { put("inputName", inputName) }).getOrNull() ?: return
        val status = ObsMediaStatus(
            state = ObsMediaState.fromObs(mediaObj.strOrNull("mediaState")),
            cursorMs = mediaObj.long("mediaCursor"),
            durationMs = mediaObj.long("mediaDuration")
        )
        _snapshot.update { snap ->
            snap.copy(mediaStatuses = snap.mediaStatuses + (inputName to status))
        }
    }

    override suspend fun refreshSceneItems(sceneName: String) {
        if (sceneName.isBlank()) return
        val items = fetchSceneItems(sceneName)
        _snapshot.update { snap ->
            snap.copy(sceneItemsByScene = snap.sceneItemsByScene + (sceneName to items))
        }
    }

    override suspend fun refreshInputDetails(inputName: String) {
        refreshFiltersForSource(inputName)
        if (_snapshot.value.mediaStatuses.containsKey(inputName)) {
            refreshMediaStatus(inputName)
        }
    }

    // --- Scenes, Studio Mode & Transitions ---

    override suspend fun setProgramScene(sceneName: String): Result<Unit> =
        requestUnit("SetCurrentProgramScene", buildJsonObject { put("sceneName", sceneName) })

    override suspend fun setPreviewScene(sceneName: String): Result<Unit> =
        requestUnit("SetCurrentPreviewScene", buildJsonObject { put("sceneName", sceneName) })

    override suspend fun setStudioMode(enabled: Boolean): Result<Unit> =
        requestUnit("SetStudioModeEnabled", buildJsonObject { put("studioModeEnabled", enabled) })

    override suspend fun triggerTransition(): Result<Unit> =
        requestUnit("TriggerStudioModeTransition")

    override suspend fun setTransition(transitionName: String): Result<Unit> =
        requestUnit("SetCurrentSceneTransition", buildJsonObject { put("transitionName", transitionName) })

    override suspend fun setTransitionDuration(durationMs: Int): Result<Unit> =
        requestUnit("SetCurrentSceneTransitionDuration", buildJsonObject { put("transitionDuration", durationMs) })

    override suspend fun setTBarPosition(position: Double, release: Boolean): Result<Unit> =
        requestUnit(
            "SetTBarPosition",
            buildJsonObject {
                put("position", position.coerceIn(0.0, 1.0))
                put("release", release)
            }
        )

    // --- Outputs ---

    override suspend fun toggleStream(): Result<Unit> = requestUnit("ToggleStream")
    override suspend fun startStream(): Result<Unit> = requestUnit("StartStream")
    override suspend fun stopStream(): Result<Unit> = requestUnit("StopStream")

    override suspend fun toggleRecord(): Result<Unit> = requestUnit("ToggleRecord")
    override suspend fun startRecord(): Result<Unit> = requestUnit("StartRecord")
    override suspend fun stopRecord(): Result<Unit> = requestUnit("StopRecord")
    override suspend fun toggleRecordPause(): Result<Unit> = requestUnit("ToggleRecordPause")
    override suspend fun splitRecordFile(): Result<Unit> = requestUnit("SplitRecordFile")
    override suspend fun createRecordChapter(chapterName: String?): Result<Unit> =
        requestUnit(
            "CreateRecordChapter",
            if (chapterName.isNullOrBlank()) null else buildJsonObject { put("chapterName", chapterName) }
        )

    override suspend fun toggleReplayBuffer(): Result<Unit> = requestUnit("ToggleReplayBuffer")
    override suspend fun startReplayBuffer(): Result<Unit> = requestUnit("StartReplayBuffer")
    override suspend fun stopReplayBuffer(): Result<Unit> = requestUnit("StopReplayBuffer")
    override suspend fun saveReplayBuffer(): Result<Unit> = requestUnit("SaveReplayBuffer")

    override suspend fun toggleVirtualCam(): Result<Unit> = requestUnit("ToggleVirtualCam")
    override suspend fun startVirtualCam(): Result<Unit> = requestUnit("StartVirtualCam")
    override suspend fun stopVirtualCam(): Result<Unit> = requestUnit("StopVirtualCam")

    // --- Audio ---

    override suspend fun toggleMute(inputName: String): Result<Unit> =
        requestUnit("ToggleInputMute", buildJsonObject { put("inputName", inputName) })

    override suspend fun setMute(inputName: String, muted: Boolean): Result<Unit> =
        requestUnit(
            "SetInputMute",
            buildJsonObject {
                put("inputName", inputName)
                put("inputMuted", muted)
            }
        )

    override suspend fun setVolumeMul(inputName: String, volumeMul: Float): Result<Unit> {
        val clamped = volumeMul.coerceIn(0f, 1f)
        // Optimistic update for responsive slider feel
        _snapshot.update { snap ->
            snap.copy(audioInputs = snap.audioInputs.map {
                if (it.name == inputName) it.copy(volumeMul = clamped, volumeDb = AudioCurve.mulToDb(clamped)) else it
            })
        }
        return requestUnit(
            "SetInputVolume",
            buildJsonObject {
                put("inputName", inputName)
                put("inputVolumeMul", clamped.toDouble())
            }
        )
    }

    override suspend fun setVolumeDb(inputName: String, volumeDb: Float): Result<Unit> {
        val clamped = volumeDb.coerceIn(-100f, 20f)
        return requestUnit(
            "SetInputVolume",
            buildJsonObject {
                put("inputName", inputName)
                put("inputVolumeDb", clamped.toDouble())
            }
        )
    }

    override suspend fun setMonitorType(inputName: String, monitorType: ObsMonitorType): Result<Unit> =
        requestUnit(
            "SetInputAudioMonitorType",
            buildJsonObject {
                put("inputName", inputName)
                put("monitorType", monitorType.obsValue)
            }
        )

    override suspend fun setSyncOffset(inputName: String, offsetMs: Long): Result<Unit> =
        requestUnit(
            "SetInputAudioSyncOffset",
            buildJsonObject {
                put("inputName", inputName)
                put("inputAudioSyncOffset", offsetMs.coerceIn(-950L, 20000L))
            }
        )

    override suspend fun setBalance(inputName: String, balance: Float): Result<Unit> =
        requestUnit(
            "SetInputAudioBalance",
            buildJsonObject {
                put("inputName", inputName)
                put("inputAudioBalance", balance.coerceIn(0f, 1f).toDouble())
            }
        )

    override suspend fun getInputPropertyListItems(
        inputName: String,
        propertyName: String
    ): Result<List<ObsPropertyOption>> = request(
        "GetInputPropertiesListPropertyItems",
        buildJsonObject {
            put("inputName", inputName)
            put("propertyName", propertyName)
        }
    ).map { data ->
        data.arr("propertyItems")?.mapNotNull { elem ->
            val obj = elem.asObjOrNull() ?: return@mapNotNull null
            val name = obj.str("itemName")
            val value = obj.str("itemValue")
            if (name.isBlank() && value.isBlank()) return@mapNotNull null
            ObsPropertyOption(
                name = name.ifBlank { value },
                value = value,
                enabled = obj.bool("itemEnabled", true)
            )
        }.orEmpty()
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

    // --- Sources, Scene Items, Filters, Settings, Transform, Media ---

    override suspend fun setSceneItemEnabled(sceneName: String, sceneItemId: Int, enabled: Boolean): Result<Unit> =
        requestUnit(
            "SetSceneItemEnabled",
            buildJsonObject {
                put("sceneName", sceneName)
                put("sceneItemId", sceneItemId)
                put("sceneItemEnabled", enabled)
            }
        )

    override suspend fun setSceneItemEnabledByName(
        sceneName: String,
        sourceName: String,
        mode: ToggleMode
    ): Result<Unit> {
        val targetScene = sceneName.ifBlank { _snapshot.value.programScene }
        var items = _snapshot.value.itemsForScene(targetScene)
        if (items.isEmpty() && targetScene.isNotBlank()) {
            items = fetchSceneItems(targetScene)
        }
        val item = findItemRecursive(items, sourceName)
            ?: return Result.failure(IllegalArgumentException("Source '$sourceName' not found in '$targetScene'"))
        val nextEnabled = when (mode) {
            ToggleMode.TOGGLE -> !item.enabled
            ToggleMode.ENABLE -> true
            ToggleMode.DISABLE -> false
        }
        return setSceneItemEnabled(targetScene, item.sceneItemId, nextEnabled)
    }

    private fun findItemRecursive(items: List<ObsSceneItem>, sourceName: String): ObsSceneItem? {
        for (item in items) {
            if (item.sourceName == sourceName) return item
            if (item.groupChildren.isNotEmpty()) {
                val found = findItemRecursive(item.groupChildren, sourceName)
                if (found != null) return found
            }
        }
        return null
    }

    override suspend fun setSceneItemLocked(sceneName: String, sceneItemId: Int, locked: Boolean): Result<Unit> =
        requestUnit(
            "SetSceneItemLocked",
            buildJsonObject {
                put("sceneName", sceneName)
                put("sceneItemId", sceneItemId)
                put("sceneItemLocked", locked)
            }
        )

    override suspend fun setSceneItemIndex(sceneName: String, sceneItemId: Int, index: Int): Result<Unit> =
        requestUnit(
            "SetSceneItemIndex",
            buildJsonObject {
                put("sceneName", sceneName)
                put("sceneItemId", sceneItemId)
                put("sceneItemIndex", index.coerceAtLeast(0))
            }
        )

    override suspend fun setSceneItemTransform(
        sceneName: String,
        sceneItemId: Int,
        transformPatch: JsonObject
    ): Result<Unit> = requestUnit(
        "SetSceneItemTransform",
        buildJsonObject {
            put("sceneName", sceneName)
            put("sceneItemId", sceneItemId)
            put("sceneItemTransform", transformPatch)
        }
    )

    override suspend fun fitSceneItemToScreen(sceneName: String, sceneItemId: Int): Result<Unit> {
        val snap = _snapshot.value
        val item = snap.itemsForScene(sceneName).firstOrNull { it.sceneItemId == sceneItemId }
        val t = item?.transform
        val cw = snap.baseCanvasWidth.toDouble().coerceAtLeast(1.0)
        val ch = snap.baseCanvasHeight.toDouble().coerceAtLeast(1.0)
        val sw = (t?.sourceWidth ?: cw).coerceAtLeast(1.0)
        val sh = (t?.sourceHeight ?: ch).coerceAtLeast(1.0)
        val scale = kotlin.math.min(cw / sw, ch / sh)
        val newW = sw * scale
        val newH = sh * scale
        val patch = buildJsonObject {
            put("positionX", (cw - newW) / 2.0)
            put("positionY", (ch - newH) / 2.0)
            put("scaleX", scale)
            put("scaleY", scale)
            put("rotation", 0.0)
        }
        return setSceneItemTransform(sceneName, sceneItemId, patch)
    }

    override suspend fun resetSceneItemTransform(sceneName: String, sceneItemId: Int): Result<Unit> {
        val patch = buildJsonObject {
            put("positionX", 0.0)
            put("positionY", 0.0)
            put("scaleX", 1.0)
            put("scaleY", 1.0)
            put("rotation", 0.0)
            put("cropTop", 0)
            put("cropBottom", 0)
            put("cropLeft", 0)
            put("cropRight", 0)
        }
        return setSceneItemTransform(sceneName, sceneItemId, patch)
    }

    override suspend fun setFilterEnabled(sourceName: String, filterName: String, enabled: Boolean): Result<Unit> =
        requestUnit(
            "SetSourceFilterEnabled",
            buildJsonObject {
                put("sourceName", sourceName)
                put("filterName", filterName)
                put("filterEnabled", enabled)
            }
        )

    override suspend fun toggleFilterByName(sourceName: String, filterName: String, mode: ToggleMode): Result<Unit> {
        val filters = _snapshot.value.filtersBySource[sourceName]
            ?: parseFilters(request("GetSourceFilterList", buildJsonObject { put("sourceName", sourceName) }).getOrNull())
        val target = filters.firstOrNull { it.name == filterName }
            ?: return Result.failure(IllegalArgumentException("Filter '$filterName' not found on '$sourceName'"))
        val next = when (mode) {
            ToggleMode.TOGGLE -> !target.enabled
            ToggleMode.ENABLE -> true
            ToggleMode.DISABLE -> false
        }
        return setFilterEnabled(sourceName, filterName, next)
    }

    override suspend fun getInputSettings(inputName: String): Result<JsonObject> =
        request("GetInputSettings", buildJsonObject { put("inputName", inputName) })

    override suspend fun setInputSettings(inputName: String, settings: JsonObject, overlay: Boolean): Result<Unit> =
        requestUnit(
            "SetInputSettings",
            buildJsonObject {
                put("inputName", inputName)
                put("inputSettings", settings)
                put("overlay", overlay)
            }
        )

    override suspend fun pressInputPropertiesButton(inputName: String, propertyName: String): Result<Unit> =
        requestUnit(
            "PressInputPropertiesButton",
            buildJsonObject {
                put("inputName", inputName)
                put("propertyName", propertyName)
            }
        )

    override suspend fun triggerMediaAction(inputName: String, action: ObsMediaAction): Result<Unit> =
        requestUnit(
            "TriggerMediaInputAction",
            buildJsonObject {
                put("inputName", inputName)
                put("mediaAction", action.obsInputAction)
            }
        )

    override suspend fun setMediaCursor(inputName: String, cursorMs: Long): Result<Unit> =
        requestUnit(
            "SetMediaInputCursor",
            buildJsonObject {
                put("inputName", inputName)
                put("mediaCursor", cursorMs.coerceAtLeast(0L))
            }
        )

    // --- System & Raw ---

    override suspend fun setProfile(profileName: String): Result<Unit> =
        requestUnit("SetCurrentProfile", buildJsonObject { put("profileName", profileName) })

    override suspend fun setSceneCollection(collectionName: String): Result<Unit> =
        requestUnit("SetCurrentSceneCollection", buildJsonObject { put("sceneCollectionName", collectionName) })

    override suspend fun triggerHotkey(hotkeyName: String): Result<Unit> =
        requestUnit("TriggerHotkeyByName", buildJsonObject { put("hotkeyName", hotkeyName) })

    override suspend fun callVendorRequest(
        vendorName: String,
        requestType: String,
        requestData: JsonObject?
    ): Result<JsonObject> = request(
        "CallVendorRequest",
        buildJsonObject {
            put("vendorName", vendorName)
            put("requestType", requestType)
            if (requestData != null) put("requestData", requestData)
        }
    )

    override suspend fun sendRawRequest(requestType: String, requestData: JsonObject?): Result<JsonObject> =
        request(requestType, requestData)
}

class ObsRequestException(
    val requestType: String,
    val code: Int,
    override val message: String
) : Exception("$requestType [$code]: $message")
