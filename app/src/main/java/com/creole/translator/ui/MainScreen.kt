package com.creole.translator.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creole.translator.data.AnalyticsManager
import com.creole.translator.data.AudioRecorder
import com.creole.translator.data.HDVoiceCredits
import com.creole.translator.model.FeedbackRating
import com.creole.translator.model.Language
import com.creole.translator.model.TranslationSource
import com.creole.translator.ui.theme.WarningOrange
import com.creole.translator.ui.theme.appPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, rewardedAd: RewardedAdManager) {
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val direction by viewModel.direction.collectAsState()
    val turns by viewModel.turns.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val ttsError by viewModel.ttsError.collectAsState()
    val historyEntries by viewModel.historyEntries.collectAsState()
    val inputMode by viewModel.inputMode.collectAsState()
    val typedInput by viewModel.typedInput.collectAsState()
    val currentSampleId by viewModel.currentSampleId.collectAsState()
    val feedbackGiven by viewModel.feedbackGiven.collectAsState()
    val suggestedPartner by viewModel.suggestedPartner.collectAsState()
    val showWhatsNew by viewModel.showWhatsNew.collectAsState()
    var showCommentDialog by remember { mutableStateOf(false) }
    // Bubble whose Listen is playing, so only that one shows a stop icon.
    var speakingCard by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(isSpeaking) { if (!isSpeaking) speakingCard = null }

    // ── HD voice plays (mirrors iOS ContentView playCard/watchAdForHDPlays) ──
    val hdPlaysLeft by viewModel.hdCredits.playsLeft.collectAsState()
    val englishProvider by viewModel.voiceSettings.englishProvider.collectAsState()
    val creoleProvider by viewModel.voiceSettings.creoleProvider.collectAsState()
    val spanishProvider by viewModel.voiceSettings.spanishProvider.collectAsState()
    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    // Card tapped while out of HD plays; played after the ad (or with the basic voice).
    var pendingPlay by remember { mutableStateOf<PendingPlay?>(null) }
    var showOutOfHDPrompt by remember { mutableStateOf(false) }
    var showHDInfo by remember { mutableStateOf(false) }
    var showHDGranted by remember { mutableStateOf(false) }
    var showNoVideo by remember { mutableStateOf(false) }
    var isWatchingAd by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { rewardedAd.preload() }

    // Read so a provider change in Settings recomposes the HD chips.
    @Suppress("UNUSED_VARIABLE") val providers = Triple(englishProvider, creoleProvider, spanishProvider)
    fun usesHDVoice(language: String) = viewModel.voiceSettings.usesHDVoice(language)

    fun playCard(card: String, text: String, language: String) {
        if (isSpeaking) {
            viewModel.stopSpeaking()
            speakingCard = null
            return
        }
        if (hdPlaysLeft == 0 && viewModel.usesHDPlay(text, language)) {
            if (isWatchingAd) return
            pendingPlay = PendingPlay(card, text, language)
            showOutOfHDPrompt = true
            AnalyticsManager.logHdVoiceOutPrompt(language)
            return
        }
        speakingCard = card
        viewModel.speakText(text, language)
    }

    fun playPendingWithBasicVoice() {
        val pending = pendingPlay ?: return
        pendingPlay = null
        AnalyticsManager.logHdVoiceBasicChosen(pending.language)
        speakingCard = pending.card
        viewModel.speakText(pending.text, pending.language, basicVoice = true)
    }

    // Plays the card that triggered the ad; the audio is the confirmation.
    fun finishHDGrant() {
        val pending = pendingPlay
        if (pending == null) {
            showHDGranted = true
            return
        }
        pendingPlay = null
        speakingCard = pending.card
        viewModel.speakText(pending.text, pending.language)
    }

    fun grantHDPlays(via: String) {
        isWatchingAd = false
        viewModel.hdCredits.grantAdReward()
        AnalyticsManager.logHdPlaysGranted(via)
        finishHDGrant()
    }

    // No video could be shown. The first time today counts as a reward;
    // after that the pending phrase plays with the basic voice instead.
    fun grantWithoutAd(via: String) {
        val credits = viewModel.hdCredits
        if (credits.canGrantWithoutAd) {
            credits.recordGrantWithoutAd()
            grantHDPlays(via)
            return
        }
        isWatchingAd = false
        val total = credits.recordBlockedGrant()
        AnalyticsManager.logHdGrantBlocked(via, total)
        pendingPlay?.let { pending ->
            pendingPlay = null
            speakingCard = pending.card
            viewModel.speakText(pending.text, pending.language, basicVoice = true)
        }
        showNoVideo = true
    }

    // Waits up to 3s for the ad to load. A no-fill or present failure grants
    // the plays anyway, but only once a day (HDVoiceCredits.NO_AD_GRANTS_PER_DAY).
    fun watchAdForHDPlays() {
        if (isWatchingAd) return
        isWatchingAd = true
        scope.launch {
            var waitedMs = 0L
            while (!rewardedAd.isReady.value && waitedMs < 3000) {
                delay(250)
                waitedMs += 250
            }
            var earned = false
            val shown = activity != null && rewardedAd.show(
                activity,
                onReward = {
                    earned = true
                    viewModel.hdCredits.grantAdReward()
                    AnalyticsManager.logHdPlaysGranted("rewarded_ad")
                },
                onDismiss = {
                    isWatchingAd = false
                    if (earned) finishHDGrant() else pendingPlay = null
                },
                onPresentFailure = { grantWithoutAd("present_failed") }
            )
            if (!shown) grantWithoutAd("no_fill")
        }
    }

    fun hdChipTapped() {
        if (hdPlaysLeft == 0) watchAdForHDPlays() else showHDInfo = true
    }

    if (showOutOfHDPrompt) {
        AlertDialog(
            onDismissRequest = {
                showOutOfHDPrompt = false
                pendingPlay = null
            },
            title = { Text("Out of HD voice plays") },
            text = {
                Text("Hear it in a natural AI voice: watch one short video for ${HDVoiceCredits.PLAYS_PER_AD} more plays. ${HDVoiceCredits.FREE_PLAYS_PER_DAY} free plays come back every day.\n\nGade yon ti videyo pou ${HDVoiceCredits.PLAYS_PER_AD} lòt lekti ak vwa HD.")
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = {
                        showOutOfHDPrompt = false
                        watchAdForHDPlays()
                    }) { Text("Watch Video — ${HDVoiceCredits.PLAYS_PER_AD} HD Plays") }
                    TextButton(onClick = {
                        showOutOfHDPrompt = false
                        playPendingWithBasicVoice()
                    }) { Text("Play Basic Voice") }
                    TextButton(onClick = {
                        showOutOfHDPrompt = false
                        pendingPlay = null
                    }) { Text("Not Now") }
                }
            }
        )
    }

    if (showHDInfo) {
        AlertDialog(
            onDismissRequest = { showHDInfo = false },
            title = { Text("HD Voice") },
            text = {
                Text("$hdPlaysLeft HD plays left. You get ${HDVoiceCredits.FREE_PLAYS_PER_DAY} free every day — watch a short video anytime for ${HDVoiceCredits.PLAYS_PER_AD} more.\n\nOu gen $hdPlaysLeft lekti HD ki rete. Gade yon ti videyo pou ${HDVoiceCredits.PLAYS_PER_AD} lòt.")
            },
            confirmButton = {
                TextButton(onClick = {
                    showHDInfo = false
                    watchAdForHDPlays()
                }) { Text("Watch Video") }
            },
            dismissButton = {
                TextButton(onClick = { showHDInfo = false }) { Text("OK") }
            }
        )
    }

    if (showHDGranted) {
        AlertDialog(
            onDismissRequest = { showHDGranted = false },
            title = { Text("HD Plays Added") },
            text = { Text("${HDVoiceCredits.PLAYS_PER_AD} more HD voice plays are ready.\n\n${HDVoiceCredits.PLAYS_PER_AD} lòt lekti HD pare.") },
            confirmButton = { TextButton(onClick = { showHDGranted = false }) { Text("OK") } }
        )
    }

    if (showNoVideo) {
        AlertDialog(
            onDismissRequest = { showNoVideo = false },
            title = { Text("No Video Available") },
            text = { Text("We couldn't load a video. The basic voice works in the meantime — check your connection and try again for ${HDVoiceCredits.PLAYS_PER_AD} HD plays.\n\nNou pa t ka chaje videyo a. Tcheke koneksyon ou epi eseye ankò.") },
            confirmButton = { TextButton(onClick = { showNoVideo = false }) { Text("OK") } }
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

    val partner = direction.partner
    val prompt = speakPrompt(partner, isRecording, isProcessing)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    fun switchToTyping() {
        viewModel.setInputMode(InputMode.TEXT)
    }
    fun send() {
        focusManager.clearFocus()
        keyboard?.hide()
        viewModel.submitTypedText()
    }

    // Spoken results are announced, as on iOS (UIAccessibility announcement).
    val lastTurn = turns.lastOrNull()
    LaunchedEffect(lastTurn?.id) {
        if (lastTurn != null && lastTurn.input == TranslationSource.VOICE) {
            @Suppress("DEPRECATION")
            view.announceForAccessibility("${lastTurn.direction.target.nativeName}: ${lastTurn.translation}")
        }
    }

    val notices: @Composable () -> Unit = {
        Notices(
            suggestedPartner = if (isProcessing) null else suggestedPartner,
            onAcceptSuggestion = { viewModel.acceptSuggestedPartner() },
            statusMessage = statusMessage,
            onDismissStatus = { viewModel.clearStatus() },
            errorMessage = errorMessage,
            ttsError = ttsError,
            onDismissError = { viewModel.clearError() }
        )
    }
    val pairChip: @Composable () -> Unit = {
        PairChip(
            partner = partner,
            enabled = !isProcessing && !isRecording,
            onChoose = { viewModel.choosePartner(it) }
        )
    }

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
                .imePadding()
        ) {
            if (turns.isEmpty() && inputMode == InputMode.VOICE) {
                // First screen: one mic and one sentence saying what to do, in both languages.
                HeroScreen(
                    pairChip = pairChip,
                    notices = notices,
                    whatsNew = if (showWhatsNew && !isRecording && !isProcessing) {
                        { WhatsNewCard(onDismiss = { viewModel.dismissWhatsNew() }) }
                    } else null,
                    mic = {
                        MicButton(
                            size = 132.dp,
                            isRecording = isRecording,
                            isProcessing = isProcessing,
                            contentDescription = "${prompt.first}. ${prompt.second}",
                            onClick = { viewModel.toggleRecording() }
                        )
                    },
                    prompt = prompt,
                    recording = if (isRecording) { { RecordingProgress(elapsedMs = recordingElapsedMs) } } else null,
                    typeLabel = "Ekri · " + UIText.pair(partner, "Type instead", "Escribir"),
                    typeEnabled = !isProcessing && !isRecording,
                    typeVisible = !isRecording,
                    onType = { switchToTyping() }
                )
            } else {
                val listState = rememberLazyListState()
                // The newest bubble is always brought into view.
                LaunchedEffect(turns.size, isProcessing, inputMode) {
                    val count = listState.layoutInfo.totalItemsCount
                    if (count > 0) listState.animateScrollToItem(count - 1)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item { pairChip() }
                    items(turns, key = { it.id }) { turn ->
                        val latest = turn.id == turns.last().id
                        val target = turn.direction.target
                        Bubble(
                            turn = turn,
                            latest = latest,
                            speaking = isSpeaking && speakingCard == turn.id,
                            onListen = { playCard(turn.id, turn.translation, target.code) },
                            hdPlaysLeft = if (usesHDVoice(target.code)) hdPlaysLeft else null,
                            onHDChip = { hdChipTapped() },
                            feedback = if (latest && turn.sampleId != null && turn.sampleId == currentSampleId) FeedbackState(
                                given = feedbackGiven,
                                onUp = { viewModel.rateTranslation(FeedbackRating.UP) },
                                onDown = { showCommentDialog = true }
                            ) else null,
                            onOtherWay = { viewModel.translateLastOtherWay() },
                            otherWayEnabled = !isProcessing,
                            partner = partner
                        )
                    }
                    if (isProcessing) {
                        item {
                            Row(
                                modifier = Modifier.heightIn(min = 44.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                                    color = appPalette.secondaryText
                                )
                                Text("${prompt.first} · ${prompt.second}", fontSize = 15.sp, color = appPalette.secondaryText)
                            }
                        }
                    }
                    item { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { notices() } }
                }
                if (inputMode == InputMode.VOICE) {
                    VoiceDock(
                        isRecording = isRecording,
                        isProcessing = isProcessing,
                        recordingElapsedMs = recordingElapsedMs,
                        prompt = prompt,
                        partner = partner,
                        onMic = { viewModel.toggleRecording() },
                        onType = { switchToTyping() }
                    )
                } else {
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    TypingDock(
                        text = typedInput,
                        partner = partner,
                        isProcessing = isProcessing,
                        focusRequester = focusRequester,
                        onTextChange = { viewModel.setTypedInput(it) },
                        onSend = { send() },
                        onSpeak = {
                            focusManager.clearFocus()
                            keyboard?.hide()
                            viewModel.setInputMode(InputMode.VOICE)
                        }
                    )
                }
            }
        }
    }
}

/** (Kreyòl line, other-language line) for the mic, in the current state. Mirrors iOS `speakPrompt`. */
private fun speakPrompt(partner: Language, isRecording: Boolean, isProcessing: Boolean): Pair<String, String> = when {
    isProcessing -> "N ap tradui…" to UIText.pair(partner, "Translating…", "Traduciendo…")
    isRecording -> "M ap koute…" to UIText.pair(partner, "Listening. Tap to stop", "Escuchando. Toca para parar")
    else -> "Pale kreyòl oswa ${partner.kreyolName.lowercase()}" to
        UIText.pair(partner, "Speak Kreyòl or English", "Habla kreyòl o español")
}

/** Plain bar: app name + History / Phrasebook / Settings (iOS dropped the gradient header). */
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
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Creole Translator",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f).semantics { heading() }
        )
        BadgedBox(
            badge = {
                if (historyCount > 0) {
                    Badge { Text(historyCount.coerceAtMost(99).toString()) }
                }
            }
        ) {
            HeaderButton(Icons.Default.History, UIText.t("History", "Historial"), onHistoryClick)
        }
        HeaderButton(Icons.Default.MenuBook, UIText.t("Phrasebook", "Frases"), onPhrasebookClick)
        HeaderButton(Icons.Default.Settings, UIText.t("Settings", "Configuración"), onSettingsClick)
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun HeroScreen(
    pairChip: @Composable () -> Unit,
    notices: @Composable () -> Unit,
    whatsNew: (@Composable () -> Unit)?,
    mic: @Composable () -> Unit,
    prompt: Pair<String, String>,
    recording: (@Composable () -> Unit)?,
    typeLabel: String,
    typeEnabled: Boolean,
    typeVisible: Boolean,
    onType: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            pairChip()
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { notices() }
            whatsNew?.let { Box(Modifier.padding(top = 12.dp)) { it() } }
            Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
            mic()
            Text(
                prompt.first,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp)
            )
            Text(
                prompt.second,
                fontSize = 17.sp,
                color = appPalette.secondaryText,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
            recording?.let { Box(Modifier.padding(top = 18.dp)) { it() } }
            Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
            TextButton(
                onClick = onType,
                enabled = typeEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .alpha(if (typeVisible) 1f else 0f)
                    .padding(bottom = 8.dp)
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null, tint = appPalette.accent)
                Spacer(Modifier.width(8.dp))
                Text(typeLabel, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = appPalette.accent)
            }
        }
    }
}

/** What changed in this version, said once: one mic for both languages, and Spanish. */
@Composable
private fun WhatsNewCard(onDismiss: () -> Unit) {
    @Composable
    fun line(icon: ImageVector, text: String, kreyol: String) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}
        ) {
            Icon(icon, contentDescription = null, tint = appPalette.accent, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(kreyol, fontSize = 15.sp, color = appPalette.secondaryText)
            }
        }
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = appPalette.controlSurface,
        border = BorderStroke(1.5.dp, appPalette.controlBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                UIText.t("New", "Nuevo") + " · Nouvo",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            line(
                Icons.Default.Mic,
                UIText.t("One button for both languages. Just speak.", "Un solo botón para los dos idiomas. Solo habla."),
                "Yon sèl bouton pou de lang yo. Annik pale."
            )
            line(
                Icons.Default.Public,
                UIText.t("Now in Spanish too. Tap the languages at the top.", "Ahora también en español. Toca los idiomas arriba."),
                "Kounye a an panyòl tou. Peze lang yo anlè a."
            )
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                Text(UIText.t("Got it", "Entendido") + " · Dakò", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = appPalette.accent)
            }
        }
    }
}

/** Kreyòl is always one side, so the only choice is the other language. */
@Composable
private fun PairChip(partner: Language, enabled: Boolean, onChoose: (Language) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            enabled = enabled,
            shape = RoundedCornerShape(50),
            color = appPalette.controlSurface,
            border = BorderStroke(1.5.dp, appPalette.controlBorder),
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clearAndSetSemantics {
                    contentDescription = UIText.pair(
                        partner,
                        "Languages: Kreyòl and ${partner.nativeName}. Change the other language",
                        "Idiomas: kreyòl y ${partner.nativeName}. Cambiar el otro idioma"
                    )
                    role = Role.Button
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "${Language.CREOLE.flag} ${Language.CREOLE.nativeName} + ${partner.flag} ${partner.nativeName}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            listOf(Language.ENGLISH, Language.SPANISH).forEach { language ->
                DropdownMenuItem(
                    text = { Text("${language.nativeName} · ${language.kreyolName}") },
                    leadingIcon = {
                        if (language == partner) Icon(Icons.Default.Check, contentDescription = null)
                        else Spacer(Modifier.size(24.dp))
                    },
                    onClick = {
                        open = false
                        onChoose(language)
                    }
                )
            }
        }
    }
}

/** Round mic: filled when it can be pressed, red stop while recording, spinner while translating. */
@Composable
private fun MicButton(
    size: Dp,
    isRecording: Boolean,
    isProcessing: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val enabled = !isProcessing || isRecording
    val red = appPalette.recordingRed
    Box(
        modifier = Modifier
            .size(size * 1.36f)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        if (isRecording) {
            Box(Modifier.size(size * 1.36f).background(red.copy(alpha = 0.10f), CircleShape))
            Box(Modifier.size(size * 1.18f).background(red.copy(alpha = 0.18f), CircleShape))
        }
        Box(
            modifier = Modifier
                .size(size)
                .background(
                    if (!enabled) appPalette.controlSurface else if (isRecording) red else appPalette.accentFill,
                    CircleShape
                )
                .then(if (enabled) Modifier else Modifier.border(1.5.dp, appPalette.controlBorder, CircleShape)),
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    color = appPalette.secondaryText,
                    modifier = Modifier.size(size * 0.36f)
                )
            } else {
                Icon(
                    if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.42f)
                )
            }
        }
    }
}

/** Under the conversation in voice mode: the 84dp mic with "Ekri" beside it. */
@Composable
private fun VoiceDock(
    isRecording: Boolean,
    isProcessing: Boolean,
    recordingElapsedMs: Long,
    prompt: Pair<String, String>,
    partner: Language,
    onMic: () -> Unit,
    onType: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (isRecording) RecordingProgress(elapsedMs = recordingElapsedMs)
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            MicButton(
                size = 84.dp,
                isRecording = isRecording,
                isProcessing = isProcessing,
                contentDescription = "${prompt.first}. ${prompt.second}",
                onClick = onMic
            )
            if (!isRecording) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .sizeIn(minWidth = 56.dp, minHeight = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !isProcessing, role = Role.Button, onClick = onType)
                        .clearAndSetSemantics {
                            contentDescription = UIText.pair(partner, "Type instead", "Escribir")
                            role = Role.Button
                        }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, tint = appPalette.accent, modifier = Modifier.size(24.dp))
                    Text("Ekri", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = appPalette.accent)
                }
            }
        }
        Text(
            if (isRecording || isProcessing) "${prompt.first} · ${prompt.second}" else prompt.first,
            fontSize = 13.sp,
            color = appPalette.secondaryText,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {}
        )
    }
}

/** Typing row: back-to-mic button, text box, send arrow. The words decide the direction. */
@Composable
private fun TypingDock(
    text: String,
    partner: Language,
    isProcessing: Boolean,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onSpeak: () -> Unit
) {
    val hasText = text.isNotBlank()
    val canSend = hasText && !isProcessing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoundControl(
            onClick = onSpeak,
            enabled = !isProcessing,
            filled = false,
            contentDescription = UIText.pair(partner, "Speak instead", "Hablar")
        ) {
            Icon(Icons.Default.Mic, contentDescription = null, tint = appPalette.accent, modifier = Modifier.size(22.dp))
        }
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    "Ekri kreyòl oswa ${partner.kreyolName.lowercase()}…\n" +
                        UIText.pair(partner, "Type Kreyòl or English…", "Escribe kreyòl o español…"),
                    color = appPalette.secondaryText
                )
            },
            enabled = !isProcessing,
            minLines = 2,
            maxLines = 5,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = appPalette.controlSurface,
                focusedContainerColor = appPalette.controlSurface,
                disabledContainerColor = appPalette.controlSurface,
                unfocusedBorderColor = appPalette.controlBorder,
                focusedBorderColor = appPalette.accent,
                disabledBorderColor = appPalette.controlBorder
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            modifier = Modifier.weight(1f).focusRequester(focusRequester)
        )
        RoundControl(
            onClick = onSend,
            enabled = canSend,
            filled = canSend,
            contentDescription = "Tradui · " + UIText.pair(partner, "Translate", "Traducir")
        ) {
            if (isProcessing) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = appPalette.secondaryText)
            } else {
                Icon(
                    Icons.Default.ArrowUpward,
                    contentDescription = null,
                    tint = if (canSend) Color.White else appPalette.secondaryText,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/** 50dp circle: filled accent, or a bordered surface. */
@Composable
private fun RoundControl(
    onClick: () -> Unit,
    enabled: Boolean,
    filled: Boolean,
    contentDescription: String,
    size: Dp = 50.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) appPalette.accentFill else appPalette.controlSurface)
            .then(if (filled) Modifier else Modifier.border(1.5.dp, appPalette.controlBorder, CircleShape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * One sentence and its translation. What was said is on top; the translation
 * is the larger line, and Listen plays the translation. Kreyòl on the left
 * (bordered surface), the other language on the right (filled). Only the
 * newest bubble carries 👍/👎, the HD chip and ⇄ "translate the other way".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(
    turn: ConversationTurn,
    latest: Boolean,
    speaking: Boolean,
    onListen: () -> Unit,
    hdPlaysLeft: Int?,
    onHDChip: () -> Unit,
    feedback: FeedbackState?,
    onOtherWay: () -> Unit,
    otherWayEnabled: Boolean,
    partner: Language
) {
    val fromCreole = turn.direction.source == Language.CREOLE
    val target = turn.direction.target
    val onBubble = if (fromCreole) MaterialTheme.colorScheme.onSurface else Color.White
    val listenLabel = UIText.listen(target)

    val listen: @Composable () -> Unit = {
        Surface(
            onClick = onListen,
            shape = RoundedCornerShape(50),
            color = appPalette.accentFill,
            modifier = Modifier
                .heightIn(min = 44.dp)
                .clearAndSetSemantics {
                    contentDescription = "$listenLabel, ${target.nativeName}"
                    role = Role.Button
                }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(if (speaking) Icons.Default.Stop else Icons.Default.VolumeUp, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(20.dp))
                Text(listenLabel, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (fromCreole) Alignment.Start else Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = if (fromCreole) appPalette.controlSurface else appPalette.accentFill,
            border = if (fromCreole) BorderStroke(1.5.dp, appPalette.controlBorder) else null,
            modifier = Modifier
                .padding(start = if (fromCreole) 0.dp else 32.dp, end = if (fromCreole) 32.dp else 0.dp)
                .semantics(mergeDescendants = true) {}
        ) {
            SelectionContainer {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "${turn.direction.source.flag} ${turn.direction.source.nativeName}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (fromCreole) appPalette.secondaryText else Color.White
                    )
                    Text(turn.source, fontSize = 17.sp, color = onBubble)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (fromCreole) appPalette.controlBorder.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.4f))
                    )
                    Text(
                        turn.translation,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (fromCreole) appPalette.accent else Color.White
                    )
                }
            }
        }

        if (latest) {
            val extras: @Composable () -> Unit = {
                feedback?.let { fb ->
                    listOf(FeedbackRating.UP, FeedbackRating.DOWN).forEach { rating ->
                        val chosen = fb.given == rating
                        RoundControl(
                            onClick = if (rating == FeedbackRating.UP) fb.onUp else fb.onDown,
                            enabled = fb.given == null,
                            filled = false,
                            size = 44.dp,
                            contentDescription = if (rating == FeedbackRating.UP) "Translation was good" else "Translation was wrong"
                        ) {
                            Icon(
                                if (rating == FeedbackRating.UP) {
                                    if (chosen) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp
                                } else {
                                    if (chosen) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown
                                },
                                contentDescription = null,
                                tint = if (fb.given == null || chosen) appPalette.accent else appPalette.secondaryText,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                RoundControl(
                    onClick = onOtherWay,
                    enabled = otherWayEnabled,
                    filled = false,
                    size = 44.dp,
                    contentDescription = UIText.pair(partner, "Wrong language? Translate the other way",
                        "¿Idioma equivocado? Traducir al revés")
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null, tint = appPalette.accent, modifier = Modifier.size(22.dp))
                }
            }
            val hd: @Composable () -> Unit = {
                if (hdPlaysLeft != null) {
                    // Solid surface behind the tint so the chip text keeps its contrast on the body.
                    HDPlaysChip(playsLeft = hdPlaysLeft, onClick = onHDChip, backing = appPalette.controlSurface)
                }
            }
            // Listen sits under its own bubble's side; the row wraps at large text sizes.
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, if (fromCreole) Alignment.Start else Alignment.End),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (fromCreole) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { listen(); hd() }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { extras() }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { extras() }
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { hd(); listen() }
                }
            }
        } else {
            listen()
        }
    }
}

/** Things that are not part of the conversation: a wrong-pair offer, short status lines, and errors. */
@Composable
private fun Notices(
    suggestedPartner: Language?,
    onAcceptSuggestion: () -> Unit,
    statusMessage: String?,
    onDismissStatus: () -> Unit,
    errorMessage: String?,
    ttsError: String?,
    onDismissError: () -> Unit
) {
    // Wrong pair (Spanish said into the English pair): offer it, don't assume it.
    suggestedPartner?.let { other ->
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = appPalette.controlSurface,
            border = BorderStroke(1.5.dp, appPalette.accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Public, contentDescription = null, tint = appPalette.accent, modifier = Modifier.size(20.dp))
                Text(
                    UIText.t("That looked like ${other.nativeName}.", "Eso parecía ${other.nativeName}."),
                    fontSize = 15.sp,
                    color = appPalette.accent,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onAcceptSuggestion, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(
                        UIText.t("Switch to ${other.nativeName}", "Cambiar a ${other.nativeName}"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = appPalette.accent
                    )
                }
            }
        }
    }
    statusMessage?.let { StatusCard(message = it, isError = false, onDismiss = onDismissStatus) }
    ttsError?.let { StatusCard(message = "TTS: $it", isError = true, onDismiss = onDismissError) }
    errorMessage?.let { StatusCard(message = it, isError = true, onDismiss = onDismissError) }
}

/**
 * Fills toward AudioRecorder.MAX_DURATION_MS so people can see how long they have left.
 * Turns orange past WARN_AFTER_MS, where translation accuracy starts dropping.
 * Mirrors iOS RecordingProgressView.
 */
@Composable
private fun RecordingProgress(elapsedMs: Long) {
    val isLong = elapsedMs >= AudioRecorder.WARN_AFTER_MS
    val remaining = ((AudioRecorder.MAX_DURATION_MS - elapsedMs).coerceAtLeast(0L) + 999) / 1000
    val color = if (isLong) appPalette.warningText else appPalette.errorText

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clearAndSetSemantics { contentDescription = "Recording, $remaining seconds left" }
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(
                progress = { elapsedMs.toFloat() / AudioRecorder.MAX_DURATION_MS },
                color = color,
                trackColor = color.copy(alpha = 0.2f),
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val textColor = if (isLong) appPalette.warningText else appPalette.secondaryText
                Text(
                    if (isLong) "Shorter phrases translate best" else "Speak one or two sentences",
                    fontSize = 12.sp,
                    color = textColor
                )
                Text("${remaining}s left", fontSize = 12.sp, color = textColor)
            }
        }
    }
}

/** Thumbs state for the newest bubble; null hides the thumbs entirely. */
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

private data class PendingPlay(val card: String, val text: String, val language: String)

/**
 * Sits beside a card's speaker button: "✨ HD 3" while plays remain, then
 * "▶ +10 HD" — a tap that offers the rewarded video. Mirrors iOS HDPlaysChip.
 */
@Composable
private fun HDPlaysChip(playsLeft: Int, onClick: () -> Unit, backing: Color? = null) {
    val hasPlays = playsLeft > 0
    val label = if (hasPlays) "$playsLeft HD voice plays left"
                else "Out of HD voice plays. Watch a short video for ${HDVoiceCredits.PLAYS_PER_AD} more"
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (!hasPlays) WarningOrange
                else appPalette.accent.copy(alpha = 0.12f).let { if (backing != null) it.compositeOver(backing) else it },
        modifier = Modifier.clearAndSetSemantics { contentDescription = label }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val fg = if (hasPlays) appPalette.accent else Color.Black
            Icon(
                if (hasPlays) Icons.Default.AutoAwesome else Icons.Default.SmartDisplay,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp)
            )
            Text(
                if (hasPlays) "HD $playsLeft" else "+${HDVoiceCredits.PLAYS_PER_AD} HD",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = fg
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
