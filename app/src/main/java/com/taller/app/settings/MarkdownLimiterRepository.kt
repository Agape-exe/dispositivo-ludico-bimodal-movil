package com.taller.app.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

data class MarkdownLimiterFile(
    val id: String,
    val displayName: String,
    val fileName: String,
    val sizeBytes: Long,
    val importedAt: Long,
    val enabled: Boolean = true
)

data class MarkdownLimiterImportResult(
    val success: Boolean,
    val message: String,
    val file: MarkdownLimiterFile? = null
)

data class MarkdownLimiterRulesSnapshot(
    val activeFileCount: Int,
    val usedCharacters: Int,
    val truncated: Boolean,
    val content: String
)

class MarkdownLimiterRepository(private val context: Context) {

    private val directory: File = File(context.filesDir, LIMITERS_DIR)
    private val metadataFile: File = File(directory, METADATA_FILE)

    fun listFiles(): List<MarkdownLimiterFile> = readMetadata()

    fun readContent(id: String): String? {
        val item = readMetadata().firstOrNull { it.id == id } ?: return null
        val file = safeFile(item.fileName) ?: return null
        return runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
    }

    fun setEnabled(id: String, enabled: Boolean): Boolean {
        val updated = readMetadata().map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        writeMetadata(updated)
        return updated.any { it.id == id }
    }

    fun delete(id: String): Boolean {
        val current = readMetadata()
        val item = current.firstOrNull { it.id == id } ?: return false
        val file = safeFile(item.fileName) ?: return false
        val deleted = !file.exists() || file.delete()
        if (deleted) writeMetadata(current.filterNot { it.id == id })
        return deleted
    }

    fun importFromUri(uri: Uri): MarkdownLimiterImportResult {
        ensureDirectory()
        val rawName = queryDisplayName(uri).ifBlank { uri.lastPathSegment.orEmpty() }
        val displayName = sanitizeDisplayName(rawName)
        if (!displayName.endsWith(".md", ignoreCase = true)) {
            return MarkdownLimiterImportResult(false, "Solo se permiten archivos .md.")
        }

        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes(MAX_FILE_SIZE_BYTES + 1)
            }
        }.getOrNull() ?: return MarkdownLimiterImportResult(false, "No se pudo leer el archivo.")

        if (bytes.size > MAX_FILE_SIZE_BYTES) {
            return MarkdownLimiterImportResult(false, "El archivo supera el limite de 100 KB.")
        }

        val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
            ?: return MarkdownLimiterImportResult(false, "El archivo debe estar en UTF-8.")
        if (text.isBlank()) {
            return MarkdownLimiterImportResult(false, "El archivo esta vacio.")
        }

        val now = System.currentTimeMillis()
        val id = "md_$now"
        val fileName = "$id.md"
        val target = safeFile(fileName)
            ?: return MarkdownLimiterImportResult(false, "Nombre de archivo invalido.")
        runCatching { target.writeText(text, Charsets.UTF_8) }.getOrElse {
            return MarkdownLimiterImportResult(false, "No se pudo guardar el archivo.")
        }

        val metadata = MarkdownLimiterFile(
            id = id,
            displayName = displayName,
            fileName = fileName,
            sizeBytes = bytes.size.toLong(),
            importedAt = now,
            enabled = true
        )
        writeMetadata(readMetadata() + metadata)
        return MarkdownLimiterImportResult(true, "Archivo .md importado.", metadata)
    }

    fun activeRulesSnapshot(maxCharacters: Int = DEFAULT_RULES_CHARACTER_LIMIT): MarkdownLimiterRulesSnapshot {
        val active = readMetadata().filter { it.enabled }
        val builder = StringBuilder()
        var truncated = false
        for (item in active) {
            val content = readContent(item.id)?.trim().orEmpty()
            if (content.isBlank()) continue
            val header = "\n\n# ${item.displayName}\n"
            val next = header + content
            val remaining = maxCharacters - builder.length
            if (remaining <= 0) {
                truncated = true
                break
            }
            if (next.length > remaining) {
                builder.append(next.take(remaining))
                truncated = true
                break
            }
            builder.append(next)
        }
        return MarkdownLimiterRulesSnapshot(
            activeFileCount = active.size,
            usedCharacters = builder.length,
            truncated = truncated,
            content = builder.toString().trim()
        )
    }

    private fun queryDisplayName(uri: Uri): String {
        val cursor = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        }.getOrNull() ?: return ""
        cursor.use {
            return if (it.moveToFirst()) {
                it.getString(0).orEmpty()
            } else {
                ""
            }
        }
    }

    private fun readMetadata(): List<MarkdownLimiterFile> {
        if (!metadataFile.exists()) return emptyList()
        return runCatching {
            val json = JSONArray(metadataFile.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.getJSONObject(index)
                    add(
                        MarkdownLimiterFile(
                            id = item.optString("id"),
                            displayName = item.optString("displayName"),
                            fileName = item.optString("fileName"),
                            sizeBytes = item.optLong("sizeBytes"),
                            importedAt = item.optLong("importedAt"),
                            enabled = item.optBoolean("enabled", true)
                        )
                    )
                }
            }.filter { it.id.isNotBlank() && it.fileName.endsWith(".md") }
        }.getOrDefault(emptyList())
    }

    private fun writeMetadata(files: List<MarkdownLimiterFile>) {
        ensureDirectory()
        val json = JSONArray()
        files.forEach { file ->
            json.put(
                JSONObject()
                    .put("id", file.id)
                    .put("displayName", file.displayName)
                    .put("fileName", file.fileName)
                    .put("sizeBytes", file.sizeBytes)
                    .put("importedAt", file.importedAt)
                    .put("enabled", file.enabled)
            )
        }
        metadataFile.writeText(json.toString(), Charsets.UTF_8)
    }

    private fun safeFile(fileName: String): File? {
        if (!fileName.matches(Regex("[A-Za-z0-9_-]+\\.md"))) return null
        ensureDirectory()
        val candidate = File(directory, fileName).canonicalFile
        val root = directory.canonicalFile
        return if (candidate.path.startsWith(root.path + File.separator)) candidate else null
    }

    private fun ensureDirectory() {
        if (!directory.exists()) directory.mkdirs()
    }

    companion object {
        const val MAX_FILE_SIZE_BYTES = 100 * 1024
        const val DEFAULT_RULES_CHARACTER_LIMIT = 4_000
        private const val LIMITERS_DIR = "seven_markdown_limiters"
        private const val METADATA_FILE = "metadata.json"

        fun sanitizeDisplayName(raw: String): String {
            val base = raw.substringAfterLast('/').substringAfterLast('\\').ifBlank { "limitador.md" }
            val normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .replace(Regex("[^A-Za-z0-9._ -]"), "_")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(80)
            val safe = normalized.ifBlank { "limitador.md" }
            return if (safe.endsWith(".md", ignoreCase = true)) safe else "$safe.md"
        }
    }
}

private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = java.io.ByteArrayOutputStream()
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read <= 0) break
        total += read
        if (total > limit) {
            output.write(buffer, 0, read.coerceAtMost(limit - (total - read)))
            break
        }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
