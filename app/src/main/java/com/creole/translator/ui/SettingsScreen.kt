package com.creole.translator.ui

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.creole.translator.data.TTSProvider
import com.creole.translator.data.AnalyticsManager
import com.creole.translator.data.VoiceSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class PendingUnlock(val voiceId: String, val isCreole: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, rewardedAd: RewardedAdManager) {
    BackHandler { viewModel.showMain() }
    val englishProvider by viewModel.voiceSettings.englishProvider.collectAsState()
    val creoleProvider by viewModel.voiceSettings.creoleProvider.collectAsState()
    val openAIVoice by viewModel.voiceSettings.openAIVoice.collectAsState()
    val englishOpenAIVoice by viewModel.voiceSettings.englishOpenAIVoice.collectAsState()
    val englishSpeed by viewModel.voiceSettings.englishPlaybackSpeed.collectAsState()
    val creoleSpeed by viewModel.voiceSettings.creolePlaybackSpeed.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val ttsError by viewModel.ttsError.collectAsState()
    val premiumUnlockedUntil by viewModel.voiceSettings.premiumUnlockedUntil.collectAsState()

    val activity = LocalContext.current as? Activity
    val scope = rememberCoroutineScope()
    var pendingUnlock by remember { mutableStateOf<PendingUnlock?>(null) }
    var showUnlockPrompt by remember { mutableStateOf(false) }
    var showUnlockedConfirmation by remember { mutableStateOf(false) }
    var isUnlocking by remember { mutableStateOf(false) }

    val premiumUnlocked = System.currentTimeMillis() < premiumUnlockedUntil

    LaunchedEffect(Unit) { rewardedAd.preload() }

    fun setVoice(pending: PendingUnlock) {
        if (pending.isCreole) viewModel.voiceSettings.setOpenAIVoice(pending.voiceId)
        else viewModel.voiceSettings.setEnglishOpenAIVoice(pending.voiceId)
    }

    fun grantUnlock(pending: PendingUnlock) {
        isUnlocking = false
        viewModel.voiceSettings.unlockPremiumVoices()
        setVoice(pending)
        AnalyticsManager.logRewardedUnlock("no_fill", pending.voiceId)
        showUnlockedConfirmation = true
    }

    fun selectVoice(voiceId: String, isCreole: Boolean, locked: Boolean) {
        val pending = PendingUnlock(voiceId, isCreole)
        if (!locked) {
            setVoice(pending)
            return
        }
        if (isUnlocking) return
        pendingUnlock = pending
        showUnlockPrompt = true
    }

    fun startUnlock() {
        val pending = pendingUnlock ?: return
        if (isUnlocking) return
        pendingUnlock = null
        isUnlocking = true
        scope.launch {
            // The manager preloads when the screen opens; give a slow network
            // up to 3s before falling back to a free grant.
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
                    viewModel.voiceSettings.unlockPremiumVoices()
                    setVoice(pending)
                    AnalyticsManager.logRewardedUnlock("rewarded_ad", pending.voiceId)
                },
                onDismiss = {
                    isUnlocking = false
                    // Confirmation must wait until the ad is off screen.
                    if (earned) showUnlockedConfirmation = true
                },
                onPresentFailure = {
                    AnalyticsManager.logRewardedUnlock("present_failed", pending.voiceId)
                    grantUnlock(pending)
                }
            )
            // Still no ad after waiting — don't block the user on a missing ad.
            if (!shown) grantUnlock(pending)
        }
    }

    if (showUnlockPrompt) {
        AlertDialog(
            onDismissRequest = {
                showUnlockPrompt = false
                pendingUnlock = null
            },
            title = { Text("Unlock Extra Voices") },
            text = { Text("Watch one short ad. All voices free for 24 hours.\n\nGade yon ti piblisite. Tout vwa yo gratis pou 24 èdtan.") },
            confirmButton = {
                TextButton(onClick = {
                    showUnlockPrompt = false
                    startUnlock()
                }) { Text("Watch Ad") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnlockPrompt = false
                    pendingUnlock = null
                }) { Text("Not Now") }
            }
        )
    }

    if (showUnlockedConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnlockedConfirmation = false },
            title = { Text("Voices Unlocked") },
            text = { Text("All voices are free for the next 24 hours.\n\nTout vwa yo gratis pou pwochen 24 èdtan yo.") },
            confirmButton = {
                TextButton(onClick = { showUnlockedConfirmation = false }) { Text("OK") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Voice Settings") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.showMain() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            // ── English section ──────────────────────────────────────────────

            item {
                LanguageSectionHeader(flag = "🇺🇸", language = "English", subtitle = "Voice Provider")
            }
            items(VoiceSettings.englishProviders) { provider ->
                ProviderRow(
                    provider = provider,
                    isSelected = englishProvider == provider,
                    onClick = { viewModel.voiceSettings.setEnglishProvider(provider) }
                )
            }
            if (englishProvider != TTSProvider.SYSTEM) {
                item { VoiceListHeader("Choose English Voice") }
                items(VoiceSettings.openAIVoices) { voice ->
                    val inAdTier = voice.id !in VoiceSettings.freeVoiceIds && !premiumUnlocked
                    val locked = inAdTier && voice.id != englishOpenAIVoice
                    VoiceRow(
                        voice = voice,
                        isSelected = englishOpenAIVoice == voice.id,
                        locked = locked,
                        inAdTier = inAdTier,
                        onClick = { selectVoice(voice.id, isCreole = false, locked = locked) }
                    )
                }
                item { UnlockFooter(premiumUnlocked, premiumUnlockedUntil) }
            }
            item {
                SpeedSlider(
                    label = "English Playback Speed",
                    speed = englishSpeed,
                    defaultSpeed = 1.0,
                    footer = null,
                    onSpeedChange = { viewModel.voiceSettings.setEnglishPlaybackSpeed(it) }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // ── Haitian Creole section ───────────────────────────────────────

            item {
                LanguageSectionHeader(flag = "🇭🇹", language = "Haitian Creole", subtitle = "Voice Provider")
            }
            items(VoiceSettings.creoleProviders) { provider ->
                ProviderRow(
                    provider = provider,
                    isSelected = creoleProvider == provider,
                    onClick = { viewModel.voiceSettings.setCreoleProvider(provider) }
                )
            }
            if (creoleProvider != TTSProvider.SYSTEM) {
                item { VoiceListHeader("Choose Haitian Creole Voice") }
                items(VoiceSettings.openAIVoices) { voice ->
                    val inAdTier = voice.id !in VoiceSettings.freeVoiceIds && !premiumUnlocked
                    val locked = inAdTier && voice.id != openAIVoice
                    VoiceRow(
                        voice = voice,
                        isSelected = openAIVoice == voice.id,
                        locked = locked,
                        inAdTier = inAdTier,
                        onClick = { selectVoice(voice.id, isCreole = true, locked = locked) }
                    )
                }
                item { UnlockFooter(premiumUnlocked, premiumUnlockedUntil) }
            }
            item {
                SpeedSlider(
                    label = "Creole Playback Speed",
                    speed = creoleSpeed,
                    defaultSpeed = 0.7,
                    footer = "Default is 0.70× — Creole voices tend to speak fast.",
                    onSpeedChange = { viewModel.voiceSettings.setCreolePlaybackSpeed(it) }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // ── Test section ─────────────────────────────────────────────────

            item {
                SectionHeader(
                    title = "Test Voice",
                    subtitle = "Tap to preview the selected voice and speed for each language."
                )
            }
            item {
                TestVoiceRow(
                    label = "Test Haitian Creole Voice",
                    sample = "Bonjou, kijan ou rele?",
                    isSpeaking = isSpeaking,
                    onTap = {
                        if (isSpeaking) viewModel.stopSpeaking()
                        else viewModel.speakText("Bonjou, kijan ou rele?", "ht")
                    }
                )
            }
            item {
                TestVoiceRow(
                    label = "Test English Voice",
                    sample = "Hello, how are you doing today?",
                    isSpeaking = isSpeaking,
                    onTap = {
                        if (isSpeaking) viewModel.stopSpeaking()
                        else viewModel.speakText("Hello, how are you doing today?", "en")
                    }
                )
            }
            if (isSpeaking) {
                item {
                    TextButton(
                        onClick = { viewModel.stopSpeaking() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Stop", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            ttsError?.let { error ->
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // ── Privacy section (mirrors iOS DataPrivacyConsent) ─────────────
            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            item {
                SectionHeader(
                    title = "Privacy",
                    subtitle = "Manage ad personalization and data consent."
                )
            }
            item {
                val ctx = LocalContext.current
                TextButton(
                    onClick = {
                        (ctx as? android.app.Activity)?.let { ConsentManager.showPrivacyOptions(it) }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text("Privacy Options")
                }
            }
            item {
                Text(
                    "Your speech is sent to Groq AI for transcription/translation and to OpenAI for spoken audio. Audio is processed temporarily and never stored; translations are saved only on your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LanguageSectionHeader(flag: String, language: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    ) {
        Text(
            text = "$flag $language — $subtitle",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VoiceListHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 2.dp)
    )
}

@Composable
private fun ProviderRow(
    provider: TTSProvider,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(VoiceSettings.displayName(provider), style = MaterialTheme.typography.bodyLarge)
            Text(
                VoiceSettings.description(provider),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun VoiceRow(
    voice: VoiceSettings.Voice,
    isSelected: Boolean,
    locked: Boolean,
    inAdTier: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(voice.name, style = MaterialTheme.typography.bodyLarge)
                if (locked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                when {
                    locked -> "Free with a short ad"
                    inAdTier -> "Your current voice — always available"
                    else -> voice.description
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (locked) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun UnlockFooter(premiumUnlocked: Boolean, premiumUnlockedUntil: Long) {
    val text = if (premiumUnlocked) {
        val hoursLeft = ((premiumUnlockedUntil - System.currentTimeMillis()) / 3600_000.0)
            .let { kotlin.math.ceil(it).toInt() }
        val remaining = if (hoursLeft <= 1) "less than 1 hour" else "$hoursLeft hours"
        "All voices unlocked ✓ $remaining left"
    } else {
        "Extra voices are free for 24 hours after one short ad."
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun SpeedSlider(
    label: String,
    speed: Double,
    defaultSpeed: Double,
    footer: String?,
    onSpeedChange: (Double) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🐢", style = MaterialTheme.typography.bodySmall)
            Slider(
                value = speed.toFloat(),
                onValueChange = { raw ->
                    // Snap to 0.05 increments
                    val snapped = ((raw / 0.05f).roundToInt() * 0.05).coerceIn(0.5, 1.5)
                    onSpeedChange(snapped)
                },
                valueRange = 0.5f..1.5f,
                steps = 19,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
            Text("🐇", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            speedLabel(speed),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        if (speed != defaultSpeed) {
            TextButton(
                onClick = { onSpeedChange(defaultSpeed) },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Reset to Default", style = MaterialTheme.typography.labelMedium)
            }
        }
        footer?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TestVoiceRow(
    label: String,
    sample: String,
    isSpeaking: Boolean,
    onTap: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                "\"$sample\"",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            if (isSpeaking) Icons.Default.Stop else Icons.Default.Check,
            contentDescription = if (isSpeaking) "Stop" else "Play",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun speedLabel(speed: Double): String {
    val formatted = "%.2f".format(speed)
    return when {
        speed < 0.65 -> "Very Slow (${formatted}×)"
        speed < 0.85 -> "Slow (${formatted}×)"
        speed < 1.1  -> "Normal (${formatted}×)"
        speed < 1.3  -> "Fast (${formatted}×)"
        else         -> "Very Fast (${formatted}×)"
    }
}
