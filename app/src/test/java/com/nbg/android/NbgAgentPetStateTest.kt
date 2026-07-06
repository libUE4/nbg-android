package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NbgAgentPetStateTest {
  @Test
  fun initialStateExposesLoadedPetStoreState() {
    val initial = petStoreState(currentSlug = "petdex-002")
    val state = NbgAgentPetState(initial)

    assertSame(initial, state.petState)
    assertNull(state.manifest)
    assertFalse(state.loadingManifest)
    assertNull(state.installingSlug)
    assertNull(state.error)
  }

  @Test
  fun manifestRefreshSetsBusyClearsErrorAndRejectsConcurrentRefresh() {
    val state = NbgAgentPetState(petStoreState())
    state.applyManifestRefreshFailure(IllegalStateException("offline"))

    assertTrue(state.beginManifestRefresh())
    assertFalse(state.beginManifestRefresh())

    assertTrue(state.loadingManifest)
    assertNull(state.error)
  }

  @Test
  fun manifestSuccessStoresResultAndFinishClearsBusyState() {
    val state = NbgAgentPetState(petStoreState())
    val manifest = petManifest("petdex-003")

    assertTrue(state.beginManifestRefresh())
    state.applyManifestRefreshSuccess(manifest)
    state.finishManifestRefresh()

    assertSame(manifest, state.manifest)
    assertFalse(state.loadingManifest)
  }

  @Test
  fun manifestFailureStoresRecoverableMessageWithClassFallback() {
    val state = NbgAgentPetState(petStoreState())

    state.applyManifestRefreshFailure(RuntimeException())

    assertEquals("RuntimeException", state.error)
  }

  @Test
  fun installSetsBusyClearsErrorAndRejectsConcurrentInstall() {
    val state = NbgAgentPetState(petStoreState())
    state.applyInstallFailure(IllegalStateException("disk full"))

    assertTrue(state.beginInstall(petManifestPet("petdex-003")))
    assertFalse(state.beginInstall(petManifestPet("petdex-004")))

    assertTrue(state.isInstalling)
    assertEquals("petdex-003", state.installingSlug)
    assertNull(state.error)
  }

  @Test
  fun installSuccessAndStoreUpdatesReplacePetState() {
    val state = NbgAgentPetState(petStoreState(currentSlug = "petdex-002"))
    val installed = petStoreState(currentSlug = "petdex-003")
    val hidden = installed.copy(hidden = true)

    state.applyInstallSuccess(installed)
    assertSame(installed, state.petState)

    state.applyStoreState(hidden)
    assertSame(hidden, state.petState)
  }

  @Test
  fun installFailureStoresPrefixedMessageAndFinishRequiresMatchingSlug() {
    val state = NbgAgentPetState(petStoreState())

    assertTrue(state.beginInstall(petManifestPet("petdex-003")))
    state.applyInstallFailure(IllegalStateException("checksum mismatch"))
    state.finishInstall("petdex-004")

    assertEquals("安装失败：checksum mismatch", state.error)
    assertEquals("petdex-003", state.installingSlug)

    state.finishInstall("petdex-003")
    assertNull(state.installingSlug)
    assertFalse(state.isInstalling)
  }

  private fun petStoreState(currentSlug: String = "petdex-002"): NbgPetStoreState =
    NbgPetStoreState(
      installedPets = listOf(
        NbgPetSummary(
          slug = currentSlug,
          displayName = currentSlug,
          kind = "character",
          submittedBy = "test",
          source = "test",
        ),
      ),
      currentPetSlug = currentSlug,
      hidden = false,
    )

  private fun petManifest(slug: String): NbgPetDexManifest =
    NbgPetDexManifest(
      generatedAt = "2026-07-05T00:00:00Z",
      total = 1,
      pets = listOf(petManifestPet(slug)),
    )

  private fun petManifestPet(slug: String): NbgPetDexManifestPet =
    NbgPetDexManifestPet(
      slug = slug,
      displayName = slug,
      kind = "character",
      submittedBy = "test",
      spritesheetUrl = "https://example.invalid/$slug.png",
      petJsonUrl = "https://example.invalid/$slug.json",
      zipUrl = "https://example.invalid/$slug.zip",
    )
}
