package com.launcher.mc.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Botón que se encoge al pulsar y rebota al soltar. Usa el mismo InteractionSource que el botón. */
fun Modifier.pressScale(src: InteractionSource, amount: Float = 0.94f): Modifier = composed {
val pressed by src.collectIsPressedAsState()
val scale by animateFloatAsState(
targetValue = if (pressed) amount else 1f,
animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
label = "press"
)
graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Desenfoque horizontal según la velocidad en páginas por segundo. Máximo 18 dp. */
fun motionBlur(pagesPerSecond: Float): Dp {
val f = (abs(pagesPerSecond) / 5f).coerceIn(0f, 1f)
return (f * 18f).dp
}
