package com.triforge.app.ui.legal

import androidx.annotation.RawRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** A text file from res/raw, read once per composition. */
@Composable
fun rawText(@RawRes id: Int): String {
    val context = LocalContext.current
    return remember(id) {
        context.resources.openRawResource(id).bufferedReader().use { it.readText() }
    }
}
