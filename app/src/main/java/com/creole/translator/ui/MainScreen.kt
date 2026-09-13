package com.creole.translator.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creole.translator.model.FeedbackRating
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
    val inputMode by viewModel.inputMode.collectAsState()
    val typedInput by viewModel.typedInput.collectAsState()
    val currentSampleId by viewModel.currentSampleId.collectAsState()
    val feedbackGiven by viewModel.feedbackGiven.collectAsState()
    val autoDetectOverrode by viewModel.autoDetectOverrode.collectAsState()
    var showCommentDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppHeader(
                historyCount = historyEntries.size,
                onHistoryClick = { viewModel.showHistory() },
                onPhrasebookClick = { viewModel.showPhrasebook() },
                onSettingsClick = { viewModel.showSettings() }
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

            // Auto-detect flipped the direction for this translation — say so, offer Undo.
            autoDetectOverrode?.let { manual ->
                AutoDetectChip(
                    detected = direction,
                    onUndo = { viewModel.undoAutoDetect() },
                    enabled = !isProcessing
                )
            }

            // Voice / Text input mode picker
            InputModePicker(
                inputMode = inputMode,
                enabled = !isRecording && !isProcessing,
                onModeChange = { viewModel.setInputMode(it) }
            )

            // Input control — record button or text field
            if (inputMode == InputMode.VOICE) {
                RecordButton(
                    isRecording = isRecording,
                    isProcessing = isProcessing,
                    onClick = { viewModel.toggleRecording() }
                )
            } else {
                TextInputSection(
                    text = typedInput,
                    direction = direction,
                    isProcessing = isProcessing,
                    onTextChange = { viewModel.setTypedInput(it) },
                    onTranslate = { viewModel.submitTypedText() }
                )
            }

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
                    isSpeaking = isSpeaking,
                    feedback = if (currentSampleId != null && !isProcessing) FeedbackState(
                        given = feedbackGiven,
                        onUp = { viewModel.rateTranslation(FeedbackRating.UP) },
                        onDown = { showCommentDialog = true }
                    ) else null
                )
            }

            if (showCommentDialog) {
                FeedbackCommentDialog(
                    onSend = { comment ->
                        showCommentDialog = false
                        viewModel.rateTranslation(FeedbackRating.DOWN, comment)
                    },
                    onDismiss = { showCommentDialog = false }
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AppHeader(
    historyCount: Int,
    onHistoryClick: () -> Unit,
    onPhrasebookClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(listOf(BrandPurple, BrandPink))
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            IconButton(onClick = onSettingsClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Voice Settings",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onPhrasebookClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.MenuBook,
                    contentDescription = "Phrasebook",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uD83C\uDFA4 Creole Translator",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = "Haitian Creole \u2194 English",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1
            )
        }

        BadgedBox(
            badge = {
                if (historyCount > 0) {
                    Badge { Text(historyCount.coerceAtMost(99).toString()) }
                }
            }
        ) {
            IconButton(onClick = onHistoryClick, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.History,
                    contentDescription = "Translation History",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputModePicker(
    inputMode: InputMode,
    enabled: Boolean,
    onModeChange: (InputMode) -> Unit
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = inputMode == InputMode.VOICE,
            onClick = { onModeChange(InputMode.VOICE) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            enabled = enabled,
            icon = {}
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Voice")
            }
        }
        SegmentedButton(
            selected = inputMode == InputMode.TEXT,
            onClick = { onModeChange(InputMode.TEXT) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            enabled = enabled,
            icon = {}
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Type")
            }
        }
    }
}

@Composable
private fun TextInputSection(
    text: String,
    direction: TranslationDirection,
    isProcessing: Boolean,
    onTextChange: (String) -> Unit,
    onTranslate: () -> Unit
) {
    val placeholder = if (direction == TranslationDirection.CREOLE_TO_ENGLISH)
        "Enter Haitian Creole text…"
    else
        "Enter English text…"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(placeholder) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp, max = 180.dp),
            enabled = !isProcessing,
            maxLines = 6,
            shape = RoundedCornerShape(12.dp)
        )

        Button(
            onClick = onTranslate,
            enabled = text.trim().isNotEmpty() && !isProcessing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandPurple)
        ) {
            Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Translate", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AutoDetectChip(detected: TranslationDirection, onUndo: () -> Unit, enabled: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(8.dp))
            Text(
                "Auto-detected ${detected.sourceLabel} → ${detected.targetLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUndo, enabled = enabled) { Text("Undo") }
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

/** Thumbs state for a result card; null hides the thumbs entirely. */
private data class FeedbackState(
    val given: FeedbackRating?,
    val onUp: () -> Unit,
    val onDown: () -> Unit
)

@Composable
private fun FeedbackCommentDialog(onSend: (String?) -> Unit, onDismiss: () -> Unit) {
    var comment by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sa a pa bon? / Not right?") },
        text = {
            Column {
                Text(
                    "Di nou sa ki mal (opsyonèl). / Tell us what was wrong (optional).",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = comment,
                    onValueChange = { if (it.length <= 200) comment = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(comment.trim().ifBlank { null }) }) { Text("Send") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ResultCard(
    label: String,
    flag: String,
    text: String?,
    onSpeak: () -> Unit,
    isSpeaking: Boolean,
    feedback: FeedbackState? = null
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
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            if (text == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                Text(
                    text = text,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth()
                )
                if (feedback != null && text.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val given = feedback.given
                        Text(
                            text = when (given) {
                                FeedbackRating.UP -> "Mèsi! / Thanks!"
                                FeedbackRating.DOWN -> "Mèsi, n ap gade l. / Thanks, we'll review it."
                                null -> "Bon? / Good?"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        IconButton(onClick = feedback.onUp, enabled = given == null) {
                            Icon(
                                Icons.Default.ThumbUp,
                                contentDescription = "Good translation",
                                tint = if (given == FeedbackRating.UP) BrandPurple
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = if (given == null) 0.6f else 0.25f)
                            )
                        }
                        IconButton(onClick = feedback.onDown, enabled = given == null) {
                            Icon(
                                Icons.Default.ThumbDown,
                                contentDescription = "Bad translation",
                                tint = if (given == FeedbackRating.DOWN) BrandPink
                                       else MaterialTheme.colorScheme.onSurface.copy(alpha = if (given == null) 0.6f else 0.25f)
                            )
                        }
                    }
                }
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
