package com.shaderstudio.app.ui

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.shaderstudio.app.R
import com.shaderstudio.app.shaders.ShaderEffect
import com.shaderstudio.app.shaders.ShaderEffects
import com.shaderstudio.app.util.applyShaderToBitmap
import com.shaderstudio.app.util.saveToGallery
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(
    bitmap: Bitmap,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effects = ShaderEffects.all

    var selected by remember { mutableStateOf(effects.first { it.id == "dotmatrix" }) }
    val paramStore = remember { HashMap<String, SnapshotStateList<Float>>() }
    val params = remember(selected.id) {
        paramStore.getOrPut(selected.id) {
            selected.params.map { it.default }.toMutableStateList()
        }
    }
    val shader = remember(selected.id) { selected.agsl?.let { RuntimeShader(it) } }

    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selected.id) {
        if (!selected.animated) return@LaunchedEffect
        val start = System.nanoTime()
        while (isActive) {
            withFrameNanos { now -> time = (now - start) / 1_000_000_000f }
        }
    }

    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val savedMsg = stringResource(R.string.saved_toast)
    val failMsg = stringResource(R.string.save_failed)

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close_editor))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "effectTitle",
                ) { e ->
                    Column {
                        Text(e.name, style = MaterialTheme.typography.titleLarge)
                        Text(
                            e.tagline,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Button(
                onClick = {
                    if (saving) return@Button
                    scope.launch {
                        saving = true
                        try {
                            val rendered = applyShaderToBitmap(bitmap, selected, params.toList(), time)
                            val ok = saveToGallery(context, rendered)
                            Toast.makeText(context, if (ok) savedMsg else failMsg, Toast.LENGTH_SHORT).show()
                        } catch (t: Throwable) {
                            Toast.makeText(context, failMsg, Toast.LENGTH_SHORT).show()
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving,
                shape = RoundedCornerShape(18.dp),
            ) {
                if (saving) {
                    LoadingIndicator(modifier = Modifier.size(22.dp))
                } else {
                    Icon(Icons.Rounded.Done, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.save))
                }
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
            Image(
                bitmap = imageBitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(28.dp))
                    .onSizeChanged { layerSize = it }
                    .graphicsLayer {
                        val s = shader
                        if (s != null && layerSize.width > 0) {
                            s.setFloatUniform(
                                "uResolution",
                                layerSize.width.toFloat(),
                                layerSize.height.toFloat(),
                            )
                            s.setFloatUniform("uTime", time)
                            selected.params.forEachIndexed { i, p ->
                                s.setFloatUniform("uParam${i + 1}", params.getOrElse(i) { p.default })
                            }
                            renderEffect = android.graphics.RenderEffect
                                .createRuntimeShaderEffect(s, "uImage")
                                .asComposeRenderEffect()
                        } else {
                            renderEffect = null
                        }
                    },
            )
        }

        Text(
            text = stringResource(R.string.effects_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, top = 10.dp, bottom = 10.dp),
        )
        val effectRows = remember(effects) {
            listOf(
                effects.filterIndexed { i, _ -> i % 2 == 0 },
                effects.filterIndexed { i, _ -> i % 2 == 1 },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            effectRows.forEach { row ->
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(row, key = { it.id }) { effect ->
                        EffectCard(
                            effect = effect,
                            isSelected = effect.id == selected.id,
                            onClick = { selected = effect },
                        )
                    }
                }
            }
        }

        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 18.dp)) {
                if (selected.params.isEmpty()) {
                    Text(
                        text = selected.tagline,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    selected.params.forEachIndexed { i, p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                p.label,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${(params.getOrElse(i) { p.default } * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Slider(
                            value = params.getOrElse(i) { p.default },
                            onValueChange = { params[i] = it },
                        )
                    }
                    TextButton(
                        onClick = {
                            selected.params.forEachIndexed { i, p -> params[i] = p.default }
                        },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.reset_params))
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectCard(
    effect: ShaderEffect,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.06f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "cardScale",
    )
    val shape = RoundedCornerShape(22.dp)
    val background = if (isSelected) {
        Modifier.background(
            Brush.linearGradient(
                listOf(Color(effect.accentStart), Color(effect.accentEnd))
            )
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .then(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
    ) {
        Column {
            Text(
                effect.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                effect.tagline,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) Color.White.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
