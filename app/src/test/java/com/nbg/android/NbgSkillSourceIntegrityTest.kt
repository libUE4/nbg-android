package com.nbg.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory

class NbgSkillSourceIntegrityTest {
  @Test
  fun reviewBlocksPlainExternalHttpAndCredentialUrls() {
    val plainHttp = nbgReviewSkillInstallSource("http://example.com/SKILL.md")
    val credentials = nbgReviewSkillInstallSource("https://token@example.com/SKILL.md")

    assertFalse(plainHttp.allowInstall)
    assertEquals(NbgSkillTrustTier.Blocked, plainHttp.trustTier)
    assertTrue(plainHttp.reason.contains("https"))

    assertFalse(credentials.allowInstall)
    assertEquals(NbgSkillTrustTier.Blocked, credentials.trustTier)
    assertTrue(credentials.reason.contains("token"))
  }

  @Test
  fun reviewAllowsHttpsAndLocalhostAsReviewRequiredSources() {
    val https = nbgReviewSkillInstallSource("https://raw.githubusercontent.com/acme/repo/main/SKILL.md?token=secret")
    val localhost = nbgReviewSkillInstallSource("http://127.0.0.1:8080/SKILL.md")
    val localPath = nbgReviewSkillInstallSource("/root/skills/demo/SKILL.md")

    assertTrue(https.allowInstall)
    assertEquals(NbgSkillSourceKind.RemoteHttps, https.sourceKind)
    assertEquals(NbgSkillTrustTier.UnverifiedExternal, https.trustTier)
    assertTrue(https.requiresReview)
    assertEquals("raw.githubusercontent.com", https.sourceHost)
    assertFalse(https.redactedLocation.contains("token=secret"))

    assertTrue(localhost.allowInstall)
    assertEquals(NbgSkillSourceKind.LocalhostHttp, localhost.sourceKind)
    assertEquals(NbgSkillTrustTier.UserManaged, localhost.trustTier)

    assertTrue(localPath.allowInstall)
    assertEquals(NbgSkillSourceKind.LocalPath, localPath.sourceKind)
    assertEquals("[local-path]", localPath.redactedLocation)
  }

  @Test
  fun skillSummaryReviewSeparatesBundledUserAndExternalSources() {
    val bundled = nbgSkillSummarySourceReview(HanakoSkillSummary(name = "nbg-engineering-core", source = "builtin"))
    val unknownBuiltin = nbgSkillSummarySourceReview(HanakoSkillSummary(name = "same-name", source = "builtin"))
    val pathBuiltin = nbgSkillSummarySourceReview(
      HanakoSkillSummary(name = "nbg-engineering-core", source = "builtin", filePath = "/root/private/SKILL.md"),
    )
    val user = nbgSkillSummarySourceReview(HanakoSkillSummary(name = "custom", source = "user", filePath = "/root/private/SKILL.md"))
    val external = nbgSkillSummarySourceReview(
      HanakoSkillSummary(name = "external", source = "external", externalPath = "/root/other/skills"),
    )

    assertTrue(bundled.trustedDefault)
    assertEquals(NbgSkillTrustTier.TrustedBundled, bundled.trustTier)
    assertFalse(bundled.requiresReview)

    assertFalse(unknownBuiltin.trustedDefault)
    assertEquals(NbgSkillTrustTier.UnverifiedExternal, unknownBuiltin.trustTier)

    assertFalse(pathBuiltin.trustedDefault)
    assertEquals(NbgSkillTrustTier.UserManaged, pathBuiltin.trustTier)

    assertEquals(NbgSkillTrustTier.UserManaged, user.trustTier)
    assertTrue(user.requiresReview)

    assertEquals(NbgSkillTrustTier.UnverifiedExternal, external.trustTier)
    assertTrue(external.requiresReview)
  }

  @Test
  fun defaultEnablementDropsSameNameUntrustedBundledSkills() {
    val merged = nbgMergeTrustedBundledSkillEnablement(
      skills = listOf(
        HanakoSkillSummary(
          name = NBG_ENGINEERING_CORE_SKILL_NAME,
          source = "external",
          enabled = true,
          externalPath = "/root/external/skills",
        ),
        HanakoSkillSummary(name = "custom-user-skill", source = "user", enabled = true, filePath = "/root/custom/SKILL.md"),
        HanakoSkillSummary(name = NBG_ENGINEERING_CORE_SKILL_NAME, source = "builtin", enabled = false),
      ),
      verifiedBundledSkillNames = listOf(NBG_ENGINEERING_CORE_SKILL_NAME),
    )

    assertEquals(listOf("custom-user-skill", NBG_ENGINEERING_CORE_SKILL_NAME), merged)
  }

  @Test
  fun manualInstallEnablementDropsNewReviewRequiredSkills() {
    val before = listOf(
      HanakoSkillSummary(name = "already-enabled-user", source = "user", enabled = true, filePath = "/root/old/SKILL.md"),
      HanakoSkillSummary(name = "trusted-before", source = "builtin", enabled = true),
    )
    val after = listOf(
      HanakoSkillSummary(name = "already-enabled-user", source = "user", enabled = true, filePath = "/root/old/SKILL.md"),
      HanakoSkillSummary(name = "trusted-before", source = "builtin", enabled = true),
      HanakoSkillSummary(name = "new-user-skill", source = "user", enabled = true, filePath = "/root/new/SKILL.md"),
      HanakoSkillSummary(name = "new-external-skill", source = "external", enabled = true, externalPath = "/root/external"),
      HanakoSkillSummary(name = NBG_ENGINEERING_CORE_SKILL_NAME, source = "builtin", enabled = true),
    )

    assertEquals(
      listOf("already-enabled-user", "trusted-before", NBG_ENGINEERING_CORE_SKILL_NAME),
      nbgEnabledSkillsAfterManualInstall(before, after),
    )
  }

  @Test
  fun manualInstallEnablementDropsSameNameReviewRequiredReplacement() {
    val before = listOf(
      HanakoSkillSummary(name = NBG_ENGINEERING_CORE_SKILL_NAME, source = "builtin", enabled = true),
      HanakoSkillSummary(name = "same-user", source = "user", enabled = true, filePath = "/root/old/SKILL.md"),
    )
    val after = listOf(
      HanakoSkillSummary(
        name = NBG_ENGINEERING_CORE_SKILL_NAME,
        source = "user",
        enabled = true,
        filePath = "/root/replacement/SKILL.md",
      ),
      HanakoSkillSummary(name = "same-user", source = "user", enabled = true, filePath = "/root/old/SKILL.md"),
    )

    assertEquals(
      listOf("same-user"),
      nbgEnabledSkillsAfterManualInstall(before, after),
    )
  }

  @Test
  fun bundledSkillAssetPinsMatchSourceAssets() {
    val review = nbgReviewBundledSkillAssets(NBG_ENGINEERING_CORE_SKILL_NAME) { relativePath ->
      File("src/main/assets/${nbgBundledSkillAssetPath(NBG_ENGINEERING_CORE_SKILL_NAME, relativePath)}")
        .takeIf { it.isFile }
        ?.readBytes()
    }
    val pins = nbgBundledSkillAssetPins(NBG_ENGINEERING_CORE_SKILL_NAME)
    val skill = File("src/main/assets/nbg-default-skills/nbg-engineering-core/SKILL.md")

    assertTrue(review.trustedDefaultEligible)
    assertEquals(1, review.checkedAssetCount)
    assertEquals(1, pins.size)
    assertEquals("SKILL.md", pins.single().relativePath)
    assertEquals(skill.length().toInt(), pins.single().sizeBytes)
    assertEquals(skill.readBytes().sha256HexForTest(), pins.single().sha256)
    assertEquals(listOf(NBG_ENGINEERING_CORE_SKILL_NAME), nbgVerifiedAndroidBundledSkillNames { skillName, relativePath ->
      File("src/main/assets/${nbgBundledSkillAssetPath(skillName, relativePath)}").takeIf { it.isFile }?.readBytes()
    })
  }

  @Test
  fun bundledSkillHashMismatchBlocksTrustedDefaultEligibility() {
    val mismatched = nbgReviewBundledSkillAssets(NBG_ENGINEERING_CORE_SKILL_NAME) { "tampered".toByteArray() }
    val missing = nbgReviewBundledSkillAssets(NBG_ENGINEERING_CORE_SKILL_NAME) { null }

    assertFalse(mismatched.trustedDefaultEligible)
    assertTrue(mismatched.reason.contains("mismatch"))
    assertFalse(missing.trustedDefaultEligible)
    assertTrue(missing.reason.contains("missing"))
    assertEquals(emptyList<String>(), nbgVerifiedAndroidBundledSkillNames { _, _ -> "tampered".toByteArray() })
  }

  @Test
  fun externalSkillPromotionRequiresSignedBundleAndPinnedSha256ButStaysUntrustedInV1() {
    val remoteReview = nbgReviewSkillInstallSource("https://example.com/SKILL.md")
    val noEvidence = nbgReviewExternalSkillTrustedPromotion(
      review = remoteReview,
      signedBundlePresent = false,
      pinnedSha256AllowlistPresent = false,
    )
    val bothFutureEvidence = nbgReviewExternalSkillTrustedPromotion(
      review = remoteReview,
      signedBundlePresent = true,
      pinnedSha256AllowlistPresent = true,
    )

    assertEquals(NBG_SKILL_TRUSTED_EXTERNAL_PROMOTION_POLICY, noEvidence.policyVersion)
    assertEquals(listOf("signed_bundle", "pinned_sha256_allowlist"), noEvidence.requiredEvidence)
    assertEquals(emptyList<String>(), noEvidence.presentEvidence)
    assertFalse(noEvidence.eligibleForTrustedDefault)
    assertEquals(NbgSkillTrustTier.UnverifiedExternal, noEvidence.trustTier)
    assertTrue(noEvidence.requiresReview)

    assertEquals(listOf("signed_bundle", "pinned_sha256_allowlist"), bothFutureEvidence.presentEvidence)
    assertFalse("v1 must not promote external Skills even when future evidence labels are present", bothFutureEvidence.eligibleForTrustedDefault)
    assertEquals(NbgSkillTrustTier.UnverifiedExternal, bothFutureEvidence.trustTier)
    assertTrue(bothFutureEvidence.reason.contains("requires both signed bundle"))
  }

  @Test
  fun provenanceRecordsDownloadedFileHashWithoutRawSourceUrl() {
    val dir = createTempDirectory(prefix = "nbg-skill-provenance").toFile()
    val skill = dir.resolve("SKILL.md")
    val skillText = "---\nname: demo\n---\n# Demo\n"
    skill.writeText(skillText)
    val source = "https://example.com/SKILL.md?token=secret"
    val review = nbgReviewSkillInstallSource(source)

    val file = nbgWriteSkillDownloadProvenance(review, source, skill, downloadedAtMs = 123L)
    val json = JSONObject(file.readText())

    assertEquals(NBG_SKILL_PROVENANCE_SCHEMA, json.getString("schema"))
    assertEquals(NbgSkillSourceKind.RemoteHttps.wireName, json.getString("sourceKind"))
    assertEquals(NbgSkillTrustTier.UnverifiedExternal.wireName, json.getString("trustTier"))
    assertTrue(json.getBoolean("requiresReview"))
    assertEquals("example.com", json.getString("sourceHost"))
    assertEquals("SKILL.md", json.getString("fileName"))
    assertEquals(skillText.toByteArray().size, json.getInt("sizeBytes"))
    assertEquals(skillText.sha256Hex(), json.getString("sha256"))
    assertEquals(123L, json.getLong("downloadedAtMs"))
    assertFalse(file.readText().contains("token=secret"))
  }

  private fun ByteArray.sha256HexForTest(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString("") { "%02x".format(it) }
  }
}
