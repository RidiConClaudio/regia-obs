package com.regiaobs.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.FilterBAndW
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object StudioColors {
    val Canvas = Color(0xFF0A0C10)
    val Panel = Color(0xFF12161E)
    val Elevated = Color(0xFF1A202C)
    val ElevatedHover = Color(0xFF222A39)
    val Border = Color(0xFF262F40)
    val BorderStrong = Color(0xFF39465E)

    val TallyRed = Color(0xFFEF3B3B)
    val TallyRedSoft = Color(0x2BEF3B3B)
    val TallyGreen = Color(0xFF22C55E)
    val TallyGreenSoft = Color(0x2822C55E)
    val RecAmber = Color(0xFFF59E0B)
    val RecAmberSoft = Color(0x28F59E0B)
    val ReplayCyan = Color(0xFF38BDF8)
    val ReplayCyanSoft = Color(0x2838BDF8)
    val StudioPurple = Color(0xFFA78BFA)
    val StudioPurpleSoft = Color(0x28A78BFA)

    val TextPrimary = Color(0xFFF3F6FA)
    val TextSecondary = Color(0xFF93A0B8)
    val TextMuted = Color(0xFF647087)
}

private val StudioColorScheme = darkColorScheme(
    primary = StudioColors.ReplayCyan,
    onPrimary = Color(0xFF04131E),
    primaryContainer = Color(0xFF133348),
    onPrimaryContainer = StudioColors.TextPrimary,
    secondary = StudioColors.TallyGreen,
    onSecondary = Color(0xFF031A0B),
    secondaryContainer = Color(0xFF123620),
    onSecondaryContainer = StudioColors.TextPrimary,
    tertiary = StudioColors.RecAmber,
    onTertiary = Color(0xFF1F1202),
    error = StudioColors.TallyRed,
    onError = Color.White,
    background = StudioColors.Canvas,
    onBackground = StudioColors.TextPrimary,
    surface = StudioColors.Panel,
    onSurface = StudioColors.TextPrimary,
    surfaceVariant = StudioColors.Elevated,
    onSurfaceVariant = StudioColors.TextSecondary,
    outline = StudioColors.Border,
    outlineVariant = StudioColors.Border
)

private val StudioTypography = Typography(
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.02).sp
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleSmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium
    )
)

fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

@Composable
fun RegiaObsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StudioColorScheme,
        typography = StudioTypography,
        content = content
    )
}

class BroadcastHaptics(
    private val enabled: Boolean,
    private val haptic: HapticFeedback
) {
    fun tap() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun strong() {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }
}

@Composable
fun rememberBroadcastHaptics(enabled: Boolean): BroadcastHaptics {
    val haptic = LocalHapticFeedback.current
    return remember(enabled, haptic) { BroadcastHaptics(enabled, haptic) }
}

fun padIconForKey(key: String): ImageVector = when (key) {
    "live" -> Icons.Rounded.Cast
    "record" -> Icons.Rounded.FiberManualRecord
    "replay" -> Icons.Rounded.Replay
    "cam" -> Icons.Rounded.Videocam
    "mic" -> Icons.Rounded.Mic
    "mic_off" -> Icons.Rounded.MicOff
    "music" -> Icons.Rounded.GraphicEq
    "scene" -> Icons.Rounded.Tv
    "transition" -> Icons.Rounded.SwapHoriz
    "studio" -> Icons.Rounded.ViewQuilt
    "play" -> Icons.Rounded.PlayArrow
    "pause" -> Icons.Rounded.Pause
    "refresh" -> Icons.Rounded.Cached
    "text" -> Icons.Rounded.TextFields
    "filter" -> Icons.Rounded.FilterBAndW
    "eye" -> Icons.Rounded.Visibility
    "sparkles" -> Icons.Rounded.AutoAwesome
    "timer" -> Icons.Rounded.Timer
    "settings" -> Icons.Rounded.Settings
    "radio" -> Icons.Rounded.RadioButtonChecked
    else -> Icons.Rounded.Bolt
}
