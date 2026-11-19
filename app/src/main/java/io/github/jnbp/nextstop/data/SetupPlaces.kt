package io.github.jnbp.nextstop.data

/*
 * The step "Orte" of the setup (#229): Zuhause, optionally Arbeit, and more places, kept in the
 * same favourite and saved places as the start page (FavoritePlace, SavedPlace). They count for
 * the disruption alerts on your places and routes and give quick routes on the start page.
 */

/**
 * [favorites] with [place] as the place of the first favourite with [icon] (its name kept); where
 * none has that icon (removed on the start page), a new one at the end. The place is stored
 * without a distance, as a pick of the search is (see pickFavoritePlace).
 */
fun withFavoriteAt(favorites: List<FavoritePlace>, icon: PlaceIcon, place: Stop): List<FavoritePlace> {
    val stored = place.copy(distanceMeters = null)
    val index = favorites.indexOfFirst { it.icon == icon }
    return if (index < 0) {
        favorites + FavoritePlace(icon, place = stored)
    } else {
        favorites.mapIndexed { at, favorite -> if (at == index) favorite.copy(place = stored) else favorite }
    }
}

/** [favorites] with the place of the first favourite with [icon] cleared; the favourite itself stays (as on the start page). */
fun withoutFavoriteAt(favorites: List<FavoritePlace>, icon: PlaceIcon): List<FavoritePlace> {
    val index = favorites.indexOfFirst { it.icon == icon }
    if (index < 0 || favorites[index].place == null) return favorites
    return favorites.mapIndexed { at, favorite -> if (at == index) favorite.copy(place = null) else favorite }
}

/** The place of the first favourite with [icon], or null. */
fun favoriteAt(favorites: List<FavoritePlace>, icon: PlaceIcon): Stop? = favorites.firstOrNull { it.icon == icon }?.place

/**
 * [saved] with [place] added at the end as a saved place with [id] (a pin, named by the place),
 * unless the same place is saved already (by its id), or is one of [favorites].
 */
fun withSetupSaved(saved: List<SavedPlace>, favorites: List<FavoritePlace>, id: String, place: Stop): List<SavedPlace> {
    if (saved.any { it.place.id == place.id } || favorites.any { it.place?.id == place.id }) return saved
    return saved + SavedPlace(id = id, icon = PlaceIcon.Pin, name = "", place = place.copy(distanceMeters = null))
}
