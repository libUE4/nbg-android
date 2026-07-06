package com.nbg.android

import org.json.JSONObject
import java.io.File
import java.net.URI
import java.security.MessageDigest

internal const val NBG_SKILL_SOURCE_INTEGRITY_VERSION = "nbg-skill-source-integrity-v1"
internal const val NBG_SKILL_PROVENANCE_SCHEMA = "nbg-skill-source-provenance-v1"
internal const val NBG_SKILL_PROVENANCE_FILE = ".nbg-skill-provenance.json"
internal const val NBG_DEFAULT_SKILLS_ASSET_ROOT = "nbg-default-skills"
internal const val NBG_SKILL_TRUSTED_EXTERNAL_PROMOTION_POLICY = "nbg-skill-trusted-external-promotion-v1"

internal val NBG_SKILL_TRUSTED_EXTERNAL_REQUIRED_EVIDENCE: List<String> =
  listOf("signed_bundle", "pinned_sha256_allowlist")

internal enum class NbgSkillSourceKind(val wireName: String) {
  BuiltIn("builtin"),
  UserInstalled("user_installed"),
  LocalPath("local_path"),
  LocalhostHttp("localhost_http"),
  RemoteHttps("remote_https"),
  ExternalPath("external_path"),
  Unknown("unknown"),
}

internal enum class NbgSkillTrustTier(val wireName: String) {
  TrustedBundled("trusted_bundled"),
  UserManaged("user_managed"),
  UnverifiedExternal("unverified_external"),
  Blocked("blocked"),
}

internal data class NbgSkillSourceReview(
  val sourceKind: NbgSkillSourceKind,
  val trustTier: NbgSkillTrustTier,
  val allowInstall: Boolean,
  val requiresReview: Boolean,
  val sourceHost: String = "",
  val redactedLocation: String = "",
  val reason: String = "",
) {
  val trustedDefault: Boolean
    get() = trustTier == NbgSkillTrustTier.TrustedBundled && !requiresReview && allowInstall
}

internal data class NbgBundledSkillAssetPin(
  val skillName: String,
  val relativePath: String,
  val sizeBytes: Int,
  val sha256: String,
)

internal data class NbgBundledSkillIntegrityReview(
  val skillName: String,
  val trustedDefaultEligible: Boolean,
  val checkedAssetCount: Int,
  val reason: String = "",
)

internal data class NbgExternalSkillPromotionReview(
  val policyVersion: String,
  val eligibleForTrustedDefault: Boolean,
  val trustTier: NbgSkillTrustTier,
  val requiresReview: Boolean,
  val requiredEvidence: List<String>,
  val presentEvidence: List<String>,
  val reason: String,
)

internal val NBG_ANDROID_BUNDLED_SKILL_NAMES: List<String> =
  listOf(NBG_ENGINEERING_CORE_SKILL_NAME)

internal val NBG_ANDROID_BUNDLED_SKILL_ASSET_PINS: List<NbgBundledSkillAssetPin> =
  listOf(
    NbgBundledSkillAssetPin(
      skillName = NBG_ENGINEERING_CORE_SKILL_NAME,
      relativePath = "SKILL.md",
      sizeBytes = 9719,
      sha256 = "6ce4f8e231309558af0346f007447013b5122249adacc00adae0f0e23df00520",
    ),
  )

internal fun nbgReviewSkillInstallSource(rawSource: String): NbgSkillSourceReview {
  val source = rawSource.trim()
  if (source.isBlank()) {
    return nbgBlockedSkillSource("Skill 路径不能为空")
  }
  if (source.startsWith("/")) {
    return NbgSkillSourceReview(
      sourceKind = NbgSkillSourceKind.LocalPath,
      trustTier = NbgSkillTrustTier.UserManaged,
      allowInstall = true,
      requiresReview = true,
      redactedLocation = "[local-path]",
      reason = "本地绝对路径 Skill 由用户管理，启用前需要人工确认来源。",
    )
  }

  val uri = runCatching { URI(source) }.getOrNull()
    ?: return nbgBlockedSkillSource("Skill 链接格式无效")
  val scheme = uri.scheme.orEmpty().lowercase()
  val host = uri.host.orEmpty().lowercase()
  val redacted = uri.toNbgRedactedSkillLocation()
  if (scheme !in setOf("http", "https")) {
    return nbgBlockedSkillSource("Skill 链接必须使用 https；本机调试可使用 localhost http。", host, redacted)
  }
  if (!uri.userInfo.isNullOrBlank()) {
    return nbgBlockedSkillSource("Skill 链接不能包含用户名、密码或 token。", host, redacted)
  }
  if (scheme == "http" && !host.isNbgLoopbackSkillHost()) {
    return nbgBlockedSkillSource("外部 Skill 链接必须使用 https；本机调试可使用 localhost http。", host, redacted)
  }
  val sourceKind = if (scheme == "http") NbgSkillSourceKind.LocalhostHttp else NbgSkillSourceKind.RemoteHttps
  val trustTier = if (sourceKind == NbgSkillSourceKind.LocalhostHttp) {
    NbgSkillTrustTier.UserManaged
  } else {
    NbgSkillTrustTier.UnverifiedExternal
  }
  return NbgSkillSourceReview(
    sourceKind = sourceKind,
    trustTier = trustTier,
    allowInstall = true,
    requiresReview = true,
    sourceHost = host,
    redactedLocation = redacted,
    reason = "远程 Skill 下载后记录 provenance 和 sha256，默认仍视为未验证外部来源。",
  )
}

internal fun nbgSkillSummarySourceReview(skill: HanakoSkillSummary): NbgSkillSourceReview {
  val source = skill.source.trim().lowercase()
  return when {
    source == "external" || skill.externalPath.isNotBlank() -> NbgSkillSourceReview(
      sourceKind = NbgSkillSourceKind.ExternalPath,
      trustTier = NbgSkillTrustTier.UnverifiedExternal,
      allowInstall = true,
      requiresReview = true,
      redactedLocation = "[external-skill-path]",
      reason = "外部 Skill 路径不进入可信默认集合，启用前需要人工确认。",
    )
    source == "user" || skill.filePath.isNotBlank() || skill.baseDir.isNotBlank() -> NbgSkillSourceReview(
      sourceKind = NbgSkillSourceKind.UserInstalled,
      trustTier = NbgSkillTrustTier.UserManaged,
      allowInstall = true,
      requiresReview = true,
      redactedLocation = "[skill-path]",
      reason = "用户安装或 Agent 写入的 Skill 需要保持可审查、可删除、可禁用。",
    )
    source == "builtin" && skill.name in NBG_ANDROID_BUNDLED_SKILL_NAMES -> NbgSkillSourceReview(
      sourceKind = NbgSkillSourceKind.BuiltIn,
      trustTier = NbgSkillTrustTier.TrustedBundled,
      allowInstall = true,
      requiresReview = false,
      reason = "随 APK 打包的默认 Skill，完整性由 Android pinned hash 和 release gate 资产 checksum 覆盖。",
    )
    source == "builtin" -> NbgSkillSourceReview(
      sourceKind = NbgSkillSourceKind.BuiltIn,
      trustTier = NbgSkillTrustTier.UnverifiedExternal,
      allowInstall = true,
      requiresReview = true,
      reason = "未在 Android pinned hash manifest 中登记的 builtin Skill 不进入可信默认集合。",
    )
    else -> NbgSkillSourceReview(
      sourceKind = NbgSkillSourceKind.Unknown,
      trustTier = NbgSkillTrustTier.UnverifiedExternal,
      allowInstall = true,
      requiresReview = true,
      reason = "未知来源 Skill 默认按未验证外部来源处理。",
    )
  }
}

internal fun nbgReviewExternalSkillTrustedPromotion(
  review: NbgSkillSourceReview,
  signedBundlePresent: Boolean,
  pinnedSha256AllowlistPresent: Boolean,
): NbgExternalSkillPromotionReview {
  val presentEvidence = buildList {
    if (signedBundlePresent) add("signed_bundle")
    if (pinnedSha256AllowlistPresent) add("pinned_sha256_allowlist")
  }
  return NbgExternalSkillPromotionReview(
    policyVersion = NBG_SKILL_TRUSTED_EXTERNAL_PROMOTION_POLICY,
    eligibleForTrustedDefault = false,
    trustTier = review.trustTier,
    requiresReview = true,
    requiredEvidence = NBG_SKILL_TRUSTED_EXTERNAL_REQUIRED_EVIDENCE,
    presentEvidence = presentEvidence,
    reason = "Public beta v1 does not promote external Skills to trusted defaults; future promotion requires both signed bundle evidence and a pinned SHA-256 allowlist.",
  )
}

internal fun nbgMergeTrustedBundledSkillEnablement(
  skills: List<HanakoSkillSummary>,
  verifiedBundledSkillNames: List<String>,
): List<String> {
  val trustedVisibleNames = skills
    .filter { nbgSkillSummarySourceReview(it).trustedDefault }
    .map { it.name }
    .toSet()
  val required = verifiedBundledSkillNames.filter { it in trustedVisibleNames }
  return (skills
    .filter { it.enabled }
    .filterNot { it.name in NBG_ANDROID_BUNDLED_SKILL_NAMES && !nbgSkillSummarySourceReview(it).trustedDefault }
    .map { it.name } + required)
    .distinct()
}

internal fun nbgEnabledSkillsAfterManualInstall(
  beforeInstall: List<HanakoSkillSummary>,
  afterInstall: List<HanakoSkillSummary>,
): List<String> {
  val previouslyEnabled = beforeInstall
    .filter { it.enabled }
    .associateBy { it.name }
  return afterInstall
    .filter { skill ->
      skill.enabled && (
        !nbgSkillSummarySourceReview(skill).requiresReview ||
          previouslyEnabled[skill.name]?.let { before -> nbgSkillReviewIdentity(before) == nbgSkillReviewIdentity(skill) } == true
        )
    }
    .map { it.name }
    .distinct()
}

private fun nbgSkillReviewIdentity(skill: HanakoSkillSummary): String {
  val review = nbgSkillSummarySourceReview(skill)
  return listOf(
    skill.name,
    skill.source.trim().lowercase(),
    skill.filePath.trim(),
    skill.baseDir.trim(),
    skill.externalPath.trim(),
    review.sourceKind.wireName,
    review.trustTier.wireName,
    review.requiresReview.toString(),
  ).joinToString("\u001f")
}

internal fun nbgBundledSkillAssetPins(skillName: String): List<NbgBundledSkillAssetPin> =
  NBG_ANDROID_BUNDLED_SKILL_ASSET_PINS.filter { it.skillName == skillName }

internal fun nbgBundledSkillAssetPath(skillName: String, relativePath: String): String =
  "$NBG_DEFAULT_SKILLS_ASSET_ROOT/$skillName/$relativePath"

internal fun nbgReviewBundledSkillAssets(
  skillName: String,
  readAssetBytes: (relativePath: String) -> ByteArray?,
): NbgBundledSkillIntegrityReview {
  val pins = nbgBundledSkillAssetPins(skillName)
  if (pins.isEmpty()) {
    return NbgBundledSkillIntegrityReview(
      skillName = skillName,
      trustedDefaultEligible = false,
      checkedAssetCount = 0,
      reason = "Bundled Skill is not pinned in Android source integrity manifest.",
    )
  }
  val failures = pins.mapNotNull { pin ->
    val bytes = readAssetBytes(pin.relativePath)
      ?: return@mapNotNull "${pin.relativePath}:missing"
    val actualSha256 = bytes.nbgSha256Hex()
    when {
      bytes.size != pin.sizeBytes -> "${pin.relativePath}:size"
      actualSha256 != pin.sha256 -> "${pin.relativePath}:sha256"
      else -> null
    }
  }
  return NbgBundledSkillIntegrityReview(
    skillName = skillName,
    trustedDefaultEligible = failures.isEmpty(),
    checkedAssetCount = pins.size,
    reason = if (failures.isEmpty()) {
      "Bundled Skill assets match Android pinned hash manifest."
    } else {
      "Bundled Skill integrity mismatch: ${failures.joinToString(", ")}"
    },
  )
}

internal fun nbgVerifiedAndroidBundledSkillNames(
  readAssetBytes: (skillName: String, relativePath: String) -> ByteArray?,
): List<String> =
  NBG_ANDROID_BUNDLED_SKILL_NAMES.filter { skillName ->
    nbgReviewBundledSkillAssets(skillName) { relativePath -> readAssetBytes(skillName, relativePath) }
      .trustedDefaultEligible
  }

internal fun nbgWriteSkillDownloadProvenance(
  review: NbgSkillSourceReview,
  rawSource: String,
  target: File,
  downloadedAtMs: Long = System.currentTimeMillis(),
): File {
  val bytes = target.readBytes()
  val provenance = JSONObject()
    .put("schema", NBG_SKILL_PROVENANCE_SCHEMA)
    .put("sourceKind", review.sourceKind.wireName)
    .put("trustTier", review.trustTier.wireName)
    .put("requiresReview", review.requiresReview)
    .put("sourceHost", review.sourceHost)
    .put("sourceUrlHash", review.redactedLocation.ifBlank { rawSource.take(0) }.sha256Hex())
    .put("redactedSource", review.redactedLocation)
    .put("fileName", target.name)
    .put("sizeBytes", bytes.size)
    .put("sha256", bytes.nbgSha256Hex())
    .put("downloadedAtMs", downloadedAtMs)
  val file = File(target.parentFile ?: target.absoluteFile.parentFile, NBG_SKILL_PROVENANCE_FILE)
  file.writeText(provenance.toString(2) + "\n", Charsets.UTF_8)
  return file
}

private fun nbgBlockedSkillSource(
  reason: String,
  host: String = "",
  redactedLocation: String = "",
): NbgSkillSourceReview =
  NbgSkillSourceReview(
    sourceKind = NbgSkillSourceKind.Unknown,
    trustTier = NbgSkillTrustTier.Blocked,
    allowInstall = false,
    requiresReview = true,
    sourceHost = host,
    redactedLocation = redactedLocation,
    reason = reason,
  )

private fun String.isNbgLoopbackSkillHost(): Boolean =
  this == "localhost" || this == "127.0.0.1" || this == "::1" || this == "[::1]"

private fun URI.toNbgRedactedSkillLocation(): String {
  val scheme = scheme.orEmpty().lowercase().ifBlank { "unknown" }
  val host = host.orEmpty().lowercase().ifBlank { "invalid" }
  val fileName = path.orEmpty().substringAfterLast('/').ifBlank { "SKILL.md" }
  return "$scheme://$host/.../$fileName"
}

private fun ByteArray.nbgSha256Hex(): String {
  val digest = MessageDigest.getInstance("SHA-256").digest(this)
  return digest.joinToString("") { "%02x".format(it) }
}
