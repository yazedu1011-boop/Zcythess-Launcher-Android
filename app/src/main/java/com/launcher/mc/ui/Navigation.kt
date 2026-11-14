package com.launcher.mc.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

private class LauncherTab(val icon: String, val name: String, val page: @Composable () -> Unit)

@Composable
fun LauncherRoot() {
var tab by remember { mutableIntStateOf(0) }
val tabs = remember {
listOf(
LauncherTab("🎮", "Versiones") { VersionsScreen() },
LauncherTab("🧩", "Mods") { ModsScreen() },
LauncherTab("🧍", "Skins") { SkinsScreen() },
LauncherTab("🎛️", "Controles") { ControlsScreen() },
LauncherTab("👤", "Cuenta") { AccountScreen() }
)
}
Scaffold(bottomBar = {
NavigationBar {
tabs.forEachIndexed { i, t ->
val src = remember { MutableInteractionSource() }
NavigationBarItem(
selected = tab == i,
onClick = { tab = i },
interactionSource = src,
icon = { TabIcon(t.icon, selected = tab == i, src = src) },
label = { Text(t.name) }
)
}
}
}) { pad ->
Box(Modifier.padding(pad)) {
PagedTabs(current = tab, pages = tabs.map { it.page })
}
}
}

@Composable
private fun TabIcon(icon: String, selected: Boolean, src: MutableInteractionSource) {
val grow by animateFloatAsState(if (selected) 1.12f else 1f, label = "tabIcon")
Text(icon, modifier = Modifier.pressScale(src).graphicsLayer { scaleX = grow; scaleY = grow })
}

/**

- Todas las pantallas viven en fila. Al cambiar de pestaña, la posición se anima de la actual

- a la nueva, así que las pantallas intermedias pasan por la vista.
  */
  @Composable
  private fun PagedTabs(current: Int, pages: List<@Composable () -> Unit>) {
  val pos = remember { Animatable(current.toFloat()) }

  LaunchedEffect(current) {
  val distance = abs(pos.value - current)
  pos.animateTo(
  targetValue = current.toFloat(),
  animationSpec = tween(
  durationMillis = (300 + 120 * distance).toInt().coerceAtMost(900),
  easing = FastOutSlowInEasing
  )
  )
  }

  BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
  val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
  val blur = motionBlur(pos.velocity)
  val blurMod = if (blur > 0.dp) Modifier.blur(radiusX = blur, radiusY = 0.dp) else Modifier

  pages.forEachIndexed { i, page ->
  Box(
  Modifier
  .fillMaxSize()
  .graphicsLayer {
  val p = pos.value
  val d = abs(i - p).coerceAtMost(1f)
  translationX = (i - p) * widthPx
  alpha = 1f - 0.35f * d
  scaleX = 1f - 0.04f * d
  scaleY = 1f - 0.04f * d
  }
  .then(blurMod)
  ) { page() }
  }

  }
  }
