package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgMemoryContextPolicyTest {
  @Test
  fun reviewAllowsKnownMemoryTypesAndNormalizesUnknownType() {
    val review = nbgReviewMemoryInput(
      HanakoMemoryInput(
        type = "unknown",
        title = " Decision ",
        content = "Use local-first diagnostics for beta support.",
        tags = listOf(" beta ", "beta", " release "),
      ),
    )

    assertTrue(review.allowSave)
    assertTrue(review.allowContextInjection)
    assertTrue(review.requiresExplicitConfirmation)
    assertEquals("project_fact", review.normalizedType)
    assertEquals(NbgMemoryContextRisk.Low, review.risk)
  }

  @Test
  fun reviewBlocksSecretsTokensPasswordsAndPrivateKeys() {
    val samples = listOf(
      "api_key=sk-live-secret-1234567890",
      "Authorization: Bearer abcdefghijklmnopqrstuvwxyz",
      "password: hunter2secret",
      "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----",
      "ghp_abcdefghijklmnopqrstuvwxyz123456",
      "AKIAABCDEFGHIJKLMNOP",
    )

    samples.forEach { sample ->
      val review = nbgReviewMemoryInput(HanakoMemoryInput(content = sample))
      assertFalse(sample, review.allowSave)
      assertFalse(sample, review.allowContextInjection)
      assertEquals(sample, NbgMemoryContextRisk.BlockedSensitive, review.risk)
      assertTrue(sample, review.userMessage.contains("敏感"))
    }
  }

  @Test
  fun reviewBlocksEmptyAndTooLargeContent() {
    val empty = nbgReviewMemoryInput(HanakoMemoryInput(content = "   "))
    val tooLarge = nbgReviewMemoryInput(HanakoMemoryInput(content = "a".repeat(NBG_MEMORY_MAX_CONTENT_CHARS + 1)))

    assertEquals(NbgMemoryContextRisk.BlockedEmpty, empty.risk)
    assertFalse(empty.allowSave)
    assertEquals("记忆内容不能为空", empty.userMessage)

    assertEquals(NbgMemoryContextRisk.BlockedTooLarge, tooLarge.risk)
    assertFalse(tooLarge.allowSave)
    assertTrue(tooLarge.userMessage.contains(NBG_MEMORY_MAX_CONTENT_CHARS.toString()))
  }

  @Test
  fun memoryExportPolicyRequiresUserTriggerSelectionAndScanButIsDisabledInV1() {
    val noEvidence = nbgReviewMemoryExportRequest(
      selectedItemCount = -4,
      explicitUserTrigger = false,
      perItemSelection = false,
      sensitiveScanPassed = false,
    )
    val allFutureEvidence = nbgReviewMemoryExportRequest(
      selectedItemCount = 3,
      explicitUserTrigger = true,
      perItemSelection = true,
      sensitiveScanPassed = true,
    )

    assertEquals(NBG_MEMORY_EXPORT_POLICY_VERSION, noEvidence.policyVersion)
    assertEquals(listOf("explicit_user_trigger", "per_item_selection", "sensitive_scan_passed"), noEvidence.requiredEvidence)
    assertEquals(emptyList<String>(), noEvidence.presentEvidence)
    assertEquals(0, noEvidence.selectedItemCount)
    assertFalse(noEvidence.allowExport)

    assertEquals(listOf("explicit_user_trigger", "per_item_selection", "sensitive_scan_passed"), allFutureEvidence.presentEvidence)
    assertEquals(3, allFutureEvidence.selectedItemCount)
    assertFalse("v1 must not export Memory content even when future evidence labels are present", allFutureEvidence.allowExport)
    assertTrue(allFutureEvidence.reason.contains("does not expose Memory export"))
  }

  @Test
  fun memoryUpstreamPolicyAdoptionKeepsAndroidPatchGateInV1() {
    val noEvidence = nbgReviewMemoryUpstreamPolicyAdoption(
      upstreamPolicyVersionMatches = false,
      androidPatchMarkerMatches = false,
      parityTestsPassed = false,
    )
    val allFutureEvidence = nbgReviewMemoryUpstreamPolicyAdoption(
      upstreamPolicyVersionMatches = true,
      androidPatchMarkerMatches = true,
      parityTestsPassed = true,
    )

    assertEquals(NBG_MEMORY_UPSTREAM_POLICY_ADOPTION_VERSION, noEvidence.policyVersion)
    assertEquals(
      listOf("upstream_policy_version_match", "android_patch_marker_match", "parity_tests_passed"),
      noEvidence.requiredEvidence,
    )
    assertEquals(emptyList<String>(), noEvidence.presentEvidence)
    assertFalse(noEvidence.mayRemoveAndroidRuntimePatch)

    assertEquals(
      listOf("upstream_policy_version_match", "android_patch_marker_match", "parity_tests_passed"),
      allFutureEvidence.presentEvidence,
    )
    assertFalse("v1 must keep the Android Memory runtime patch gate even when future evidence labels are present", allFutureEvidence.mayRemoveAndroidRuntimePatch)
    assertTrue(allFutureEvidence.reason.contains("keeps the Android Memory runtime patch gate"))
  }

  @Test
  fun reviewMemoryItemKeepsDisabledItemsOutOfInjectableDiagnostics() {
    val item = HanakoMemoryItem(
      id = "m1",
      type = "decision",
      title = "Use local-first",
      content = "Keep diagnostics local and user-triggered.",
      enabled = false,
    )
    val review = nbgReviewMemoryItem(item)

    assertTrue(review.allowSave)
    assertTrue(review.allowContextInjection)
    assertFalse(review.requiresExplicitConfirmation)
    assertEquals("decision", review.normalizedType)
  }

  @Test
  fun memoryAuditEventsKeepOnlyMetadataAndStayBounded() {
    val event = nbgMemoryAuditEvent(
      action = "create /root/private/memory.md",
      result = "success token=secret",
      normalizedType = "decision",
      risk = NbgMemoryContextRisk.Low.wireName,
      timestampMillis = -1,
    )
    val normal = nbgMemoryAuditEvent(
      action = "update",
      result = "blocked",
      normalizedType = "user_preference",
      risk = NbgMemoryContextRisk.BlockedSensitive.wireName,
      timestampMillis = 42,
    )
    val state = (0..NBG_MEMORY_AUDIT_MAX_EVENTS).fold(HanakoMemoryState()) { current, index ->
      current.withMemoryAuditEvent(normal.copy(timestampMillis = index.toLong()))
    }

    assertEquals("unknown", event.action)
    assertEquals("unknown", event.result)
    assertEquals("decision", event.normalizedType)
    assertEquals(NbgMemoryContextRisk.Low.wireName, event.risk)
    assertEquals(0, event.timestampMillis)

    assertEquals("update", normal.action)
    assertEquals("blocked", normal.result)
    assertEquals("user_preference", normal.normalizedType)
    assertEquals(NbgMemoryContextRisk.BlockedSensitive.wireName, normal.risk)
    assertEquals(NBG_MEMORY_AUDIT_MAX_EVENTS, state.auditEvents.size)
    assertEquals(1L, state.auditEvents.first().timestampMillis)
  }
}
