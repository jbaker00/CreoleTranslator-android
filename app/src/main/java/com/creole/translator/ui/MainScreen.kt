package com.creole.translator.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creole.translator.model.TranslationDirection
import com.creole.translator.ui.theme.BrandPink
import com.creole.translator.ui.theme.BrandPurple
import com.creole.translator.ui.theme.RecordingRed

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val isRecording by viewModel.isRecording.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val direction by viewModel.direction.collectAsState()
    val transcription by viewModel.transcription.collectAsState()
    val translation by viewModel.translation.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val ttsError by viewModel.ttsError.collectAsState()
    val historyEntries by viewModel.historyEntries.collectAsState()

    Scaffold(
        topBar = {
            AppHeader(
                historyCount = historyEntries.size,
                onHistoryClick = { viewModel.showHistory() }
            )
        },
        bottomBar = { BannerAd() },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Direction indicator
            DirectionIndicator(direction)

            // Record button
            RecordButton(
                isRecording = isRecording,
                isProcessing = isProcessing,
                onClick = { viewModel.toggleRecording() }
            )

            // Status / error messages
            statusMessage?.let { msg ->
                StatusCard(message = msg, isError = false) {
                    viewModel.clearStatus()
                }
            }
            errorMessage?.let { msg ->
                StatusCard(message = msg, isError = true) {
                    viewModel.clearError()
                }
            }
            ttsError?.let { msg ->
                StatusCard(message = "TTS: $msg", isError = true) {
                    viewModel.clearError()
                }
            }

            // Processing indicator
            if (isProcessing) {
                ProcessingCard()
            }

            // Source result card
            if (transcription.isNotBlank() || isProcessing) {
                ResultCard(
                    label = direction.sourceLabel,
                    flag = direction.sourceFlag,
                    text = if (isProcessing && transcription.isBlank()) null else transcription,
                    onSpeak = { viewModel.speakText(transcription, direction.sourceLanguage) },
                    isSpeaking = isSpeaking
                )
            }

            // Switch direction button (always visible)
            SwitchDirectionButton(
                onClick = { viewModel.switchDirection() },
                enabled = !isRecording && !isProcessing
            )

            // Target result card
            if (translation.isNotBlank() || (isProcessing && transcription.isNotBlank())) {
                ResultCard(
                    label = direction.targetLabel,
                    flag = direction.targetFlag,
                    text = if (isProcessing && translation.isBlank()) null else translation,
                    onSpeak = { viewModel.speakText(translation, direction.targetLanguage) },
                    isSpeaking = isSpeaking
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppHeader(historyCount: Int, onHistoryClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(BrandPurple, BrandPink))
            )
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uD83C\uDFA4 Creole Translator",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Haitian Creole \u2194 English",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        // History button top-right
        BadgedBox(
            badge = {
                if (historyCount > 0) {
                    Badge { Text(historyCount.coerceAtMost(99).toString()) }
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            IconButton(onClick = onHistoryClick) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "Translation History",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun DirectionIndicator(direction: TranslationDirection) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${direction.sourceFlag} ${direction.sourceLabel}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "  \u2192  ",
                fontSize = 18.sp,
                color = BrandPurple
            )
            Text(
                text = "${direction.targetFlag} ${direction.targetLabel}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    isProcessing: Boolean,
    onClick: () -> Unit
) {
    val buttonColor = when {
        isRecording -> RecordingRed
        isProcessing -> MaterialTheme.colorScheme.surfaceVariant
        else -> BrandPurple
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = !isProcessing,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = buttonColor),
            modifier = Modifier.size(90.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                    modifier = Modifier.size(40.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                isProcessing -> "Processing..."
                isRecording -> "Tap to stop"
                else -> "Tap to record"
            },
            fontSize = 13.sp,
            color = if (isRecording) RecordingRed else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SwitchDirectionButton(onClick: () -> Unit, enabled: Boolean) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(0.7f)
    ) {
        Icon(
            Icons.Default.SwapVert,
            contentDescription = "Switch Direction",
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text("Switch Direction")
    }
}

@Composable
private fun ResultCard(
    label: String,
    flag: String,
    text: String?,
    onSpeak: () -> Unit,
    isSpeaking: Boolean
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$flag $label",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrandPurple
                )
                IconButton(
                    onClick = onSpeak,
                    enabled = text != null && text.isNotBlank()
                ) {
                    Icon(
                        if (isSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Speak",
                        tint = if (text != null && text.isNotBlank()) BrandPurple
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            if (text == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ProcessingCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BrandPurple.copy(alpha = 0.08f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                "Transcribing and translating...",
                fontSize = 14.sp,
                color = BrandPurple
            )
        }
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isError) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message,
                fontSize = 13.sp,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(16.dp))
            }
        }
    }
}
