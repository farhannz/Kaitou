package com.farhannz.kaitou.presentation.ocr

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.farhannz.kaitou.domain.MorphemeData


@Preview
@Composable
fun PreviewMorpheme() {
    Column {
        StickyHeader("ないわ", {})
        MorphemeCard()
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
        if (useDarkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)

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

                    //            IconButton(
                    //                onClick = onDismiss,
                    //                modifier = Modifier.size(24.dp)
                    //            ) {
                    //                Icon(
                    //                    imageVector = Icons.Default.Close,
                    //                    contentDescription = "Close",
                    //                    tint = MaterialTheme.colorScheme.onSurface
                    //                )
                    //            }
                }
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorphemeCard(
    data: MorphemeData =
        MorphemeData(
            text = "友達",
            reading = "ともだち",
            meaning = "friend",
            type = "noun"
        )
) {
    val useDarkTheme = isSystemInDarkTheme()
    val fontSize = when {
        data.meaning.length <= 12 -> 20.sp
        data.meaning.length <= 20 -> 16.sp
        else -> 14.sp
    }
    val colors =
        if (useDarkTheme) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    MaterialTheme(
        colorScheme = colors
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionContainer {
                    // Kanji Column
                    Column(
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = data.text,
                            fontSize = 24.sp,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = data.reading,
                            fontSize = 12.sp,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                // Meaning Column
                Column(
                    horizontalAlignment = Alignment.Start, // Align to start (left)
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = data.meaning,
                        fontSize = fontSize,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = data.type,
                        fontSize = 12.sp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}