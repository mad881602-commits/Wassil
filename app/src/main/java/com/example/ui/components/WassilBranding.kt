package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.i18n.AppLanguage
import com.example.i18n.LocalWassilStrings
import com.example.ui.theme.WassilBlueDark
import com.example.ui.theme.WassilBlueLight
import com.example.ui.theme.WassilBluePrimary
import com.example.ui.theme.WassilSkyAccent

/**
 * Custom original WASSIL (وَصِّل) vector logo representing two devices exchanging files
 * across a direct peer-to-peer connection wave.
 */
@Composable
fun WassilLogoMark(
    size: Dp = 52.dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(WassilBluePrimary, WassilSkyAccent)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            val w = this.size.width
            val h = this.size.height
            val strokeWidth = w * 0.085f

            // Left phone outline
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(w * 0.06f, h * 0.18f),
                size = Size(w * 0.34f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
                style = Stroke(width = strokeWidth)
            )

            // Right phone outline
            drawRoundRect(
                color = Color(0xFFE0F2FE),
                topLeft = Offset(w * 0.60f, h * 0.18f),
                size = Size(w * 0.34f, h * 0.64f),
                cornerRadius = CornerRadius(w * 0.07f, w * 0.07f),
                style = Stroke(width = strokeWidth)
            )

            // Upper transfer arrow (Left -> Right)
            val topArrow = Path().apply {
                moveTo(w * 0.26f, h * 0.38f)
                lineTo(w * 0.74f, h * 0.38f)
                lineTo(w * 0.63f, h * 0.27f)
            }
            drawPath(
                path = topArrow,
                color = Color.White,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Lower transfer arrow (Right -> Left)
            val bottomArrow = Path().apply {
                moveTo(w * 0.74f, h * 0.62f)
                lineTo(w * 0.26f, h * 0.62f)
                lineTo(w * 0.37f, h * 0.73f)
            }
            drawPath(
                path = bottomArrow,
                color = Color(0xFFBAE6FD),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
fun WassilTopHeader(
    deviceName: String,
    onEditDeviceNameClick: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalWassilStrings.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WassilLogoMark(size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = strings.appNameArabic,
                        style = MaterialTheme.typography.titleLarge,
                        color = WassilBluePrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.appTagline,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Language Switcher Pill (Arabic RTL <-> English LTR)
            Surface(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleLanguage)
                    .testTag("language_toggle_button"),
                color = WassilBlueLight,
                shape = CircleShape
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Switch Language",
                        tint = WassilBluePrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (strings.language == AppLanguage.ARABIC) "EN" else "عربي",
                        style = MaterialTheme.typography.labelLarge,
                        color = WassilBlueDark,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Device Identity Bar (No account required, simple custom device name)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                .clickable(onClick = onEditDeviceNameClick)
                .testTag("device_identity_card"),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(WassilBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = WassilBluePrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = strings.deviceIdentityLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = deviceName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = strings.editDeviceName,
                        tint = WassilBluePrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = strings.editDeviceName,
                        style = MaterialTheme.typography.labelMedium,
                        color = WassilBluePrimary
                    )
                }
            }
        }
    }
}
