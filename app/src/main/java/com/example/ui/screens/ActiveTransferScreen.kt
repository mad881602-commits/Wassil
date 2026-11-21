package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.i18n.AppLanguage
import com.example.i18n.LocalWassilStrings
import com.example.model.TransferDirection
import com.example.model.TransferSessionState
import com.example.security.SecurityUtils
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.WassilBlueDark
import com.example.ui.theme.WassilBlueLight
import com.example.ui.theme.WassilBluePrimary
import com.example.ui.theme.WassilSuccessBg
import com.example.ui.theme.WassilSuccessGreen
import com.example.ui.theme.WassilWarningAmber
import com.example.ui.theme.WassilWarningBg

@Composable
fun ActiveTransferScreen(
    sessionState: TransferSessionState,
    onTogglePauseResume: () -> Unit,
    onCancelTransfer: () -> Unit,
    onRetryAndResume: () -> Unit,
    onDoneReturnHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        when (sessionState) {
            is TransferSessionState.Transferring -> onCancelTransfer()
            else -> onDoneReturnHome()
        }
    }

    val strings = LocalWassilStrings.current
    val isArabic = strings.language == AppLanguage.ARABIC

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Text(
            text = strings.activeTransferTitle,
            style = MaterialTheme.typography.headlineMedium,
            color = WassilBluePrimary,
            fontWeight = FontWeight.Bold
        )

        when (sessionState) {
            is TransferSessionState.Transferring -> {
                val p = sessionState.progress
                val animatedProgress by animateFloatAsState(
                    targetValue = (p.overallPercentage / 100f).coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 180),
                    label = "transfer_progress"
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            color = WassilBlueLight,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (sessionState.direction == TransferDirection.SENT) {
                                    "${strings.sendingToLabel}: ${sessionState.peerDeviceName}"
                                } else {
                                    "${strings.receivingFromLabel}: ${sessionState.peerDeviceName}"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = WassilBlueDark,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        Text(
                            text = "${p.overallPercentage}%",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 44.sp,
                            fontWeight = FontWeight.Bold,
                            color = WassilBluePrimary,
                            modifier = Modifier.testTag("transfer_percentage_text")
                        )

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(14.dp)
                                .clip(RoundedCornerShape(50)),
                            color = WassilBluePrimary,
                            trackColor = WassilBlueLight,
                            strokeCap = StrokeCap.Round
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(WassilBlueLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = categoryIcon(p.currentFileCategory),
                                        contentDescription = null,
                                        tint = WassilBluePrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "(${p.currentFileIndex}/${p.totalFilesCount}) ${p.currentFileName}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${SecurityUtils.formatBytes(p.currentFileBytesTransferred)} / ${SecurityUtils.formatBytes(p.currentFileTotalBytes)}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile(
                                label = strings.transferredSizeLabel,
                                value = "${SecurityUtils.formatBytes(p.overallBytesTransferred)} / ${SecurityUtils.formatBytes(p.overallTotalBytes)}",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = strings.speedLabel,
                                value = SecurityUtils.formatSpeed(p.speedBytesPerSec),
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = strings.remainingTimeLabel,
                                value = SecurityUtils.formatDurationSeconds(p.estimatedRemainingSeconds, isArabic),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (p.isVerifyingSha256) {
                            Surface(
                                color = WassilSuccessBg,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = null,
                                        tint = WassilSuccessGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = strings.verifyingIntegrity,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = WassilSuccessGreen
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onTogglePauseResume,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pause_resume_transfer_button")
                            ) {
                                Icon(
                                    imageVector = if (p.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (p.isPaused) strings.resumeTransfer else strings.pauseTransfer,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Button(
                                onClick = onCancelTransfer,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("cancel_active_transfer_button")
                            ) {
                                Icon(Icons.Default.Cancel, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(strings.cancelTransfer, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            is TransferSessionState.InterruptedOrCancelled -> {
                val p = sessionState.progress
                val stoppedPercent = p?.overallPercentage ?: 0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(WassilWarningBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = WassilWarningAmber,
                                modifier = Modifier.size(38.dp)
                            )
                        }

                        Text(
                            text = strings.transferStoppedTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = WassilWarningAmber,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        // Show exact saved progress point
                        Text(
                            text = "$stoppedPercent%",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.Bold,
                            color = WassilBluePrimary
                        )

                        LinearProgressIndicator(
                            progress = { (stoppedPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(50)),
                            color = WassilWarningAmber,
                            trackColor = WassilBlueLight,
                            strokeCap = StrokeCap.Round
                        )

                        if (p != null) {
                            Text(
                                text = "${strings.transferredSizeLabel}: ${SecurityUtils.formatBytes(p.overallBytesTransferred)} / ${SecurityUtils.formatBytes(p.overallTotalBytes)}",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelLarge,
                                color = WassilBlueDark,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = if (isArabic) sessionState.messageAr else sessionState.messageEn,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // Prominent Retry & Resume from Stopped Point Button
                        Button(
                            onClick = onRetryAndResume,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WassilSuccessGreen),
                            contentPadding = PaddingValues(vertical = 15.dp, horizontal = 20.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("retry_resume_transfer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = strings.retryAndResumeButton,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = onDoneReturnHome,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("abandon_transfer_button")
                        ) {
                            Text(
                                text = strings.endSessionAndExitButton,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            is TransferSessionState.Completed -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(WassilSuccessBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = WassilSuccessGreen,
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Text(
                            text = strings.transferSuccessTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = WassilSuccessGreen,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = strings.transferSuccessSubtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Surface(
                            color = WassilSuccessBg,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = WassilSuccessGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.verifiedSafeBadge,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = WassilSuccessGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile(
                                label = strings.selectedFilesCount,
                                value = "${sessionState.filesCount}",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = strings.totalTransferSize,
                                value = SecurityUtils.formatBytes(sessionState.totalBytes),
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                label = strings.speedLabel,
                                value = SecurityUtils.formatSpeed(sessionState.averageSpeedBps),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = onDoneReturnHome,
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = WassilBluePrimary),
                            contentPadding = PaddingValues(vertical = 14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("transfer_done_button")
                        ) {
                            Text(
                                text = strings.doneButton,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            is TransferSessionState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = if (isArabic) sessionState.messageAr else sessionState.messageEn,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = onRetryAndResume,
                            colors = ButtonDefaults.buttonColors(containerColor = WassilSuccessGreen),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.retryAndResumeButton, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onDoneReturnHome,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(strings.endSessionAndExitButton)
                        }
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelLarge,
                color = WassilBlueDark,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
