package com.nbg.android

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal class NbgPetStore(context: Context) {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun loadState(): NbgPetStoreState {
    ensureBundledPetsSeeded()
    val installed = loadInstalledPets()
    val current = prefs.getString(KEY_CURRENT, NBG_DEFAULT_PET_SLUG).orEmpty().ifBlank { NBG_DEFAULT_PET_SLUG }
    val validCurrent = installed.firstOrNull { it.slug == current }?.slug ?: NBG_DEFAULT_PET_SLUG
    if (validCurrent != current) {
      prefs.edit().putString(KEY_CURRENT, validCurrent).apply()
    }
    return NbgPetStoreState(
      installedPets = installed,
      currentPetSlug = validCurrent,
      hidden = prefs.getBoolean(KEY_HIDDEN, false),
    )
  }

  fun setCurrent(slug: String): NbgPetStoreState {
    val installed = loadInstalledPets()
    val valid = installed.firstOrNull { it.slug == slug }?.slug ?: NBG_DEFAULT_PET_SLUG
    prefs.edit()
      .putString(KEY_CURRENT, valid)
      .putBoolean(KEY_HIDDEN, false)
      .apply()
    return loadState()
  }

  fun setHidden(hidden: Boolean): NbgPetStoreState {
    prefs.edit().putBoolean(KEY_HIDDEN, hidden).apply()
    return loadState()
  }

  fun delete(slug: String): NbgPetStoreState {
    if (slug == NBG_DEFAULT_PET_SLUG) return loadState()
    val next = loadInstalledPets().filterNot { it.slug == slug || it.builtIn }
    val safeSlug = slug.safePetFileName()
    if (safeSlug.isNotBlank() && safeSlug == slug) {
      File(petsDir(), safeSlug).deleteRecursively()
    }
    prefs.edit()
      .putString(KEY_INSTALLED, JSONArray(next.map { it.toJson() }).toString())
      .putString(KEY_CURRENT, if (prefs.getString(KEY_CURRENT, "") == slug) NBG_DEFAULT_PET_SLUG else prefs.getString(KEY_CURRENT, NBG_DEFAULT_PET_SLUG))
      .apply()
    return loadState()
  }

  suspend fun install(manifestPet: NbgPetDexManifestPet): NbgPetSummary =
    withContext(Dispatchers.IO) {
      val review = nbgReviewPetDexInstall(manifestPet)
      if (!review.allowInstall) error(review.reason)
      val slug = review.safeSlug.ifBlank { manifestPet.displayName.sha256Hex().take(16) }
      val dir = File(petsDir(), slug)
      val stagingDir = File(petsDir(), ".$slug-installing").apply {
        deleteRecursively()
        mkdirs()
      }
      try {
        val spriteStagingFile = File(stagingDir, "sprite.webp")
        val petJsonStagingFile = File(stagingDir, "pet.json")
        val installedAtMs = System.currentTimeMillis()
        val spriteDownload = downloadToFile(
          url = manifestPet.spritesheetUrl,
          file = spriteStagingFile,
          fileKind = NbgPetResourceFileKind.Sprite,
          maxBytes = NBG_PET_MAX_SPRITE_BYTES,
        )
        val petJsonDownload = downloadToFile(
          url = manifestPet.petJsonUrl,
          file = petJsonStagingFile,
          fileKind = NbgPetResourceFileKind.PetJson,
          maxBytes = NBG_PET_MAX_JSON_BYTES,
        )
        nbgWritePetResourceProvenance(
          review = review,
          pet = manifestPet,
          sprite = spriteDownload,
          petJson = petJsonDownload,
          targetDir = stagingDir,
          installedAtMs = installedAtMs,
        )
        commitStagedPetDir(stagingDir, dir, slug)
        val spriteFile = File(dir, "sprite.webp")
        val petJsonFile = File(dir, "pet.json")
        val installed = NbgPetSummary(
          slug = slug,
          displayName = manifestPet.displayName,
          kind = manifestPet.kind,
          submittedBy = manifestPet.submittedBy,
          source = "petdex",
          spritesheetUrl = manifestPet.spritesheetUrl,
          petJsonUrl = manifestPet.petJsonUrl,
          spritePath = spriteFile.absolutePath,
          petJsonPath = petJsonFile.absolutePath,
          installedAtMs = installedAtMs,
        )
        val next = (loadInstalledPets().filterNot { it.slug == installed.slug || it.builtIn } + installed)
          .sortedByDescending { it.installedAtMs }
        prefs.edit()
          .putString(KEY_INSTALLED, JSONArray(next.map { it.toJson() }).toString())
          .putString(KEY_CURRENT, installed.slug)
          .putBoolean(KEY_HIDDEN, false)
          .apply()
        installed
      } finally {
        if (stagingDir.exists()) stagingDir.deleteRecursively()
      }
    }

  private fun loadInstalledPets(): List<NbgPetSummary> {
    val saved = runCatching {
      val raw = prefs.getString(KEY_INSTALLED, "[]").orEmpty()
      val array = JSONArray(raw)
      buildList {
        for (index in 0 until array.length()) {
          parsePet(array.optJSONObject(index))?.let { add(it) }
        }
      }
    }.getOrDefault(emptyList())
    return (listOf(nbgDefaultPetSummary()) + saved.filterNot { it.slug == NBG_DEFAULT_PET_SLUG })
      .distinctBy { it.slug }
  }

  private fun petsDir(): File = File(appContext.filesDir, "nbg-pets").apply { mkdirs() }

  private fun ensureBundledPetsSeeded() {
    val seededVersion = prefs.getInt(KEY_BUNDLED_VERSION, 0)
    if (seededVersion >= BUNDLED_VERSION) return
    val installed = loadInstalledPets().filterNot { it.builtIn }
    val installedSlugs = installed.map { it.slug }.toSet()
    val seeded = nbgBundledPetSeeds()
      .filterNot { it.slug in installedSlugs }
      .mapNotNull { seed -> installBundledPet(seed) }
    val next = (installed + seeded)
      .distinctBy { it.slug }
      .sortedByDescending { it.installedAtMs }
    prefs.edit()
      .putString(KEY_INSTALLED, JSONArray(next.map { it.toJson() }).toString())
      .putInt(KEY_BUNDLED_VERSION, BUNDLED_VERSION)
      .apply()
  }

  private fun installBundledPet(seed: NbgBundledPetSeed): NbgPetSummary? =
    runCatching {
      val dir = File(petsDir(), seed.slug.safePetFileName()).apply { mkdirs() }
      val spriteFile = File(dir, "sprite.webp")
      val petJsonFile = File(dir, "pet.json")
      copyAssetIfMissing("nbg-default-pets/${seed.slug}/sprite.webp", spriteFile)
      copyAssetIfMissing("nbg-default-pets/${seed.slug}/pet.json", petJsonFile)
      val installedAtMs = BUNDLED_INSTALLED_AT_MS - nbgBundledPetSeeds().indexOf(seed)
      nbgWriteBundledPetResourceProvenance(
        seed = seed,
        spriteFile = spriteFile,
        petJsonFile = petJsonFile,
        targetDir = dir,
        installedAtMs = installedAtMs,
      )
      NbgPetSummary(
        slug = seed.slug,
        displayName = seed.displayName,
        kind = seed.kind,
        submittedBy = seed.submittedBy,
        source = "built-in",
        spritesheetUrl = seed.spritesheetUrl,
        petJsonUrl = seed.petJsonUrl,
        spritePath = spriteFile.absolutePath,
        petJsonPath = petJsonFile.absolutePath,
        installedAtMs = installedAtMs,
        builtIn = true,
      )
    }.getOrNull()

  private fun copyAssetIfMissing(assetPath: String, target: File) {
    if (target.isFile && target.length() > 0L) return
    val tmp = File(target.parentFile ?: petsDir(), "${target.name}.tmp")
    appContext.assets.open(assetPath).use { input ->
      tmp.outputStream().use { output -> input.copyTo(output) }
    }
    if (!tmp.renameTo(target)) {
      tmp.copyTo(target, overwrite = true)
      tmp.delete()
    }
  }

  private fun downloadToFile(
    url: String,
    file: File,
    fileKind: NbgPetResourceFileKind,
    maxBytes: Long,
  ): NbgPetDownloadedResource {
    val review = nbgReviewPetDexResourceUrl(url, fileKind)
    if (!review.allowUse) error(review.reason)
    val request = Request.Builder().url(url).get().build()
    client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) error("HTTP ${response.code}")
      val finalUrlReview = nbgReviewPetDexResourceUrl(response.request.url.toString(), fileKind)
      if (!finalUrlReview.allowUse) error(finalUrlReview.reason)
      val length = response.body?.contentLength() ?: -1L
      if (length > maxBytes) {
        error("PetDex ${fileKind.wireName} 文件过大，最大 ${maxBytes / 1024L}KB")
      }
      val body = response.body ?: error("empty body")
      val parent = file.parentFile ?: error("missing parent directory")
      val tmp = File(parent, "${file.name}.tmp")
      val digest = MessageDigest.getInstance("SHA-256")
      var copied = 0L
      try {
        body.byteStream().use { input ->
          tmp.outputStream().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
              val read = input.read(buffer)
              if (read < 0) break
              copied += read
              if (copied > maxBytes) {
                throw IOException("PetDex ${fileKind.wireName} 文件过大，最大 ${maxBytes / 1024L}KB")
              }
              digest.update(buffer, 0, read)
              output.write(buffer, 0, read)
            }
          }
        }
        nbgMoveReplacingWithAtomicFallback(tmp, file)
      } finally {
        if (tmp.isFile) tmp.delete()
      }
      return NbgPetDownloadedResource(
        fileKind = fileKind,
        fileName = file.name,
        sizeBytes = copied,
        sha256 = digest.digest().joinToString("") { "%02x".format(it) },
        sourceHost = finalUrlReview.sourceHost,
        redactedSource = finalUrlReview.redactedLocation,
      )
    }
  }

  private fun commitStagedPetDir(stagingDir: File, targetDir: File, slug: String) {
    val backupDir = File(petsDir(), ".$slug-backup-${System.currentTimeMillis()}")
    var backupCreated = false
    if (targetDir.exists()) {
      nbgMoveReplacingWithAtomicFallback(targetDir, backupDir)
      backupCreated = true
    }
    try {
      nbgMoveReplacingWithAtomicFallback(stagingDir, targetDir)
      if (backupCreated) backupDir.deleteRecursively()
    } catch (error: Throwable) {
      if (targetDir.exists()) targetDir.deleteRecursively()
      if (backupCreated && backupDir.exists()) {
        runCatching { nbgMoveReplacingWithAtomicFallback(backupDir, targetDir) }
      }
      throw error
    } finally {
      if (backupDir.exists()) backupDir.deleteRecursively()
    }
  }

  private fun parsePet(root: JSONObject?): NbgPetSummary? {
    if (root == null) return null
    val slug = root.optString("slug").trim()
    if (slug.isBlank()) return null
    val spritePath = root.optString("spritePath").trim().ifBlank { null }
    if (spritePath != null && !File(spritePath).isFile) return null
    return NbgPetSummary(
      slug = slug,
      displayName = root.optString("displayName").trim().ifBlank { slug },
      kind = root.optString("kind").trim().ifBlank { "character" },
      submittedBy = root.optString("submittedBy").trim(),
      source = root.optString("source").trim(),
      spritesheetUrl = root.optString("spritesheetUrl").trim(),
      petJsonUrl = root.optString("petJsonUrl").trim(),
      spritePath = spritePath,
      petJsonPath = root.optString("petJsonPath").trim().ifBlank { null },
      installedAtMs = root.optLong("installedAtMs", 0L),
      builtIn = root.optBoolean("builtIn", false),
    )
  }

  private fun NbgPetSummary.toJson(): JSONObject =
    JSONObject()
      .put("slug", slug)
      .put("displayName", displayName)
      .put("kind", kind)
      .put("submittedBy", submittedBy)
      .put("source", source)
      .put("spritesheetUrl", spritesheetUrl)
      .put("petJsonUrl", petJsonUrl)
      .put("spritePath", spritePath.orEmpty())
      .put("petJsonPath", petJsonPath.orEmpty())
      .put("installedAtMs", installedAtMs)
      .put("builtIn", builtIn)

  private companion object {
    const val PREFS = "nbg_pet_store"
    const val KEY_INSTALLED = "installed"
    const val KEY_CURRENT = "current"
    const val KEY_HIDDEN = "hidden"
    const val KEY_BUNDLED_VERSION = "bundled_version"
    const val BUNDLED_VERSION = 1
    const val BUNDLED_INSTALLED_AT_MS = 1_782_650_000_000L
    val client: OkHttpClient = OkHttpClient.Builder()
      .connectTimeout(12, TimeUnit.SECONDS)
      .readTimeout(45, TimeUnit.SECONDS)
      .writeTimeout(20, TimeUnit.SECONDS)
      .followRedirects(true)
      .build()
  }
}
