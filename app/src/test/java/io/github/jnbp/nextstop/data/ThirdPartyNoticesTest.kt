package io.github.jnbp.nextstop.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps the list of "Open-source licenses" in step with gradle/libs.versions.toml. */
class ThirdPartyNoticesTest {
    private val catalog: String by lazy {
        // Gradle runs the tests of the module in app/; an IDE may run them at the root.
        listOf("../gradle/libs.versions.toml", "gradle/libs.versions.toml").map(::File).first { it.exists() }
            .readText(Charsets.UTF_8)
    }

    /** The aliases of the [libraries] table, in their order. */
    private fun libraryAliases(toml: String): List<String> {
        val aliases = mutableListOf<String>()
        var inLibraries = false
        toml.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("[") -> inLibraries = line == "[libraries]"
                inLibraries && line.isNotEmpty() && !line.startsWith("#") -> {
                    val alias = line.substringBefore("=").trim().trim('"')
                    if (line.contains("=") && alias.isNotEmpty()) aliases += alias
                }
            }
        }
        return aliases
    }

    @Test
    fun theCatalogIsRead() {
        val aliases = libraryAliases(catalog)
        assertTrue("no libraries found in libs.versions.toml", aliases.size > 10)
        assertTrue(aliases.contains("maplibre-android"))
    }

    @Test
    fun everyLibraryOfTheCatalogIsListedOrMarkedAsNotShipped() {
        val listed = THIRD_PARTY_LIBRARIES.flatMap { it.aliases }.toSet()
        val missing = libraryAliases(catalog).filter { it !in listed && it !in NOT_SHIPPED_ALIASES }
        assertEquals(
            "add these libraries to THIRD_PARTY_LIBRARIES (data/ThirdPartyNotices.kt), or to NOT_SHIPPED_ALIASES if no release build contains them",
            emptyList<String>(),
            missing,
        )
    }

    @Test
    fun noEntryNamesALibraryTheCatalogDoesNotHave() {
        val known = libraryAliases(catalog).toSet()
        val stale = (THIRD_PARTY_LIBRARIES.flatMap { it.aliases } + NOT_SHIPPED_ALIASES).filter { it !in known }
        assertEquals("entries for libraries no longer in libs.versions.toml", emptyList<String>(), stale)
    }

    @Test
    fun noLibraryIsBothListedAndNotShipped() {
        val listed = THIRD_PARTY_LIBRARIES.flatMap { it.aliases }
        assertEquals(listed.size, listed.toSet().size)
        assertTrue(listed.none { it in NOT_SHIPPED_ALIASES })
    }

    @Test
    fun everyEntryHasALicenseAndAnAddress() {
        (THIRD_PARTY_LIBRARIES + BUNDLED_FONTS).forEach { notice ->
            assertTrue(notice.name, notice.license.isNotBlank() && notice.maintainer.isNotBlank())
            assertTrue(notice.name, notice.url.startsWith("https://"))
        }
    }

    @Test
    fun theProjectPagesFollowTheLanguage() {
        assertEquals("https://nextstop.bapo.me/datenschutz/", ProjectLinks.privacyPolicy("de"))
        assertEquals("https://nextstop.bapo.me/en/privacy/", ProjectLinks.privacyPolicy("en"))
        assertEquals("https://nextstop.bapo.me/", ProjectLinks.website("de"))
        assertEquals("https://nextstop.bapo.me/en/", ProjectLinks.website("en"))
    }
}
