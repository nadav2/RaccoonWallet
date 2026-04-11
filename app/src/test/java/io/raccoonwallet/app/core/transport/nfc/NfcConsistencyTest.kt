package io.raccoonwallet.app.core.transport.nfc

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class NfcConsistencyTest {

    @Test
    fun `AID in apdu_service xml matches Kotlin constant`() {
        val xmlAid = parseAidFromXml()
        val codeAid = RaccoonWalletHceService.AID

        val xmlBytes = hexStringToBytes(xmlAid)
        assertArrayEquals(
            "AID in apdu_service.xml ($xmlAid) must match RaccoonWalletHceService.AID " +
                "(${codeAid.joinToString("") { "%02X".format(it) }})",
            codeAid,
            xmlBytes
        )
    }

    @Test
    fun `AID is valid ISO 7816 length`() {
        val aid = RaccoonWalletHceService.AID
        assertTrue(
            "AID must be 5-16 bytes per ISO 7816, got ${aid.size}",
            aid.size in 5..16
        )
    }

    private fun parseAidFromXml(): String {
        // Walk up from the test working directory to find the XML file
        val projectRoot = findProjectRoot()
        val xmlFile = File(projectRoot, "app/src/main/res/xml/apdu_service.xml")
        assertTrue("apdu_service.xml not found at ${xmlFile.absolutePath}", xmlFile.exists())

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val aidFilters = doc.getElementsByTagName("aid-filter")
        assertTrue("Expected at least one <aid-filter>", aidFilters.length > 0)

        val value = aidFilters.item(0).attributes.getNamedItem("android:name").nodeValue
        assertNotNull("android:name attribute not found on <aid-filter>", value)
        return value!!
    }

    private fun findProjectRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        // Walk up until we find settings.gradle.kts or build.gradle.kts at root
        while (dir.parentFile != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile!!
        }
        // Fallback: assume current directory is the project root
        return File(System.getProperty("user.dir") ?: ".")
    }

    private fun hexStringToBytes(hex: String): ByteArray {
        val clean = hex.replace(" ", "")
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        return ByteArray(clean.length / 2) { i ->
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            ((hi shl 4) or lo).toByte()
        }
    }
}
