package cl.gakuto.kizuna.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InternetArchiveS3PageParserTest {
    @Test
    fun readsCurrentPageWhenInputsHaveNoNameOrId() {
        val html = """
            <h1>Get Your S3-Like API Keys</h1>
            <label>Your S3 access key</label>
            <div><input class="form-control" readonly value="ABCD1234EFGH5678"></div>
            <label>Your S3 secret key</label>
            <div><input class="form-control" readonly value="WXYZ9876QRST5432"></div>
        """.trimIndent()

        val keys = InternetArchiveS3PageParser.parse(html)

        assertEquals("ABCD1234EFGH5678", keys?.access)
        assertEquals("WXYZ9876QRST5432", keys?.secret)
    }

    @Test
    fun keepsLegacyNameAndIdFormatsRegardlessOfAttributeOrder() {
        val html = """
            <input value="ABCD1234EFGH56&#55;8" class="key" id="access">
            <input data-purpose="copy" name='secret' value='WXYZ9876QRST5432'>
        """.trimIndent()

        val keys = InternetArchiveS3PageParser.parse(html)

        assertEquals("ABCD1234EFGH5678", keys?.access)
        assertEquals("WXYZ9876QRST5432", keys?.secret)
    }

    @Test
    fun ignoresHiddenTokensAndUsesOnlyVisibleKeyFields() {
        val html = """
            <input type="hidden" value="HIDDEN12TOKEN345">
            <p>Your S3 access key</p><input value="ABCD1234EFGH5678">
            <p>Your S3 secret key</p><input value="WXYZ9876QRST5432">
        """.trimIndent()

        val keys = InternetArchiveS3PageParser.parse(html)

        assertEquals("ABCD1234EFGH5678", keys?.access)
        assertEquals("WXYZ9876QRST5432", keys?.secret)
    }

    @Test
    fun rejectsUnrelatedOrAmbiguousSixteenCharacterValues() {
        assertNull(
            InternetArchiveS3PageParser.parse(
                "<input value='ABCD1234EFGH5678'><input value='WXYZ9876QRST5432'>",
            ),
        )
        assertNull(
            InternetArchiveS3PageParser.parse(
                """
                    <p>Your S3 access key</p><p>Your S3 secret key</p>
                    <input value="ABCD1234EFGH5678">
                    <input value="WXYZ9876QRST5432">
                    <input value="MORE1234TOKEN567">
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun parsesQuotedWebViewResultWithoutLoggingOrJsonDependencies() {
        val quoted = InternetArchiveS3PageParser.parseJavascriptResult(
            "\"ABCD1234EFGH5678|WXYZ9876QRST5432\"",
        )
        val direct = InternetArchiveS3PageParser.parseJavascriptResult(
            "ABCD1234EFGH5678|WXYZ9876QRST5432",
        )

        assertEquals("ABCD1234EFGH5678", quoted?.access)
        assertEquals("WXYZ9876QRST5432", quoted?.secret)
        assertEquals(quoted, direct)
        assertNull(InternetArchiveS3PageParser.parseJavascriptResult("null"))
        assertNull(InternetArchiveS3PageParser.parseJavascriptResult("\"too-short|also-short\""))
    }

    @Test
    fun manualNormalizationTrimsButRejectsIncompleteOrDuplicateKeys() {
        val keys = InternetArchiveS3PageParser.normalized(
            "  ABCD1234EFGH5678 ",
            "WXYZ9876QRST5432\n",
        )

        assertEquals("ABCD1234EFGH5678", keys?.access)
        assertEquals("WXYZ9876QRST5432", keys?.secret)
        assertNull(InternetArchiveS3PageParser.normalized("ABCD1234", "WXYZ9876QRST5432"))
        assertNull(InternetArchiveS3PageParser.normalized("ABCD1234EFGH5678", "ABCD1234EFGH5678"))
    }
}
