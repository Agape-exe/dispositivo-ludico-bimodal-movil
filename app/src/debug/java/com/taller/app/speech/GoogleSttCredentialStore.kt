package com.taller.app.speech

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

/**
 * Almacen de credenciales disponible unicamente en builds debug.
 *
 * El JSON se valida y se copia a noBackupFilesDir, nunca a recursos ni a una
 * ubicacion compartida. Su contenido no se registra ni se expone en la UI.
 */
object GoogleSttCredentialStore {
    private const val DIRECTORY = "google_cloud_stt"
    private const val FILE_NAME = "service-account.json"
    private const val MAX_BYTES = 128 * 1024

    fun isImportSupported(): Boolean = true

    fun hasImportedCredential(context: Context): Boolean = credentialFile(context).isFile

    fun importFromUri(context: Context, uri: Uri): GoogleSttCredentialImportResult = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_BYTES) { "El archivo excede el limite permitido." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("No se pudo abrir el archivo seleccionado.")

        validateServiceAccount(bytes)
        val target = credentialFile(context)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        if (target.exists()) {
            check(target.delete()) { "No se pudo reemplazar la credencial anterior." }
        }
        check(temporary.renameTo(target)) {
            temporary.delete()
            "No se pudo guardar la credencial."
        }
        GoogleSttCredentialImportResult(
            success = true,
            message = "Credencial importada en almacenamiento interno privado."
        )
    }.getOrElse {
        GoogleSttCredentialImportResult(
            success = false,
            message = "El archivo no es una cuenta de servicio valida de Google Cloud."
        )
    }

    fun removeImportedCredential(context: Context): Boolean {
        val file = credentialFile(context)
        return !file.exists() || file.delete()
    }

    /** Reservado para el futuro cliente Google STT; nunca devuelve el contenido a la UI. */
    internal fun readCredential(context: Context): ByteArray? =
        credentialFile(context).takeIf(File::isFile)?.readBytes()

    private fun credentialFile(context: Context): File =
        File(File(context.noBackupFilesDir, DIRECTORY), FILE_NAME)

    private fun validateServiceAccount(bytes: ByteArray) {
        val json = JSONObject(bytes.toString(Charsets.UTF_8))
        require(json.optString("type") == "service_account")
        require(json.optString("project_id").isNotBlank())
        require(json.optString("client_email").isNotBlank())
        require(json.optString("private_key_id").isNotBlank())
        require(json.optString("private_key").contains("BEGIN PRIVATE KEY"))
        require(json.optString("token_uri").startsWith("https://"))
    }
}

data class GoogleSttCredentialImportResult(
    val success: Boolean,
    val message: String
)
