package com.creole.translator.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.creole.translator.model.TranslationEntry
import com.creole.translator.ui.theme.BrandPink
import com.creole.translator.ui.theme.BrandPurple
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val entries by viewModel.historyEntries.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear History") },
            text = { Text("Are you sure you want to delete all translation history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) { Text("Clear All", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        bottomBar = { BannerAd() },
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.horizontalGradient(listOf(BrandPurple, BrandPink)))
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.showMain() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = "Translation History",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear All", tint = Color.White)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\uD83D\uDCDD", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "No translation history yet",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Record something to get started!",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        onSpeak = { text, lang -> viewModel.speakText(text, lang) },
                        onDelete = { viewModel.deleteHistoryEntry(entry) }
                    )
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: TranslationEntry,
    onSpeak: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.US)
    val formattedDate = dateFormat.format(Date(entry.timestamp))

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${entry.direction.sourceFlag} \u2192 ${entry.direction.targetFlag}  |  $formattedDate",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Divider(modifier = Modifier.padding(vertical = 6.dp))

            // Source text
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${entry.direction.sourceFlag} ",
                    fontSize = 14.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.sourceText.let {
                            if (!expanded && it.length > 100) it.take(100) + "..." else it
                        },
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
                IconButton(
                    onClick = { onSpeak(entry.sourceText, entry.direction.sourceLanguage) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Speak source",
                        modifier = Modifier.size(16.dp),
                        tint = BrandPurple
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Translation text
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = "${entry.direction.targetFlag} ",
                    fontSize = 14.sp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.translatedText.let {
                            if (!expanded && it.length > 100) it.take(100) + "..." else it
                        },
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(
                    onClick = { onSpeak(entry.translatedText, entry.direction.targetLanguage) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Speak translation",
                        modifier = Modifier.size(16.dp),
                        tint = BrandPurple
                    )
                }
            }

            // Expand/collapse if text is long
            if (entry.sourceText.length > 100 || entry.translatedText.length > 100) {
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        if (expanded) "Show less" else "Show more",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
