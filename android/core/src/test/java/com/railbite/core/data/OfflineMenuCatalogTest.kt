package com.railbite.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OfflineMenuCatalogTest {

    @Test
    fun `default_menu asset parses cleanly with deterministic numeric IDs and official MRPs`() {
        val candidates = listOf(
            File("src/main/assets/default_menu.json"),
            File("core/src/main/assets/default_menu.json"),
            File("android/core/src/main/assets/default_menu.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
        assertTrue("default_menu.json asset missing", file != null)

        val entries = OfflineMenuCatalog.parseBundledJson(file!!.readText())
        assertTrue(entries.size >= 15)
        assertEquals((1..entries.size).toList(), entries.map { it.numericId })
        assertTrue(entries.all { it.name.isNotBlank() && it.mrpPaise > 0 && it.category.isNotBlank() })
        assertEquals("Masala Chai", entries.first().name)
        assertEquals(1500, entries.first().mrpPaise)
    }
}

