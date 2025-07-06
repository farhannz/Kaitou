package com.farhannz.kaitou.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ModifierLocalBeyondBoundsLayout
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.farhannz.kaitou.data.models.*


@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Preview()
@Composable

fun OCRScreen(onClicked : () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000)) // semi-transparent
            .clickable {
                onClicked()
            }
    ) {
        Text(text="Test")
    }

//    Scaffold(modifier = Modifier.fillMaxSize().background(Color.Transparent)) {
//    }
}