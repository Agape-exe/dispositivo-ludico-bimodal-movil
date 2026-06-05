package com.taller.app.bimodal.mediation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudGenerativeMediationProviderTest {

    // ----- Construccion de la URL (formato OpenAI v1 de Azure AI Foundry) -------

    @Test
    fun chatCompletionsUrl_appendsSuffixToFoundryEndpoint() {
        assertEquals(
            "https://ludictoyus2.services.ai.azure.com/openai/v1/chat/completions",
            CloudGenerativeMediationProvider.chatCompletionsUrl(
                "https://ludictoyus2.services.ai.azure.com/openai/v1"
            )
        )
    }

    @Test
    fun chatCompletionsUrl_toleratesTrailingSlash() {
        assertEquals(
            "https://ludictoyus2.services.ai.azure.com/openai/v1/chat/completions",
            CloudGenerativeMediationProvider.chatCompletionsUrl(
                "https://ludictoyus2.services.ai.azure.com/openai/v1/"
            )
        )
    }

    @Test
    fun chatCompletionsUrl_doesNotDuplicateSuffix() {
        val withSuffix = "https://host/openai/v1/chat/completions"
        assertEquals(withSuffix, CloudGenerativeMediationProvider.chatCompletionsUrl(withSuffix))
    }

    @Test
    fun chatCompletionsUrl_doesNotAddDeploymentsOrApiVersion() {
        val url = CloudGenerativeMediationProvider.chatCompletionsUrl(
            "https://host/openai/v1"
        )
        assertFalse(url.contains("/deployments/"))
        assertFalse(url.contains("api-version"))
        assertTrue(url.endsWith("/openai/v1/chat/completions"))
    }

    // ----- Deteccion de saturacion ---------------------------------------------

    @Test
    fun looksOverloaded_detectsMarkerCaseInsensitive() {
        assertTrue(CloudGenerativeMediationProvider.looksOverloaded("API Error: Overloaded"))
        assertTrue(CloudGenerativeMediationProvider.looksOverloaded("the service is OVERLOADED"))
        assertFalse(CloudGenerativeMediationProvider.looksOverloaded("todo correcto"))
        assertFalse(CloudGenerativeMediationProvider.looksOverloaded(null))
    }

    @Test
    fun isOverloaded_detectsByStatusCode() {
        assertTrue(CloudGenerativeMediationProvider.isOverloaded(429, null))
        assertTrue(CloudGenerativeMediationProvider.isOverloaded(503, null))
        assertFalse(CloudGenerativeMediationProvider.isOverloaded(404, null))
        assertFalse(CloudGenerativeMediationProvider.isOverloaded(400, "bad request"))
    }

    @Test
    fun isOverloaded_detectsByBodyMarkerOnAnyCode() {
        assertTrue(CloudGenerativeMediationProvider.isOverloaded(400, "{\"error\":\"Overloaded\"}"))
    }
}
