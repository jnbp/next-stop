package io.github.jnbp.nextstop.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The step "Orte" of the setup (#229): the same favourite and saved places as the start page. */
class SetupPlacesTest {
    private val street = Stop("addr:1", "Musterstraße 12", 52.50, 13.40, 340, kind = PlaceKind.Address)
    private val office = Stop("addr:2", "Beispielweg 3", 52.53, 13.38, null, kind = PlaceKind.Address)
    private val defaults = listOf(FavoritePlace(PlaceIcon.Home), FavoritePlace(PlaceIcon.Work))

    @Test
    fun homeIsSetInTheFavouriteOfItsIconWithoutADistance() {
        val changed = withFavoriteAt(defaults, PlaceIcon.Home, street)
        assertEquals(street.copy(distanceMeters = null), favoriteAt(changed, PlaceIcon.Home))
        assertNull(favoriteAt(changed, PlaceIcon.Work))
        assertEquals(2, changed.size)
    }

    @Test
    fun aRemovedFavouriteComesBackAtTheEnd() {
        val onlyWork = listOf(FavoritePlace(PlaceIcon.Work, name = "Büro"))
        val changed = withFavoriteAt(onlyWork, PlaceIcon.Home, street)
        assertEquals(listOf(PlaceIcon.Work, PlaceIcon.Home), changed.map { it.icon })
        // The name given before stays.
        assertEquals("Büro", withFavoriteAt(onlyWork, PlaceIcon.Work, office).first().name)
    }

    @Test
    fun clearingKeepsTheFavourite() {
        val set = withFavoriteAt(defaults, PlaceIcon.Work, office)
        val cleared = withoutFavoriteAt(set, PlaceIcon.Work)
        assertEquals(defaults, cleared)
        assertSame(defaults, withoutFavoriteAt(defaults, PlaceIcon.Work))
    }

    @Test
    fun morePlacesAreSavedOnceAndNotTwiceAsAFavourite() {
        val favorites = withFavoriteAt(defaults, PlaceIcon.Home, street)
        val saved = withSetupSaved(emptyList(), favorites, "id-1", office)
        assertEquals(listOf("id-1"), saved.map { it.id })
        assertEquals(PlaceIcon.Pin, saved.first().icon)
        assertNull(saved.first().place.distanceMeters)
        assertSame(saved, withSetupSaved(saved, favorites, "id-2", office))
        assertEquals(saved, withSetupSaved(saved, favorites, "id-3", street))
    }
}
