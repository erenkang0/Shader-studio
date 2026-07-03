package com.shaderstudio.app.ui

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shaderstudio.app.R
import com.shaderstudio.app.shaders.LayerBlendMode
import com.shaderstudio.app.shaders.LayerCompositor
import com.shaderstudio.app.shaders.LayerSpec
import com.shaderstudio.app.shaders.ShaderEffect
import com.shaderstudio.app.shaders.ShaderEffects
import com.shaderstudio.app.util.applyLayerStackToBitmap
import com.shaderstudio.app.util.saveToGallery
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

private class EffectLayer(val id: Int, initialEffect: ShaderEffect) {
    var effect by mutableStateOf(initialEffect)
    val params = initialEffect.params.map { it.default }.toMutableStateList()
    var blend by mutableStateOf(LayerBlendMode.NORMAL)
    var opacity by mutableFloatStateOf(1f)
    var visible by mutableStateOf(true)
    var centerX by mutableFloatStateOf(0.5f)
    var centerY by mutableFloatStateOf(0.5f)

    fun switchEffect(e: ShaderEffect) {
        effect = e
        params.clear()
        params.addAll(e.params.map { it.default })
    }
}

private enum class PanelMode { NONE, EFFECT, LAYER }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(
    bitmap: Bitmap,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val effects = remember { ShaderEffects.all.filter { it.agsl != null } }

    var nextId by remember { mutableIntStateOf(1) }
    val layers = remember {
        mutableStateListOf(EffectLayer(0, effects.first { it.id == "dotmatrix" }))
    }
    var selectedLayerIndex by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf(PanelMode.NONE) }
    val selectedLayer = layers.getOrNull(selectedLayerIndex)

    val structureKey = layers.joinToString("|") { it.effect.id }
    val compositeShader = remember(structureKey) {
        if (layers.isEmpty()) null
        else RuntimeShader(LayerCompositor.generateSource(layers.map { it.effect }))
    }

    // Continuous clock with a random start so animated effects differ per session.
    val timeOffset = remember { Random.nextFloat() * 1024f }
    var time by remember { mutableFloatStateOf(0f) }
    val anyAnimated = layers.any { it.visible && it.effect.animated }
    LaunchedEffect(anyAnimated) {
        if (!anyAnimated) return@LaunchedEffect
        val epoch = System.nanoTime()
        val base = time
        while (isActive) {
            withFrameNanos { now -> time = base + (now - epoch) / 1_000_000_000f }
        }
    }

    var saving by remember { mutableStateOf(false) }
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val savedMsg = stringResource(R.string.saved_toast)
    val failMsg = stringResource(R.string.save_failed)

    fun addLayer(effect: ShaderEffect) {
        if (layers.size >= LayerCompositor.MAX_LAYERS) return
        layers.add(EffectLayer(nextId++, effect))
        selectedLayerIndex = layers.lastIndex
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {

            // ---- Top bar ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close_editor))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = selectedLayer?.effect,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "effectTitle",
                    ) { e ->
                        Column {
                            Text(
                                e?.name ?: stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                e?.tagline ?: stringResource(R.string.no_layers_hint),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
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
                                val specs = layers.filter { it.visible }.map {
                                    LayerSpec(
                                        effect = it.effect,
                                        params = it.params.toList(),
                                        blend = it.blend,
                                        opacity = it.opacity,
                                        centerX = it.centerX,
                                        centerY = it.centerY,
                                    )
                                }
                                val rendered = applyLayerStackToBitmap(bitmap, specs, timeOffset + time)
                                val ok = saveToGallery(context, rendered)
                                Toast.makeText(context, if (ok) savedMsg else failMsg, Toast.LENGTH_SHORT).show()
                            } catch (t: Throwable) {
                                Toast.makeText(context, failMsg, Toast.LENGTH_SHORT).show()
                            } finally {
                                saving = false
                            }
                        }
                    },
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

            // ---- Preview (big) ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
                val positionable = selectedLayer != null &&
                    selectedLayer.visible && selectedLayer.effect.positionable

                Box(
                    Modifier
                        .aspectRatio(ratio)
                        .clip(RoundedCornerShape(24.dp))
                        .pointerInput(selectedLayerIndex, positionable) {
                            if (!positionable) return@pointerInput
                            detectDragGestures { change, _ ->
                                change.consume()
                                layers.getOrNull(selectedLayerIndex)?.let { l ->
                                    l.centerX = (change.position.x / size.width).coerceIn(0f, 1f)
                                    l.centerY = (change.position.y / size.height).coerceIn(0f, 1f)
                                }
                            }
                        }
                        .pointerInput(selectedLayerIndex, positionable, "tap") {
                            if (!positionable) return@pointerInput
                            detectTapGestures { pos ->
                                layers.getOrNull(selectedLayerIndex)?.let { l ->
                                    l.centerX = (pos.x / size.width).coerceIn(0f, 1f)
                                    l.centerY = (pos.y / size.height).coerceIn(0f, 1f)
                                }
                            }
                        },
                ) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = compositeShader
                                if (s != null && size.width > 0f && layers.isNotEmpty()) {
                                    s.setFloatUniform("uResolution", size.width, size.height)
                                    s.setFloatUniform("uTime", timeOffset + time)
                                    layers.forEachIndexed { i, layer ->
                                        layer.effect.params.forEachIndexed { j, p ->
                                            s.setFloatUniform(
                                                "uL${i}P${j + 1}",
                                                layer.params.getOrElse(j) { p.default },
                                            )
                                        }
                                        s.setFloatUniform("uL${i}Center", layer.centerX, layer.centerY)
                                        s.setIntUniform("uL${i}Mode", layer.blend.ordinal)
                                        s.setFloatUniform(
                                            "uL${i}Opacity",
                                            if (layer.visible) layer.opacity else 0f,
                                        )
                                    }
                                    renderEffect = android.graphics.RenderEffect
                                        .createRuntimeShaderEffect(s, "uImage")
                                        .asComposeRenderEffect()
                                } else {
                                    renderEffect = null
                                }
                            },
                    )
                    if (positionable && selectedLayer != null) {
                        Canvas(Modifier.fillMaxSize()) {
                            val c = androidx.compose.ui.geometry.Offset(
                                selectedLayer.centerX * size.width,
                                selectedLayer.centerY * size.height,
                            )
                            drawCircle(Color.Black.copy(alpha = 0.35f), 12.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                            drawCircle(Color.White.copy(alpha = 0.95f), 11.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                            drawCircle(Color.White, 3.dp.toPx(), c)
                        }
                    }
                }
            }

            // ---- Layer strip ----
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp),
            ) {
                item(key = "base") {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    ) {
                        Text(
                            stringResource(R.string.base_photo),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
                itemsIndexed(layers, key = { _, l -> l.id }) { i, layer ->
                    LayerChip(
                        layer = layer,
                        isSelected = i == selectedLayerIndex,
                        onClick = {
                            if (selectedLayerIndex == i) {
                                panel = if (panel == PanelMode.LAYER) PanelMode.NONE else PanelMode.LAYER
                            } else {
                                selectedLayerIndex = i
                            }
                        },
                    )
                }
                if (layers.size < LayerCompositor.MAX_LAYERS) {
                    item(key = "add") {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                                addLayer(effects.first { it.id == "dotmatrix" })
                            },
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    contentDescription = stringResource(R.string.add_layer),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }

            // ---- Effect carousel (2 compact rows) ----
            val effectRows = remember(effects) {
                listOf(
                    effects.filterIndexed { i, _ -> i % 2 == 0 },
                    effects.filterIndexed { i, _ -> i % 2 == 1 },
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            ) {
                effectRows.forEach { row ->
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(row, key = { it.id }) { effect ->
                            EffectCard(
                                effect = effect,
                                isSelected = selectedLayer?.effect?.id == effect.id,
                                onClick = {
                                    val l = layers.getOrNull(selectedLayerIndex)
                                    when {
                                        l == null -> addLayer(effect)
                                        l.effect.id == effect.id ->
                                            panel = if (panel == PanelMode.EFFECT) PanelMode.NONE else PanelMode.EFFECT
                                        else -> l.switchEffect(effect)
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // ---- Effect params panel (hidden until re-tap on selected effect) ----
            androidx.compose.animation.AnimatedVisibility(
                visible = panel == PanelMode.EFFECT && selectedLayer != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val l = selectedLayer ?: return@AnimatedVisibility
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp)) {
                        l.effect.params.forEachIndexed { i, p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    p.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${(l.params.getOrElse(i) { p.default } * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(
                                value = l.params.getOrElse(i) { p.default },
                                onValueChange = { l.params[i] = it },
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (l.effect.positionable) {
                                Text(
                                    stringResource(R.string.drag_hint),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            TextButton(onClick = {
                                l.effect.params.forEachIndexed { i, p -> l.params[i] = p.default }
                                l.centerX = 0.5f
                                l.centerY = 0.5f
                            }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.reset_params))
                            }
                        }
                    }
                }
            }

            // ---- Layer panel: blend modes, opacity, visibility, delete ----
            androidx.compose.animation.AnimatedVisibility(
                visible = panel == PanelMode.LAYER && selectedLayer != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                val l = selectedLayer ?: return@AnimatedVisibility
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(Modifier.padding(top = 12.dp, bottom = 14.dp)) {
                        val blendRows = remember {
                            LayerBlendMode.entries.toList().chunked((LayerBlendMode.entries.size + 1) / 2)
                        }
                        blendRows.forEach { row ->
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(bottom = 6.dp),
                            ) {
                                items(row, key = { it.name }) { mode ->
                                    val active = l.blend == mode
                                    Surface(
                                        shape = CircleShape,
                                        color = if (active) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { l.blend = mode },
                                    ) {
                                        Text(
                                            mode.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (active) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        )
                                    }
                                }
                            }
                        }
                        Column(Modifier.padding(horizontal = 20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.opacity),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${(l.opacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Slider(value = l.opacity, onValueChange = { l.opacity = it })
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.layer_visible),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Spacer(Modifier.width(10.dp))
                                Switch(checked = l.visible, onCheckedChange = { l.visible = it })
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = {
                                    val idx = layers.indexOf(l)
                                    if (idx >= 0) layers.removeAt(idx)
                                    selectedLayerIndex = (selectedLayerIndex - 1).coerceAtLeast(0)
                                    if (layers.isEmpty()) panel = PanelMode.NONE
                                }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(stringResource(R.string.delete_layer))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayerChip(
    layer: EffectLayer,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val background = if (isSelected) {
        Modifier.background(
            Brush.linearGradient(
                listOf(Color(layer.effect.accentStart), Color(layer.effect.accentEnd))
            )
        )
    } else {
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    Box(
        modifier = Modifier
            .alpha(if (layer.visible) 1f else 0.45f)
            .clip(shape)
            .then(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                layer.effect.name,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${layer.blend.label} · ${(layer.opacity * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.White.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "cardScale",
    )
    val shape = RoundedCornerShape(18.dp)
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
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Column {
            Text(
                effect.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                effect.tagline,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) Color.White.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
