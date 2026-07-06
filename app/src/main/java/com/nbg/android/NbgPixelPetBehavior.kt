package com.nbg.android

import kotlin.math.abs

internal enum class NbgPixelPetMood {
  Idle,
  Resting,
  Reading,
  Thinking,
  Streaming,
  Tooling,
  Connecting,
  Working,
  Warning,
  Dragging,
}

internal enum class NbgPixelPetActivity {
  IdlePatrol,
  IdleBlink,
  IdleWave,
  IdleCheer,
  IdleLookAround,
  Rest,
  ReadCard,
  Think,
  CompressContext,
  StreamFollow,
  ToolTerminal,
  ToolFile,
  ToolSearch,
  ToolInspect,
  ConnectWait,
  Prewarm,
  LocalHistory,
  ComposerFocus,
  Alert,
  Manual,
}

internal enum class NbgPetdex002State(
  val row: Int,
  val frames: Int,
  val frameDelayMs: Long,
) {
  Idle(row = 0, frames = 6, frameDelayMs = 260L),
  RunningRight(row = 1, frames = 8, frameDelayMs = 118L),
  RunningLeft(row = 2, frames = 8, frameDelayMs = 118L),
  Waving(row = 3, frames = 4, frameDelayMs = 290L),
  Jumping(row = 4, frames = 5, frameDelayMs = 270L),
  Failed(row = 5, frames = 6, frameDelayMs = 340L),
  Waiting(row = 6, frames = 6, frameDelayMs = 360L),
  Running(row = 7, frames = 6, frameDelayMs = 230L),
  Review(row = 8, frames = 6, frameDelayMs = 280L),
}

internal data class NbgPetdex002Animation(
  val row: Int,
  val frames: Int,
  val frameDelayMs: Long,
)

private fun NbgPetdex002State.animation(frameDelayMs: Long = this.frameDelayMs): NbgPetdex002Animation =
  NbgPetdex002Animation(row = row, frames = frames, frameDelayMs = frameDelayMs)

internal fun nbgPetdex002Animation(
  mood: NbgPixelPetMood,
  activity: NbgPixelPetActivity,
  walking: Boolean,
  readingText: Boolean,
  facingLeft: Boolean,
): NbgPetdex002Animation =
  when {
    walking && facingLeft -> NbgPetdex002State.RunningLeft.animation()
    walking -> NbgPetdex002State.RunningRight.animation()
    activity == NbgPixelPetActivity.IdleBlink -> NbgPetdex002State.Idle.animation(frameDelayMs = 280L)
    activity == NbgPixelPetActivity.IdleWave -> NbgPetdex002State.Waving.animation(frameDelayMs = 290L)
    activity == NbgPixelPetActivity.IdleCheer -> NbgPetdex002State.Jumping.animation(frameDelayMs = 280L)
    activity == NbgPixelPetActivity.IdleLookAround -> NbgPetdex002State.Review.animation(frameDelayMs = 330L)
    activity == NbgPixelPetActivity.Rest || mood == NbgPixelPetMood.Resting -> NbgPetdex002State.Failed.animation(frameDelayMs = 380L)
    activity == NbgPixelPetActivity.ToolTerminal ||
      activity == NbgPixelPetActivity.ToolFile ||
      activity == NbgPixelPetActivity.ToolSearch ||
      activity == NbgPixelPetActivity.ToolInspect ||
      mood == NbgPixelPetMood.Tooling -> NbgPetdex002State.Running.animation(frameDelayMs = 230L)
    activity == NbgPixelPetActivity.StreamFollow || mood == NbgPixelPetMood.Streaming -> NbgPetdex002State.Waving.animation(frameDelayMs = 320L)
    activity == NbgPixelPetActivity.CompressContext -> NbgPetdex002State.Review.animation(frameDelayMs = 340L)
    activity == NbgPixelPetActivity.Think || mood == NbgPixelPetMood.Thinking -> NbgPetdex002State.Review.animation(frameDelayMs = 300L)
    activity == NbgPixelPetActivity.ComposerFocus -> NbgPetdex002State.Waiting.animation(frameDelayMs = 380L)
    activity == NbgPixelPetActivity.Prewarm -> NbgPetdex002State.Waiting.animation(frameDelayMs = 360L)
    activity == NbgPixelPetActivity.LocalHistory -> NbgPetdex002State.Review.animation(frameDelayMs = 360L)
    activity == NbgPixelPetActivity.ConnectWait || mood == NbgPixelPetMood.Connecting -> NbgPetdex002State.Waiting.animation(frameDelayMs = 360L)
    activity == NbgPixelPetActivity.Alert || mood == NbgPixelPetMood.Warning -> NbgPetdex002State.Failed.animation(frameDelayMs = 320L)
    readingText || mood == NbgPixelPetMood.Reading -> NbgPetdex002State.Review.animation(frameDelayMs = 320L)
    mood == NbgPixelPetMood.Idle -> NbgPetdex002State.Idle.animation(frameDelayMs = 260L)
    mood == NbgPixelPetMood.Dragging -> NbgPetdex002State.RunningRight.animation(frameDelayMs = 150L)
    else -> NbgPetdex002State.Idle.animation(frameDelayMs = 260L)
  }

internal fun nbgPixelPetMood(runStatus: NbgChatRunStatus): NbgPixelPetMood =
  when {
    runStatus.warning -> NbgPixelPetMood.Warning
    runStatus.label.contains("调用工具") || runStatus.label.contains("工具") -> NbgPixelPetMood.Tooling
    runStatus.label.contains("输出") -> NbgPixelPetMood.Streaming
    runStatus.label.contains("思考") || runStatus.label.contains("压缩") -> NbgPixelPetMood.Thinking
    runStatus.label.contains("连接") || runStatus.label.contains("预热") -> NbgPixelPetMood.Connecting
    runStatus.label.contains("本地历史") -> NbgPixelPetMood.Reading
    runStatus.active -> NbgPixelPetMood.Tooling
    else -> NbgPixelPetMood.Idle
  }

internal fun nbgPixelPetActivity(
  runStatus: NbgChatRunStatus,
  mood: NbgPixelPetMood = nbgPixelPetMood(runStatus),
  hasVisibleCards: Boolean,
  step: Int,
  composerActive: Boolean = false,
): NbgPixelPetActivity =
  when {
    mood == NbgPixelPetMood.Warning -> NbgPixelPetActivity.Alert
    runStatus.label.contains("压缩") -> NbgPixelPetActivity.CompressContext
    runStatus.label.contains("预热") -> NbgPixelPetActivity.Prewarm
    runStatus.label.contains("本地历史") -> NbgPixelPetActivity.LocalHistory
    mood == NbgPixelPetMood.Tooling -> nbgPixelPetToolActivity(runStatus)
    mood == NbgPixelPetMood.Streaming -> NbgPixelPetActivity.StreamFollow
    mood == NbgPixelPetMood.Thinking -> NbgPixelPetActivity.Think
    mood == NbgPixelPetMood.Connecting -> NbgPixelPetActivity.ConnectWait
    composerActive -> NbgPixelPetActivity.ComposerFocus
    hasVisibleCards && step % 20 == 17 -> NbgPixelPetActivity.ReadCard
    !composerActive && step % 16 == 3 -> NbgPixelPetActivity.IdleBlink
    !composerActive && step % 16 == 5 -> NbgPixelPetActivity.Rest
    !composerActive && step % 16 == 9 -> NbgPixelPetActivity.IdleWave
    !composerActive && step % 16 == 13 -> NbgPixelPetActivity.IdleCheer
    !composerActive && step % 16 == 15 -> NbgPixelPetActivity.IdleLookAround
    else -> NbgPixelPetActivity.IdlePatrol
  }

internal fun nbgPixelPetToolActivity(runStatus: NbgChatRunStatus): NbgPixelPetActivity {
  val detail = runStatus.detail.orEmpty().lowercase()
  val label = runStatus.label.lowercase()
  val text = "$label $detail"
  return when {
    text.contains("terminal") ||
      text.contains("shell") ||
      text.contains("bash") ||
      text.contains("命令") ||
      text.contains("终端") ||
      text.contains("运行") -> NbgPixelPetActivity.ToolTerminal
    text.contains("diff") ||
      text.contains("patch") ||
      text.contains("file") ||
      text.contains("文件") ||
      text.contains("读取") ||
      text.contains("写入") ||
      text.contains("编辑") -> NbgPixelPetActivity.ToolFile
    text.contains("search") ||
      text.contains("web") ||
      text.contains("url") ||
      text.contains("搜索") ||
      text.contains("网页") ||
      text.contains("浏览") -> NbgPixelPetActivity.ToolSearch
    else -> NbgPixelPetActivity.ToolInspect
  }
}

internal fun nbgPixelPetShouldHoldPosition(
  activity: NbgPixelPetActivity,
  step: Int = 0,
): Boolean =
  when (activity) {
    NbgPixelPetActivity.IdlePatrol -> step % 4 != 0
    NbgPixelPetActivity.IdleBlink,
    NbgPixelPetActivity.IdleWave,
    NbgPixelPetActivity.IdleCheer,
    NbgPixelPetActivity.IdleLookAround,
    NbgPixelPetActivity.Rest,
    NbgPixelPetActivity.Manual -> true
    NbgPixelPetActivity.Think,
    NbgPixelPetActivity.CompressContext,
    NbgPixelPetActivity.StreamFollow,
    NbgPixelPetActivity.ToolTerminal,
    NbgPixelPetActivity.ToolFile,
    NbgPixelPetActivity.ToolSearch,
    NbgPixelPetActivity.ToolInspect,
    NbgPixelPetActivity.ConnectWait,
    NbgPixelPetActivity.Prewarm,
    NbgPixelPetActivity.LocalHistory,
    NbgPixelPetActivity.ComposerFocus,
    NbgPixelPetActivity.Alert,
    NbgPixelPetActivity.ReadCard -> true
  }

internal fun nbgPixelPetShouldWalkForDelta(deltaX: Float, petWidthPx: Int): Boolean {
  val minimumWalkDelta = (petWidthPx * 0.28f).coerceAtLeast(24f)
  return abs(deltaX) >= minimumWalkDelta
}

internal fun nbgPixelPetBubbleText(
  runStatus: NbgChatRunStatus,
  mood: NbgPixelPetMood = nbgPixelPetMood(runStatus),
  activity: NbgPixelPetActivity = nbgPixelPetActivity(runStatus, mood, hasVisibleCards = false, step = 0),
): String =
  when (activity) {
    NbgPixelPetActivity.Alert -> "需要处理"
    NbgPixelPetActivity.ToolTerminal -> "终端"
    NbgPixelPetActivity.ToolFile -> "文件"
    NbgPixelPetActivity.ToolSearch -> "搜索"
    NbgPixelPetActivity.ToolInspect -> runStatus.detail?.take(6)?.ifBlank { "工具" } ?: "工具"
    NbgPixelPetActivity.StreamFollow -> "输出"
    NbgPixelPetActivity.Think -> "思考"
    NbgPixelPetActivity.CompressContext -> "压缩"
    NbgPixelPetActivity.ConnectWait -> ""
    NbgPixelPetActivity.Prewarm -> "预热"
    NbgPixelPetActivity.LocalHistory -> "历史"
    NbgPixelPetActivity.ComposerFocus -> ""
    NbgPixelPetActivity.ReadCard -> "阅读"
    NbgPixelPetActivity.Rest -> "休息"
    NbgPixelPetActivity.Manual -> "跟随"
    NbgPixelPetActivity.IdleBlink,
    NbgPixelPetActivity.IdleWave,
    NbgPixelPetActivity.IdleCheer,
    NbgPixelPetActivity.IdleLookAround,
    NbgPixelPetActivity.IdlePatrol -> ""
  }
