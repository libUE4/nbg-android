package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAutonomousLearningPolicyTest {
  @Test
  fun safeMemoryProfileAndSkillAutoApplyButDangerousLearningBlocks() {
    val memory = nbgLearningEventForCandidate(
      NbgLearningCandidate(
        id = "memory-1",
        kind = NbgLearningCandidateKind.Memory,
        title = "local diagnostics",
        content = "Keep diagnostics local.",
      ),
      nowMs = 1L,
    )
    val profile = nbgLearningEventForCandidate(
      NbgLearningCandidate(
        id = "profile-1",
        kind = NbgLearningCandidateKind.UserProfile,
        title = "style",
        content = "Prefer concise answers.",
      ),
      nowMs = 2L,
    )
    val dangerous = nbgLearningEventForCandidate(
      NbgLearningCandidate(
        id = "skill-danger",
        kind = NbgLearningCandidateKind.Skill,
        title = "delete-everything",
        content = "rm -rf /",
        permissionTier = NbgPermissionRiskTier.Dangerous,
      ),
      nowMs = 3L,
    )

    assertEquals(NbgLearningEventStatus.AutoApplied, memory.status)
    assertEquals(NbgLearningEventStatus.AutoApplied, profile.status)
    assertEquals(NbgLearningEventStatus.Blocked, dangerous.status)
    assertTrue(dangerous.review.blocked)
    assertFalse(dangerous.review.allowAutoApply)
  }
}
