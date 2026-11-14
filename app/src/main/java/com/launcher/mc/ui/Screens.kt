package com.launcher.mc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.launcher.mc.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val SECTIONS = listOf(
"Último lanzamiento" to "latest", "Vanilla" to "vanilla", "Fabric" to "fabric",
"Forge" to "forge", "OptiFine (solo 1.8.9)" to "optifine", "NeoForge" to "neoforge", "Quilt" to "quilt"
)

/** Ejecuta trabajo de red/disco fuera del hilo principal. Los errores van a la barra de estado. */
private fun <T> CoroutineScope.runTask(work: () -> T, onOk: (T) -> Unit = {}) = launch {
AppState.busy = true
try {
onOk(withContext(Dispatchers.IO) { work() })
} catch (e: Exception) {
AppState.say(e.message ?: e.javaClass.simpleName)
} finally {
AppState.busy = false
}
}

private fun importFile(ctx: Context, uri: android.net.Uri): File {
val name = ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
} ?: "import.jar"
val f = Dirs.root.resolve("installers/$name")
f.parentFile?.mkdirs()
ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { i.copyTo(it) } }
return f
}

@Composable
fun VersionsScreen() {
val scope = rememberCoroutineScope()
val ctx = LocalContext.current
var open by remember { mutableStateOf<String?>(null) }
var picked by remember { mutableStateOf<Target?>(null) }
var readyId by remember { mutableStateOf<String?>(null) }
val pickJar = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
if (uri != null) scope.runTask({ Installer.installOptiFine(importFile(ctx, uri)) }) { readyId = it }
}

Column(Modifier.fillMaxSize().padding(16.dp)) {
Text("Versiones", style = MaterialTheme.typography.headlineSmall)
Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
SECTIONS.forEach { (title, key) ->
Section(title, key, expanded = open == key,
onToggle = { open = if (open == key) null else key }) { t ->
readyId = null
picked = t
if (t.kind == "optifine") pickJar.launch("/")
}
}
}

AnimatedContent(targetState = picked, label = "picked") { t ->
if (t != null) {
Card(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
Column(Modifier.padding(12.dp)) {
Text("${t.kind} ${t.mc}", style = MaterialTheme.typography.titleMedium)
Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
if (t.kind != "optifine") {
PressButton("Instalar", enabled = !AppState.busy) {
scope.runTask({ Installer.install(t) { AppState.say(it) } }) {
readyId = it
AppState.say("Instalado: $it")
}
}
}
val id = readyId
PressButton("Jugar", enabled = id != null && !AppState.busy) {
if (id != null) scope.runTask({
Launch.start(AccountStore.load() ?: error("Inicia sesión en la pestaña Cuenta"), id)
}) {}
}
}
}
}
}
}

AnimatedVisibility(
visible = AppState.busy,
enter = fadeIn() + expandVertically(),
exit = fadeOut() + shrinkVertically()
) {
LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
}
Crossfade(targetState = AppState.status, label = "status") { msg ->
Text(msg, style = MaterialTheme.typography.bodySmall)
}

}

}

@Composable
private fun Section(
title: String, key: String, expanded: Boolean,
onToggle: () -> Unit, onPick: (Target) -> Unit
) {
var versions by remember { mutableStateOf<List<String>?>(null) }
var err by remember { mutableStateOf<String?>(null) }
val arrow by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
val src = remember { MutableInteractionSource() }

LaunchedEffect(expanded) {
if (expanded && versions == null && err == null) {
runCatching {
withContext(Dispatchers.IO) {
if (key == "latest") listOf(Versions.latestRelease()) else Versions.list(key)
}
}.onSuccess { versions = it }.onFailure { err = it.message ?: "Error" }
}
}

Column(Modifier.fillMaxWidth()) {
TextButton(
onClick = onToggle,
modifier = Modifier.fillMaxWidth().pressScale(src),
interactionSource = src
) {
Text(title)
Spacer(Modifier.weight(1f))
Text("↓", Modifier.graphicsLayer { rotationZ = arrow })
}
AnimatedVisibility(
visible = expanded,
enter = expandVertically() + fadeIn(),
exit = shrinkVertically() + fadeOut()
) {
Column {
val list = versions
when {
err != null -> Text("Error: $err", color = MaterialTheme.colorScheme.error)
list == null -> CircularProgressIndicator(Modifier.padding(8.dp))
else -> list.forEachIndexed { i, mc ->
VersionButton(i, mc) { onPick(Target(if (key == "latest") "vanilla" else key, mc)) }
}
}
}
}
}

}

@Composable
private fun VersionButton(index: Int, label: String, onClick: () -> Unit) {
StaggerIn(index) {
PressButton(label, Modifier.fillMaxWidth(), outlined = true, onClick = onClick)
}
}

/** Botón con animación de pulsación. Sirve para cualquier botón de la app. */
@Composable
private fun PressButton(
text: String,
modifier: Modifier = Modifier,
enabled: Boolean = true,
outlined: Boolean = false,
onClick: () -> Unit
) {
val src = remember { MutableInteractionSource() }
val m = modifier.pressScale(src)
if (outlined) {
OutlinedButton(onClick = onClick, enabled = enabled, modifier = m, interactionSource = src) { Text(text) }
} else {
Button(onClick = onClick, enabled = enabled, modifier = m, interactionSource = src) { Text(text) }
}
}

/** Entrada escalonada: cada elemento aparece un poco después que el anterior. */
@Composable
private fun StaggerIn(index: Int, content: @Composable () -> Unit) {
var shown by remember { mutableStateOf(false) }
LaunchedEffect(Unit) { shown = true }
val delay = (index * 30).coerceAtMost(300)
AnimatedVisibility(
visible = shown,
enter = fadeIn(tween(220, delayMillis = delay)) +
slideInVertically(tween(260, delayMillis = delay)) { it / 4 }
) {
content()
}
}

@Composable
fun AccountScreen() {
val scope = rememberCoroutineScope()
val ctx = LocalContext.current
var nick by remember { mutableStateOf("") }
val code by MicrosoftAuth.pendingFlow.collectAsState()

// Vuelta del navegador con ?code=...: intercambia el código por la cuenta
LaunchedEffect(code) {
val c = code ?: return@LaunchedEffect
scope.runTask({ MicrosoftAuth.finish(c) }) {
AppState.account = it
AppState.say("Sesión: ${it.name}")
}
}

Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
StaggerIn(0) { Text("Cuenta", style = MaterialTheme.typography.headlineSmall) }
StaggerIn(1) { Text(AppState.account?.let { "${it.name} · ${it.kind}" } ?: "Sin sesión") }
StaggerIn(2) {
PressButton("Iniciar sesión con Microsoft", Modifier.fillMaxWidth()) {
runCatching { MicrosoftAuth.authUrl() }
.onSuccess { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
.onFailure { AppState.say(it.message.orEmpty()) }
}
}
StaggerIn(3) {
OutlinedTextField(
value = nick, onValueChange = { nick = it },
label = { Text("Nombre offline") }, singleLine = true,
modifier = Modifier.fillMaxWidth()
)
}
StaggerIn(4) {
PressButton("Entrar offline", Modifier.fillMaxWidth()) {
scope.runTask({ Auth.offline(nick.trim()) }) { AppState.account = it }
}
}
StaggerIn(5) {
TextButton(onClick = { AccountStore.clear(); AppState.account = null }) { Text("Cerrar sesión") }
}
Crossfade(targetState = AppState.status, label = "status") { msg ->
Text(msg, style = MaterialTheme.typography.bodySmall)
}
}

}

@Composable
fun ModsScreen() = Placeholder("Mods", "Modrinth, CurseForge y dos sitios más: fase 3.")

@Composable
fun SkinsScreen() = Placeholder("Skins", "NameMC y otra web en WebView: fase 4.")

@Composable
fun ControlsScreen() = Placeholder("Controles", "Editor de botones y recomendaciones para 2, 3 y 4 dedos: fase 5.")

@Composable
private fun Placeholder(title: String, desc: String) {
Column(Modifier.fillMaxSize().padding(16.dp)) {
StaggerIn(0) { Text(title, style = MaterialTheme.typography.headlineSmall) }
StaggerIn(1) { Text(desc) }
}
}
