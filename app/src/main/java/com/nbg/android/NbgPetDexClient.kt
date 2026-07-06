package com.nbg.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal class NbgPetDexClient(
  private val manifestProvenanceDir: File? = null,
) {
  private val client = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(45, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

  suspend fun fetchManifest(): NbgPetDexManifest =
    withContext(Dispatchers.IO) {
      val review = nbgReviewPetDexResourceUrl(NBG_PETDEX_MANIFEST_URL, NbgPetResourceFileKind.Manifest)
      if (!review.allowUse) error(review.reason)
      val request = Request.Builder().url(NBG_PETDEX_MANIFEST_URL).get().build()
      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        val finalUrlReview = nbgReviewPetDexResourceUrl(response.request.url.toString(), NbgPetResourceFileKind.Manifest)
        if (!finalUrlReview.allowUse) error(finalUrlReview.reason)
        val length = response.body?.contentLength() ?: -1L
        if (length > NBG_PETDEX_MANIFEST_MAX_BYTES) error("PetDex manifest 文件过大，最大 ${NBG_PETDEX_MANIFEST_MAX_BYTES / 1024L}KB")
        val bytes = response.body?.byteStream()?.use {
          nbgReadPetResourceBytesWithLimit(it, NBG_PETDEX_MANIFEST_MAX_BYTES, "PetDex manifest")
        } ?: error("empty body")
        val rawManifest = bytes.toString(Charsets.UTF_8)
        val manifest = nbgParsePetDexManifest(rawManifest)
        manifestProvenanceDir?.let { dir ->
          runCatching {
            nbgWritePetDexManifestProvenance(
              review = finalUrlReview,
              rawManifest = rawManifest,
              manifest = manifest,
              targetDir = dir,
              rawManifestBytes = bytes,
            )
          }.getOrElse {
            error("PetDex manifest provenance 写入失败")
          }
        }
        manifest
      }
    }
}

internal fun nbgParsePetDexManifest(raw: String): NbgPetDexManifest {
  val root = JSONObject(raw)
  val array = root.optJSONArray("pets") ?: JSONArray()
  val pets = buildList {
    for (index in 0 until array.length()) {
      val item = array.optJSONObject(index) ?: continue
      val slug = item.optString("slug").trim()
      val spritesheetUrl = item.optString("spritesheetUrl").trim()
      val petJsonUrl = item.optString("petJsonUrl").trim()
      if (slug.isBlank() || spritesheetUrl.isBlank() || petJsonUrl.isBlank()) continue
      add(
        NbgPetDexManifestPet(
          slug = slug,
          displayName = item.optString("displayName").trim().ifBlank { slug },
          kind = item.optString("kind").trim().ifBlank { "character" },
          submittedBy = item.optString("submittedBy").trim(),
          spritesheetUrl = spritesheetUrl,
          petJsonUrl = petJsonUrl,
          zipUrl = item.optString("zipUrl").trim(),
        ),
      )
    }
  }
  return NbgPetDexManifest(
    generatedAt = root.optString("generatedAt"),
    total = root.optInt("total", pets.size),
    pets = pets,
  )
}

internal fun nbgFilterPetDexPets(
  pets: List<NbgPetDexManifestPet>,
  query: String,
  characterOnly: Boolean = true,
  limit: Int = NBG_PETDEX_VISIBLE_LIMIT,
): List<NbgPetDexManifestPet> {
  val needle = query.trim().lowercase()
  return pets.asSequence()
    .filter { !characterOnly || it.kind.lowercase() == "character" || it.kind.lowercase() == "creature" }
    .filter {
      needle.isBlank() ||
        it.displayName.lowercase().contains(needle) ||
        it.slug.lowercase().contains(needle) ||
        it.submittedBy.lowercase().contains(needle)
    }
    .take(limit.coerceAtLeast(1))
    .toList()
}

internal fun nbgCountPetDexMatches(
  pets: List<NbgPetDexManifestPet>,
  query: String,
  characterOnly: Boolean = true,
): Int {
  val needle = query.trim().lowercase()
  return pets.asSequence()
    .filter { !characterOnly || it.kind.lowercase() == "character" || it.kind.lowercase() == "creature" }
    .count {
      needle.isBlank() ||
        it.displayName.lowercase().contains(needle) ||
        it.slug.lowercase().contains(needle) ||
        it.submittedBy.lowercase().contains(needle)
    }
}

private const val NBG_PETDEX_MANIFEST_URL = "https://petdex.dev/api/manifest"
