package org.commonlink.entity

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * Persists a `List<String>` as a JSON array string in a `text` column.
 *
 * Preferred over `@JdbcTypeCode(SqlTypes.JSON)` on the collection: Hibernate cannot reliably
 * round-trip a generic Kotlin `List<String>` through jsonb (it fails to resolve the element
 * type on read), and applying the JSON type code to a raw `String` field double-encodes it.
 * A dedicated converter keeps the (de)serialization explicit and dependable.
 */
@Converter
class StringListJsonConverter : AttributeConverter<List<String>, String> {

    override fun convertToDatabaseColumn(attribute: List<String>?): String =
        MAPPER.writeValueAsString(attribute ?: emptyList<String>())

    override fun convertToEntityAttribute(dbData: String?): List<String> =
        if (dbData.isNullOrBlank()) emptyList()
        else MAPPER.readValue(dbData, Array<String>::class.java).toList()

    private companion object {
        private val MAPPER = ObjectMapper()
    }
}
