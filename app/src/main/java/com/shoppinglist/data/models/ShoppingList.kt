package com.shoppinglist.data.models

data class ShoppingList(
    val id: String = "",
    val name: String = "",
    val ownerUid: String? = null,
    val membersEmails: List<String> = emptyList()
)
