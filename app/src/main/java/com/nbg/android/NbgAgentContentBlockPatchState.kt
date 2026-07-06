package com.nbg.android

internal class NbgAgentContentBlockPatchState {
  private val pendingPatches = mutableMapOf<String, HanakoContentBlockPatch>()

  val hasPending: Boolean
    get() = pendingPatches.isNotEmpty()

  val pendingCount: Int
    get() = pendingPatches.size

  fun clear() {
    pendingPatches.clear()
  }

  fun enqueue(taskId: String, patch: HanakoContentBlockPatch) {
    pendingPatches[taskId] = pendingPatches[taskId].merge(patch)
  }

  fun consumePatchFor(block: HanakoContentBlock): HanakoContentBlock {
    val patch = pendingPatches.remove(block.taskId) ?: return block
    return block.applyPatch(patch)
  }
}
