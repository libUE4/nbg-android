package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgTeamDelegationPolicyTest {
  @Test
  fun boundedTemplatesDeclareBudgetsToolsCancellationAndConsolidation() {
    val diff = nbgReviewTeamDelegationRequest("review diff for current change")
    val verification = nbgReviewTeamDelegationRequest("run verification and test the app")
    val module = nbgReviewTeamDelegationRequest("inspect module terminal-core")
    val security = nbgReviewTeamDelegationRequest("security pass for skill install")

    assertEquals(NBG_TEAM_DELEGATION_POLICY_VERSION, diff.policyVersion)
    assertTrue(diff.allowStart)
    assertEquals(NbgTeamDelegationTemplate.DiffReview, diff.template)
    assertEquals("审查 Diff", diff.title)
    assertEquals(listOf("read", "diff", "memory_read"), diff.allowedTools)
    assertEquals(3, diff.maxSubtasks)
    assertEquals(8, diff.maxMinutes)
    assertEquals(NbgPermissionRiskTier.Low, diff.permissionTier)
    assertFalse(diff.requiresConfirmation)
    assertTrue(diff.supportsCancel)
    assertTrue(diff.consolidationRequired)

    assertEquals(NbgTeamDelegationTemplate.Verification, verification.template)
    assertTrue(verification.allowedTools.contains("terminal"))
    assertEquals(NbgPermissionRiskTier.Medium, verification.permissionTier)
    assertTrue(verification.requiresConfirmation)

    assertEquals(NbgTeamDelegationTemplate.ModuleInspection, module.template)
    assertEquals(NbgPermissionRiskTier.Low, module.permissionTier)

    assertEquals(NbgTeamDelegationTemplate.SecurityPass, security.template)
    assertEquals(NbgPermissionRiskTier.High, security.permissionTier)
    assertTrue(security.requiresConfirmation)
  }

  @Test
  fun unknownTeamDelegationRequestIsBlockedBeforeBackgroundExecution() {
    val blank = nbgReviewTeamDelegationRequest("   ")
    val unknown = nbgReviewTeamDelegationRequest("make this app amazing in the background")

    assertFalse(blank.allowStart)
    assertFalse(unknown.allowStart)
    assertTrue(unknown.reason.contains("bounded 模板"))
    assertEquals(emptyList<String>(), unknown.allowedTools)
    assertEquals(0, unknown.maxSubtasks)
    assertEquals(0, unknown.maxMinutes)
    assertTrue(unknown.requiresConfirmation)
    assertTrue(unknown.supportsCancel)
    assertTrue(unknown.consolidationRequired)
  }
}
