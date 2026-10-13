package com.wisebirds.assignment

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        Box(
            modifier = Modifier.fillMaxSize().safeContentPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Text("wisebirds kmp assignment")
        }
    }
}
