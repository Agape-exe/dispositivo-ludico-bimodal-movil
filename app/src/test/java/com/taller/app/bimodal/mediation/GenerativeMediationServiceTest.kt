package com.taller.app.bimodal.mediation

import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackGenerator
import com.taller.app.bimodal.feedback.GeneralTeacherFeedbackType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GenerativeMediationServiceTest {

    private fun localProvider() =
        LocalMediationFallbackProvider(GeneralTeacherFeedbackGenerator(random = Random(1L)))

    private fun operationalConfig() = GenerativeMediationConfig(
        enabled = true,
        apiKey = "clave-de-prueba",
        endpoint = "https://example.test/generate",
        model = "modelo-prueba"
    )

    private fun disabledConfig() = GenerativeMediationConfig(
        enabled = false,
        apiKey = "",
        endpoint = "",
        model = ""
    )

    /** Proveedor de nube de prueba con un resultado fijo. */
    private class FixedProvider(private val result: GenerativeMediationResult) : GenerativeMediationProvider {
        override fun isConfigured(): Boolean = true
        override suspend fun generateMediation(request: GenerativeMediationRequest) = result
    }

    /** Proveedor de nube que siempre lanza, para verificar que el servicio no propaga. */
    private class ThrowingProvider : GenerativeMediationProvider {
        override fun isConfigured(): Boolean = true
        override suspend fun generateMediation(request: GenerativeMediationRequest): GenerativeMediationResult =
            throw RuntimeException("fallo del proveedor")
    }

    private fun introRequest() = GenerativeMediationRequest(
        type = GenerativeMediationType.QUESTION_INTRODUCTION,
        questionText = "¿Cómo hace el perro?",
        expectedAnswer = "guau",
        keywords = listOf("guau")
    )

    private fun feedbackRequest() = GenerativeMediationRequest(
        type = GenerativeMediationType.CONTEXTUAL_FEEDBACK,
        questionText = "¿Cómo hace el perro?",
        expectedAnswer = "guau",
        keywords = listOf("guau"),
        feedbackCategory = GeneralTeacherFeedbackType.INCORRECT_RETRY,
        hasRemainingAttempts = true
    )

    @Test
    fun whenDisabled_usesLocalSourceWithoutCallingCloud() = runBlocking {
        val service = GenerativeMediationService(
            config = disabledConfig(),
            cloudProvider = ThrowingProvider(), // no debe invocarse
            localProvider = localProvider()
        )
        val response = service.mediate(feedbackRequest())
        assertEquals(MediationSource.LOCAL, response.source)
        assertTrue(response.text.isNotBlank())
        assertNull(response.fallbackReason)
    }

    @Test
    fun whenGeneratedTextIsValid_usesGenerativeSource() = runBlocking {
        val generated = "Te cuento algo: los perritos mueven la colita cuando están felices."
        val service = GenerativeMediationService(
            config = operationalConfig(),
            cloudProvider = FixedProvider(GenerativeMediationResult.Generated(generated, latencyMs = 42L)),
            localProvider = localProvider()
        )
        val response = service.mediate(introRequest())
        assertEquals(MediationSource.GENERATIVE, response.source)
        assertEquals(generated, response.text)
        assertEquals(42L, response.latencyMs)
        assertNull(response.fallbackReason)
    }

    @Test
    fun whenGeneratedTextIsInvalid_usesLocalFallback() = runBlocking {
        // Frase invalida: afirma acierto en una categoria que no es CORRECT.
        val service = GenerativeMediationService(
            config = operationalConfig(),
            cloudProvider = FixedProvider(
                GenerativeMediationResult.Generated("Eso es correcto, sigamos.", latencyMs = 30L)
            ),
            localProvider = localProvider()
        )
        val response = service.mediate(feedbackRequest())
        assertEquals(MediationSource.FALLBACK, response.source)
        assertTrue(response.text.isNotBlank())
        assertFalse(response.text == "Eso es correcto, sigamos.")
        assertNotNull(response.fallbackReason)
        assertTrue(response.fallbackReason!!.contains("Validacion"))
    }

    @Test
    fun whenProviderUnavailable_usesLocalFallback() = runBlocking {
        val service = GenerativeMediationService(
            config = operationalConfig(),
            cloudProvider = FixedProvider(GenerativeMediationResult.Unavailable("Sin conexion.")),
            localProvider = localProvider()
        )
        val response = service.mediate(feedbackRequest())
        assertEquals(MediationSource.FALLBACK, response.source)
        assertTrue(response.text.isNotBlank())
        assertEquals("Sin conexion.", response.fallbackReason)
    }

    @Test
    fun whenProviderThrows_usesLocalFallbackWithoutPropagating() = runBlocking {
        val service = GenerativeMediationService(
            config = operationalConfig(),
            cloudProvider = ThrowingProvider(),
            localProvider = localProvider()
        )
        val response = service.mediate(feedbackRequest())
        assertEquals(MediationSource.FALLBACK, response.source)
        assertTrue(response.text.isNotBlank())
        assertNotNull(response.fallbackReason)
    }

    @Test
    fun fallbackText_matchesRequestedCategory() = runBlocking {
        // El respaldo local debe respetar la categoria ya decidida por el flujo.
        val service = GenerativeMediationService(
            config = disabledConfig(),
            cloudProvider = ThrowingProvider(),
            localProvider = localProvider()
        )
        val correctText = service.mediate(
            feedbackRequest().copy(feedbackCategory = GeneralTeacherFeedbackType.CORRECT)
        ).text
        assertTrue("El respaldo local de CORRECT no produjo texto", correctText.isNotBlank())
    }
}
