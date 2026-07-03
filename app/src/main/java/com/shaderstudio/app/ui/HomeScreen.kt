package com.shaderstudio.app.ui

import android.graphics.Bitmap
import android.graphics.RuntimeShader
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shaderstudio.app.R
import com.shaderstudio.app.shaders.HERO_SHADERS
import com.shaderstudio.app.util.GalleryItem
import com.shaderstudio.app.util.loadBitmap
import com.shaderstudio.app.util.loadThumbnail
import com.shaderstudio.app.util.queryEditedImages
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun HomeScreen(onOpen: (Bitmap) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // A random abstract background with a random palette on every launch.
    val heroSource = remember { HERO_SHADERS.random() }
    val heroShader = remember { RuntimeShader(heroSource) }
    val heroSeed = remember { Random.nextFloat() }
    val timeOffset = remember { Random.nextFloat() * 4096f }
    var time by remember { mutableFloatStateOf(timeOffset) }
    LaunchedEffect(Unit) {
        val start = System.nanoTime()
        while (isActive) {
            withFrameNanos { now -> time = timeOffset + (now - start) / 1_000_000_000f }
        }
    }

    val album = remember { mutableStateListOf<GalleryItem>() }
    LaunchedEffect(Unit) {
        album.clear()
        album.addAll(queryEditedImages(context))
    }

    // Fly-in animation: the chosen photo shrinks into the pick button.
    var flyBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var buttonCenter by remember { mutableStateOf<Offset?>(null) }
    val flyProgress = remember { Animatable(0f) }

    fun openWithAnimation(bmp: Bitmap) {
        flyBitmap = bmp
        scope.launch {
            flyProgress.snapTo(0f)
            flyProgress.animateTo(1f, tween(durationMillis = 480))
            onOpen(bmp)
            flyBitmap = null
            flyProgress.snapTo(0f)
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch { loadBitmap(context, uri)?.let { openWithAnimation(it) } }
        }
    }

    fun pickPhoto() {
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun openAlbumItem(item: GalleryItem) {
        scope.launch { loadBitmap(context, item.uri)?.let { openWithAnimation(it) } }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Animated shader background.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    heroShader.setFloatUniform("uResolution", size.width, size.height)
                    heroShader.setFloatUniform("uSeed", heroSeed)
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
                        1.0f to Color(0xF2000000),
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

            // ---- Album of edited photos ----
            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(R.string.album_title),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(10.dp))
            if (album.isEmpty()) {
                Text(
                    text = stringResource(R.string.album_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(album, key = { it.id }) { item ->
                        AlbumTile(item = item, onClick = { openAlbumItem(item) })
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { pickPhoto() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .onGloballyPositioned {
                        val pos = it.positionInRoot()
                        buttonCenter = Offset(pos.x + it.size.width / 2f, pos.y + it.size.height / 2f)
                    },
                shape = RoundedCornerShape(24.dp),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.pick_photo), style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
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

        // ---- Fly-in overlay ----
        val fb = flyBitmap
        if (fb != null) {
            val target = buttonCenter
            val img = remember(fb) { fb.asImageBitmap() }
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = flyProgress.value
                        val scale = 1f - 0.82f * p
                        scaleX = scale
                        scaleY = scale
                        if (target != null) {
                            translationX = (target.x - size.width / 2f) * p
                            translationY = (target.y - size.height / 2f) * p
                        }
                        alpha = 1f - (p * p)
                        val corner = 48f * p
                        shape = RoundedCornerShape(corner.dp)
                        clip = true
                    },
            )
        }
    }
}

@Composable
private fun AlbumTile(item: GalleryItem, onClick: () -> Unit) {
    val context = LocalContext.current
    var thumb by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.id) {
        thumb = loadThumbnail(context, item.uri)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0x22FFFFFF),
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
    ) {
        val t = thumb
        if (t != null) {
            Image(
                bitmap = t.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
