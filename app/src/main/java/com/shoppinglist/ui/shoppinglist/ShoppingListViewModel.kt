// Ruta: app/src/main/java/com/shoppinglist/ui/shoppinglist/ShoppingListViewModel.kt

package com.shoppinglist.ui.shoppinglist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel : ViewModel() {

    private val repository = ShoppingListRepository()
    private val auth = Firebase.auth

    val items: StateFlow<List<ShoppingItem>> = repository.getItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addItem(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newItem = ShoppingItem(
                name = name,
                inShoppingList = true,
                addedByUid = auth.currentUser?.uid ?: "unknown"
            )
            repository.addItem(newItem)
        }
    }

    fun toggleItemStatus(item: ShoppingItem) {
        viewModelScope.launch {
            val updatedItem = item.copy(
                inShoppingList = !item.inShoppingList,
                addedByUid = auth.currentUser?.uid ?: "unknown"
            )
            repository.updateItem(updatedItem)
        }
    }

    fun addImageToItem(item: ShoppingItem, imageUri: Uri) {
        viewModelScope.launch {
            try {
                val imageUrl = repository.uploadImage(imageUri)
                val updatedItem = item.copy(imageUrl = imageUrl)
                repository.updateItem(updatedItem)
            } catch (e: Exception) {
                // Future: Handle upload error
            }
        }
    }
}