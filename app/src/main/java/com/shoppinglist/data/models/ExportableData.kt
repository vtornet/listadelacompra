package com.shoppinglist.data.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Modelos para serialización/deserialización de listas para exportar/importar.
 * No incluyen campos internos como ownerUid ni addedByUid ya que se regeneran al importar.
 */

@Serializable
data class ExportableItem(
    val name: String,
    val inShoppingList: Boolean = true,
    val imageUrl: String? = null,
    val price: Double? = null,
    val previousPrice: Double? = null,
    val quantity: Int = 1
)

@Serializable
data class ExportableList(
    val name: String,
    val items: List<ExportableItem> = emptyList()
)

@Serializable
data class ExportableData(
    val version: Int = 1,
    val exportDate: Long = System.currentTimeMillis(),
    val lists: List<ExportableList>
) {
    fun toJson(): String = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }.encodeToString(this)

    companion object {
        fun fromJson(jsonString: String): ExportableData? = try {
            Json { ignoreUnknownKeys = true }.decodeFromString<ExportableData>(jsonString)
        } catch (e: Exception) {
            null
        }
    }
}
