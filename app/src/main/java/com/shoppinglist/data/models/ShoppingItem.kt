package com.shoppinglist.data.models

data class ShoppingItem(
    val id: String = "",
    val name: String = "",
    val inShoppingList: Boolean = true, // true = pendiente; false = comprado
    val listId: String? = null,
    val addedByUid: String? = null,
    val imageUrl: String? = null,
    val price: Double? = null,          // precio actual
    val previousPrice: Double? = null,  // precio anterior
    val quantity: Int = 1,              // cantidad (mínimo 1)
    val createdAt: Long = System.currentTimeMillis() // fecha de creación en ms
)
