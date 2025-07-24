package com.farhannz.kaitou.presentation.components

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhannz.kaitou.data.models.*
import com.farhannz.kaitou.domain.LookupResult
import com.farhannz.kaitou.domain.MorphemeData
import com.farhannz.kaitou.helpers.DatabaseManager
import com.farhannz.kaitou.helpers.posMapping
import com.farhannz.kaitou.impl.JMDict
import com.farhannz.kaitou.presentation.ocr.MorphemeCard
import com.farhannz.kaitou.presentation.ocr.StickyHeader


//@Composable
////@Preview
//fun previewBottomSheet() {
//    BottomSheetContent(listOf(TokenInfo("元気", "元気", "asd")), "元気ですか", {})
//}

@Composable
fun BottomSheetContent(
    merged: List<TokenInfo>,
    selectedWord: String,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxHeight = screenHeight * 2 / 3
    val useDarkTheme = isSystemInDarkTheme()
    val colors =
        if (useDarkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    MaterialTheme(colorScheme = colors) {
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
        val result = JMDict.lookup(token)
        when (result) {
            is LookupResult.Success -> {
                state = LookupState.Done(result)
            }

            is LookupResult.Error -> {
                state = LookupState.NotFound
            }
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
            val entry = s.result.morphemeData
            logger.DEBUG(entry.toString())
            MorphemeCard(
                entry
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

//@Preview
//@Composable
//fun PreviewPopUp() {
//    PopUpDict(TokenInfo("test", "test", "test"))
//}

sealed class LookupState {
    object LookingUp : LookupState()
    data class Done(val result: LookupResult.Success) : LookupState()
    object NotFound : LookupState()
}
