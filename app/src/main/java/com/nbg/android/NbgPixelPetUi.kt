package com.nbg.android

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val NBG_PIXEL_PET_IDLE_PULSE_MS = 2_600L
private const val NBG_PIXEL_PET_ACTIVE_PULSE_MS = 900L

@Composable
internal fun NbgPixelPetOverlay(
  runStatus: NbgChatRunStatus,
  pet: NbgPetSummary,
  bottomInset: androidx.compose.ui.unit.Dp,
  composerBoundsInRoot: Rect? = null,
  composerActive: Boolean = false,
  onOpenPets: () -> Unit = {},
  onHidePet: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var dragX by remember { mutableFloatStateOf(0f) }
  var dragY by remember { mutableFloatStateOf(0f) }
  var containerWidthPx by remember { mutableIntStateOf(0) }
  var containerHeightPx by remember { mutableIntStateOf(0) }
  var petWidthPx by remember { mutableIntStateOf(0) }
  var petHeightPx by remember { mutableIntStateOf(0) }
  var positionInitialized by remember { mutableStateOf(false) }
  var dragging by remember { mutableStateOf(false) }
  var facingLeft by remember { mutableStateOf(false) }
  var overlayTopInRoot by remember { mutableFloatStateOf(0f) }
  var focusPulse by remember { mutableIntStateOf(0) }
  var menuOpen by remember { mutableStateOf(false) }
  val density = LocalDensity.current
  val startInsetPx = with(density) { 14.dp.toPx() }
  val safeBottomInset = bottomInset.coerceAtLeast(80.dp).coerceAtMost(112.dp)
  val fallbackBottomInsetPx = with(density) { (safeBottomInset + 8.dp).toPx() }
  val composerTopGapPx = with(density) { 3.dp.toPx() }
  val mood = nbgPixelPetMood(runStatus)
  val activity = if (dragging) {
    NbgPixelPetActivity.Manual
  } else {
    nbgPixelPetActivity(
      runStatus = runStatus,
      mood = mood,
      hasVisibleCards = false,
      step = 0,
      composerActive = composerActive,
    )
  }
  val displayedMood = if (dragging) NbgPixelPetMood.Dragging else mood
  val scale by animateFloatAsState(
    targetValue = when (displayedMood) {
      NbgPixelPetMood.Tooling -> 1.03f
      NbgPixelPetMood.Warning -> 1.04f
      NbgPixelPetMood.Resting -> 0.96f
      else -> 1f
    },
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "pixel pet scale",
  )
  val haloAlpha by animateFloatAsState(
    targetValue = when (displayedMood) {
      NbgPixelPetMood.Warning -> 0.66f
      NbgPixelPetMood.Tooling,
      NbgPixelPetMood.Streaming,
      NbgPixelPetMood.Thinking,
      NbgPixelPetMood.Connecting -> 0.42f
      NbgPixelPetMood.Dragging -> 0.58f
      else -> if (composerActive) 0.08f else 0.15f
    },
    animationSpec = tween(durationMillis = nbgMotionDuration(220), easing = FastOutSlowInEasing),
    label = "pixel pet halo alpha",
  )
  val petAlpha by animateFloatAsState(
    targetValue = when {
      menuOpen || dragging -> 1f
      composerActive -> 0.74f
      else -> 0.82f
    },
    animationSpec = tween(durationMillis = nbgMotionDuration(180), easing = FastOutSlowInEasing),
    label = "pixel pet alpha",
  )
  val composerBoundsKey = composerBoundsInRoot?.let { bounds ->
    "${bounds.left.roundToInt()}:${bounds.top.roundToInt()}:${bounds.right.roundToInt()}:${bounds.bottom.roundToInt()}"
  } ?: "none"
  val menuWidth = 142.dp
  val menuRightOffset = 58.dp
  val menuLeftOffset = -(menuWidth + 8.dp)
  val menuOpenToLeft = containerWidthPx > 0 && with(density) {
    startInsetPx + dragX + menuRightOffset.toPx() + menuWidth.toPx() > containerWidthPx - startInsetPx
  }

  fun minDragX(): Float = -startInsetPx

  fun maxDragX(): Float {
    if (containerWidthPx <= 0 || petWidthPx <= 0) return dragX
    return (containerWidthPx - startInsetPx - petWidthPx).coerceAtLeast(minDragX())
  }

  fun bottomInsetPx(): Float {
    val composerTop = composerBoundsInRoot?.top ?: return fallbackBottomInsetPx
    val localComposerTop = composerTop - overlayTopInRoot
    if (containerHeightPx <= 0 || localComposerTop <= 0f) return fallbackBottomInsetPx
    val maxInset = containerHeightPx.toFloat()
    val minInset = fallbackBottomInsetPx.coerceAtMost(maxInset)
    return (containerHeightPx - localComposerTop + composerTopGapPx).coerceIn(minInset, maxInset)
  }

  fun minDragY(): Float {
    if (containerHeightPx <= 0 || petHeightPx <= 0) return dragY
    return (petHeightPx + bottomInsetPx() - containerHeightPx).coerceAtMost(0f)
  }

  fun maxDragY(): Float = 0f

  fun clampDragX(value: Float): Float {
    if (containerWidthPx <= 0 || petWidthPx <= 0) return value
    return value.coerceIn(minDragX(), maxDragX())
  }

  fun clampDragY(value: Float): Float {
    if (containerHeightPx <= 0 || petHeightPx <= 0) return value
    return value.coerceIn(minDragY(), maxDragY())
  }

  fun rightDefaultX(): Float = clampDragX(maxDragX())

  LaunchedEffect(containerWidthPx, containerHeightPx, petWidthPx, petHeightPx, composerBoundsKey) {
    if (containerWidthPx <= 0 || containerHeightPx <= 0 || petWidthPx <= 0 || petHeightPx <= 0) return@LaunchedEffect
    if (!positionInitialized) {
      dragX = rightDefaultX()
      dragY = clampDragY(0f)
      positionInitialized = true
    } else {
      dragX = clampDragX(dragX)
      dragY = clampDragY(dragY)
    }
  }

  LaunchedEffect(displayedMood, activity) {
    while (true) {
      delay(
        if (displayedMood == NbgPixelPetMood.Idle && activity == NbgPixelPetActivity.IdlePatrol) {
          NBG_PIXEL_PET_IDLE_PULSE_MS
        } else {
          NBG_PIXEL_PET_ACTIVE_PULSE_MS
        },
      )
      focusPulse++
    }
  }

  val orbitEnabled = activity in setOf(
    NbgPixelPetActivity.Think,
    NbgPixelPetActivity.StreamFollow,
    NbgPixelPetActivity.ToolInspect,
    NbgPixelPetActivity.ToolTerminal,
    NbgPixelPetActivity.ToolFile,
    NbgPixelPetActivity.ToolSearch,
    NbgPixelPetActivity.Alert,
  )

  Box(
    modifier = modifier
      .onSizeChanged { size ->
        containerWidthPx = size.width
        containerHeightPx = size.height
      }
      .onGloballyPositioned { coordinates ->
        overlayTopInRoot = coordinates.boundsInRoot().top
      },
  ) {
    if (menuOpen) {
      Box(
        modifier = Modifier
          .matchParentSize()
          .zIndex(1f)
          .pointerInput(Unit) {
            detectTapGestures { menuOpen = false }
          },
      )
    }
    Box(
      modifier = Modifier
        .align(Alignment.BottomStart)
        .zIndex(2f)
        .size(width = 96.dp, height = 90.dp)
        .offset {
          IntOffset(
            x = (startInsetPx + dragX).roundToInt(),
            y = (dragY - bottomInsetPx()).roundToInt(),
          )
        }
        .graphicsLayer {
          scaleX = scale
          scaleY = scale
          alpha = petAlpha
        }
        .pointerInput(Unit) {
          detectTapGestures {
            menuOpen = !menuOpen
          }
        }
        .pointerInput(Unit) {
          detectDragGestures(
            onDragStart = {
              menuOpen = false
              dragging = true
            },
            onDragEnd = {
              dragging = false
            },
            onDragCancel = {
              dragging = false
            },
          ) { change, dragAmount ->
            change.consume()
            if (dragAmount.x != 0f) facingLeft = dragAmount.x < 0f
            dragX = clampDragX(dragX + dragAmount.x)
            dragY = clampDragY(dragY + dragAmount.y)
          }
        }
        .semantics { contentDescription = "像素桌宠" },
    ) {
      Box(
          modifier = Modifier
            .align(Alignment.BottomStart)
          .size(width = 70.dp, height = 78.dp)
          .onSizeChanged { size ->
            petWidthPx = size.width
            petHeightPx = size.height
          },
        contentAlignment = Alignment.BottomCenter,
      ) {
        NbgPixelPetStage(
          mood = displayedMood,
          activity = activity,
          focusPulse = focusPulse,
          haloAlpha = haloAlpha,
          orbitEnabled = orbitEnabled,
          modifier = Modifier.fillMaxSize(),
        )
        NbgPixelPetSprite(
          pet = pet,
          mood = displayedMood,
          activity = activity,
          walking = false,
          facingLeft = facingLeft,
          readingText = false,
          eyeBias = 0f,
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
      if (menuOpen) {
        NbgPixelPetActionMenu(
          onOpenPets = {
            menuOpen = false
            onOpenPets()
          },
          onHidePet = {
            menuOpen = false
            onHidePet()
          },
          modifier = Modifier
            .align(Alignment.TopStart)
            .offset(x = if (menuOpenToLeft) menuLeftOffset else menuRightOffset, y = 2.dp),
        )
      }
    }
  }
}

@Composable
internal fun NbgPixelPetActionMenu(
  onOpenPets: () -> Unit,
  onHidePet: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    modifier = modifier.width(142.dp),
    shape = RoundedCornerShape(14.dp),
    color = NbgAgentColors.SurfaceContainerHigh,
    border = BorderStroke(1.dp, NbgAgentColors.InputBorder.copy(alpha = 0.72f)),
  ) {
    Column(modifier = Modifier.padding(6.dp)) {
      NbgPixelPetMenuRow(text = "进入桌宠页面", onClick = onOpenPets)
      Box(
        modifier = Modifier
          .width(130.dp)
          .height(1.dp)
          .background(NbgAgentColors.InputBorder.copy(alpha = 0.5f)),
      )
      NbgPixelPetMenuRow(text = "隐藏桌宠", onClick = onHidePet)
    }
  }
}

@Composable
private fun NbgPixelPetMenuRow(
  text: String,
  onClick: () -> Unit,
) {
  Box(
    modifier = Modifier
      .width(130.dp)
      .height(34.dp)
      .clickable(onClick = onClick)
      .semantics { contentDescription = text },
    contentAlignment = Alignment.CenterStart,
  ) {
    Text(
      text = text,
      color = NbgAgentColors.TextStrong,
      fontSize = 12.sp,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 9.dp),
    )
  }
}
