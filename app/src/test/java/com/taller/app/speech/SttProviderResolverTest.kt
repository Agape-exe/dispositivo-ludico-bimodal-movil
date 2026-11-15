package com.taller.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SttProviderResolverTest {

    private val defaults = SttSettings.defaults()

    @Test
    fun porDefecto_googlePrincipalOpenAiRespaldoAndroidAlFinal() {
        assertEquals(SttProviderType.GOOGLE_CLOUD, defaults.primaryProvider)
        assertEquals(SttProviderType.OPENAI_TRANSCRIPTION, defaults.fallbackProvider)
        assertEquals("es-PE", defaults.languageTag)
    }

    @Test
    fun conTodoConfigurado_laCadenaEsGoogleOpenAiAndroid() {
        val chain = SttProviderResolver.resolveChain(
            defaults,
            SttAvailability(googleConfigured = true, openAiConfigured = true, androidAvailable = true)
        )
        assertEquals(
            listOf(
                SttProviderType.GOOGLE_CLOUD,
                SttProviderType.OPENAI_TRANSCRIPTION,
                SttProviderType.ANDROID_SYSTEM
            ),
            chain
        )
    }

    @Test
    fun sinGoogle_openAiPasaAPrimeroYAndroidCierra() {
        val chain = SttProviderResolver.resolveChain(
            defaults,
            SttAvailability(googleConfigured = false, openAiConfigured = true, androidAvailable = true)
        )
        assertEquals(
            listOf(SttProviderType.OPENAI_TRANSCRIPTION, SttProviderType.ANDROID_SYSTEM),
            chain
        )
    }

    @Test
    fun sinClavesRemotas_androidQuedaComoUnicoRespaldoTecnico() {
        val chain = SttProviderResolver.resolveChain(
            defaults,
            SttAvailability(googleConfigured = false, openAiConfigured = false, androidAvailable = true)
        )
        assertEquals(listOf(SttProviderType.ANDROID_SYSTEM), chain)
        assertEquals(
            SttProviderType.ANDROID_SYSTEM,
            SttProviderResolver.effectiveProvider(
                defaults,
                SttAvailability(false, false, true)
            )
        )
    }

    @Test
    fun sinNingunProveedor_devuelveCadenaVaciaSinCrashear() {
        val chain = SttProviderResolver.resolveChain(
            defaults,
            SttAvailability(googleConfigured = false, openAiConfigured = false, androidAvailable = false)
        )
        assertTrue(chain.isEmpty())
        assertNull(
            SttProviderResolver.effectiveProvider(
                defaults,
                SttAvailability(false, false, false)
            )
        )
    }

    @Test
    fun proveedorRepetido_noSeDuplicaEnLaCadena() {
        val settings = SttSettings(
            primaryProvider = SttProviderType.ANDROID_SYSTEM,
            fallbackProvider = SttProviderType.ANDROID_SYSTEM
        )
        val chain = SttProviderResolver.resolveChain(
            settings,
            SttAvailability(googleConfigured = true, openAiConfigured = true, androidAvailable = true)
        )
        assertEquals(listOf(SttProviderType.ANDROID_SYSTEM), chain)
    }
}
