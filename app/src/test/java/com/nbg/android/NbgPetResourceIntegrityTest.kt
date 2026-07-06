package com.nbg.android

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.io.path.createTempDirectory

class NbgPetResourceIntegrityTest {
  @Test
  fun petDexInstallReviewAllowsHttpsAssetsAndMarksUnverifiedPetDex() {
    val review = nbgReviewPetDexInstall(samplePet())

    assertTrue(review.allowInstall)
    assertTrue(review.requiresReview)
    assertEquals("boba", review.safeSlug)
    assertEquals(NbgPetResourceSourceKind.PetDexHttps, review.sourceKind)
    assertEquals(NbgPetTrustTier.UnverifiedPetDex, review.trustTier)
    assertEquals("assets.petdex.dev", review.sourceHost)
    assertEquals("https://assets.petdex.dev/.../sprite.webp", review.spriteReview?.redactedLocation)
  }

  @Test
  fun petDexInstallReviewBlocksUnsafeUrls() {
    val plainHttp = samplePet(sprite = "http://assets.petdex.dev/pets/boba/sprite.webp")
    val localhost = samplePet(sprite = "https://localhost/pets/boba/sprite.webp")
    val privateIp = samplePet(sprite = "https://192.168.1.2/pets/boba/sprite.webp")
    val credentials = samplePet(sprite = "https://token@assets.petdex.dev/pets/boba/sprite.webp")
    val wrongHost = samplePet(sprite = "https://example.com/pets/boba/sprite.webp")
    val wrongExtension = samplePet(sprite = "https://assets.petdex.dev/pets/boba/sprite.png")

    listOf(plainHttp, localhost, privateIp, credentials, wrongHost, wrongExtension).forEach {
      assertFalse(nbgReviewPetDexInstall(it).allowInstall)
      assertEquals(NbgPetTrustTier.Blocked, nbgReviewPetDexInstall(it).trustTier)
    }
  }

  @Test
  fun petDexInstallReviewBlocksUnsafeSlugAndLongSlug() {
    val empty = samplePet(slug = "")
    val traversal = samplePet(slug = "../boba")
    val uppercase = samplePet(slug = "Boba")
    val tooLong = samplePet(slug = "a".repeat(81))

    listOf(empty, traversal, uppercase, tooLong).forEach {
      val review = nbgReviewPetDexInstall(it)
      assertFalse(review.allowInstall)
      assertEquals(NbgPetTrustTier.Blocked, review.trustTier)
    }
  }

  @Test
  fun petResourceProvenanceRecordsHashesAndRedactsUrls() {
    val dir = createTempDirectory(prefix = "nbg-pet-provenance").toFile()
    val pet = samplePet(
      sprite = "https://assets.petdex.dev/pets/boba/sprite.webp?token=secret",
      json = "https://assets.petdex.dev/pets/boba/petjson.json?api_key=secret",
    )
    val review = nbgReviewPetDexInstall(pet)
    val sprite = NbgPetDownloadedResource(
      fileKind = NbgPetResourceFileKind.Sprite,
      fileName = "sprite.webp",
      sizeBytes = 4,
      sha256 = "sprite-sha",
      sourceHost = "assets.petdex.dev",
      redactedSource = "https://assets.petdex.dev/.../sprite.webp",
    )
    val petJson = NbgPetDownloadedResource(
      fileKind = NbgPetResourceFileKind.PetJson,
      fileName = "pet.json",
      sizeBytes = 2,
      sha256 = "json-sha",
      sourceHost = "assets.petdex.dev",
      redactedSource = "https://assets.petdex.dev/.../petjson.json",
    )

    val provenance = nbgWritePetResourceProvenance(review, pet, sprite, petJson, dir, installedAtMs = 123L)
    val raw = provenance.readText()
    val json = JSONObject(raw)

    assertEquals(NBG_PET_RESOURCE_INTEGRITY_VERSION, json.getString("schema"))
    assertEquals(NbgPetTrustTier.UnverifiedPetDex.wireName, json.getString("trustTier"))
    assertTrue(json.getBoolean("requiresReview"))
    assertEquals("assets.petdex.dev", json.getString("sourceHost"))
    assertEquals("sprite-sha", json.getJSONObject("sprite").getString("sha256"))
    assertEquals(4L, json.getJSONObject("sprite").getLong("sizeBytes"))
    assertEquals(NBG_PET_MAX_SPRITE_BYTES, json.getJSONObject("sprite").getLong("maxBytes"))
    assertEquals(123L, json.getLong("installedAtMs"))
    assertFalse(raw.contains("token=secret"))
    assertFalse(raw.contains("api_key=secret"))
  }

  @Test
  fun petDexManifestProvenanceRecordsCountsHashAndNoRawManifestData() {
    val dir = createTempDirectory(prefix = "nbg-petdex-manifest-provenance").toFile()
    val rawManifest = """
      {
        "generatedAt": "2026-07-06T00:00:00.000Z",
        "total": 2,
        "pets": [
          {
            "slug": "boba",
            "displayName": "Boba",
            "kind": "character",
            "submittedBy": "PetDex",
            "spritesheetUrl": "https://assets.petdex.dev/pets/boba/sprite.webp",
            "petJsonUrl": "https://assets.petdex.dev/pets/boba/petjson.json"
          }
        ]
      }
    """.trimIndent()
    val manifest = nbgParsePetDexManifest(rawManifest)
    val review = nbgReviewPetDexResourceUrl(
      "https://petdex.dev/api/manifest?token=secret",
      NbgPetResourceFileKind.Manifest,
    )

    val provenanceFile = nbgWritePetDexManifestProvenance(
      review = review,
      rawManifest = rawManifest,
      manifest = manifest,
      targetDir = dir,
      fetchedAtMs = 456L,
    )
    val raw = provenanceFile.readText()
    val json = JSONObject(raw)
    val provenance = nbgReadPetDexManifestProvenance(dir)

    assertEquals(NBG_PETDEX_MANIFEST_PROVENANCE_FILE, provenanceFile.name)
    assertEquals(NBG_PET_RESOURCE_INTEGRITY_VERSION, json.getString("schema"))
    assertEquals(NbgPetResourceSourceKind.PetDexHttps.wireName, json.getString("sourceKind"))
    assertEquals(NbgPetTrustTier.UnverifiedPetDex.wireName, json.getString("trustTier"))
    assertTrue(json.getBoolean("requiresReview"))
    assertEquals("petdex.dev", json.getString("sourceHost"))
    assertEquals("https://petdex.dev/.../manifest", json.getString("redactedSource"))
    assertEquals(2, json.getInt("declaredTotal"))
    assertEquals(1, json.getInt("parsedPetCount"))
    assertEquals(456L, json.getLong("fetchedAtMs"))
    assertEquals(64, json.getString("sha256").length)
    assertEquals(1, provenance?.parsedPetCount)
    assertEquals(NbgPetTrustTier.UnverifiedPetDex, provenance?.trustTier)
    assertFalse(raw.contains("token=secret"))
    assertFalse(raw.contains("displayName"))
    assertFalse(raw.contains("boba"))
    assertFalse(raw.contains("\"pets\""))
  }

  @Test
  fun petDexTrustedPromotionRequiresSignedManifestAndResourceHashesButStaysUntrustedInV1() {
    val installReview = nbgReviewPetDexInstall(samplePet())
    val noEvidence = nbgReviewPetDexTrustedPromotion(
      review = installReview,
      signedManifestPresent = false,
      perResourceSha256ManifestPresent = false,
    )
    val signedOnly = nbgReviewPetDexTrustedPromotion(
      review = installReview,
      signedManifestPresent = true,
      perResourceSha256ManifestPresent = false,
    )
    val bothFutureEvidence = nbgReviewPetDexTrustedPromotion(
      review = installReview,
      signedManifestPresent = true,
      perResourceSha256ManifestPresent = true,
    )

    assertEquals(NBG_PETDEX_TRUSTED_PROMOTION_POLICY, noEvidence.policyVersion)
    assertEquals(listOf("signed_manifest", "per_resource_sha256_manifest"), noEvidence.requiredEvidence)
    assertEquals(emptyList<String>(), noEvidence.presentEvidence)
    assertFalse(noEvidence.eligibleForTrustedDefault)
    assertEquals(NbgPetTrustTier.UnverifiedPetDex, noEvidence.trustTier)
    assertTrue(noEvidence.requiresReview)

    assertEquals(listOf("signed_manifest"), signedOnly.presentEvidence)
    assertFalse(signedOnly.eligibleForTrustedDefault)
    assertEquals(listOf("signed_manifest", "per_resource_sha256_manifest"), bothFutureEvidence.presentEvidence)
    assertFalse("v1 must not promote PetDex resources even when future evidence labels are present", bothFutureEvidence.eligibleForTrustedDefault)
    assertEquals(NbgPetTrustTier.UnverifiedPetDex, bothFutureEvidence.trustTier)
    assertTrue(bothFutureEvidence.reason.contains("requires both signed manifest"))
  }

  @Test
  fun petSummaryReviewSeparatesPetDexUserManagedAndBlockedSources() {
    val bundled = nbgDefaultPetSummary()
    val legacyPetDex = NbgPetSummary(
      slug = "boba",
      displayName = "Boba",
      kind = "character",
      submittedBy = "PetDex",
      source = "petdex",
    )
    val sideLoaded = legacyPetDex.copy(source = "side-loaded")
    val userManaged = legacyPetDex.copy(source = "user_managed")
    val spoofedBundled = legacyPetDex.copy(source = "built_in")
    val unknown = legacyPetDex.copy(source = "mystery-store")
    val blank = legacyPetDex.copy(source = " ")

    assertEquals(NbgPetTrustTier.TrustedBundled, nbgPetSummarySourceReview(bundled).trustTier)
    assertFalse(nbgPetSummarySourceReview(bundled).requiresReview)

    assertEquals(NbgPetTrustTier.UnverifiedPetDex, nbgPetSummarySourceReview(legacyPetDex).trustTier)
    assertTrue(nbgPetSummarySourceReview(legacyPetDex).requiresReview)

    assertEquals(NbgPetResourceSourceKind.UserManaged, nbgPetSummarySourceReview(sideLoaded).sourceKind)
    assertEquals(NbgPetTrustTier.UserManaged, nbgPetSummarySourceReview(sideLoaded).trustTier)
    assertTrue(nbgPetSummarySourceReview(sideLoaded).requiresReview)

    assertEquals(NbgPetResourceSourceKind.UserManaged, nbgPetSummarySourceReview(userManaged).sourceKind)
    assertEquals(NbgPetTrustTier.UserManaged, nbgPetSummarySourceReview(userManaged).trustTier)
    assertTrue(nbgPetSummarySourceReview(userManaged).requiresReview)

    assertEquals(NbgPetResourceSourceKind.Unknown, nbgPetSummarySourceReview(spoofedBundled).sourceKind)
    assertEquals(NbgPetTrustTier.Blocked, nbgPetSummarySourceReview(spoofedBundled).trustTier)
    assertTrue(nbgPetSummarySourceReview(spoofedBundled).requiresReview)

    assertEquals(NbgPetTrustTier.Blocked, nbgPetSummarySourceReview(unknown).trustTier)
    assertTrue(nbgPetSummarySourceReview(unknown).requiresReview)

    assertEquals(NbgPetResourceSourceKind.Unknown, nbgPetSummarySourceReview(blank).sourceKind)
    assertEquals(NbgPetTrustTier.Blocked, nbgPetSummarySourceReview(blank).trustTier)
    assertTrue(nbgPetSummarySourceReview(blank).requiresReview)
  }

  @Test
  fun petSummaryReviewAcceptsBundledProvenanceForSeededPets() {
    val dir = createTempDirectory(prefix = "nbg-bundled-pet").toFile()
    val sprite = File(dir, "sprite.webp").apply { writeText("sprite") }
    File(dir, NBG_PET_PROVENANCE_FILE).writeText(
      JSONObject()
        .put("schema", NBG_PET_RESOURCE_INTEGRITY_VERSION)
        .put("sourceKind", NbgPetResourceSourceKind.BuiltIn.wireName)
        .put("trustTier", NbgPetTrustTier.TrustedBundled.wireName)
        .put("requiresReview", false)
        .put("slug", "rikka")
        .toString(),
    )
    val seededBundled = NbgPetSummary(
      slug = "rikka",
      displayName = "Rikka",
      kind = "character",
      submittedBy = "NBG",
      source = "built-in",
      spritePath = sprite.absolutePath,
    )

    val review = nbgPetSummarySourceReview(seededBundled)

    assertEquals(NbgPetResourceSourceKind.BuiltIn, review.sourceKind)
    assertEquals(NbgPetTrustTier.TrustedBundled, review.trustTier)
    assertFalse(review.requiresReview)
  }

  @Test
  fun readPetResourceBytesWithLimitRejectsOversizedResources() {
    val bytes = ByteArray(8) { 1 }

    val accepted = nbgReadPetResourceBytesWithLimit(ByteArrayInputStream(bytes), maxBytes = 8, label = "PetDex sprite")
    assertEquals(8, accepted.size)

    val oversized = runCatching {
      nbgReadPetResourceBytesWithLimit(ByteArrayInputStream(bytes), maxBytes = 7, label = "PetDex sprite")
    }
    assertTrue(oversized.isFailure)
    assertTrue(oversized.exceptionOrNull()?.message.orEmpty().contains("文件过大"))
  }

  @Test
  fun petResourceProvenancePresentUsesSiblingMetadataOnly() {
    val dir = createTempDirectory(prefix = "nbg-pet-summary").toFile()
    val sprite = File(dir, "sprite.webp").apply { writeText("sprite") }
    val pet = NbgPetSummary(
      slug = "boba",
      displayName = "Boba",
      kind = "character",
      submittedBy = "PetDex",
      source = "petdex",
      spritePath = sprite.absolutePath,
    )

    assertFalse(nbgPetResourceProvenancePresent(pet))
    File(dir, NBG_PET_PROVENANCE_FILE).writeText("{}")
    assertTrue(nbgPetResourceProvenancePresent(pet))
  }

  private fun samplePet(
    slug: String = "boba",
    sprite: String = "https://assets.petdex.dev/pets/boba/sprite.webp",
    json: String = "https://assets.petdex.dev/pets/boba/petjson.json",
  ): NbgPetDexManifestPet =
    NbgPetDexManifestPet(
      slug = slug,
      displayName = "Boba",
      kind = "character",
      submittedBy = "PetDex",
      spritesheetUrl = sprite,
      petJsonUrl = json,
      zipUrl = "",
    )
}
