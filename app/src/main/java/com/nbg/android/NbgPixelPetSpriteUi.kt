package com.nbg.android

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private const val PETDEX_002_FRAME_WIDTH_PX = 192
private const val PETDEX_002_FRAME_HEIGHT_PX = 208
private const val PETDEX_002_COLUMNS = 8

private val nbgPetSpritesheetCache = object : LruCache<String, ImageBitmap>(24) {
  override fun sizeOf(key: String, value: ImageBitmap): Int = 1
}

@Composable
internal fun NbgPixelPetSprite(
  pet: NbgPetSummary,
  mood: NbgPixelPetMood,
  activity: NbgPixelPetActivity,
  walking: Boolean,
  facingLeft: Boolean,
  readingText: Boolean,
  eyeBias: Float,
  modifier: Modifier = Modifier,
) {
  val animation = nbgPetdex002Animation(mood, activity, walking, readingText, facingLeft)
  val spritesheet = nbgRememberPetSpritesheet(pet)
  var frame by remember { mutableIntStateOf(0) }
  LaunchedEffect(animation) {
    frame = 0
    while (true) {
      delay(animation.frameDelayMs)
      frame = (frame + 1) % animation.frames
    }
  }
  Canvas(
    modifier = modifier
      .size(width = 76.dp, height = 82.dp)
      .graphicsLayer {
        scaleX = if (facingLeft && !walking) -1f else 1f
      },
  ) {
    drawImage(
      image = spritesheet,
      srcOffset = IntOffset(
        x = (frame % PETDEX_002_COLUMNS) * PETDEX_002_FRAME_WIDTH_PX,
        y = animation.row * PETDEX_002_FRAME_HEIGHT_PX,
      ),
      srcSize = IntSize(PETDEX_002_FRAME_WIDTH_PX, PETDEX_002_FRAME_HEIGHT_PX),
      dstOffset = IntOffset(0, 0),
      dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
      filterQuality = FilterQuality.None,
    )
    if (!walking && abs(eyeBias) > 0.18f) {
      val direction = if (facingLeft) -eyeBias else eyeBias
      val eyeColor = nbgPixelPetAccent(mood).copy(alpha = 0.34f)
      drawCircle(
        color = eyeColor,
        radius = 2.2f,
        center = Offset(size.width * (0.53f + direction * 0.035f), size.height * 0.42f),
      )
    }
  }
}

@Composable
internal fun nbgRememberPetSpritesheet(pet: NbgPetSummary): ImageBitmap {
  val path = pet.spritePath
  return remember(path) {
    if (!path.isNullOrBlank()) {
      nbgPetSpritesheetCache.get(path) ?: BitmapFactory.decodeFile(path)?.asImageBitmap()?.also {
        nbgPetSpritesheetCache.put(path, it)
      }
    } else {
      null
    }
  } ?: ImageBitmap.imageResource(id = R.drawable.petdex_002_spritesheet)
}

@Composable
internal fun NbgPixelPetStage(
  mood: NbgPixelPetMood,
  activity: NbgPixelPetActivity,
  focusPulse: Int,
  haloAlpha: Float,
  orbitEnabled: Boolean,
  modifier: Modifier = Modifier,
) {
  val pulse by animateFloatAsState(
    targetValue = if (focusPulse % 2 == 0) 0.72f else 1f,
    animationSpec = tween(durationMillis = nbgMotionDuration(240), easing = FastOutSlowInEasing),
    label = "pixel pet focus pulse",
  )
  val accent = nbgPixelPetAccent(mood)
  Canvas(modifier = modifier) {
    val center = Offset(size.width * 0.54f, size.height * 0.62f)
    val haloRadius = size.minDimension * (0.25f + 0.035f * pulse)
    drawOval(
      color = Color.Black.copy(alpha = 0.15f),
      topLeft = Offset(size.width * 0.26f, size.height * 0.82f),
      size = Size(size.width * 0.5f, size.height * 0.11f),
    )
    drawCircle(
      color = accent.copy(alpha = haloAlpha * 0.2f),
      radius = haloRadius,
      center = center,
    )
    drawCircle(
      color = accent.copy(alpha = haloAlpha * 0.38f),
      radius = haloRadius,
      center = center,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f),
    )
    if (orbitEnabled) {
      val radians = Math.toRadians(((focusPulse % 8) * 45f).toDouble())
      val dotCenter = Offset(
        x = center.x + kotlin.math.cos(radians).toFloat() * haloRadius * 0.92f,
        y = center.y + kotlin.math.sin(radians).toFloat() * haloRadius * 0.42f,
      )
      drawCircle(color = Color.White.copy(alpha = 0.72f), radius = 2.6f, center = dotCenter)
      drawCircle(color = accent.copy(alpha = 0.92f), radius = 1.8f, center = dotCenter)
    }
  }
}

private fun nbgPixelPetAccent(mood: NbgPixelPetMood): Color =
  when (mood) {
    NbgPixelPetMood.Warning -> NbgAgentColors.StatusRed
    NbgPixelPetMood.Working -> Color(0xFF6BD17D)
    NbgPixelPetMood.Tooling -> Color(0xFF6BD17D)
    NbgPixelPetMood.Streaming -> Color(0xFF5EC7F0)
    NbgPixelPetMood.Thinking -> NbgAgentColors.Primary
    NbgPixelPetMood.Connecting -> NbgAgentColors.StatusYellow
    NbgPixelPetMood.Resting -> Color(0xFFB8D8A2)
    NbgPixelPetMood.Reading -> Color(0xFF8EC5FF)
    NbgPixelPetMood.Dragging -> Color(0xFFFFC857)
    NbgPixelPetMood.Idle -> NbgAgentColors.PrimarySoft
  }
