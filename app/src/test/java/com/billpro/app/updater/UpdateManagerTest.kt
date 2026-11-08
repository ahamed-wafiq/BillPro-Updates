package com.billpro.app.updater

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class UpdateManagerTest {

    @Test
    fun testIsNewerVersion() {
        // Newer versions
        assertTrue(UpdateManager.isNewerVersion("v1.0.1", "1.0.0"))
        assertTrue(UpdateManager.isNewerVersion("1.1.0", "1.0.0"))
        assertTrue(UpdateManager.isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(UpdateManager.isNewerVersion("1.0.0.1", "1.0.0"))

        // Same or older versions
        assertFalse(UpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(UpdateManager.isNewerVersion("v1.0.0", "1.0.0"))
        assertFalse(UpdateManager.isNewerVersion("0.9.9", "1.0.0"))
        assertFalse(UpdateManager.isNewerVersion("v0.9.0", "1.0.0"))
    }

    @Test
    fun testExtractSha256FromText() {
        val testHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        val textWithHash = """
            # BillPro v1.0.1 Release
            Changes in this release:
            - Performance improvements
            
            SHA256: $testHash
        """.trimIndent()

        val extracted = UpdateManager.extractSha256FromText(textWithHash)
        assertEquals(testHash, extracted)
    }

    @Test
    fun testCalculateSha256() {
        val tempFile = File.createTempFile("test_sha", ".txt")
        try {
            tempFile.writeText("BillPro Test Update Checksum")
            val computedHash = UpdateManager.calculateSha256(tempFile)
            assertNotNull(computedHash)
            assertEquals(64, computedHash.length)
        } finally {
            tempFile.delete()
        }
    }
}

