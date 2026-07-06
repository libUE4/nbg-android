package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class NbgAgentPetState(initialPetState: NbgPetStoreState) {
  var petState by mutableStateOf(initialPetState)
    private set
  var manifest by mutableStateOf<NbgPetDexManifest?>(null)
    private set
  var loadingManifest by mutableStateOf(false)
    private set
  var installingSlug by mutableStateOf<String?>(null)
    private set
  var error by mutableStateOf<String?>(null)
    private set

  val isInstalling: Boolean
    get() = installingSlug != null

  fun beginManifestRefresh(): Boolean {
    if (loadingManifest) return false
    loadingManifest = true
    error = null
    return true
  }

  fun applyManifestRefreshSuccess(nextManifest: NbgPetDexManifest) {
    manifest = nextManifest
  }

  fun applyManifestRefreshFailure(error: Throwable) {
    this.error = nbgPetDexFailureMessage(error)
  }

  fun finishManifestRefresh() {
    loadingManifest = false
  }

  fun beginInstall(pet: NbgPetDexManifestPet): Boolean {
    if (isInstalling) return false
    installingSlug = pet.slug
    error = null
    return true
  }

  fun applyInstallSuccess(nextPetState: NbgPetStoreState) {
    petState = nextPetState
  }

  fun applyInstallFailure(error: Throwable) {
    this.error = "安装失败：${nbgPetDexFailureMessage(error)}"
  }

  fun finishInstall(slug: String) {
    if (installingSlug == slug) {
      installingSlug = null
    }
  }

  fun applyStoreState(nextPetState: NbgPetStoreState) {
    petState = nextPetState
  }
}

private fun nbgPetDexFailureMessage(error: Throwable): String =
  error.message.orEmpty().ifBlank { error::class.java.simpleName }
