package com.nbg.android

internal const val NBG_DEFAULT_PET_SLUG = "petdex-002"
internal const val NBG_PET_FRAME_WIDTH_PX = 192
internal const val NBG_PET_FRAME_HEIGHT_PX = 208
internal const val NBG_PET_COLUMNS = 8
internal const val NBG_PETDEX_VISIBLE_LIMIT = 50

internal data class NbgPetSummary(
  val slug: String,
  val displayName: String,
  val kind: String,
  val submittedBy: String,
  val source: String,
  val spritesheetUrl: String = "",
  val petJsonUrl: String = "",
  val spritePath: String? = null,
  val petJsonPath: String? = null,
  val installedAtMs: Long = System.currentTimeMillis(),
  val builtIn: Boolean = false,
)

internal data class NbgPetStoreState(
  val installedPets: List<NbgPetSummary>,
  val currentPetSlug: String,
  val hidden: Boolean,
) {
  val currentPet: NbgPetSummary
    get() = installedPets.firstOrNull { it.slug == currentPetSlug } ?: nbgDefaultPetSummary()
}

internal data class NbgPetDexManifestPet(
  val slug: String,
  val displayName: String,
  val kind: String,
  val submittedBy: String,
  val spritesheetUrl: String,
  val petJsonUrl: String,
  val zipUrl: String,
)

internal data class NbgPetDexManifest(
  val generatedAt: String,
  val total: Int,
  val pets: List<NbgPetDexManifestPet>,
)

internal fun nbgDefaultPetSummary(): NbgPetSummary =
  NbgPetSummary(
    slug = NBG_DEFAULT_PET_SLUG,
    displayName = "PetDex 002",
    kind = "character",
    submittedBy = "PetDex",
    source = "built-in",
    builtIn = true,
  )
