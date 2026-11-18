package com.triforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.triforge.app.ui.TriForgeRoot
import com.triforge.app.ui.theme.TriForgeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TriForgeApp).container
        setContent {
            TriForgeTheme {
                TriForgeRoot(container)
            }
        }
    }
}
