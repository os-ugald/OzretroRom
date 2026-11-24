package cl.gakuto.kizuna.ftp

import cl.gakuto.kizuna.hub.GameVaultHubClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpAndHubPolicyTest {

    @Test
    fun `ftp target resolves overrides and base paths and roundtrips JSON`() {
        val defaults = FtpTargetConfig.defaultTargets()
        assertEquals(3, defaults.size)

        val ps4 = defaults.first { it.id == "ps4-emusuni" }
        assertEquals("/data/EmuSuNi/Roms/snes", ps4.resolveRemoteDir("snes"))
        assertEquals("/data/EmuSuNi/Roms/arcade", ps4.resolveRemoteDir("mame"))
        assertEquals("/data/EmuSuNi/Roms/n64", ps4.resolveRemoteDir("n64"))

        val json = FtpTargetConfig.listToJson(defaults)
        val restored = FtpTargetConfig.listFromJson(json)
        assertEquals(defaults, restored)
    }

    @Test
    fun `hub client restricts plain http to RFC1918 private LAN addresses`() {
        assertTrue(GameVaultHubClient.isAllowedLanHost("192.168.100.173"))
        assertTrue(GameVaultHubClient.isAllowedLanHost("10.0.0.15"))
        assertTrue(GameVaultHubClient.isAllowedLanHost("172.16.4.2"))
        assertTrue(GameVaultHubClient.isAllowedLanHost("172.31.255.1"))
        assertTrue(GameVaultHubClient.isAllowedLanHost("localhost"))

        assertFalse(GameVaultHubClient.isAllowedLanHost("172.32.0.1"))
        assertFalse(GameVaultHubClient.isAllowedLanHost("192.169.1.1"))
        assertFalse(GameVaultHubClient.isAllowedLanHost("8.8.8.8"))
        assertFalse(GameVaultHubClient.isAllowedLanHost("example.com"))
    }
}
