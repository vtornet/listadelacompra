// Ruta: app/src/main/java/com/shoppinglist/data/models/ShoppingItem.kt

package com.shoppinglist.data.models

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val inShoppingList: Boolean = true,
    val imageUrl: String? = null,
    val lastPrice: Double? = null,
    val addedByUid: String = "",
    @ServerTimestamp
    val lastModified: Date? = null
)