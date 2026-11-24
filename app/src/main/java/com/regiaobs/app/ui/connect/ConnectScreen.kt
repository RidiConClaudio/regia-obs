package com.regiaobs.app.ui.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.regiaobs.app.obs.ConnectionState
import com.regiaobs.app.obs.DiscoveredObsHost
import com.regiaobs.app.obs.LanDiscovery
import com.regiaobs.app.obs.LanScanProgress
import com.regiaobs.app.obs.ObsServerProfile
import com.regiaobs.app.obs.ObsUriParser
import com.regiaobs.app.ui.theme.StudioColors
import com.regiaobs.app.ui.theme.tabular
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    profiles: List<ObsServerProfile>,
    connectionState: ConnectionState,
    onConnect: (ObsServerProfile) -> Unit,
    onSaveProfile: (ObsServerProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onStartDemo: () -> Unit,
    onCancelReconnect: () -> Unit
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var nameInput by remember { mutableStateOf("Studio PC") }
    var hostInput by remember { mutableStateOf(profiles.firstOrNull()?.host ?: "192.168.1.") }
    var portInput by remember { mutableStateOf(profiles.firstOrNull()?.port?.toString() ?: "4455") }
    var passwordInput by remember { mutableStateOf(profiles.firstOrNull()?.password ?: "") }
    var useTls by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    var showQrScanner by remember { mutableStateOf(false) }
    var showLanScanner by remember { mutableStateOf(false) }

    val isBusy = connectionState is ConnectionState.Connecting ||
        connectionState is ConnectionState.Authenticating ||
        connectionState is ConnectionState.Reconnecting

    fun loadIntoForm(p: ObsServerProfile) {
        editingId = p.id
        nameInput = p.name
        hostInput = p.host
        portInput = p.port.toString()
        passwordInput = p.password
        useTls = p.useTls
        formError = null
    }

    fun buildProfileFromForm(): ObsServerProfile? {
        val rawHost = hostInput.trim()
        if (rawHost.startsWith("obsws://", ignoreCase = true) ||
            rawHost.startsWith("ws://", ignoreCase = true) ||
            rawHost.startsWith("wss://", ignoreCase = true)
        ) {
            val parsed = ObsUriParser.parse(rawHost)
            if (parsed != null) {
                return parsed.copy(
                    id = editingId ?: UUID.randomUUID().toString(),
                    name = nameInput.trim().ifBlank { parsed.name },
                    password = passwordInput.ifBlank { parsed.password }
                )
            }
        }
        val cleanHost = rawHost.removeSuffix(".")
        if (cleanHost.isBlank()) {
            formError = "Enter the IP address of the PC running OBS."
            return null
        }
        val port = portInput.trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            formError = "Invalid port (default is 4455)."
            return null
        }
        formError = null
        return ObsServerProfile(
            id = editingId ?: UUID.randomUUID().toString(),
            name = nameInput.trim().ifBlank { "OBS ($cleanHost)" },
            host = cleanHost,
            port = port,
            password = passwordInput,
            useTls = useTls
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioColors.Canvas),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "RegiaOBS",
                    style = MaterialTheme.typography.titleLarge,
                    color = StudioColors.TextPrimary
                )
                Text(
                    text = "Control OBS Studio from Android over local Wi-Fi or VPN",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextSecondary
                )
            }
        }

        if (connectionState is ConnectionState.Error) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioColors.TallyRedSoft,
                    border = BorderStroke(1.dp, StudioColors.TallyRed)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (connectionState.isAuthError) "Wrong password" else "Connection failed",
                            style = MaterialTheme.typography.titleSmall,
                            color = StudioColors.TallyRed
                        )
                        Text(
                            text = connectionState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextPrimary
                        )
                    }
                }
            }
        }

        if (isBusy) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioColors.Panel,
                    border = BorderStroke(1.dp, StudioColors.ReplayCyan)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = StudioColors.ReplayCyan
                            )
                            Text(
                                text = when (connectionState) {
                                    is ConnectionState.Connecting -> "Connecting to PC..."
                                    is ConnectionState.Authenticating -> "Authenticating with OBS..."
                                    is ConnectionState.Reconnecting -> "Reconnecting (attempt ${connectionState.attempt})..."
                                    else -> "Connecting..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioColors.TextPrimary
                            )
                        }
                        TextButton(onClick = onCancelReconnect) {
                            Text("Cancel", color = StudioColors.TallyRed)
                        }
                    }
                }
            }
        }

        // Quick Discovery Actions: QR & LAN Scan
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showQrScanner = true },
                    shape = RoundedCornerShape(14.dp),
                    color = StudioColors.Panel,
                    border = BorderStroke(1.dp, StudioColors.ReplayCyan.copy(alpha = 0.55f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.QrCodeScanner,
                            contentDescription = null,
                            tint = StudioColors.ReplayCyan
                        )
                        Text(
                            text = "Scan OBS QR",
                            style = MaterialTheme.typography.titleSmall,
                            color = StudioColors.TextPrimary
                        )
                        Text(
                            text = "Instant IP, port & password",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextSecondary
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showLanScanner = true },
                    shape = RoundedCornerShape(14.dp),
                    color = StudioColors.Panel,
                    border = BorderStroke(1.dp, StudioColors.Border)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Radar,
                            contentDescription = null,
                            tint = StudioColors.TallyGreen
                        )
                        Text(
                            text = "Scan Wi-Fi",
                            style = MaterialTheme.typography.titleSmall,
                            color = StudioColors.TextPrimary
                        )
                        Text(
                            text = "Find OBS on local network",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextSecondary
                        )
                    }
                }
            }
        }

        // Saved Profiles
        if (profiles.isNotEmpty()) {
            item {
                Text(
                    text = "Saved PCs",
                    style = MaterialTheme.typography.titleSmall,
                    color = StudioColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            items(profiles, key = { it.id }) { profile ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = StudioColors.Panel,
                    border = BorderStroke(1.dp, StudioColors.Border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onConnect(profile) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(StudioColors.Elevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Computer,
                                    contentDescription = null,
                                    tint = StudioColors.ReplayCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StudioColors.TextPrimary
                                )
                                Text(
                                    text = "${profile.host}:${profile.port}",
                                    style = MaterialTheme.typography.bodySmall.tabular(),
                                    color = StudioColors.TextSecondary
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = { loadIntoForm(profile) }) {
                                Icon(
                                    imageVector = Icons.Rounded.Edit,
                                    contentDescription = "Edit",
                                    tint = StudioColors.TextSecondary
                                )
                            }
                            IconButton(onClick = { onDeleteProfile(profile.id) }) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete",
                                    tint = StudioColors.TextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // Manual Connection Form
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioColors.Panel,
                border = BorderStroke(1.dp, StudioColors.Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (editingId != null) "Edit PC" else "Manual connection",
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioColors.TextPrimary
                    )

                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Profile name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = hostInput,
                            onValueChange = { hostInput = it },
                            label = { Text("Host IP or obsws:// URI") },
                            placeholder = { Text("192.168.1.50") },
                            singleLine = true,
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Port") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("WebSocket password (optional)") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Show password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Use secure WSS (TLS)",
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TextSecondary
                        )
                        Switch(checked = useTls, onCheckedChange = { useTls = it })
                    }

                    if (formError != null) {
                        Text(
                            text = formError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioColors.TallyRed
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                buildProfileFromForm()?.let { profile ->
                                    onSaveProfile(profile)
                                    editingId = null
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }
                        Button(
                            onClick = {
                                buildProfileFromForm()?.let { profile ->
                                    editingId = null
                                    onConnect(profile)
                                }
                            },
                            enabled = !isBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioColors.ReplayCyan,
                                contentColor = StudioColors.Canvas
                            ),
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Text("Connect to OBS", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Demo Mode + Setup Guide
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioColors.Panel,
                border = BorderStroke(1.dp, StudioColors.Border)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "How to enable on OBS",
                        style = MaterialTheme.typography.titleSmall,
                        color = StudioColors.TextPrimary
                    )
                    Text(
                        text = "On your PC open OBS Studio → Tools → WebSocket Server Settings → check \"Enable WebSocket server\" and click \"Show Connect Info\" for the QR Code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StudioColors.TextSecondary
                    )
                    OutlinedButton(
                        onClick = onStartDemo,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Try Demo Mode (Simulator)")
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        QrScannerDialog(
            onProfileScanned = { scanned ->
                showQrScanner = false
                loadIntoForm(scanned)
                onConnect(scanned)
            },
            onDismiss = { showQrScanner = false }
        )
    }

    if (showLanScanner) {
        LanScannerSheet(
            defaultPort = portInput.toIntOrNull() ?: 4455,
            onHostSelected = { host ->
                showLanScanner = false
                hostInput = host.host
                portInput = host.port.toString()
                nameInput = "OBS (${host.host})"
                editingId = null
            },
            onDismiss = { showLanScanner = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanScannerSheet(
    defaultPort: Int,
    onHostSelected: (DiscoveredObsHost) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var scanProgress by remember { mutableStateOf<LanScanProgress?>(null) }
    var scanJob by remember { mutableStateOf<Job?>(null) }

    fun startScan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            LanDiscovery.scanLocalSubnet(defaultPort).collect { prog ->
                scanProgress = prog
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        startScan()
    }

    ModalBottomSheet(
        onDismissRequest = {
            scanJob?.cancel()
            onDismiss()
        },
        containerColor = StudioColors.Panel
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WifiFind,
                        contentDescription = null,
                        tint = StudioColors.TallyGreen
                    )
                    Text(
                        text = "Scanning Wi-Fi network (port $defaultPort)",
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioColors.TextPrimary
                    )
                }
                TextButton(onClick = { startScan() }) {
                    Text("Rescan")
                }
            }

            val prog = scanProgress
            if (prog != null && !prog.isFinished && prog.total > 0) {
                LinearProgressIndicator(
                    progress = { prog.scanned.toFloat() / prog.total.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                    color = StudioColors.ReplayCyan
                )
                Text(
                    text = "Scanned ${prog.scanned} of ${prog.total} addresses...",
                    style = MaterialTheme.typography.bodySmall.tabular(),
                    color = StudioColors.TextSecondary
                )
            }

            val found = prog?.found.orEmpty()
            if (found.isEmpty() && prog?.isFinished == true) {
                Text(
                    text = "No OBS server found on port $defaultPort. Make sure OBS is running, WebSocket is enabled, and Windows Firewall allows port $defaultPort.",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioColors.TextSecondary
                )
            } else {
                found.forEach { host ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = StudioColors.Elevated,
                        border = BorderStroke(1.dp, StudioColors.TallyGreen.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scanJob?.cancel()
                                onHostSelected(host)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = host.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = StudioColors.TextPrimary
                                )
                                Text(
                                    text = "${host.host}:${host.port}",
                                    style = MaterialTheme.typography.bodySmall.tabular(),
                                    color = StudioColors.TallyGreen
                                )
                            }
                            Text(
                                text = "Use",
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioColors.ReplayCyan
                            )
                        }
                    }
                }
            }
        }
    }
}
