package com.creole.translator.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.creole.translator.data.TTSProvider
import com.creole.translator.data.VoiceSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val englishProvider by viewModel.voiceSettings.englishProvider.collectAsState()
    val creoleProvider by viewModel.voiceSettings.creoleProvider.collectAsState()
    val openAIVoice by viewModel.voiceSettings.openAIVoice.collectAsState()
    val englishOpenAIVoice by viewModel.voiceSettings.englishOpenAIVoice.collectAsState()
    val englishSpeed by viewModel.voiceSettings.englishPlaybackSpeed.collectAsState()
    val creoleSpeed by viewModel.voiceSettings.creolePlaybackSpeed.collectAsState()
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val ttsError by viewModel.ttsError.collectAsState()

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
                    VoiceRow(
                        voice = voice,
                        isSelected = englishOpenAIVoice == voice.id,
                        onClick = { viewModel.voiceSettings.setEnglishOpenAIVoice(voice.id) }
                    )
                }
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
                    VoiceRow(
                        voice = voice,
                        isSelected = openAIVoice == voice.id,
                        onClick = { viewModel.voiceSettings.setOpenAIVoice(voice.id) }
                    )
                }
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
            Text(voice.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                voice.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
