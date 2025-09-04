package com.shoppinglist.data.models

data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val inShoppingList: Boolean = true,
    val price: Double? = null,
    val previousPrice: Double? = null,
    val imageUrl: String? = null,
    val addedByUid: String? = null,
    val listId: String? = null
)
