package com.nbg.android

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

internal const val NBG_PET_RESOURCE_INTEGRITY_VERSION = "nbg-pet-resource-integrity-v1"
internal const val NBG_PET_PROVENANCE_FILE = ".nbg-pet-provenance.json"
internal const val NBG_PETDEX_MANIFEST_PROVENANCE_FILE = ".nbg-petdex-manifest-provenance.json"
internal const val NBG_PETDEX_TRUSTED_PROMOTION_POLICY = "nbg-petdex-trusted-promotion-v1"
internal const val NBG_PETDEX_MANIFEST_MAX_BYTES = 1L * 1024L * 1024L
internal const val NBG_PET_MAX_SPRITE_BYTES = 8L * 1024L * 1024L
internal const val NBG_PET_MAX_JSON_BYTES = 512L * 1024L

internal val NBG_PETDEX_TRUSTED_REQUIRED_EVIDENCE: List<String> =
  listOf("signed_manifest", "per_resource_sha256_manifest")

internal enum class NbgPetResourceFileKind(val wireName: String, val maxBytes: Long) {
  Manifest("manifest", NBG_PETDEX_MANIFEST_MAX_BYTES),
  Sprite("sprite", NBG_PET_MAX_SPRITE_BYTES),
  PetJson("pet_json", NBG_PET_MAX_JSON_BYTES),
}

internal enum class NbgPetResourceSourceKind(val wireName: String) {
  BuiltIn("built_in"),
  PetDexHttps("petdex_https"),
  UserManaged("user_managed"),
  Unknown("unknown"),
}

internal enum class NbgPetTrustTier(val wireName: String) {
  TrustedBundled("trusted_bundled"),
  UnverifiedPetDex("unverified_petdex"),
  UserManaged("user_managed"),
  Blocked("blocked"),
}

internal data class NbgPetResourceUrlReview(
  val fileKind: NbgPetResourceFileKind,
  val rawUrl: String,
  val allowUse: Boolean,
  val sourceHost: String = "",
  val redactedLocation: String = "",
  val reason: String = "",
)

internal data class NbgPetDexInstallReview(
  val sourceKind: NbgPetResourceSourceKind,
  val trustTier: NbgPetTrustTier,
  val allowInstall: Boolean,
  val requiresReview: Boolean,
  val safeSlug: String = "",
  val sourceHost: String = "",
  val spriteReview: NbgPetResourceUrlReview? = null,
  val petJsonReview: NbgPetResourceUrlReview? = null,
  val reason: String = "",
)

internal data class NbgPetDownloadedResource(
  val fileKind: NbgPetResourceFileKind,
  val fileName: String,
  val sizeBytes: Long,
  val sha256: String,
  val sourceHost: String,
  val redactedSource: String,
)

internal data class NbgPetDexManifestProvenance(
  val schema: String,
  val sourceKind: NbgPetResourceSourceKind,
  val trustTier: NbgPetTrustTier,
  val requiresReview: Boolean,
  val sourceHost: String,
  val redactedSource: String,
  val sizeBytes: Long,
  val sha256: String,
  val maxBytes: Long,
  val declaredTotal: Int,
  val parsedPetCount: Int,
  val fetchedAtMs: Long,
)

internal data class NbgPetDexTrustedPromotionReview(
  val policyVersion: String,
  val eligibleForTrustedDefault: Boolean,
  val trustTier: NbgPetTrustTier,
  val requiresReview: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val reason: String,
)

internal data class NbgPetSummaryResourceReview(
  val sourceKind: NbgPetResourceSourceKind,
  val trustTier: NbgPetTrustTier,
  val requiresReview: Boolean,
  val reason: String,
)

internal fun nbgPetDexManifestProvenanceDir(filesDir: File): File =
  File(filesDir, "nbg-pets")

internal fun nbgReviewPetDexTrustedPromotion(
  review: NbgPetDexInstallReview,
  signedManifestPresent: Boolean,
  perResourceSha256ManifestPresent: Boolean,
): NbgPetDexTrustedPromotionReview {
  val presentEvidence = buildList {
    if (signedManifestPresent) add("signed_manifest")
    if (perResourceSha256ManifestPresent) add("per_resource_sha256_manifest")
  }
  return NbgPetDexTrustedPromotionReview(
    policyVersion = NBG_PETDEX_TRUSTED_PROMOTION_POLICY,
    eligibleForTrustedDefault = false,
    trustTier = review.trustTier,
    requiresReview = true,
    requiredEvidence = NBG_PETDEX_TRUSTED_REQUIRED_EVIDENCE,
    presentEvidence = presentEvidence,
    reason = "Public beta v1 does not promote PetDex resources to trusted bundled defaults; future promotion requires both signed manifest evidence and per-resource SHA-256 manifest evidence.",
  )
}

internal fun nbgReviewPetDexInstall(pet: NbgPetDexManifestPet): NbgPetDexInstallReview {
  val rawSlug = pet.slug.trim()
  val safeSlug = rawSlug.safePetFileName()
  if (rawSlug.isBlank()) return nbgBlockedPetDexInstall("PetDex slug 不能为空")
  if (safeSlug.isBlank() || rawSlug != safeSlug) {
    return nbgBlockedPetDexInstall("PetDex slug 必须只包含小写字母、数字、点、下划线和连字符，且最长 80 个字符")
  }

  val spriteReview = nbgReviewPetDexResourceUrl(pet.spritesheetUrl, NbgPetResourceFileKind.Sprite)
  if (!spriteReview.allowUse) return nbgBlockedPetDexInstall(spriteReview.reason, spriteReview.sourceHost)

  val petJsonReview = nbgReviewPetDexResourceUrl(pet.petJsonUrl, NbgPetResourceFileKind.PetJson)
  if (!petJsonReview.allowUse) return nbgBlockedPetDexInstall(petJsonReview.reason, petJsonReview.sourceHost)

  return NbgPetDexInstallReview(
    sourceKind = NbgPetResourceSourceKind.PetDexHttps,
    trustTier = NbgPetTrustTier.UnverifiedPetDex,
    allowInstall = true,
    requiresReview = true,
    safeSlug = safeSlug,
    sourceHost = spriteReview.sourceHost.ifBlank { petJsonReview.sourceHost },
    spriteReview = spriteReview,
    petJsonReview = petJsonReview,
    reason = "PetDex 资源通过 HTTPS 与来源证明进入 beta 路径，默认仍视为未验证外部资源。",
  )
}

internal fun nbgReviewPetDexResourceUrl(
  rawUrl: String,
  fileKind: NbgPetResourceFileKind,
): NbgPetResourceUrlReview {
  val source = rawUrl.trim()
  if (source.isBlank()) {
    return nbgBlockedPetResourceUrl(fileKind, source, "PetDex 资源 URL 不能为空")
  }
  val uri = runCatching { URI(source) }.getOrNull()
    ?: return nbgBlockedPetResourceUrl(fileKind, source, "PetDex 资源 URL 格式无效")
  val scheme = uri.scheme.orEmpty().lowercase()
  val host = uri.host.orEmpty().lowercase()
  val redacted = uri.toNbgRedactedPetLocation(fileKind)
  if (scheme != "https") {
    return nbgBlockedPetResourceUrl(fileKind, source, "PetDex 资源必须使用 https", host, redacted)
  }
  if (!uri.userInfo.isNullOrBlank()) {
    return nbgBlockedPetResourceUrl(fileKind, source, "PetDex 资源 URL 不能包含用户名、密码或 token", host, redacted)
  }
  if (host.isBlank() || host.isNbgUnsafePetHostLiteral()) {
    return nbgBlockedPetResourceUrl(fileKind, source, "PetDex 资源 host 无效或指向本机/私有地址", host, redacted)
  }
  if (!host.isNbgAllowedPetDexHost()) {
    return nbgBlockedPetResourceUrl(fileKind, source, "PetDex 资源 host 必须属于 petdex.dev", host, redacted)
  }
  val path = uri.path.orEmpty().lowercase()
  val validPath = when (fileKind) {
    NbgPetResourceFileKind.Manifest -> path == "/api/manifest" || path.endsWith("/manifest.json") || path.endsWith(".json")
    NbgPetResourceFileKind.Sprite -> path.endsWith(".webp")
    NbgPetResourceFileKind.PetJson -> path.endsWith(".json")
  }
  if (!validPath) {
    return nbgBlockedPetResourceUrl(fileKind, source, "PetDex ${fileKind.wireName} 文件扩展名不符合预期", host, redacted)
  }
  return NbgPetResourceUrlReview(
    fileKind = fileKind,
    rawUrl = source,
    allowUse = true,
    sourceHost = host,
    redactedLocation = redacted,
  )
}

internal fun nbgPetSummarySourceReview(pet: NbgPetSummary): NbgPetSummaryResourceReview {
  val source = pet.source.trim().lowercase()
  return when {
    pet.builtIn || pet.hasNbgTrustedBundledPetProvenance(source) -> NbgPetSummaryResourceReview(
      sourceKind = NbgPetResourceSourceKind.BuiltIn,
      trustTier = NbgPetTrustTier.TrustedBundled,
      requiresReview = false,
      reason = "随 APK 打包的默认 Pet 资产，由 release gate 资产 checksum 覆盖。",
    )
    source == "petdex" || source == NbgPetResourceSourceKind.PetDexHttps.wireName -> NbgPetSummaryResourceReview(
      sourceKind = NbgPetResourceSourceKind.PetDexHttps,
      trustTier = NbgPetTrustTier.UnverifiedPetDex,
      requiresReview = true,
      reason = "PetDex 远程资源属于 beta 路径，需要保留来源证明和可删除能力。",
    )
    source.isNbgBuiltInPetSourceLabel() -> NbgPetSummaryResourceReview(
      sourceKind = NbgPetResourceSourceKind.Unknown,
      trustTier = NbgPetTrustTier.Blocked,
      requiresReview = true,
      reason = "内置 Pet 标签必须由 bundled 标记或可信 provenance 证明，不能只凭来源字符串进入可信集合。",
    )
    source.isNbgUserManagedPetSource() -> NbgPetSummaryResourceReview(
      sourceKind = NbgPetResourceSourceKind.UserManaged,
      trustTier = NbgPetTrustTier.UserManaged,
      requiresReview = true,
      reason = "用户导入或本地侧载的 Pet 由用户管理，必须保持可审查、可删除且不能进入可信默认集合。",
    )
    else -> NbgPetSummaryResourceReview(
      sourceKind = NbgPetResourceSourceKind.Unknown,
      trustTier = NbgPetTrustTier.Blocked,
      requiresReview = true,
      reason = "未知 Pet 来源不进入可信集合。",
    )
  }
}

internal fun nbgPetResourceProvenancePresent(pet: NbgPetSummary): Boolean {
  val spritePath = pet.spritePath?.trim().orEmpty()
  if (spritePath.isBlank()) return false
  val parent = File(spritePath).parentFile ?: return false
  return File(parent, NBG_PET_PROVENANCE_FILE).isFile
}

internal fun nbgWritePetDexManifestProvenance(
  review: NbgPetResourceUrlReview,
  rawManifest: String,
  manifest: NbgPetDexManifest,
  targetDir: File,
  rawManifestBytes: ByteArray = rawManifest.toByteArray(Charsets.UTF_8),
  fetchedAtMs: Long = System.currentTimeMillis(),
): File {
  if (!review.allowUse || review.fileKind != NbgPetResourceFileKind.Manifest) {
    error(review.reason.ifBlank { "PetDex manifest provenance requires an allowed manifest URL review" })
  }
  val provenance = JSONObject()
    .put("schema", NBG_PET_RESOURCE_INTEGRITY_VERSION)
    .put("sourceKind", NbgPetResourceSourceKind.PetDexHttps.wireName)
    .put("trustTier", NbgPetTrustTier.UnverifiedPetDex.wireName)
    .put("requiresReview", true)
    .put("sourceHost", review.sourceHost)
    .put("redactedSource", review.redactedLocation)
    .put("sizeBytes", rawManifestBytes.size.toLong())
    .put("sha256", rawManifestBytes.nbgPetSha256Hex())
    .put("maxBytes", NBG_PETDEX_MANIFEST_MAX_BYTES)
    .put("declaredTotal", manifest.total.coerceAtLeast(0))
    .put("parsedPetCount", manifest.pets.size.coerceAtLeast(0))
    .put("fetchedAtMs", fetchedAtMs)
  targetDir.mkdirs()
  val file = File(targetDir, NBG_PETDEX_MANIFEST_PROVENANCE_FILE)
  file.writeText(provenance.toString(2) + "\n", Charsets.UTF_8)
  return file
}

internal fun nbgReadPetDexManifestProvenance(targetDir: File): NbgPetDexManifestProvenance? {
  val file = File(targetDir, NBG_PETDEX_MANIFEST_PROVENANCE_FILE)
  if (!file.isFile) return null
  return runCatching {
    val root = JSONObject(file.readText(Charsets.UTF_8))
    if (root.optString("schema") != NBG_PET_RESOURCE_INTEGRITY_VERSION) return@runCatching null
    val sourceKind = NbgPetResourceSourceKind.entries.firstOrNull { it.wireName == root.optString("sourceKind") }
      ?: return@runCatching null
    val trustTier = NbgPetTrustTier.entries.firstOrNull { it.wireName == root.optString("trustTier") }
      ?: return@runCatching null
    val sourceHost = root.optString("sourceHost").trim().lowercase()
    val redactedSource = root.optString("redactedSource").trim()
    val sha256 = root.optString("sha256").trim().lowercase()
    if (sourceKind != NbgPetResourceSourceKind.PetDexHttps) return@runCatching null
    if (trustTier != NbgPetTrustTier.UnverifiedPetDex) return@runCatching null
    if (!root.optBoolean("requiresReview", false)) return@runCatching null
    if (!sourceHost.isNbgAllowedPetDexHost() || sourceHost.isNbgUnsafePetHostLiteral()) return@runCatching null
    if (redactedSource.isBlank() || redactedSource.contains('?') || redactedSource.contains('@')) return@runCatching null
    if (!sha256.isNbgSha256Hex()) return@runCatching null
    val sizeBytes = root.optLong("sizeBytes", -1L)
    val maxBytes = root.optLong("maxBytes", -1L)
    val declaredTotal = root.optInt("declaredTotal", -1)
    val parsedPetCount = root.optInt("parsedPetCount", -1)
    val fetchedAtMs = root.optLong("fetchedAtMs", -1L)
    if (sizeBytes < 0L || sizeBytes > NBG_PETDEX_MANIFEST_MAX_BYTES) return@runCatching null
    if (maxBytes != NBG_PETDEX_MANIFEST_MAX_BYTES) return@runCatching null
    if (declaredTotal < 0 || parsedPetCount < 0 || fetchedAtMs < 0L) return@runCatching null
    NbgPetDexManifestProvenance(
      schema = NBG_PET_RESOURCE_INTEGRITY_VERSION,
      sourceKind = sourceKind,
      trustTier = trustTier,
      requiresReview = true,
      sourceHost = sourceHost,
      redactedSource = redactedSource,
      sizeBytes = sizeBytes,
      sha256 = sha256,
      maxBytes = maxBytes,
      declaredTotal = declaredTotal,
      parsedPetCount = parsedPetCount,
      fetchedAtMs = fetchedAtMs,
    )
  }.getOrNull()
}

internal fun nbgWritePetResourceProvenance(
  review: NbgPetDexInstallReview,
  pet: NbgPetDexManifestPet,
  sprite: NbgPetDownloadedResource,
  petJson: NbgPetDownloadedResource,
  targetDir: File,
  installedAtMs: Long = System.currentTimeMillis(),
): File {
  val provenance = JSONObject()
    .put("schema", NBG_PET_RESOURCE_INTEGRITY_VERSION)
    .put("sourceKind", review.sourceKind.wireName)
    .put("trustTier", review.trustTier.wireName)
    .put("requiresReview", review.requiresReview)
    .put("slug", review.safeSlug)
    .put("displayName", pet.displayName.trim().ifBlank { review.safeSlug })
    .put("kind", pet.kind.trim().ifBlank { "character" })
    .put("sourceHost", review.sourceHost)
    .put("sprite", sprite.toPetResourceJson())
    .put("petJson", petJson.toPetResourceJson())
    .put("installedAtMs", installedAtMs)
  val file = File(targetDir, NBG_PET_PROVENANCE_FILE)
  file.writeText(provenance.toString(2) + "\n", Charsets.UTF_8)
  return file
}

internal fun nbgWriteBundledPetResourceProvenance(
  seed: NbgBundledPetSeed,
  spriteFile: File,
  petJsonFile: File,
  targetDir: File,
  installedAtMs: Long,
): File {
  val spriteReview = nbgReviewPetDexResourceUrl(seed.spritesheetUrl, NbgPetResourceFileKind.Sprite)
  val petJsonReview = nbgReviewPetDexResourceUrl(seed.petJsonUrl, NbgPetResourceFileKind.PetJson)
  val provenance = JSONObject()
    .put("schema", NBG_PET_RESOURCE_INTEGRITY_VERSION)
    .put("sourceKind", NbgPetResourceSourceKind.BuiltIn.wireName)
    .put("trustTier", NbgPetTrustTier.TrustedBundled.wireName)
    .put("requiresReview", false)
    .put("slug", seed.slug)
    .put("displayName", seed.displayName)
    .put("kind", seed.kind)
    .put("sourceHost", spriteReview.sourceHost.ifBlank { petJsonReview.sourceHost })
    .put("sprite", spriteFile.toBundledPetResourceJson(NbgPetResourceFileKind.Sprite, spriteReview))
    .put("petJson", petJsonFile.toBundledPetResourceJson(NbgPetResourceFileKind.PetJson, petJsonReview))
    .put("installedAtMs", installedAtMs)
  val file = File(targetDir, NBG_PET_PROVENANCE_FILE)
  file.writeText(provenance.toString(2) + "\n", Charsets.UTF_8)
  return file
}

internal fun nbgReadPetResourceBytesWithLimit(
  input: InputStream,
  maxBytes: Long,
  label: String,
): ByteArray {
  val output = ByteArrayOutputStream()
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  var copied = 0L
  while (true) {
    val read = input.read(buffer)
    if (read < 0) break
    copied += read
    if (copied > maxBytes) {
      throw IOException("$label 文件过大，最大 ${maxBytes / 1024L}KB")
    }
    output.write(buffer, 0, read)
  }
  return output.toByteArray()
}

internal fun String.safePetFileName(): String =
  lowercase()
    .replace(Regex("[^a-z0-9._-]+"), "-")
    .trim('-', '.', '_')
    .take(80)

private fun String.isNbgBuiltInPetSourceLabel(): Boolean =
  trim().lowercase() in setOf("built-in", NbgPetResourceSourceKind.BuiltIn.wireName)

private fun String.isNbgUserManagedPetSource(): Boolean {
  val normalized = trim().lowercase().replace('_', '-')
  return normalized in setOf(
    "custom",
    "imported",
    "local",
    "local-path",
    "manual",
    "side-loaded",
    "sideloaded",
    "user",
    "user-imported",
    "user-installed",
    "user-managed",
  )
}

private fun NbgPetSummary.hasNbgTrustedBundledPetProvenance(source: String): Boolean {
  if (!source.isNbgBuiltInPetSourceLabel()) return false
  val spritePathValue = spritePath?.trim().orEmpty()
  if (spritePathValue.isBlank()) return false
  val provenanceFile = File(File(spritePathValue).parentFile ?: return false, NBG_PET_PROVENANCE_FILE)
  if (!provenanceFile.isFile) return false
  return runCatching {
    val root = JSONObject(provenanceFile.readText(Charsets.UTF_8))
    root.optString("schema") == NBG_PET_RESOURCE_INTEGRITY_VERSION &&
      root.optString("sourceKind") == NbgPetResourceSourceKind.BuiltIn.wireName &&
      root.optString("trustTier") == NbgPetTrustTier.TrustedBundled.wireName &&
      !root.optBoolean("requiresReview", true) &&
      root.optString("slug") == slug
  }.getOrDefault(false)
}

private fun nbgBlockedPetDexInstall(
  reason: String,
  host: String = "",
): NbgPetDexInstallReview =
  NbgPetDexInstallReview(
    sourceKind = NbgPetResourceSourceKind.Unknown,
    trustTier = NbgPetTrustTier.Blocked,
    allowInstall = false,
    requiresReview = true,
    sourceHost = host,
    reason = reason,
  )

private fun nbgBlockedPetResourceUrl(
  fileKind: NbgPetResourceFileKind,
  rawUrl: String,
  reason: String,
  host: String = "",
  redactedLocation: String = "",
): NbgPetResourceUrlReview =
  NbgPetResourceUrlReview(
    fileKind = fileKind,
    rawUrl = rawUrl,
    allowUse = false,
    sourceHost = host,
    redactedLocation = redactedLocation,
    reason = reason,
  )

private fun String.isNbgAllowedPetDexHost(): Boolean =
  this == "petdex.dev" || endsWith(".petdex.dev")

private fun String.isNbgUnsafePetHostLiteral(): Boolean {
  val normalized = trim().trim('[', ']').lowercase()
  if (normalized == "localhost" || normalized == "::1" || normalized.startsWith("127.")) return true
  val parts = normalized.split('.').mapNotNull { it.toIntOrNull() }
  if (parts.size != 4 || parts.any { it !in 0..255 }) return false
  val first = parts[0]
  val second = parts[1]
  return first == 0 ||
    first == 10 ||
    first == 127 ||
    (first == 169 && second == 254) ||
    (first == 172 && second in 16..31) ||
    (first == 192 && second == 168) ||
    first >= 224
}

private fun URI.toNbgRedactedPetLocation(fileKind: NbgPetResourceFileKind): String {
  val scheme = scheme.orEmpty().lowercase().ifBlank { "unknown" }
  val host = host.orEmpty().lowercase().ifBlank { "invalid" }
  val fallback = when (fileKind) {
    NbgPetResourceFileKind.Manifest -> "manifest.json"
    NbgPetResourceFileKind.Sprite -> "sprite.webp"
    NbgPetResourceFileKind.PetJson -> "pet.json"
  }
  val fileName = path.orEmpty().substringAfterLast('/').ifBlank { fallback }
  return "$scheme://$host/.../$fileName"
}

private fun NbgPetDownloadedResource.toPetResourceJson(): JSONObject =
  JSONObject()
    .put("kind", fileKind.wireName)
    .put("fileName", fileName)
    .put("sizeBytes", sizeBytes)
    .put("sha256", sha256)
    .put("sourceHost", sourceHost)
    .put("redactedSource", redactedSource)
    .put("maxBytes", fileKind.maxBytes)

private fun File.toBundledPetResourceJson(
  fileKind: NbgPetResourceFileKind,
  review: NbgPetResourceUrlReview,
): JSONObject =
  JSONObject()
    .put("kind", fileKind.wireName)
    .put("fileName", name)
    .put("sizeBytes", length())
    .put("sha256", nbgPetFileSha256Hex())
    .put("sourceHost", review.sourceHost)
    .put("redactedSource", review.redactedLocation)
    .put("maxBytes", fileKind.maxBytes)

private fun File.nbgPetFileSha256Hex(): String {
  val digest = MessageDigest.getInstance("SHA-256")
  inputStream().use { input ->
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
      val read = input.read(buffer)
      if (read <= 0) break
      digest.update(buffer, 0, read)
    }
  }
  return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun ByteArray.nbgPetSha256Hex(): String =
  MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString("") { "%02x".format(it) }

private fun String.isNbgSha256Hex(): Boolean =
  length == 64 && all { it in '0'..'9' || it in 'a'..'f' }
