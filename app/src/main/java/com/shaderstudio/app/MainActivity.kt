package com.shaderstudio.app

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.shaderstudio.app.ui.EditorScreen
import com.shaderstudio.app.ui.HomeScreen
import com.shaderstudio.app.ui.theme.ShaderStudioTheme
import com.shaderstudio.app.util.demoBitmap
import com.shaderstudio.app.util.loadBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                loadBitmap(context, uri)?.let { bitmap = it }
            }
        }
    }

    AnimatedContent(
        targetState = bitmap,
        transitionSpec = {
            (fadeIn(tween(350)) + scaleIn(initialScale = 0.94f, animationSpec = tween(350)))
                .togetherWith(fadeOut(tween(200)))
        },
        label = "screens",
    ) { current ->
        if (current == null) {
            HomeScreen(
                onPickPhoto = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onDemo = {
                    scope.launch {
                        bitmap = withContext(Dispatchers.Default) { demoBitmap() }
                    }
                },
            )
        } else {
            EditorScreen(
                bitmap = current,
                onClose = { bitmap = null },
            )
        }
    }
}
