package com.regiaobs.app.ui.mixer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewColumn
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.regiaobs.app.data.AppSettings
import com.regiaobs.app.obs.AudioCurve
import com.regiaobs.app.obs.ObsAudioInput
import com.regiaobs.app.obs.ObsMonitorType
import com.regiaobs.app.obs.ObsPropertyOption
import com.regiaobs.app.obs.ObsSnapshot
import com.regiaobs.app.ui.theme.BroadcastHaptics
import com.regiaobs.app.ui.theme.StudioColors
import com.regiaobs.app.ui.theme.tabular
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max

@Composable
fun MixerScreen(
    snapshot: ObsSnapshot,
    volumeMetersFlow: StateFlow<Map<String, List<Float>>>,
    settings: AppSettings,
    haptics: BroadcastHaptics,
    onToggleMute: (String) -> Unit,
    onSetVolumeMul: (String, Float) -> Unit,
    onSetMonitorType: (String, ObsMonitorType) -> Unit,
    onSetSyncOffset: (String, Long) -> Unit,
    onSetBalance: (String, Float) -> Unit,
    onToggleFilter: (String, String, Boolean) -> Unit,
    onFetchDeviceOptions: suspend (String, String) -> Result<Pair<String, List<ObsPropertyOption>>>,
    onSelectAudioDevice: (String, String, String, String) -> Unit,
    onToggleShowActiveOnly: () -> Unit,
    onToggleVerticalFaders: () -> Unit
) {
    val activeSourceNames = remember(snapshot.programScene, snapshot.sceneItemsByScene, snapshot.specialInputs) {
        snapshot.activeSourceNames()
    }
    val displayedInputs = remember(snapshot.audioInputs, settings.showActiveAudioOnly, activeSourceNames) {
        if (!settings.showActiveAudioOnly) {
            snapshot.audioInputs
        } else {
            snapshot.audioInputs.filter { inp -> inp.name in activeSourceNames }
        }
    }

    val primaryMicInput = remember(snapshot.specialInputs, snapshot.audioInputs) {
        val special = snapshot.specialInputs.primaryMic()
        snapshot.audioInputs.firstOrNull { it.name == special && it.devicePropertyName() != null }
            ?: snapshot.audioInputs.firstOrNull { it.isMicInput() && it.devicePropertyName() != null }
    }
    val primarySpeakerInput = remember(snapshot.specialInputs, snapshot.audioInputs) {
        val special = snapshot.specialInputs.primaryDesktop()
        snapshot.audioInputs.firstOrNull { it.name == special && it.devicePropertyName() != null }
            ?: snapshot.audioInputs.firstOrNull { it.isOutputDeviceInput() && it.devicePropertyName() != null }
    }

    var inspectingInputName by remember { mutableStateOf<String?>(null) }
    var devicePickerTarget by remember { mutableStateOf<ObsAudioInput?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Canvas)
    ) {
        // Mixer Header Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Audio Mixer",
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioColors.TextPrimary
                )
                FilterChip(
                    selected = settings.showActiveAudioOnly,
                    onClick = {
                        haptics.tap()
                        onToggleShowActiveOnly()
                    },
                    label = {
                        Text(
                            text = if (settings.showActiveAudioOnly) "Active only" else "All (${snapshot.audioInputs.size})",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                )
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = StudioColors.Panel,
                border = BorderStroke(1.dp, StudioColors.Border),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.tap()
                        onToggleVerticalFaders()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (settings.verticalMixerFaders) Icons.Rounded.ViewColumn else Icons.Rounded.ViewStream,
                        contentDescription = null,
                        tint = StudioColors.ReplayCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (settings.verticalMixerFaders) "Console" else "List",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioColors.TextPrimary
                    )
                }
            }
        }

        // Quick Hardware Switcher Bar (Mic & Speaker)
        if (primaryMicInput != null || primarySpeakerInput != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (primaryMicInput != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioColors.Panel,
                        border = BorderStroke(1.dp, StudioColors.Border),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.tap()
                                devicePickerTarget = primaryMicInput
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Mic,
                                contentDescription = null,
                                tint = StudioColors.ReplayCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Switch Mic",
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (primarySpeakerInput != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = StudioColors.Panel,
                        border = BorderStroke(1.dp, StudioColors.Border),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.tap()
                                devicePickerTarget = primarySpeakerInput
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Speaker,
                                contentDescription = null,
                                tint = StudioColors.TallyGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Switch Speaker",
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioColors.TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        if (displayedInputs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = StudioColors.Panel,
                    border = BorderStroke(1.dp, StudioColors.Border)
                ) {
                    Text(
                        text = "No audio sources found. Disable \"Active only\" or add an audio input in OBS.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else if (settings.verticalMixerFaders) {
            LazyRow(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedInputs, key = { it.name }) { input ->
                    VerticalChannelStrip(
                        input = input,
                        volumeMetersFlow = volumeMetersFlow,
                        haptics = haptics,
                        onToggleMute = { onToggleMute(input.name) },
                        onSetVolumeMul = { mul -> onSetVolumeMul(input.name, mul) },
                        onCycleMonitor = {
                            val next = when (input.monitorType) {
                                ObsMonitorType.NONE -> ObsMonitorType.MONITOR_ONLY
                                ObsMonitorType.MONITOR_ONLY -> ObsMonitorType.MONITOR_AND_OUTPUT
                                ObsMonitorType.MONITOR_AND_OUTPUT -> ObsMonitorType.NONE
                            }
                            onSetMonitorType(input.name, next)
                        },
                        onInspect = { inspectingInputName = input.name }
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedInputs, key = { it.name }) { input ->
                    HorizontalChannelCard(
                        input = input,
                        volumeMetersFlow = volumeMetersFlow,
                        haptics = haptics,
                        onToggleMute = { onToggleMute(input.name) },
                        onSetVolumeMul = { mul -> onSetVolumeMul(input.name, mul) },
                        onInspect = { inspectingInputName = input.name }
                    )
                }
            }
        }
    }

    val inspectingInput = snapshot.audioInputs.firstOrNull { it.name == inspectingInputName }
    if (inspectingInput != null) {
        AudioInspectorSheet(
            input = inspectingInput,
            haptics = haptics,
            onFetchDeviceOptions = onFetchDeviceOptions,
            onSelectAudioDevice = onSelectAudioDevice,
            onSetMonitorType = { onSetMonitorType(inspectingInput.name, it) },
            onSetSyncOffset = { onSetSyncOffset(inspectingInput.name, it) },
            onSetBalance = { onSetBalance(inspectingInput.name, it) },
            onToggleFilter = { filterName, enabled ->
                onToggleFilter(inspectingInput.name, filterName, enabled)
            },
            onDismiss = { inspectingInputName = null }
        )
    }

    devicePickerTarget?.let { target ->
        val propName = target.devicePropertyName()
        if (propName != null) {
            AudioDevicePickerSheet(
                input = target,
                propertyName = propName,
                haptics = haptics,
                onFetchDeviceOptions = onFetchDeviceOptions,
                onSelectAudioDevice = { deviceValue, deviceLabel ->
                    onSelectAudioDevice(target.name, propName, deviceValue, deviceLabel)
                    devicePickerTarget = null
                },
                onDismiss = { devicePickerTarget = null }
            )
        }
    }
}

@Composable
private fun VerticalChannelStrip(
    input: ObsAudioInput,
    volumeMetersFlow: StateFlow<Map<String, List<Float>>>,
    haptics: BroadcastHaptics,
    onToggleMute: () -> Unit,
    onSetVolumeMul: (Float) -> Unit,
    onCycleMonitor: () -> Unit,
    onInspect: () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragDeflection by remember(input.name) {
        mutableFloatStateOf(AudioCurve.mulToDeflection(input.volumeMul))
    }
    var lastSentMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(input.volumeMul, dragging) {
        if (!dragging) {
            dragDeflection = AudioCurve.mulToDeflection(input.volumeMul)
        }
    }

    val shownMul = if (dragging) AudioCurve.deflectionToMul(dragDeflection) else input.volumeMul
    val shownDb = if (dragging) AudioCurve.mulToDb(shownMul) else input.volumeDb

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StudioColors.Panel,
        border = BorderStroke(
            1.dp,
            if (input.muted) StudioColors.TallyRed.copy(alpha = 0.5f) else StudioColors.Border
        ),
        modifier = Modifier
            .width(126.dp)
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Channel Title + Inspector icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = input.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onInspect,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = "Details",
                        tint = StudioColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Tabular dB Readout
            Text(
                text = AudioCurve.formatDb(shownDb),
                style = MaterialTheme.typography.labelMedium.tabular(),
                color = if (input.muted) StudioColors.TallyRed else StudioColors.ReplayCyan
            )

            // Vertical Fader + VU Meter side-by-side
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                VerticalFaderTrack(
                    deflection = dragDeflection,
                    muted = input.muted,
                    onDeflectionChange = { next, finished ->
                        dragging = !finished
                        dragDeflection = next
                        val now = System.currentTimeMillis()
                        if (finished || now - lastSentMs >= 40L) {
                            lastSentMs = now
                            onSetVolumeMul(AudioCurve.deflectionToMul(next))
                        }
                    },
                    modifier = Modifier
                        .width(46.dp)
                        .fillMaxHeight()
                )
                Spacer(modifier = Modifier.width(8.dp))
                VuMeterBar(
                    inputName = input.name,
                    volumeMetersFlow = volumeMetersFlow,
                    muted = input.muted,
                    vertical = true,
                    modifier = Modifier
                        .width(18.dp)
                        .fillMaxHeight()
                )
            }

            // Monitor quick cycle pill
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (input.monitorType != ObsMonitorType.NONE) StudioColors.ReplayCyanSoft else StudioColors.Elevated,
                border = BorderStroke(
                    1.dp,
                    if (input.monitorType != ObsMonitorType.NONE) StudioColors.ReplayCyan else StudioColors.Border
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        haptics.tap()
                        onCycleMonitor()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Headphones,
                        contentDescription = null,
                        tint = if (input.monitorType != ObsMonitorType.NONE) StudioColors.ReplayCyan else StudioColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = input.monitorType.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioColors.TextPrimary
                    )
                }
            }

            // Big Mute Key
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (input.muted) StudioColors.TallyRed else StudioColors.Elevated,
                border = BorderStroke(1.dp, if (input.muted) StudioColors.TallyRed else StudioColors.BorderStrong),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        haptics.strong()
                        onToggleMute()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (input.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = if (input.muted) "Unmute" else "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (input.muted) "MUTED" else "ON",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun HorizontalChannelCard(
    input: ObsAudioInput,
    volumeMetersFlow: StateFlow<Map<String, List<Float>>>,
    haptics: BroadcastHaptics,
    onToggleMute: () -> Unit,
    onSetVolumeMul: (Float) -> Unit,
    onInspect: () -> Unit
) {
    var dragging by remember { mutableStateOf(false) }
    var dragDeflection by remember(input.name) {
        mutableFloatStateOf(AudioCurve.mulToDeflection(input.volumeMul))
    }
    var lastSentMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(input.volumeMul, dragging) {
        if (!dragging) {
            dragDeflection = AudioCurve.mulToDeflection(input.volumeMul)
        }
    }

    val shownMul = if (dragging) AudioCurve.deflectionToMul(dragDeflection) else input.volumeMul
    val shownDb = if (dragging) AudioCurve.mulToDb(shownMul) else input.volumeDb

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = StudioColors.Panel,
        border = BorderStroke(1.dp, if (input.muted) StudioColors.TallyRed.copy(alpha = 0.55f) else StudioColors.Border)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = input.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = StudioColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${AudioCurve.formatDb(shownDb)}  •  ${input.monitorType.shortLabel}",
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = if (input.muted) StudioColors.TallyRed else StudioColors.ReplayCyan
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onInspect) {
                        Icon(Icons.Rounded.Tune, "Details", tint = StudioColors.TextSecondary)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (input.muted) StudioColors.TallyRed else StudioColors.Elevated,
                        border = BorderStroke(1.dp, if (input.muted) StudioColors.TallyRed else StudioColors.BorderStrong),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.strong()
                                onToggleMute()
                            }
                    ) {
                        Icon(
                            imageVector = if (input.muted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                            contentDescription = "Mute",
                            tint = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp).size(20.dp)
                        )
                    }
                }
            }

            VuMeterBar(
                inputName = input.name,
                volumeMetersFlow = volumeMetersFlow,
                muted = input.muted,
                vertical = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )

            Slider(
                value = dragDeflection,
                onValueChange = { next ->
                    dragging = true
                    dragDeflection = next
                    val now = System.currentTimeMillis()
                    if (now - lastSentMs >= 40L) {
                        lastSentMs = now
                        onSetVolumeMul(AudioCurve.deflectionToMul(next))
                    }
                },
                onValueChangeFinished = {
                    dragging = false
                    onSetVolumeMul(AudioCurve.deflectionToMul(dragDeflection))
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = StudioColors.TextPrimary,
                    activeTrackColor = if (input.muted) StudioColors.TextMuted else StudioColors.ReplayCyan,
                    inactiveTrackColor = StudioColors.Elevated
                )
            )
        }
    }
}

@Composable
private fun VerticalFaderTrack(
    deflection: Float,
    muted: Boolean,
    onDeflectionChange: (Float, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val next = (1f - (offset.y / h)).coerceIn(0f, 1f)
                    onDeflectionChange(next, true)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val next = (1f - (offset.y / h)).coerceIn(0f, 1f)
                        onDeflectionChange(next, false)
                    },
                    onDragEnd = {
                        onDeflectionChange(deflection, true)
                    },
                    onDragCancel = {
                        onDeflectionChange(deflection, true)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val next = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                        onDeflectionChange(next, false)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackW = 8.dp.toPx()
            val trackLeft = (size.width - trackW) / 2f
            val padY = 12.dp.toPx()
            val usableH = (size.height - padY * 2f).coerceAtLeast(1f)

            // Groove
            drawRoundRect(
                color = StudioColors.Canvas,
                topLeft = Offset(trackLeft, padY),
                size = Size(trackW, usableH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Active fill
            val fillH = usableH * deflection.coerceIn(0f, 1f)
            val fillTop = padY + (usableH - fillH)
            drawRoundRect(
                color = if (muted) StudioColors.TextMuted else StudioColors.ReplayCyan,
                topLeft = Offset(trackLeft, fillTop),
                size = Size(trackW, fillH),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )

            // Fader Cap
            val capW = size.width * 0.86f
            val capH = 22.dp.toPx()
            val capLeft = (size.width - capW) / 2f
            val capCenterY = fillTop.coerceIn(padY, padY + usableH)
            val capTop = (capCenterY - capH / 2f).coerceIn(0f, size.height - capH)

            drawRoundRect(
                color = StudioColors.ElevatedHover,
                topLeft = Offset(capLeft, capTop),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            )
            // Center grip line on fader cap
            drawLine(
                color = if (muted) StudioColors.TallyRed else StudioColors.TextPrimary,
                start = Offset(capLeft + 5.dp.toPx(), capTop + capH / 2f),
                end = Offset(capLeft + capW - 5.dp.toPx(), capTop + capH / 2f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
fun VuMeterBar(
    inputName: String,
    volumeMetersFlow: StateFlow<Map<String, List<Float>>>,
    muted: Boolean,
    vertical: Boolean,
    modifier: Modifier = Modifier
) {
    // Only read inside Canvas draw scope to avoid layout recomposition at 20Hz
    val levelsState = remember(inputName) { mutableStateOf(listOf(0f, 0f)) }
    val peakHoldState = remember(inputName) { mutableStateOf(listOf(0f, 0f)) }

    LaunchedEffect(inputName, volumeMetersFlow) {
        volumeMetersFlow.collect { map ->
            val raw = map[inputName]
            if (raw == null || raw.isEmpty()) {
                levelsState.value = listOf(0f, 0f)
                peakHoldState.value = peakHoldState.value.map { (it - 0.02f).coerceAtLeast(0f) }
            } else {
                val normalized = raw.map { mul ->
                    if (mul <= 0.0001f) 0f
                    else ((AudioCurve.mulToDb(mul).coerceIn(-60f, 0f) + 60f) / 60f).coerceIn(0f, 1f)
                }
                levelsState.value = normalized
                val prevPeaks = peakHoldState.value
                peakHoldState.value = normalized.mapIndexed { idx, v ->
                    val prev = prevPeaks.getOrElse(idx) { 0f }
                    max(v, (prev - 0.015f).coerceAtLeast(0f))
                }
            }
        }
    }

    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(StudioColors.Canvas)
    ) {
        val channels = levelsState.value.ifEmpty { listOf(0f) }
        val peaks = peakHoldState.value
        val count = channels.size.coerceIn(1, 2)
        val gap = 2.dp.toPx()

        if (vertical) {
            val chW = (size.width - gap * (count - 1)) / count
            for (i in 0 until count) {
                val level = if (muted) 0f else channels[i]
                val peak = if (muted) 0f else peaks.getOrElse(i) { level }
                val x = i * (chW + gap)

                // Segmented green / yellow / red zones
                val greenEnd = 0.65f
                val yellowEnd = 0.85f
                val fillH = size.height * level

                if (fillH > 0f) {
                    val gFrac = level.coerceAtMost(greenEnd)
                    if (gFrac > 0f) {
                        val gh = size.height * gFrac
                        drawRect(
                            color = StudioColors.TallyGreen,
                            topLeft = Offset(x, size.height - gh),
                            size = Size(chW, gh)
                        )
                    }
                    if (level > greenEnd) {
                        val yTopFrac = level.coerceAtMost(yellowEnd)
                        val yh = size.height * (yTopFrac - greenEnd)
                        drawRect(
                            color = StudioColors.RecAmber,
                            topLeft = Offset(x, size.height * (1f - yTopFrac)),
                            size = Size(chW, yh)
                        )
                    }
                    if (level > yellowEnd) {
                        val rh = size.height * (level - yellowEnd)
                        drawRect(
                            color = StudioColors.TallyRed,
                            topLeft = Offset(x, size.height * (1f - level)),
                            size = Size(chW, rh)
                        )
                    }
                }

                if (peak > 0.03f) {
                    val py = (size.height * (1f - peak)).coerceIn(0f, size.height - 2.dp.toPx())
                    drawRect(
                        color = if (peak > 0.85f) StudioColors.TallyRed else StudioColors.TextPrimary,
                        topLeft = Offset(x, py),
                        size = Size(chW, 2.dp.toPx())
                    )
                }
            }
        } else {
            val chH = (size.height - gap * (count - 1)) / count
            for (i in 0 until count) {
                val level = if (muted) 0f else channels[i]
                val peak = if (muted) 0f else peaks.getOrElse(i) { level }
                val y = i * (chH + gap)

                val greenEnd = 0.65f
                val yellowEnd = 0.85f
                val gFrac = level.coerceAtMost(greenEnd)
                if (gFrac > 0f) {
                    drawRect(
                        color = StudioColors.TallyGreen,
                        topLeft = Offset(0f, y),
                        size = Size(size.width * gFrac, chH)
                    )
                }
                if (level > greenEnd) {
                    val yTop = level.coerceAtMost(yellowEnd)
                    drawRect(
                        color = StudioColors.RecAmber,
                        topLeft = Offset(size.width * greenEnd, y),
                        size = Size(size.width * (yTop - greenEnd), chH)
                    )
                }
                if (level > yellowEnd) {
                    drawRect(
                        color = StudioColors.TallyRed,
                        topLeft = Offset(size.width * yellowEnd, y),
                        size = Size(size.width * (level - yellowEnd), chH)
                    )
                }

                if (peak > 0.03f) {
                    val px = (size.width * peak).coerceIn(0f, size.width - 2.dp.toPx())
                    drawRect(
                        color = if (peak > 0.85f) StudioColors.TallyRed else StudioColors.TextPrimary,
                        topLeft = Offset(px, y),
                        size = Size(2.dp.toPx(), chH)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioInspectorSheet(
    input: ObsAudioInput,
    haptics: BroadcastHaptics,
    onFetchDeviceOptions: suspend (String, String) -> Result<Pair<String, List<ObsPropertyOption>>>,
    onSelectAudioDevice: (String, String, String, String) -> Unit,
    onSetMonitorType: (ObsMonitorType) -> Unit,
    onSetSyncOffset: (Long) -> Unit,
    onSetBalance: (Float) -> Unit,
    onToggleFilter: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var syncMs by remember(input.name, input.syncOffsetMs) { mutableFloatStateOf(input.syncOffsetMs.toFloat()) }
    var balance by remember(input.name, input.balance) { mutableFloatStateOf(input.balance) }
    val propName = remember(input.kind) { input.devicePropertyName() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioColors.Panel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = input.name,
                style = MaterialTheme.typography.titleMedium,
                color = StudioColors.TextPrimary
            )

            if (propName != null) {
                AudioDeviceSelectorSection(
                    input = input,
                    propertyName = propName,
                    haptics = haptics,
                    onFetchDeviceOptions = onFetchDeviceOptions,
                    onSelectAudioDevice = { value, label ->
                        onSelectAudioDevice(input.name, propName, value, label)
                    }
                )
            }

            // Monitor mode
            Text(
                text = "Audio monitoring",
                style = MaterialTheme.typography.labelMedium,
                color = StudioColors.TextSecondary
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ObsMonitorType.entries.forEach { mode ->
                    val selected = input.monitorType == mode
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selected) StudioColors.ReplayCyanSoft else StudioColors.Elevated,
                        border = BorderStroke(1.dp, if (selected) StudioColors.ReplayCyan else StudioColors.Border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptics.tap()
                                onSetMonitorType(mode)
                            }
                    ) {
                        Text(
                            text = mode.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Sync Offset
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sync Offset",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioColors.TextSecondary
                )
                Text(
                    text = "${syncMs.toLong()} ms",
                    style = MaterialTheme.typography.labelMedium.tabular(),
                    color = StudioColors.ReplayCyan
                )
            }
            Slider(
                value = syncMs,
                onValueChange = { syncMs = it },
                onValueChangeFinished = { onSetSyncOffset(syncMs.toLong()) },
                valueRange = -950f..2000f
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(-50L, -10L, 0L, 10L, 50L).forEach { delta ->
                    OutlinedButton(
                        onClick = {
                            val next = if (delta == 0L) 0L else (syncMs.toLong() + delta).coerceIn(-950L, 2000L)
                            syncMs = next.toFloat()
                            onSetSyncOffset(next)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (delta == 0L) "0" else if (delta > 0) "+$delta" else "$delta",
                            style = MaterialTheme.typography.labelSmall.tabular()
                        )
                    }
                }
            }

            // Stereo Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "L / R Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioColors.TextSecondary
                )
                TextButton(onClick = {
                    balance = 0.5f
                    onSetBalance(0.5f)
                }) {
                    Text("Center (${"%.0f".format((balance - 0.5f) * 200f)})")
                }
            }
            Slider(
                value = balance,
                onValueChange = { balance = it },
                onValueChangeFinished = { onSetBalance(balance) },
                valueRange = 0f..1f
            )

            // Audio Filters
            if (input.filters.isNotEmpty()) {
                Text(
                    text = "Audio filters",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioColors.TextSecondary
                )
                input.filters.forEach { filter ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = filter.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioColors.TextPrimary
                        )
                        Switch(
                            checked = filter.enabled,
                            onCheckedChange = { enabled ->
                                haptics.tap()
                                onToggleFilter(filter.name, enabled)
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioDevicePickerSheet(
    input: ObsAudioInput,
    propertyName: String,
    haptics: BroadcastHaptics,
    onFetchDeviceOptions: suspend (String, String) -> Result<Pair<String, List<ObsPropertyOption>>>,
    onSelectAudioDevice: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioColors.Panel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = input.name,
                style = MaterialTheme.typography.titleMedium,
                color = StudioColors.TextPrimary
            )
            AudioDeviceSelectorSection(
                input = input,
                propertyName = propertyName,
                haptics = haptics,
                onFetchDeviceOptions = onFetchDeviceOptions,
                onSelectAudioDevice = onSelectAudioDevice
            )
        }
    }
}

@Composable
private fun AudioDeviceSelectorSection(
    input: ObsAudioInput,
    propertyName: String,
    haptics: BroadcastHaptics,
    onFetchDeviceOptions: suspend (String, String) -> Result<Pair<String, List<ObsPropertyOption>>>,
    onSelectAudioDevice: (String, String) -> Unit
) {
    var reloadKey by remember(input.name, propertyName) { mutableIntStateOf(0) }
    var loading by remember(input.name, propertyName) { mutableStateOf(true) }
    var errorMsg by remember(input.name, propertyName) { mutableStateOf<String?>(null) }
    var selectedValue by remember(input.name, propertyName) { mutableStateOf("default") }
    var options by remember(input.name, propertyName) { mutableStateOf<List<ObsPropertyOption>>(emptyList()) }

    LaunchedEffect(input.name, propertyName, reloadKey) {
        loading = true
        errorMsg = null
        val res = onFetchDeviceOptions(input.name, propertyName)
        loading = false
        if (res.isSuccess) {
            val (cur, list) = res.getOrThrow()
            selectedValue = cur
            options = list
        } else {
            errorMsg = res.exceptionOrNull()?.message ?: "Unable to load audio devices."
        }
    }

    val title = if (propertyName == "window") "Captured application window" else "Hardware Device (Mic / Speaker)"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = StudioColors.TextSecondary
        )
        IconButton(
            onClick = { reloadKey++ },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = "Refresh devices",
                tint = StudioColors.ReplayCyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    when {
        loading -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = StudioColors.ReplayCyan)
            }
        }
        errorMsg != null -> {
            Text(
                text = errorMsg!!,
                style = MaterialTheme.typography.bodySmall,
                color = StudioColors.TallyRed
            )
        }
        options.isEmpty() -> {
            Text(
                text = "No devices returned by OBS.",
                style = MaterialTheme.typography.bodySmall,
                color = StudioColors.TextSecondary
            )
        }
        else -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { opt ->
                    val isSelected = opt.value == selectedValue
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) StudioColors.ReplayCyanSoft else StudioColors.Elevated,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) StudioColors.ReplayCyan else StudioColors.Border
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = opt.enabled) {
                                haptics.strong()
                                selectedValue = opt.value
                                onSelectAudioDevice(opt.value, opt.name)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = opt.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (opt.enabled) StudioColors.TextPrimary else StudioColors.TextMuted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Selected",
                                    tint = StudioColors.ReplayCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
