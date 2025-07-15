package com.farhannz.kaitou.ui.components

import android.widget.Toast
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
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.helpers.DatabaseManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetWithStickyHeader() {
    var showBottomSheet by remember { mutableStateOf(false) }
    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = { showBottomSheet = true }
        ) {
            Text("Show Bottom Sheet")
        }
    }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = bottomSheetState,
            dragHandle = null, // We'll create our own header
        ) {
            BottomSheetContent(
                emptyList(),
                onDismiss = { showBottomSheet = false }
            )
        }
    }
}

@Composable
@Preview
fun previewBottomSheet() {
    BottomSheetContent(emptyList<TokenInfo>(), {})
}

@Composable
fun BottomSheetContent(
    merged : List<TokenInfo>,
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
        // Sticky Header
        StickyHeader(
            title = "元気ですか",
            onDismiss = onDismiss
        )

        // Single Scrollable Morpheme Breakdown Card
        MorphemeBreakdownCard()
    }
}

@Composable
fun StickyHeader(
    title: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
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
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
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
fun MorphemeBreakdownCard() {
    // Sample morpheme data - you can replace this with your actual data
    val morphemeData = listOf(
        MorphemeData("元気", "na-adjective", "genki", "healthy, energetic"),
        MorphemeData("です", "auxiliary", "desu", "polite copula"),
        MorphemeData("か", "particle", "ka", "question particle"),
        MorphemeData("元気", "na-adjective", "genki", "healthy, energetic"),
        MorphemeData("です", "auxiliary", "desu", "polite copula"),
        MorphemeData("か", "particle", "ka", "question particle"),
        MorphemeData("元気", "na-adjective", "genki", "healthy, energetic"),
        MorphemeData("です", "auxiliary", "desu", "polite copula"),
        MorphemeData("か", "particle", "ka", "question particle"),
        MorphemeData("元気", "na-adjective", "genki", "healthy, energetic"),
        MorphemeData("です", "auxiliary", "desu", "polite copula"),
        MorphemeData("か", "particle", "ka", "question particle")
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Morpheme Breakdown:",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(morphemeData.size) { idx ->
                    MorphemeItem(
                        morpheme = morphemeData[idx].text,
                        type = morphemeData[idx].type,
                        reading = morphemeData[idx].reading,
                        meaning = morphemeData[idx].meaning
                    )
                }
            }
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

        Text(
            text = "Reading: $reading",
            fontSize = 14.sp,
            color = Color(0xFF424242)
        )

        Text(
            text = "Meaning: $meaning",
            fontSize = 14.sp,
            color = Color(0xFF424242)
        )
    }
}

@Preview
@Composable
fun PreviewPopUp(){
    PopUpDict(TokenInfo("test","test","test"))
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

    // LaunchedEffect fetches data when the token changes.
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

    // UI changes based on the current state.
    when (val state = currentState) {
        is LookupState.LookingUp -> {
            // 1. Show a loading indicator while fetching data.
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
            // 2. Data is found, process and display it.
            // Get the most likely entry from the results list.
            val wordEntry = state.result.first()

            // Extract the most likely reading and meaning using your helper functions.
            val reading = wordEntry.getMostLikelyKana(token)
            val meaning = wordEntry.getMostLikelyMeaning(token)

            // Determine the main display form (prioritize common kanji, then common kana).
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
            // 3. Show a message if the word could not be found.
            Card(modifier = Modifier
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