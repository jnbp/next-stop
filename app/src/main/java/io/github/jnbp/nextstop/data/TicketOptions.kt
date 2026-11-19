package io.github.jnbp.nextstop.data

/**
 * True when [journey] fits the options of a search: every ride of a product that is on (see
 * [productsFit]; "Only Deutschlandticket" is long-distance trains and coaches off), with the
 * bike combo every ride one known to take a bicycle, and with "Fahrrad dabei" no ride known not
 * to (see [BikeRules]; the providers are not asked to filter bicycles, #263). The providers are
 * asked without the products already; this catches what slips through (a provider that ignores
 * or cannot express a filter, a refreshed page).
 */
fun ticketFits(journey: Journey, options: SearchOptions): Boolean =
    productsFit(journey, options) &&
        (!options.bikeCombo || BikeRules.journeyCarriesBike(journey)) &&
        (!options.bike || BikeRules.journeyMayCarryBike(journey))

/** [this] page without the journeys that do not fit the options of [options]. */
fun JourneyPage.fitting(options: SearchOptions): JourneyPage =
    if (options.products.containsAll(PRODUCT_KEYS) && !options.carriesBike) {
        this
    } else {
        copy(journeys = journeys.filter { ticketFits(it, options) })
    }

/** Ticket options that differ from the plain search, for the small chips above the connections. */
fun ticketFilters(options: SearchOptions): List<TicketFilter> = buildList {
    if (options.localOnly) add(TicketFilter.LocalOnly)
    // The bike combo carries the bicycle anyway: its chip says both.
    if (options.bikeCombo) add(TicketFilter.BikeCombo) else if (options.bike) add(TicketFilter.Bike)
}

enum class TicketFilter { LocalOnly, Bike, BikeCombo }

/** [options] with [filter] switched off. */
fun withoutTicketFilter(options: SearchOptions, filter: TicketFilter): SearchOptions = when (filter) {
    TicketFilter.LocalOnly -> options.withLocalOnly(false)
    TicketFilter.Bike -> options.copy(bike = false)
    TicketFilter.BikeCombo -> options.copy(bikeCombo = false)
}
