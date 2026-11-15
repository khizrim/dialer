package com.khizrim.dialer.ui.kit

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Миниатюры контактов, уже прочитанные с диска: лента не мигает при каждом показе. */
private val thumbs = LruCache<String, ImageBitmap>(48)

/** Фото контакта, если оно есть и читается. Пока не загрузилось или его нет — ничего: никаких заглушек и букв. */
@Composable
fun rememberContactPhoto(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val photo by produceState(uri?.let { thumbs.get(it) }, uri) {
        value = uri?.let { thumbs.get(it) ?: loadThumb(context, it) }
    }
    return photo
}

private suspend fun loadThumb(context: android.content.Context, uri: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    }.getOrNull()?.also { thumbs.put(uri, it) }
}

@Composable
fun ContactPhoto(photo: ImageBitmap, size: Dp, modifier: Modifier = Modifier) {
    Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.size(size).clip(CircleShape))
}
