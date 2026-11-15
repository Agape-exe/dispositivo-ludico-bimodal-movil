package com.taller.app.speech

import android.content.Context
import android.net.Uri

/** Release no contiene almacenamiento ni lectura de cuentas de servicio. */
object GoogleSttCredentialStore {
    fun isImportSupported(): Boolean = false

    fun hasImportedCredential(context: Context): Boolean = false

    fun importFromUri(context: Context, uri: Uri): GoogleSttCredentialImportResult =
        GoogleSttCredentialImportResult(
            success = false,
            message = "La importacion de credenciales no esta disponible en release."
        )

    fun removeImportedCredential(context: Context): Boolean = true

    internal fun readCredential(context: Context): ByteArray? = null
}

data class GoogleSttCredentialImportResult(
    val success: Boolean,
    val message: String
)
