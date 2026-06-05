package com.taller.app.bimodal.mediation

import android.util.Log
import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Proveedor de mediacion ludica generativa basado en un servicio en la nube
 * compatible con el formato de chat (estilo OpenAI). Esta desacoplado del flujo y
 * es seguro por defecto:
 *
 * - Sin credenciales (config no operativa) responde [GenerativeMediationResult.Unavailable]
 *   sin tocar la red, por lo que la app funciona sin configurar nada.
 * - Con credenciales realiza una unica llamada con timeout; ante cualquier error de
 *   red, timeout, codigo HTTP o respuesta inesperada responde Unavailable.
 *
 * Privacidad: al servicio solo se envia informacion controlada de la actividad (ver
 * [GenerativeMediationRequest]). NUNCA se envia la transcripcion del nino, su
 * nombre, imagenes, frames, audios ni datos biometricos. La IA solo redacta una
 * frase breve; jamas decide si la respuesta del nino es correcta.
 *
 * La frase devuelta es solo candidata: el servicio que lo invoca la pasa por
 * [GenerativeMediationSafetyValidator] antes de reproducirla.
 *
 * @param configProvider fuente de la configuracion vigente (claves no versionadas).
 * @param clientProvider cliente HTTP perezoso, configurable para pruebas.
 */
class CloudGenerativeMediationProvider(
    private val configProvider: () -> GenerativeMediationConfig,
    clientProvider: (GenerativeMediationConfig) -> OkHttpClient = ::defaultClient
) : GenerativeMediationProvider {

    private val makeClient = clientProvider

    override fun isConfigured(): Boolean = configProvider().isOperational

    override suspend fun generateMediation(
        request: GenerativeMediationRequest
    ): GenerativeMediationResult {
        val config = configProvider()
        if (!config.isOperational) {
            return GenerativeMediationResult.Unavailable(
                "Mediacion generativa no configurada o desactivada."
            )
        }

        val start = System.nanoTime()
        return withContext(Dispatchers.IO) {
            try {
                val payload = buildPayload(request, config)
                val httpRequest = Request.Builder()
                    .url(config.endpoint)
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .addHeader("Content-Type", "application/json")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                makeClient(config).newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext unavailable("Codigo HTTP ${response.code}.")
                    }
                    val body = response.body?.string()
                    if (body.isNullOrBlank()) {
                        return@withContext unavailable("Respuesta vacia del servicio.")
                    }
                    val text = parseText(body)
                        ?: return@withContext unavailable("Respuesta sin texto utilizable.")
                    val latencyMs = (System.nanoTime() - start) / 1_000_000
                    GenerativeMediationResult.Generated(text.trim(), latencyMs)
                }
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout al contactar el servicio de mediacion")
                unavailable("Timeout del servicio de mediacion.")
            } catch (e: IOException) {
                Log.w(TAG, "Error de red con el servicio de mediacion")
                unavailable("Sin conexion con el servicio de mediacion.")
            } catch (e: Exception) {
                Log.w(TAG, "Error inesperado con el servicio de mediacion")
                unavailable("Error inesperado del servicio de mediacion.")
            }
        }
    }

    private fun unavailable(reason: String) = GenerativeMediationResult.Unavailable(reason)

    /**
     * Construye el cuerpo de la peticion con instrucciones estrictas y solo
     * informacion controlada de la actividad. No incluye transcripciones ni datos
     * personales.
     */
    private fun buildPayload(request: GenerativeMediationRequest, config: GenerativeMediationConfig): String {
        val messages = JSONArray()
            .put(
                JSONObject()
                    .put("role", "system")
                    .put("content", systemInstruction(request))
            )
            .put(
                JSONObject()
                    .put("role", "user")
                    .put("content", userContent(request))
            )

        return JSONObject().apply {
            put("model", config.model)
            put("messages", messages)
            put("temperature", 0.7)
            put("max_tokens", 80)
        }.toString()
    }

    private fun systemInstruction(request: GenerativeMediationRequest): String = buildString {
        append("Eres la voz de un juguete educativo que habla con un nino pequeno en espanol (")
        append(request.locale)
        append("). Tono: ")
        append(request.tone)
        append(". Responde con 1 o 2 frases muy breves, sin emojis, sin explicaciones largas. ")
        append("No digas que eres una IA, un modelo ni un sistema. ")
        append("No reveles la respuesta esperada. No inventes datos personales. ")
        when (request.type) {
            GenerativeMediationType.QUESTION_INTRODUCTION ->
                append(
                    "Genera solo una mini introduccion para captar la atencion antes de la pregunta. " +
                        "No formules ninguna pregunta: la pregunta original se reproducira despues."
                )
            GenerativeMediationType.CONTEXTUAL_FEEDBACK -> {
                append("Genera una breve retroalimentacion segun la situacion indicada. ")
                if (!request.hasRemainingAttempts && request.feedbackCategory != GeneralTeacherFeedbackType.CORRECT) {
                    append("No digas que la respuesta fue correcta. ")
                }
                if (request.isLastQuestion) {
                    append("Es la ultima pregunta: no invites a continuar ni a pasar a otra pregunta. ")
                }
            }
        }
    }

    private fun userContent(request: GenerativeMediationRequest): String = buildString {
        append("Pregunta original: ").append(request.questionText).append('\n')
        request.feedbackCategory?.let { append("Situacion: ").append(it.name).append('\n') }
        request.semanticResult?.let { append("Resultado: ").append(it.name).append('\n') }
        append("Quedan intentos: ").append(if (request.hasRemainingAttempts) "si" else "no").append('\n')
        append("Ultima pregunta: ").append(if (request.isLastQuestion) "si" else "no").append('\n')
        request.childAgeRange?.let { append("Nivel: ").append(it).append('\n') }
    }

    /**
     * Extrae el texto de una respuesta estilo chat completions de forma defensiva:
     * choices[0].message.content. Devuelve null si la forma no coincide.
     */
    private fun parseText(body: String): String? = try {
        val root = JSONObject(body)
        val choices = root.optJSONArray("choices")
        val message = choices?.optJSONObject(0)?.optJSONObject("message")
        message?.optString("content")?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val TAG = "CloudMediation"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(config: GenerativeMediationConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
