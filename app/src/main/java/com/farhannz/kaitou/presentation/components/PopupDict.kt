package com.farhannz.kaitou.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhannz.kaitou.data.models.TokenInfo
import com.farhannz.kaitou.domain.MorphemeData
import com.farhannz.kaitou.presentation.ocr.MorphemeCard
import com.farhannz.kaitou.presentation.ocr.MorphemeLookupState
import com.farhannz.kaitou.presentation.ocr.PopupViewModel
import com.farhannz.kaitou.presentation.ocr.StickyHeader

@Composable
fun BottomSheetContent(
    merged: List<TokenInfo>,
    selectedWord: String,
    selectedEmbedding: FloatArray,
    viewModel: PopupViewModel,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxHeight = screenHeight * 2 / 3
    val useDarkTheme = isSystemInDarkTheme()
    val colors =
        if (useDarkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(
            LocalContext.current
        )

    MaterialTheme(colorScheme = colors) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            StickyHeader(
                title = selectedWord,
                onDismiss = onDismiss
            )
            logger.DEBUG("Merged size ${merged.size}")
            MorphemeBreakdownCard(merged, selectedEmbedding, viewModel)
        }
    }
}

@Composable
fun MorphemeBreakdownCard(
    merged: List<TokenInfo>,
    selectedEmbedding: FloatArray,
    viewModel: PopupViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(merged.size) { idx ->
            MorphemeItemCard(idx, merged, selectedEmbedding, viewModel)
        }
    }
}

@Composable
fun MorphemeItemCard(
    tokenIdx: Int,
    sentenceTokens: List<TokenInfo>,
    selectedEmbedding: FloatArray,
    viewModel: PopupViewModel
) {
    val token = sentenceTokens[tokenIdx]

    LaunchedEffect(token) {
        viewModel.lookupMorpheme(tokenIdx, sentenceTokens, selectedEmbedding)
    }

    when (val state = viewModel.getMorphemeState(tokenIdx)) {
        is MorphemeLookupState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        is MorphemeLookupState.Skipped -> {
        }

        is MorphemeLookupState.Done -> {
            logger.DEBUG(state.data.toString())
            MorphemeCard(state.data)
        }

        is MorphemeLookupState.NotFound -> {
            val entry = viewModel.getFallbackMorphemeData(token)
            MorphemeCard(entry)
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