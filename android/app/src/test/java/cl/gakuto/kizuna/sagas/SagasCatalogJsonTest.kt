package cl.gakuto.kizuna.sagas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SagasCatalogJsonTest {

    @Test
    fun `sagas catalog loads from embedded shared JSON resource and supports dynamic reload`() {
        val stream = SagasRepository::class.java.classLoader?.getResourceAsStream("sagas_catalog.json")
        requireNotNull(stream) { "sagas_catalog.json debe existir en resources" }
        val json = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }

        val parsed = SagasRepository.parseCatalogJson(json)
        assertTrue(parsed.size >= 47)
        assertEquals(parsed.size, SagasRepository.CURATED_SAGAS.size)
        assertTrue(parsed.all { it.history.length >= 80 })
        assertFalse(parsed.any { it.heroArt.repo.contains(" ") })

        val reloaded = SagasRepository.reloadFromJson(json)
        assertEquals(parsed.size, reloaded)
    }
}
