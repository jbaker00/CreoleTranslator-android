package com.creole.translator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creole.translator.data.Phrasebook
import com.creole.translator.model.TranslationDirection
import com.creole.translator.ui.theme.BrandPink
import com.creole.translator.ui.theme.BrandPurple

private fun iconForCategory(name: String): ImageVector = when (name) {
    "Greetings" -> Icons.Default.WavingHand
    "Basics" -> Icons.Default.ChatBubble
    "Directions" -> Icons.Default.Signpost
    "Emergency" -> Icons.Default.Warning
    "Medical" -> Icons.Default.MedicalServices
    "Travel" -> Icons.Default.Flight
    else -> Icons.Default.Book
}

// Fallback if WavingHand/Signpost not in this icons version — handled via try
private val fallbackIcon: ImageVector = Icons.Default.Book

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhrasebookScreen(viewModel: MainViewModel) {
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    var direction by remember { mutableStateOf(TranslationDirection.ENGLISH_TO_CREOLE) }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(BrandPurple, BrandPink)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.showMain() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = "Phrasebook",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Works offline",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        },
        bottomBar = { BannerAd() }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Direction toggle — mirrors iOS arrow.up.arrow.down button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = {
                        if (isSpeaking) viewModel.stopSpeaking()
                        direction = if (direction == TranslationDirection.ENGLISH_TO_CREOLE)
                            TranslationDirection.CREOLE_TO_ENGLISH else TranslationDirection.ENGLISH_TO_CREOLE
                    },
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Icon(
                        Icons.Default.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (direction == TranslationDirection.ENGLISH_TO_CREOLE) "English → Creole"
                        else "Creole → English",
                        fontSize = 13.sp
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Phrasebook.categories.forEach { category ->
                    item(key = "header-${category.name}") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                iconForCategory(category.name),
                                contentDescription = null,
                                tint = BrandPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                category.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandPurple
                            )
                        }
                    }
                    items(category.entries, key = { it.english }) { entry ->
                        PhrasebookRow(
                            entry = entry,
                            direction = direction,
                            isSpeaking = isSpeaking,
                            onSpeak = { text, lang -> viewModel.speakText(text, lang) },
                            onStop = { viewModel.stopSpeaking() }
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PhrasebookRow(
    entry: com.creole.translator.data.PhrasebookEntry,
    direction: TranslationDirection,
    isSpeaking: Boolean,
    onSpeak: (String, String) -> Unit,
    onStop: () -> Unit
) {
    val isEnglishToCreole = direction == com.creole.translator.model.TranslationDirection.ENGLISH_TO_CREOLE
    val promptText = if (isEnglishToCreole) entry.english else entry.creole
    val primaryText = if (isEnglishToCreole) entry.creole else entry.english
    val primaryLang = if (isEnglishToCreole) "ht" else "en"
    var isSpeakingThis by remember { mutableStateOf(false) }

    LaunchedEffect(isSpeaking) {
        if (!isSpeaking) isSpeakingThis = false
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    promptText,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    primaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (isSpeaking) {
                        onStop()
                        isSpeakingThis = false
                    } else {
                        isSpeakingThis = true
                        onSpeak(primaryText, primaryLang)
                    }
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (isSpeakingThis && isSpeaking) Icons.Default.VolumeUp else Icons.Default.VolumeUp,
                    contentDescription = "Speak",
                    tint = BrandPurple,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
