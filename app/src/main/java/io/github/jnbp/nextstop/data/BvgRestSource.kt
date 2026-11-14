package io.github.jnbp.nextstop.data

/**
 * Berlin (BVG) data via the community REST API at v6.bvg.transport.rest.
 * No API key; the service allows 100 requests per minute.
 */
class BvgRestSource(baseUrl: String = DEFAULT_URL) : HafasRestSource(baseUrl) {

    /** Every product is on unless it is switched off (see [hafasProductQuery]). */
    override fun productQuery(products: Set<String>): String = hafasProductQuery(products)

    companion object {
        const val DEFAULT_URL = "https://v6.bvg.transport.rest"
    }
}

/**
 * The query parameters of a HAFAS ReST API (transport.rest) that switch off the products not in
 * [products]. HAFAS knows no coaches: they come as buses, so buses stay on while coaches are,
 * and the coaches among them are dropped afterwards when off (see [productsFit]).
 */
internal fun hafasProductQuery(products: Set<String>): String =
    PRODUCT_KEYS.filter { it !in products }.joinToString("") { key ->
        when (key) {
            "coach" -> ""
            "bus" -> if ("coach" in products) "" else "&bus=false"
            else -> "&$key=false"
        }
    }
