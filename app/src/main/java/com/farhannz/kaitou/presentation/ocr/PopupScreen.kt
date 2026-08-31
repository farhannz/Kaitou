package com.farhannz.kaitou.presentation.ocr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhannz.kaitou.domain.MorphemeData
import kotlin.math.roundToInt

@Preview
@Composable
fun PreviewMorpheme() {
    Column {
        StickyHeader("父上たちは考え方が古い", {})
        LazyColumn {
            items(sampleMorphemes) { morpheme ->
                MorphemeCard(morpheme)
            }
        }
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
    val useDarkTheme = isSystemInDarkTheme()
    val colors =
        if (useDarkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(
            LocalContext.current
        )
    MaterialTheme(colorScheme = colors) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(),
            shadowElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            SelectionContainer {
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
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorphemeCard(
    data: MorphemeData = MorphemeData(
        text = "たち",
        reading = "たち",
        meaning = "pluralizing suffix",
        type = "suffix",
        confidence = 0.063f,
        alternatives = listOf("rehearsal", "leading male role")
    )
) {
    val useDarkTheme = isSystemInDarkTheme()
    val colors = if (useDarkTheme) dynamicDarkColorScheme(LocalContext.current)
    else dynamicLightColorScheme(LocalContext.current)

    var showAlternatives by remember { mutableStateOf(false) }
    val lowConfidence = data.confidence < 0.4f
    val notFound = data.meaning.isEmpty()

    MaterialTheme(colorScheme = colors) {
        Card(
            onClick = { if (data.alternatives.isNotEmpty()) showAlternatives = !showAlternatives },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = when {
                    notFound -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                    lowConfidence -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            border = when {
                notFound -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                lowConfidence -> BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                )

                else -> null
            }
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SelectionContainer {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = data.text,
                                fontSize = 24.sp,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (data.reading != data.text) {
                                Text(
                                    text = data.reading,
                                    fontSize = 12.sp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Enhanced meaning display
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (notFound) {
                                // Not found display
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = "Not found",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Not found in dictionary",
                                        fontSize = 14.sp,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontStyle = FontStyle.Italic
                                    )
                                }
                            } else {
                                // Normal meaning display
                                Text(
                                    text = data.meaning,
                                    fontSize = if (data.meaning.length <= 20) 16.sp else 14.sp,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // Confidence indicator
                            if (lowConfidence) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Low confidence",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Show alternatives indicator
                            if (data.alternatives.isNotEmpty()) {
                                Icon(
                                    imageVector = if (showAlternatives) Icons.Default.ExpandLess
                                    else Icons.Default.ExpandMore,
                                    contentDescription = "Alternatives",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Additional info row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Part of speech
                            Text(
                                text = data.type,
                                fontSize = 12.sp,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )

                            // Confidence percentage
                            if (data.confidence > 0) {
                                val confidenceColor = when {
                                    data.confidence >= 0.7f -> MaterialTheme.colorScheme.primary
                                    data.confidence >= 0.4f -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.error
                                }

                                Text(
                                    text = "${(data.confidence * 100).roundToInt()}%",
                                    fontSize = 10.sp,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = confidenceColor
                                )
                            }

                            // Status indicators
                            if (notFound) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = "Missing",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 1.dp
                                            )
                                        )
                                    }
                                }
                            } else if (lowConfidence) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.tertiaryContainer
                                    ) {
                                        Text(
                                            text = "Low Confidence",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(
                                                horizontal = 6.dp,
                                                vertical = 1.dp
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // Hint for alternatives when not found
                        if (notFound && data.alternatives.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Tap to see alternatives",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    }
                }

                // Alternatives section
                if (showAlternatives && data.alternatives.isNotEmpty()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "Alternatives:",
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        data.alternatives.take(3).forEach { alternative ->
                            Text(
                                text = "• $alternative",
                                fontSize = 14.sp,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


// Sample data for preview
val sampleMorphemes = listOf(
    MorphemeData("父上", "ちちうえ", "honorific father", "noun", 0.95f),
    MorphemeData(
        "たち", "たち", "pluralizing suffix", "suffix", 0.063f,
        listOf("rehearsal", "leading male role", "passage of time")
    ),
    MorphemeData("考え方", "かんがえかた", "way of thinking", "noun", 0.89f),
    MorphemeData("古い", "ふるい", "old", "adjective", 0.92f),
    MorphemeData("古い", "ふるい", "", "adjective", 0.92f)
)