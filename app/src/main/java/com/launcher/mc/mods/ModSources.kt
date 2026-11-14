package com.launcher.mc.mods

import com.launcher.mc.BuildConfig
import com.launcher.mc.core.Net
import com.launcher.mc.core.objects
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest

enum class Source { MODRINTH, CURSEFORGE }

data class ModHit(
    val source: Source,
    val projectId: String,
    val title: String,
    val author: String,
    val summary: String,
    val downloads: Long,
    val icon: String?
)

data class ModFile(
    val name: String,
    val url: String,
    val sha1: String?,
    val required: List<String>
)

/** loader: fabric | quilt | forge | neoforge. Vanilla no admite mods. */
object ModSources {

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun JSONObject.str(k: String): String? =
        if (isNull(k)) null else optString(k).ifEmpty { null }

    fun search(src: Source, query: String, mc: String, loader: String): List<ModHit> =
        if (src == Source.MODRINTH) modrinthSearch(query, mc, loader)
        else curseSearch(query, mc, loader)

    /**
     * Descarga el mod elegido y sus dependencias obligatorias.
     * Verifica SHA-1 cuando la fuente lo entrega. Devuelve los nombres de archivo instalados.
     */
    fun install(
        src: Source,
        projectId: String,
        mc: String,
        loader: String,
        dir: File,
        log: (String) -> Unit
    ): List<String> {
        val done = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun walk(id: String, depth: Int) {
            if (depth > 4 || !seen.add(id)) return
            val f = latestFile(src, id, mc, loader)
            val target = dir.resolve(f.name)
            log("Descargando ${f.name}")
            com.launcher.mc.core.Net.download(f.url, target)
            f.sha1?.let { want ->
                if (!sha1(target).equals(want, ignoreCase = true)) {
                    target.delete()
                    error("Archivo corrupto: ${f.name}")
                }
            }
            done += f.name
            f.required.forEach { walk(it, depth + 1) }
        }

        walk(projectId, 0)
        return done
    }

    fun listInstalled(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }?.sortedBy { it.name }.orEmpty()

    private fun latestFile(src: Source, id: String, mc: String, loader: String): ModFile =
        if (src == Source.MODRINTH) modrinthFile(id, mc, loader) else curseFile(id, mc, loader)

    // ---------- Modrinth (no necesita clave) ----------

    private fun modrinthSearch(q: String, mc: String, loader: String): List<ModHit> {
        val facets = """[["project_type:mod"],["versions:$mc"],["categories:$loader"]]"""
        val url = "https://api.modrinth.com/v2/search?limit=20&query=${enc(q)}&facets=${enc(facets)}"
        return Net.json(url).getJSONArray("hits").objects().map { h ->
            ModHit(
                source = Source.MODRINTH,
                projectId = h.getString("project_id"),
                title = h.getString("title"),
                author = h.optString("author"),
                summary = h.optString("description"),
                downloads = h.optLong("downloads"),
                icon = h.str("icon_url")
            )
        }
    }

    private fun modrinthFile(id: String, mc: String, loader: String): ModFile {
        val url = "https://api.modrinth.com/v2/project/$id/version" +
            "?loaders=${enc("[\"$loader\"]")}&game_versions=${enc("[\"$mc\"]")}"
        val v = JSONArray(Net.get(url)).objects().firstOrNull()
            ?: error("Sin versión de $mc para $loader")
        val files = v.getJSONArray("files").objects()
        val f = files.firstOrNull { it.optBoolean("primary") } ?: files.first()
        val required = v.optJSONArray("dependencies")?.objects().orEmpty()
            .filter { it.optString("dependency_type") == "required" }
            .mapNotNull { it.str("project_id") }
        return ModFile(
            name = f.getString("filename"),
            url = f.getString("url"),
            sha1 = f.getJSONObject("hashes").str("sha1"),
            required = required
        )
    }

    // ---------- CurseForge (necesita CURSEFORGE_KEY en los Secrets) ----------

    private fun curseHeaders(): Map<String, String> {
        check(BuildConfig.CURSEFORGE_KEY.isNotEmpty()) {
            "Falta CURSEFORGE_KEY en los Secrets de GitHub"
        }
        return mapOf("x-api-key" to BuildConfig.CURSEFORGE_KEY)
    }

    /** Tipos de loader de CurseForge: 1 Forge, 4 Fabric, 5 Quilt, 6 NeoForge. */
    private fun loaderType(loader: String): Int = when (loader) {
        "forge" -> 1
        "fabric" -> 4
        "quilt" -> 5
        "neoforge" -> 6
        else -> error("Loader no soportado: $loader")
    }

    private fun curseSearch(q: String, mc: String, loader: String): List<ModHit> {
        val url = "https://api.curseforge.com/v1/mods/search?gameId=432&classId=6" +
            "&gameVersion=$mc&modLoaderType=${loaderType(loader)}&searchFilter=${enc(q)}" +
            "&sortField=2&sortOrder=desc&pageSize=20"
        return Net.json(url, curseHeaders()).getJSONArray("data").objects().map { m ->
            ModHit(
                source = Source.CURSEFORGE,
                projectId = m.getInt("id").toString(),
                title = m.getString("name"),
                author = m.optJSONArray("authors")?.optJSONObject(0)?.optString("name").orEmpty(),
                summary = m.optString("summary"),
                downloads = m.optLong("downloadCount"),
                icon = m.optJSONObject("logo")?.str("thumbnailUrl")
            )
        }
    }

    private fun curseFile(modId: String, mc: String, loader: String): ModFile {
        val url = "https://api.curseforge.com/v1/mods/$modId/files" +
            "?gameVersion=$mc&modLoaderType=${loaderType(loader)}&pageSize=5"
        val f = Net.json(url, curseHeaders()).getJSONArray("data").objects().firstOrNull()
            ?: error("Sin archivo de $mc para $loader")

        val fileId = f.getInt("id")
        val name = f.getString("fileName")
        // Si el autor desactivó la descarga por API, downloadUrl es null: se usa el CDN directamente
        val dl = if (f.isNull("downloadUrl")) {
            "https://edge.forgecdn.net/files/${fileId / 1000}/${fileId % 1000}/$name"
        } else {
            f.getString("downloadUrl")
        }
        val sha1 = f.optJSONArray("hashes")?.objects()
            ?.firstOrNull { it.optInt("algo") == 1 }?.str("value")
        val required = f.optJSONArray("dependencies")?.objects().orEmpty()
            .filter { it.optInt("relationType") == 3 }   // 3 = requerido
            .map { it.getInt("modId").toString() }
        return ModFile(name, dl, sha1, required)
    }

    private fun sha1(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
