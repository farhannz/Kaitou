package com.farhannz.kaitou.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.farhannz.kaitou.data.models.*


@Preview
@Composable
fun PreviewPopUp(){
    PopUpDict()
}

@Composable
fun PopUpDict() {
    val examples = arrayListOf(
        Example(
            japanese = "こんにちは、田中さん。",
            reading = "こんにちは、たなかさん。",
            english = "Hello, Mr. Tanaka."
        )
    )

    val vocabularyList = VocabularyEntry(
        word = "こんにちは",
        reading = "こんにちは",
        romaji = "konnichiwa",
        definition = "Hello; Good afternoon (greeting used from late morning to late afternoon)",
        partOfSpeech = "interjection",
        jlptLevel = "N5",
        examples = examples
    )
    ElevatedCard(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(vocabularyList.word, style = MaterialTheme.typography.headlineSmall)
            Text(vocabularyList.jlptLevel, style = MaterialTheme.typography.labelMedium, color = Color.Gray)

            Spacer(modifier = Modifier.height(8.dp))

            Text(vocabularyList.romaji, style = MaterialTheme.typography.bodyMedium)
//            Text("Interjection", style = MaterialTheme.typography.labelSmall)

            Spacer(modifier = Modifier.height(8.dp))

            Text(vocabularyList.definition)

            Spacer(modifier = Modifier.height(8.dp))

            Text("Example:", style = MaterialTheme.typography.titleMedium)
            Text("こんにちは、田中さん。")
            Text("Hello, Mr. Tanaka.", style = MaterialTheme.typography.bodySmall)

//            Spacer(modifier = Modifier.height(12.dp))

//            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
//                Button(onClick = { /* Copy to clipboard logic */ }) {
//                    Text("コピー")
//                }
//                Button(onClick = { /* Add to word list */ }) {
//                    Text("単語帳に追加")
//                }
//            }
        }
    }

//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(16.dp),
//        horizontalAlignment = Alignment.CenterHorizontally,
//        verticalArrangement = Arrangement.Center
//    ) {
//        Text(
//            text = "Welcome Back!",
//            style = MaterialTheme.typography.headlineMedium
//        )
//        Spacer(modifier = Modifier.height(16.dp))
//    }
}