package com.farhannz.kaitou.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.posMapping


@Composable
@Preview
fun previewBottomSheet() {
    BottomSheetContent(listOf(TokenInfo("元気", "元気", "asd")), "元気ですか", {})
}

@Composable
fun BottomSheetContent(
    merged: List<TokenInfo>,
    selectedWord: String,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxHeight = screenHeight / 2
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
    ) {
        StickyHeader(
            title = selectedWord,
            onDismiss = onDismiss
        )
        logger.DEBUG("Merged size ${merged.size}")
        MorphemeBreakdownCard(merged)
    }
}

@Composable
fun StickyHeader(
    title: String,
    onDismiss: () -> Unit
) {
    val fontSize = when {
        title.length <= 12 -> 24.sp
        title.length <= 20 -> 20.sp
        else -> 16.sp
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = fontSize,
                lineHeight = fontSize * 1.4f,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun MorphemeBreakdownCard(merged: List<TokenInfo>) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(merged.size) { idx ->
            MorphemeItemCard(merged[idx])
        }
    }
}

@Composable
fun MorphemeItemCard(token: TokenInfo) {
    var state by remember(token) { mutableStateOf<LookupState>(LookupState.LookingUp) }

    LaunchedEffect(token) {
        val result = DatabaseManager.getDatabase().dictionaryDao().lookupWord(token)
        state = if (result.isNotEmpty()) {
            LookupState.Done(result)
        } else {
            LookupState.NotFound
        }
    }

    when (val s = state) {
        is LookupState.LookingUp -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is LookupState.Done -> {
            val entry = s.result.first()

            // Use the matched kanji/kana from lookupWord result
            val surfaceForm = entry.kanji.find { it.text == token.surface }?.text
                ?: entry.kana.find { it.text == token.surface }?.text
                ?: entry.kanji.find { it.text == token.baseForm }?.text
                ?: entry.kana.find { it.text == token.baseForm }?.text
                ?: entry.kanji.find { it.common == true }?.text
                ?: entry.kana.find { it.common == true }?.text
                ?: token.surface

            val reading = entry.kana.find { it.text == token.surface }?.text
                ?: entry.kana.find { it.text == token.baseForm }?.text
                ?: entry.kana.find { it.common == true }?.text
                ?: entry.kana.firstOrNull()?.text
                ?: ""

            val meaning = entry.senses.firstOrNull()?.glosses
                ?.firstOrNull { it.lang == "eng" }?.text ?: ""

            MorphemeItem(
                morpheme = surfaceForm,
                reading = reading,
                meaning = meaning,
                type = posMapping[token.partOfSpeech]?.joinToString(",") ?: ""
            )
        }

        is LookupState.NotFound -> {
            MorphemeItem(
                morpheme = token.surface,
                reading = "",
                meaning = "",
                type = posMapping[token.partOfSpeech]?.joinToString(",") ?: ""
            )
        }
    }
}

data class MorphemeData(
    val text: String,
    val type: String,
    val reading: String,
    val meaning: String
)

@Composable
fun MorphemeItem(
    morpheme: String,
    type: String,
    reading: String,
    meaning: String
) {
    Column(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = morpheme,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF1976D2)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = type,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        if (reading.isNotEmpty()) {
            Text(
                text = reading,
                fontSize = 14.sp,
                color = Color(0xFF424242)
            )
        }
        if (meaning.isNotEmpty()) {
            Text(
                text = meaning,
                fontSize = 14.sp,
                color = Color(0xFF424242)
            )
        }
    }
}

@Preview
@Composable
fun PreviewPopUp() {
    PopUpDict(TokenInfo("test", "test", "test"))
}

sealed class LookupState {
    object LookingUp : LookupState()
    data class Done(val result: List<WordFull>) : LookupState()
    object NotFound : LookupState()
}

@Composable
fun PopUpDict(
    token: TokenInfo
) {
    var currentState by remember { mutableStateOf<LookupState>(LookupState.LookingUp) }

    LaunchedEffect(token) {
        currentState = LookupState.LookingUp
        val dictionaryDao = DatabaseManager.getDatabase().dictionaryDao()
        val result = dictionaryDao.lookupWord(token)

        currentState = if (result.isNotEmpty()) {
            LookupState.Done(result)
        } else {
            LookupState.NotFound
        }
    }

    when (val state = currentState) {
        is LookupState.LookingUp -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is LookupState.Done -> {
            val wordEntry = state.result.first()

            val reading = wordEntry.getMostLikelyKana(token)
            val meaning = wordEntry.getMostLikelyMeaning(token)

            val surfaceForm = wordEntry.kanji.firstOrNull { it.common == true }?.text
                ?: wordEntry.kana.firstOrNull { it.common == true }?.text
                ?: wordEntry.kanji.firstOrNull()?.text
                ?: wordEntry.kana.firstOrNull()?.text
                ?: token.surface // Fallback to the original token surface

            ElevatedCard(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Japanese word
                    Text(
                        text = surfaceForm,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Kana reading (only show if it's different from the main form)
                    if (!reading.isNullOrBlank() && reading != surfaceForm) {
                        Text(
                            text = reading,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    // Definition/Meaning
                    if (!meaning.isNullOrBlank()) {
                        Text(
                            text = meaning,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = "No definition available.",
                            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is LookupState.NotFound -> {
            Card(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Word not found in dictionary.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}