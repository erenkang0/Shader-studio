package com.shaderstudio.app.ui

import android.graphics.RuntimeShader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shaderstudio.app.R
import com.shaderstudio.app.shaders.HERO_AGSL
import kotlinx.coroutines.isActive
import kotlin.random.Random

@Composable
fun HomeScreen(
    onPickPhoto: () -> Unit,
    onDemo: () -> Unit,
) {
    val heroShader = remember { RuntimeShader(HERO_AGSL) }
    // Random phase offset: the liquid field opens differently on every launch.
    val timeOffset = remember { Random.nextFloat() * 4096f }
    var time by remember { mutableFloatStateOf(timeOffset) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (isActive) {
            withFrameNanos { now -> time = timeOffset + (now - start) / 1_000_000_000f }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    heroShader.setFloatUniform("uResolution", size.width, size.height)
                    val brush = ShaderBrush(heroShader)
                    onDrawBehind {
                        heroShader.setFloatUniform("uTime", time)
                        drawRect(brush)
                    }
                }
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.45f to Color(0x40000000),
                        1.0f to Color(0xEB000000),
                    )
                )
        )
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0x2EFFFFFF),
                contentColor = Color.White,
            ) {
                Text(
                    text = "EXPERIMENTS · AGSL",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Shader\nStudio",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onPickPhoto,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.pick_photo), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = onDemo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text(stringResource(R.string.demo_image), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.home_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally),
            )
        }
    }
}
