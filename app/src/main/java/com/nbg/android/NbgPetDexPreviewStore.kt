package com.nbg.android

import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.collection.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal class NbgPetDexPreviewStore {
  private val cache = LruCache<String, ImageBitmap>(64)

  suspend fun loadPreview(pet: NbgPetDexManifestPet): ImageBitmap? =
    cache[pet.slug] ?: withContext(Dispatchers.IO) {
      runCatching {
        val review = nbgReviewPetDexResourceUrl(pet.spritesheetUrl, NbgPetResourceFileKind.Sprite)
        if (!review.allowUse) return@runCatching null
        val bytes = downloadBytes(pet.spritesheetUrl)
        val preview = decodeFirstFrame(bytes) ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        if (preview != null) cache.put(pet.slug, preview)
        preview
      }.getOrNull()
    }

  private fun downloadBytes(url: String): ByteArray {
    val request = Request.Builder().url(url).get().build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) error("HTTP ${response.code}")
      val finalUrlReview = nbgReviewPetDexResourceUrl(response.request.url.toString(), NbgPetResourceFileKind.Sprite)
      if (!finalUrlReview.allowUse) error(finalUrlReview.reason)
      val length = response.body?.contentLength() ?: -1L
      if (length > NBG_PET_MAX_SPRITE_BYTES) error("PetDex sprite 文件过大，最大 ${NBG_PET_MAX_SPRITE_BYTES / 1024L}KB")
      return response.body?.byteStream()?.use {
        nbgReadPetResourceBytesWithLimit(it, NBG_PET_MAX_SPRITE_BYTES, "PetDex sprite")
      } ?: error("empty body")
    }
  }

  private fun decodeFirstFrame(bytes: ByteArray): ImageBitmap? {
    val decoder = BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false) ?: return null
    return runCatching {
      decoder.decodeRegion(
        Rect(0, 0, NBG_PET_FRAME_WIDTH_PX, NBG_PET_FRAME_HEIGHT_PX),
        BitmapFactory.Options().apply {
          inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        },
      )?.asImageBitmap()
    }.also {
      decoder.recycle()
    }.getOrNull()
  }

  private companion object {
    val client: OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(8, TimeUnit.SECONDS)
      .readTimeout(16, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()
  }
}
