package io.github.jnbp.nextstop.data

/*
 * A contact address that only has its written form (#58): many address books keep the whole
 * address in one field and leave street, postcode and town empty. Without them the address
 * could only be found by the device's geocoder, which some devices do not have, and picking
 * the contact did nothing. The written form is split into those fields where it follows the
 * usual German pattern ("Street 12, 12345 Town[, Country]"), so the address is looked up and
 * checked as any other (see resolveContactPlace).
 */

/**
 * [contact] with street, postcode, town and country taken from its written address when it
 * has neither a postcode nor a town of its own and the written one names a postcode with its
 * town; [contact] itself otherwise.
 */
fun contactWithFields(contact: ContactAddress): ContactAddress {
    if (contact.postcode != null || contact.city != null) return contact
    val parts = contact.address.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    for (index in parts.indices) {
        val town = POSTCODE_TOWN.matchEntire(parts[index]) ?: continue
        val street = contact.street ?: parts.getOrNull(index - 1)?.takeIf { index >= 1 }
        val country = contact.country ?: parts.getOrNull(index + 1)
        return contact.copy(
            street = street,
            postcode = town.groupValues[1],
            city = town.groupValues[2].trim(),
            country = country,
        )
    }
    return contact
}

// "12345 Town", also "D-12345 Town" and "DE-12345 Town".
private val POSTCODE_TOWN = Regex("(?:[A-Za-z]{1,2}-\\s?)?(\\d{5})\\s+(\\S.*)")
