package cl.gakuto.kizuna.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InternetArchiveWebLoginPolicyTest {
    @Test
    fun downloadPolicyKeepsTrustedArchiveSubdomains() {
        assertTrue(InternetArchiveWebLoginPolicy.isTrustedDownloadUrl("https://archive.org/download/item/file.zip"))
        assertTrue(InternetArchiveWebLoginPolicy.isTrustedDownloadUrl("https://s3.us.archive.org/item/file.zip"))
        assertFalse(InternetArchiveWebLoginPolicy.isTrustedDownloadUrl("https://archive.org.evil.example/file.zip"))
        assertFalse(InternetArchiveWebLoginPolicy.isTrustedDownloadUrl("http://archive.org/download/item/file.zip"))
    }

    @Test
    fun allowsOnlyHttpsArchiveHostsOnTheStandardPort() {
        assertTrue(InternetArchiveWebLoginPolicy.isAllowedWebUrl("https://archive.org/account/login"))
        assertTrue(InternetArchiveWebLoginPolicy.isAllowedWebUrl("https://www.archive.org/details/test"))
        assertTrue(InternetArchiveWebLoginPolicy.isAllowedWebUrl("https://archive.org:443/account/login"))

        assertFalse(InternetArchiveWebLoginPolicy.isAllowedWebUrl("http://archive.org/account/login"))
        assertFalse(InternetArchiveWebLoginPolicy.isAllowedWebUrl("https://archive.org.evil.example/account/login"))
        assertFalse(InternetArchiveWebLoginPolicy.isAllowedWebUrl("https://evil.example@archive.org/account/login"))
        assertFalse(InternetArchiveWebLoginPolicy.isAllowedWebUrl("https://archive.org:8443/account/login"))
    }

    @Test
    fun credentialExtractionRequiresTheExactOfficialOriginAndPath() {
        assertTrue(InternetArchiveWebLoginPolicy.isS3KeysPage("https://archive.org/account/s3.php"))
        assertTrue(InternetArchiveWebLoginPolicy.isS3KeysPage("https://archive.org/account/s3.php?from=oz"))

        assertFalse(InternetArchiveWebLoginPolicy.isS3KeysPage("https://www.archive.org/account/s3.php"))
        assertFalse(InternetArchiveWebLoginPolicy.isS3KeysPage("https://archive.org/account/s3.php/extra"))
        assertFalse(InternetArchiveWebLoginPolicy.isS3KeysPage("https://archive.org/account/not-s3.php"))
        assertFalse(InternetArchiveWebLoginPolicy.isS3KeysPage("https://archive.org.evil.example/account/s3.php"))
    }
}
