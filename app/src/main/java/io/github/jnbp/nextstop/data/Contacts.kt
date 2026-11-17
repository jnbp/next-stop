package io.github.jnbp.nextstop.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A contact of the device with one of its postal addresses. [kind] is the localised type
 * of the address (home, work, other or the contact's own label), so several addresses of
 * one person can be told apart. Read on demand, never stored or logged.
 */
data class ContactAddress(
    val name: String,
    val address: String,
    val kind: String? = null,
    /** Street with house number, as the contact holds it apart; null when not filled in. */
    val street: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    val country: String? = null,
)

/** Order of the address types within one contact: home, work, other, then custom labels. */
private fun typeRank(type: Int): Int = when (type) {
    StructuredPostal.TYPE_HOME -> 0
    StructuredPostal.TYPE_WORK -> 1
    StructuredPostal.TYPE_OTHER -> 2
    StructuredPostal.TYPE_CUSTOM -> 3
    else -> 4
}

/** All contacts that have a postal address, sorted by name; empty without the permission to read them. */
suspend fun loadContactAddresses(context: Context): List<ContactAddress> = withContext(Dispatchers.IO) {
    // Name, rank of the type and the address itself, sorted after reading.
    val found = mutableListOf<Triple<ContactAddress, Int, Int>>()
    try {
        context.contentResolver.query(
            StructuredPostal.CONTENT_URI,
            arrayOf(
                StructuredPostal.DISPLAY_NAME,
                StructuredPostal.FORMATTED_ADDRESS,
                StructuredPostal.TYPE,
                StructuredPostal.LABEL,
                StructuredPostal.STREET,
                StructuredPostal.POSTCODE,
                StructuredPostal.CITY,
                StructuredPostal.COUNTRY,
            ),
            null,
            null,
            StructuredPostal.DISPLAY_NAME + " ASC",
        )?.use { cursor ->
            var index = 0
            while (cursor.moveToNext()) {
                val name: String? = cursor.getString(0)
                // Addresses are stored with line breaks; the app shows and searches them in one line.
                val address: String? = cursor.getString(1)?.replace(Regex("\\s*\\n\\s*"), ", ")?.trim()
                    ?.let { germanStreetOrder(it, address = true) }
                val type = if (cursor.isNull(2)) StructuredPostal.TYPE_OTHER else cursor.getInt(2)
                val label: String? = cursor.getString(3)
                val kind = StructuredPostal.getTypeLabel(context.resources, type, label)?.toString()
                    ?.takeIf { it.isNotBlank() }
                // The single fields, where the contact has them, let the place search check its hit.
                fun field(column: Int): String? =
                    cursor.getString(column)?.replace(Regex("\\s*\\n\\s*"), ", ")?.trim()?.takeIf { it.isNotEmpty() }
                // A street kept in an English format ("32 Musterstraße") as Germans write it.
                val street = field(4)?.let { germanStreetOrder(it, address = true) }
                val postcode = field(5)
                val city = field(6)
                val country = field(7)
                // Without a formatted address the fields make one.
                val shown = address?.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(street, listOfNotNull(postcode, city).joinToString(" ").takeIf { it.isNotEmpty() }, country)
                        .joinToString(", ").takeIf { it.isNotEmpty() }
                if (!name.isNullOrBlank() && !shown.isNullOrBlank()) {
                    val contact = ContactAddress(name, shown, kind, street, postcode, city, country)
                    found.add(Triple(contact, typeRank(type), index++))
                }
            }
        }
    } catch (e: SecurityException) {
        // Without the permission there is nothing to list.
    }
    // Stable: the provider's order of names is kept, within one name the types are ordered.
    found
        .sortedWith(compareBy<Triple<ContactAddress, Int, Int>>({ it.first.name.lowercase() }, { it.second }, { it.third }))
        .map { it.first }
        .distinct()
}
