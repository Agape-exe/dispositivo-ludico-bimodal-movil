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
 * compatible con el formato de chat de OpenAI (Azure AI Foundry, ruta v1). Esta
 * desacoplado del flujo y es seguro por defecto:
 *
 * - Sin credenciales (config no operativa) responde [GenerativeMediationResult.Unavailable]
 *   sin tocar la red, por lo que la app funciona sin configurar nada.
 * - Con credenciales realiza una unica llamada con timeout; ante cualquier error de
 *   red, timeout, codigo HTTP o respuesta inesperada responde Unavailable.
 * - Si el proveedor esta saturado ("Overloaded"), lo trata como un error temporal:
 *   responde Unavailable de inmediato (el flujo usa el banco local) y entra en un
 *   breve periodo de enfriamiento durante el cual no vuelve a llamar a la red,
 *   evitando repetir llamadas saturadas en la misma sesion.
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

    /**
     * Marca de tiempo (epoch ms) hasta la cual se omiten llamadas a la red por una
     * saturacion reciente del proveedor. Cero significa sin enfriamiento activo.
     */
    @Volatile
    private var cooldownUntilMs: Long = 0L

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

        // Enfriamiento por saturacion previa: no se vuelve a contactar la red hasta
        // que pase la ventana, para no repetir llamadas saturadas en la sesion.
        if (System.currentTimeMillis() < cooldownUntilMs) {
            Log.d(TAG, "en enfriamiento por saturacion; se omite la llamada")
            return GenerativeMediationResult.Unavailable(OVERLOADED_REASON)
        }

        // Construye la URL final compatible con el formato OpenAI v1 de Azure AI
        // Foundry: {endpoint}/chat/completions. No usa la ruta clasica de Azure
        // OpenAI (/openai/deployments/{deployment}/chat/completions) ni anade
        // api-version: el modelo (deployment) viaja en el cuerpo JSON como "model".
        val url = chatCompletionsUrl(config.endpoint)
        // Logs tecnicos seguros: nunca incluyen la API key.
        Log.d(TAG, "endpoint base=${config.endpoint}")
        Log.d(TAG, "url final=$url")
        Log.d(TAG, "modelo=${config.model}")

        val start = System.nanoTime()
        return withContext(Dispatchers.IO) {
            try {
                val payload = buildPayload(request, config)
                val httpRequest = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer ${config.apiKey}")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                makeClient(config).newCall(httpRequest).execute().use { response ->
                    val code = response.code
                    val responseBody = response.body?.string()
                    Log.d(TAG, "respuesta HTTP code=$code")

                    if (!response.isSuccessful) {
                        val summary = summarize(responseBody)
                        Log.w(TAG, "error HTTP code=$code cuerpo=$summary")
                        // 429/503 o cuerpo con "Overloaded" => saturacion temporal.
                        if (isOverloaded(code, responseBody)) {
                            return@withContext enterCooldown()
                        }
                        return@withContext unavailable("Codigo HTTP $code.")
                    }

                    if (responseBody.isNullOrBlank()) {
                        return@withContext unavailable("Respuesta vacia del servicio.")
                    }
                    // Algunas pasarelas devuelven 200 con un error de saturacion en
                    // el cuerpo; se trata igual que un fallo temporal.
                    if (looksOverloaded(responseBody)) {
                        Log.w(TAG, "saturacion reportada en cuerpo 2xx")
                        return@withContext enterCooldown()
                    }
                    val text = parseText(responseBody)
                        ?: return@withContext unavailable("Respuesta sin texto utilizable.")
                    if (looksOverloaded(text)) {
                        Log.w(TAG, "saturacion reportada en el contenido")
                        return@withContext enterCooldown()
                    }
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

    /** Activa el enfriamiento y devuelve el resultado de saturacion para el flujo. */
    private fun enterCooldown(): GenerativeMediationResult {
        cooldownUntilMs = System.currentTimeMillis() + OVERLOADED_COOLDOWN_MS
        return GenerativeMediationResult.Unavailable(OVERLOADED_REASON)
    }

    private fun summarize(body: String?): String =
        body?.take(ERROR_BODY_LIMIT)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
            .ifBlank { "(sin cuerpo)" }

    /**
     * Construye el cuerpo de la peticion con instrucciones estrictas y solo
     * informacion controlada de la actividad. No incluye transcripciones ni datos
     * personales. Pide respuestas muy cortas para reducir carga y latencia.
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
            put("temperature", TEMPERATURE)
            put("max_tokens", MAX_TOKENS)
        }.toString()
    }

    private fun systemInstruction(request: GenerativeMediationRequest): String = buildString {
        append("Eres la voz de un juguete educativo que habla con un nino pequeno en espanol (")
        append(request.locale)
        append("). Tono: ")
        append(request.tone)
        append(". Responde con UNA sola frase de maximo 30 palabras. ")
        append("Sin explicaciones, sin emojis, sin preguntas adicionales. ")
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

        /** Maximo de caracteres del cuerpo de error que se registra (resumido). */
        private const val ERROR_BODY_LIMIT = 300

        /** Respuestas cortas: pocos tokens reducen carga y latencia. */
        private const val MAX_TOKENS = 64
        private const val TEMPERATURE = 0.7

        /** Motivo de respaldo legible cuando el proveedor esta saturado. */
        const val OVERLOADED_REASON = "Proveedor saturado (API overloaded)."

        /** Ventana de enfriamiento tras una saturacion, para no repetir llamadas. */
        private const val OVERLOADED_COOLDOWN_MS = 30_000L

        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVICE_UNAVAILABLE = 503

        /**
         * Construye la URL final compatible con el formato OpenAI v1: agrega
         * "/chat/completions" al endpoint base. Tolera barras finales y evita
         * duplicar el sufijo. No agrega "/deployments/" ni "api-version".
         */
        fun chatCompletionsUrl(endpoint: String): String {
            val base = endpoint.trim().trimEnd('/')
            return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        }

        /** Indica si un texto reporta saturacion del proveedor ("Overloaded"). */
        fun looksOverloaded(text: String?): Boolean =
            text != null && text.contains("overloaded", ignoreCase = true)

        /**
         * Clasifica una respuesta de error como saturacion temporal: por codigo
         * (429/503) o porque el cuerpo menciona "Overloaded".
         */
        fun isOverloaded(code: Int, body: String?): Boolean =
            code == HTTP_TOO_MANY_REQUESTS || code == HTTP_SERVICE_UNAVAILABLE || looksOverloaded(body)

        private fun defaultClient(config: GenerativeMediationConfig): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(config.requestTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
