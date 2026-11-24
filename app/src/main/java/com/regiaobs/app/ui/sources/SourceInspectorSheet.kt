package com.regiaobs.app.ui.sources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.regiaobs.app.obs.ObsFilter
import com.regiaobs.app.obs.ObsSceneItem
import com.regiaobs.app.obs.SceneItemTransform
import com.regiaobs.app.ui.theme.BroadcastHaptics
import com.regiaobs.app.ui.theme.StudioColors
import com.regiaobs.app.ui.theme.tabular
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceInspectorSheet(
    sceneName: String,
    item: ObsSceneItem,
    filters: List<ObsFilter>,
    haptics: BroadcastHaptics,
    onFetchSettings: suspend (String) -> Result<JsonObject>,
    onSaveSettings: (String, JsonObject) -> Unit,
    onRefreshBrowser: (String) -> Unit,
    onToggleFilter: (String, String, Boolean) -> Unit,
    onUpdateTransform: (String, Int, JsonObject) -> Unit,
    onFitToScreen: (String, Int) -> Unit,
    onResetTransform: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }

    var loadingSettings by remember { mutableStateOf(false) }
    var inputKind by remember { mutableStateOf(item.inputKind.orEmpty()) }
    var rawSettings by remember { mutableStateOf<JsonObject?>(null) }

    LaunchedEffect(item.sourceName) {
        if (!item.isGroup) {
            loadingSettings = true
            val res = onFetchSettings(item.sourceName)
            loadingSettings = false
            res.getOrNull()?.let { data ->
                inputKind = (data["inputKind"] as? JsonPrimitive)?.content ?: inputKind
                rawSettings = runCatching { data["inputSettings"]?.jsonObject }.getOrNull()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioColors.Panel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.sourceName,
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioColors.TextPrimary
                    )
                    Text(
                        text = "$sceneName  •  ${inputKind.ifBlank { if (item.isGroup) "Group" else "Source" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, "Close", tint = StudioColors.TextSecondary)
                }
            }

            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = StudioColors.Panel,
                contentColor = StudioColors.ReplayCyan
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Settings") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Filters (${filters.size})") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Transform") })
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedTab) {
                    0 -> SettingsInspectorTab(
                        sourceName = item.sourceName,
                        inputKind = inputKind,
                        loading = loadingSettings,
                        settings = rawSettings,
                        haptics = haptics,
                        onSave = { updated ->
                            rawSettings = updated
                            onSaveSettings(item.sourceName, updated)
                        },
                        onRefreshBrowser = { onRefreshBrowser(item.sourceName) }
                    )

                    1 -> FiltersInspectorTab(
                        sourceName = item.sourceName,
                        filters = filters,
                        haptics = haptics,
                        onToggle = { filterName, enabled ->
                            onToggleFilter(item.sourceName, filterName, enabled)
                        }
                    )

                    2 -> TransformInspectorTab(
                        transform = item.transform ?: SceneItemTransform(),
                        haptics = haptics,
                        onPatch = { patch -> onUpdateTransform(sceneName, item.sceneItemId, patch) },
                        onFit = { onFitToScreen(sceneName, item.sceneItemId) },
                        onReset = { onResetTransform(sceneName, item.sceneItemId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsInspectorTab(
    sourceName: String,
    inputKind: String,
    loading: Boolean,
    settings: JsonObject?,
    haptics: BroadcastHaptics,
    onSave: (JsonObject) -> Unit,
    onRefreshBrowser: () -> Unit
) {
    if (loading) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = StudioColors.ReplayCyan)
        }
        return
    }

    if (settings == null || settings.isEmpty()) {
        Text(
            text = "No editable properties returned by OBS for '$sourceName'.",
            style = MaterialTheme.typography.bodySmall,
            color = StudioColors.TextSecondary
        )
        return
    }

    val isBrowser = inputKind.contains("browser", ignoreCase = true)
    if (isBrowser) {
        OutlinedButton(
            onClick = {
                haptics.tap()
                onRefreshBrowser()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.Cached, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Refresh Browser Page Cache")
        }
    }

    // Editable primitive fields (text, url, bools, numbers)
    val editableKeys = remember(settings) {
        settings.entries.filter { it.value is JsonPrimitive }.map { it.key }
    }
    val stringValues = remember(settings) {
        mutableStateOf(
            editableKeys.associateWith { k ->
                (settings[k] as? JsonPrimitive)?.content.orEmpty()
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        editableKeys.forEach { key ->
            val orig = settings[key] as? JsonPrimitive ?: return@forEach
            val curVal = stringValues.value[key].orEmpty()
            val asBool = orig.booleanOrNull
            if (asBool != null) {
                val checked = curVal.equals("true", ignoreCase = true)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(key, style = MaterialTheme.typography.bodyMedium, color = StudioColors.TextPrimary)
                    Switch(
                        checked = checked,
                        onCheckedChange = { next ->
                            stringValues.value = stringValues.value + (key to next.toString())
                        }
                    )
                }
            } else {
                val isMultiline = key.equals("text", ignoreCase = true)
                OutlinedTextField(
                    value = curVal,
                    onValueChange = { next ->
                        stringValues.value = stringValues.value + (key to next)
                    },
                    label = { Text(key) },
                    singleLine = !isMultiline,
                    minLines = if (isMultiline) 2 else 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Button(
            onClick = {
                haptics.strong()
                val patch = buildJsonObject {
                    editableKeys.forEach { key ->
                        val orig = settings[key] as? JsonPrimitive ?: return@forEach
                        val raw = stringValues.value[key].orEmpty()
                        when {
                            orig.booleanOrNull != null -> put(key, raw.equals("true", ignoreCase = true))
                            orig.isString -> put(key, raw)
                            raw.toLongOrNull() != null -> put(key, raw.toLong())
                            raw.toDoubleOrNull() != null -> put(key, raw.toDouble())
                            else -> put(key, raw)
                        }
                    }
                }
                onSave(patch)
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = StudioColors.ReplayCyan,
                contentColor = StudioColors.Canvas
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Apply Settings to OBS", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FiltersInspectorTab(
    sourceName: String,
    filters: List<ObsFilter>,
    haptics: BroadcastHaptics,
    onToggle: (String, Boolean) -> Unit
) {
    if (filters.isEmpty()) {
        Text(
            text = "No filters applied to '$sourceName'.",
            style = MaterialTheme.typography.bodySmall,
            color = StudioColors.TextSecondary
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        filters.forEach { filter ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StudioColors.Elevated,
                border = BorderStroke(1.dp, StudioColors.Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(filter.name, style = MaterialTheme.typography.titleSmall, color = StudioColors.TextPrimary)
                        if (filter.kind.isNotBlank()) {
                            Text(filter.kind, style = MaterialTheme.typography.labelSmall, color = StudioColors.TextMuted)
                        }
                    }
                    Switch(
                        checked = filter.enabled,
                        onCheckedChange = { next ->
                            haptics.tap()
                            onToggle(filter.name, next)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransformInspectorTab(
    transform: SceneItemTransform,
    haptics: BroadcastHaptics,
    onPatch: (JsonObject) -> Unit,
    onFit: () -> Unit,
    onReset: () -> Unit
) {
    var posX by remember(transform) { mutableFloatStateOf(transform.positionX.toFloat()) }
    var posY by remember(transform) { mutableFloatStateOf(transform.positionY.toFloat()) }
    var scale by remember(transform) { mutableFloatStateOf(transform.scaleX.toFloat().coerceIn(0.05f, 3f)) }
    var rotation by remember(transform) { mutableFloatStateOf(transform.rotation.toFloat().coerceIn(-180f, 180f)) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = String.format(
                Locale.US,
                "Pos: %.0f, %.0f px  •  Scale: %.2fx  •  Rot: %.0f°",
                posX,
                posY,
                scale,
                rotation
            ),
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = StudioColors.ReplayCyan
        )

        // Directional Nudge Pad
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedButton(
                    onClick = {
                        haptics.tap()
                        posY -= 20f
                        onPatch(buildJsonObject { put("positionY", posY.toDouble()) })
                    },
                    contentPadding = PaddingValues(10.dp)
                ) { Icon(Icons.Rounded.KeyboardArrowUp, "Up") }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            posX -= 20f
                            onPatch(buildJsonObject { put("positionX", posX.toDouble()) })
                        },
                        contentPadding = PaddingValues(10.dp)
                    ) { Icon(Icons.Rounded.KeyboardArrowLeft, "Left") }

                    OutlinedButton(
                        onClick = {
                            haptics.tap()
                            posX += 20f
                            onPatch(buildJsonObject { put("positionX", posX.toDouble()) })
                        },
                        contentPadding = PaddingValues(10.dp)
                    ) { Icon(Icons.Rounded.KeyboardArrowRight, "Right") }
                }

                OutlinedButton(
                    onClick = {
                        haptics.tap()
                        posY += 20f
                        onPatch(buildJsonObject { put("positionY", posY.toDouble()) })
                    },
                    contentPadding = PaddingValues(10.dp)
                ) { Icon(Icons.Rounded.KeyboardArrowDown, "Down") }
            }
        }

        // Scale Slider
        Text("Scale", style = MaterialTheme.typography.labelMedium, color = StudioColors.TextSecondary)
        Slider(
            value = scale,
            onValueChange = { scale = it },
            onValueChangeFinished = {
                onPatch(buildJsonObject {
                    put("scaleX", scale.toDouble())
                    put("scaleY", scale.toDouble())
                })
            },
            valueRange = 0.1f..2.5f
        )

        // Rotation Slider
        Text("Rotation", style = MaterialTheme.typography.labelMedium, color = StudioColors.TextSecondary)
        Slider(
            value = rotation,
            onValueChange = { rotation = it },
            onValueChangeFinished = {
                onPatch(buildJsonObject { put("rotation", rotation.toDouble()) })
            },
            valueRange = -180f..180f
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    haptics.tap()
                    onFit()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.AspectRatio, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Fit to Screen")
            }
            OutlinedButton(
                onClick = {
                    haptics.tap()
                    onReset()
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reset")
            }
        }
    }
}
