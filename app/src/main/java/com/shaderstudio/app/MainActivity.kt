package com.shaderstudio.app

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.shaderstudio.app.ui.EditorScreen
import com.shaderstudio.app.ui.HomeScreen
import com.shaderstudio.app.ui.theme.ShaderStudioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The app is always dark; force light system-bar icons regardless of theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            ShaderStudioTheme {
                ShaderStudioApp()
            }
        }
    }
}

@Composable
fun ShaderStudioApp() {
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    AnimatedContent(
        targetState = bitmap,
        transitionSpec = {
            (fadeIn(tween(360)) + scaleIn(initialScale = 0.92f, animationSpec = tween(360)))
                .togetherWith(fadeOut(tween(220)))
        },
        label = "screens",
    ) { current ->
        if (current == null) {
            HomeScreen(onOpen = { bitmap = it })
        } else {
            EditorScreen(
                bitmap = current,
                onClose = { bitmap = null },
            )
        }
    }
}
