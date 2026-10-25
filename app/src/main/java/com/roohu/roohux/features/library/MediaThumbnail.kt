package com.roohu.roohux.features.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Real embedded audio cover art or a frame from a local video; no network access. */
@Composable
internal fun MediaThumbnail(item: LibraryItem, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = item.uri, key2 = item.isVideo) {
        value = withContext(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, Uri.parse(item.uri))
                    if (item.isVideo) {
                        retriever.getScaledFrameAtTime(
                            1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 180
                        )
                    } else {
                        retriever.embeddedPicture?.let { bytes ->
                            BitmapFactory.Options().run {
                                inJustDecodeBounds = true
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
                                inSampleSize = calculateSampleSize(outWidth, outHeight, 320)
                                inJustDecodeBounds = false
                                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, this)
                            }
                        }
                    }
                } finally { retriever.release() }
            } catch (_: Exception) { null }
        }
    }
    Box(modifier.size(width = 88.dp, height = 64.dp).clip(RoundedCornerShape(10.dp))
        .background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), contentDescription = if (item.isVideo) "Video thumbnail" else "Album artwork", modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else Text(if (item.isVideo) "VIDEO" else "AUDIO", style = MaterialTheme.typography.labelSmall)
    }
}

private fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
    var sample = 1
    while (width / sample > target * 2 || height / sample > target * 2) sample *= 2
    return sample
}
