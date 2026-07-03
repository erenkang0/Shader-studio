package com.shaderstudio.app.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A previously exported still image from Pictures/Shader Studio. */
data class GalleryItem(val uri: Uri, val id: Long)

/**
 * Lists the app's exported images (newest first) so the home screen can show
 * an album of edited photos. Only JPEG/PNG stills in Pictures/Shader Studio are
 * returned — GIFs are skipped because they can't be re-edited meaningfully.
 */
suspend fun queryEditedImages(context: Context, limit: Int = 30): List<GalleryItem> =
    withContext(Dispatchers.IO) {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.MIME_TYPE,
        )
        val selection =
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? AND ${MediaStore.Images.Media.MIME_TYPE} != ?"
        val args = arrayOf("%Shader Studio%", "image/gif")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        val items = ArrayList<GalleryItem>()
        try {
            context.contentResolver.query(collection, projection, selection, args, sort)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (c.moveToNext() && items.size < limit) {
                    val id = c.getLong(idCol)
                    val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString()).build()
                    items.add(GalleryItem(uri, id))
                }
            }
        } catch (e: Exception) {
            // Fall through to whatever we collected (possibly empty).
        }
        items
    }

/** Loads a small square thumbnail for an album tile. */
suspend fun loadThumbnail(context: Context, uri: Uri, px: Int = 256): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            context.contentResolver.loadThumbnail(uri, Size(px, px), null)
        } catch (e: Exception) {
            null
        }
    }
