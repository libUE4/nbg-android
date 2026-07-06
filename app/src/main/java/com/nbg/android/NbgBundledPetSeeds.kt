package com.nbg.android

internal data class NbgBundledPetSeed(
  val slug: String,
  val displayName: String,
  val kind: String,
  val submittedBy: String,
  val spritesheetUrl: String,
  val petJsonUrl: String,
)

internal fun nbgBundledPetSeeds(): List<NbgBundledPetSeed> =
  listOf(
    NbgBundledPetSeed(
      slug = "rikka",
      displayName = "小鸟游六花",
      kind = "character",
      submittedBy = "XUN",
      spritesheetUrl = "https://assets.petdex.dev/pets/rikka-6d098eca1235/sprite.webp",
      petJsonUrl = "https://assets.petdex.dev/pets/rikka-6d098eca1235/petjson.json",
    ),
    NbgBundledPetSeed(
      slug = "erii-2",
      displayName = "绘梨衣",
      kind = "character",
      submittedBy = "ZP D.",
      spritesheetUrl = "https://assets.petdex.dev/pets/erii-3a90e3d7dbc6/sprite.webp",
      petJsonUrl = "https://assets.petdex.dev/pets/erii-3a90e3d7dbc6/petjson.json",
    ),
    NbgBundledPetSeed(
      slug = "wangcai",
      displayName = "Wangcai",
      kind = "creature",
      submittedBy = "boxu-openai",
      spritesheetUrl = "https://assets.petdex.dev/pets/wangcai-4745956f417b/sprite.webp",
      petJsonUrl = "https://assets.petdex.dev/pets/wangcai-4745956f417b/petjson.json",
    ),
    NbgBundledPetSeed(
      slug = "claude-crab",
      displayName = "Claude Crab",
      kind = "creature",
      submittedBy = "Dery F.",
      spritesheetUrl = "https://assets.petdex.dev/pets/claude-crab-2148b922aa51/sprite.webp",
      petJsonUrl = "https://assets.petdex.dev/pets/claude-crab-2148b922aa51/petjson.json",
    ),
    NbgBundledPetSeed(
      slug = "ggbond",
      displayName = "猪猪侠",
      kind = "creature",
      submittedBy = "Hayley S.",
      spritesheetUrl = "https://assets.petdex.dev/pets/ggbond-d114a5574ef9/sprite.webp",
      petJsonUrl = "https://assets.petdex.dev/pets/ggbond-d114a5574ef9/petjson.json",
    ),
    NbgBundledPetSeed(
      slug = "homelander",
      displayName = "Homelander",
      kind = "character",
      submittedBy = "Serhat",
      spritesheetUrl = "https://assets.petdex.dev/pets/homelander-dbbb6a60a484/sprite.webp",
      petJsonUrl = "https://assets.petdex.dev/pets/homelander-dbbb6a60a484/petjson.json",
    ),
  )
