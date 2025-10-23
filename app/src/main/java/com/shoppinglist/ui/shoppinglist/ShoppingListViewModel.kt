package com.shoppinglist.ui.shoppinglist

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.data.models.ShoppingList
import com.shoppinglist.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.text.Normalizer
import java.util.Locale

class ShoppingListViewModel : ViewModel() {

    private val repository = ShoppingListRepository()
    private val auth = Firebase.auth

    private val _uid = MutableStateFlow(auth.currentUser?.uid ?: "")
    private val _email = MutableStateFlow(auth.currentUser?.email)
    private val authListener = FirebaseAuth.AuthStateListener { fb ->
        _uid.value = fb.currentUser?.uid ?: ""
        _email.value = fb.currentUser?.email
    }
    init { auth.addAuthStateListener(authListener) }
    override fun onCleared() { auth.removeAuthStateListener(authListener) }

    val lists: StateFlow<List<ShoppingList>> = combine(_uid, _email) { uid, email ->
        uid to email
    }.flatMapLatest { (uid, email) ->
        repository.observeListsForUser(uid, email)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _currentListId = MutableStateFlow("")
    val currentListId: StateFlow<String> = _currentListId.asStateFlow()

    val currentListName: StateFlow<String> = combine(lists, currentListId) { ls, id ->
        ls.firstOrNull { it.id == id }?.name ?: "Mi lista"
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "Mi lista")

    val items: StateFlow<List<ShoppingItem>> = currentListId
        .flatMapLatest { id -> repository.getItemsForList(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()
    fun clearError() { _error.value = null }

    data class DuplicatePrompt(val name: String, val onConfirm: () -> Unit)
    private val _duplicate = MutableStateFlow<DuplicatePrompt?>(null)
    val duplicate: StateFlow<DuplicatePrompt?> = _duplicate.asStateFlow()
    fun dismissDuplicate() { _duplicate.value = null }
    fun confirmDuplicate() { _duplicate.value?.onConfirm?.invoke(); _duplicate.value = null }

    init {
        viewModelScope.launch {
            val uid = _uid.value
            if (uid.isBlank()) return@launch
            _loading.value = true
            try {
                val listId = withTimeout(10_000) { repository.getOrCreateDefaultListId(uid, _email.value) }
                _currentListId.value = listId
                repository.migrateMyItemsToList(uid, listId)
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo inicializar tu lista."
            } finally { _loading.value = false }
        }
    }

    /* ===== Listas ===== */

    fun switchList(listId: String) { if (listId.isNotBlank()) _currentListId.value = listId }

    fun inviteMember(email: String) {
        val listId = _currentListId.value
        if (listId.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            try { withTimeout(10_000) { repository.addMemberEmail(listId, email.trim()) } }
            catch (e: Exception) { _error.value = e.message ?: "No se pudo invitar." }
            finally { _loading.value = false }
        }
    }

    fun createList(name: String, onCreated: (String) -> Unit = {}) {
        val uid = _uid.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            try {
                val newId = repository.createList(name, uid)
                _currentListId.value = newId
                onCreated(newId)
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo crear la lista."
            } finally { _loading.value = false }
        }
    }

    fun renameCurrentList(newName: String) {
        val id = _currentListId.value
        if (id.isBlank()) return
        viewModelScope.launch {
            _loading.value = true
            try { repository.renameList(id, newName) }
            catch (e: Exception) { _error.value = e.message ?: "No se pudo renombrar la lista." }
            finally { _loading.value = false }
        }
    }

    /** Ahora borra en cascada (items + lista). */
    fun deleteList(listId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                repository.deleteListDeep(listId)
                if (_currentListId.value == listId) {
                    val remaining = lists.value.firstOrNull { it.id != listId }?.id ?: ""
                    _currentListId.value = remaining
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo eliminar la lista."
            } finally { _loading.value = false }
        }
    }

    /* ===== Ítems ===== */

    private fun norm(s: String): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.getDefault())

    private fun existsByName(name: String): Boolean {
        val n = norm(name)
        return items.value.any { norm(it.name) == n }
    }

    fun addItem(name: String) {
        if (name.isBlank()) return
        val listId = _currentListId.value
        if (existsByName(name)) {
            _duplicate.value = DuplicatePrompt(name) { actuallyAddItem(name, listId) }
            return
        }
        actuallyAddItem(name, listId)
    }

    /** Aumenta cantidad (mín. 1). */
    fun incrementQuantity(item: ShoppingItem) {
        viewModelScope.launch {
            try {
                repository.updateItem(item.copy(quantity = (item.quantity + 1).coerceAtLeast(1)))
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /** Disminuye cantidad (mín. 1). */
    fun decrementQuantity(item: ShoppingItem) {
        viewModelScope.launch {
            try {
                val newQ = (item.quantity - 1).coerceAtLeast(1)
                if (newQ != item.quantity) repository.updateItem(item.copy(quantity = newQ))
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /** Fija cantidad explícita (mín. 1). */
    fun setQuantity(item: ShoppingItem, n: Int) {
        if (n < 1) return
        viewModelScope.launch {
            try {
                repository.updateItem(item.copy(quantity = n))
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    /** Opción para el diálogo de duplicado: sumar 1 a la cantidad del existente. */
    fun mergeDuplicateByName(name: String) {
        viewModelScope.launch {
            try {
                val existing = items.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (existing != null) {
                    repository.updateItem(existing.copy(quantity = existing.quantity + 1))
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _duplicate.value = null
            }
        }
    }


    /** Marca todos los artículos "por comprar" como comprados. */
    fun markAllToBuyAsPurchased() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val toBuy = items.value.filter { it.inShoppingList }
                for (it in toBuy) {
                    repository.updateItem(it.copy(inShoppingList = false))
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudieron marcar los artículos."
            } finally {
                _loading.value = false
            }
        }
    }


    private fun actuallyAddItem(name: String, listId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                withTimeout(10_000) {
                    val newItem = ShoppingItem(
                        name = name.trim(),
                        inShoppingList = true,
                        addedByUid = _uid.value,
                        listId = listId
                    )
                    repository.addItemToList(listId, newItem)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo añadir el artículo."
            } finally { _loading.value = false }
        }
    }

    fun addItemFromBarcode(barcode: String) {
        val listId = _currentListId.value
        viewModelScope.launch {
            _loading.value = true
            try {
                val info = withTimeout(8_000) { repository.resolveBarcodeInfo(barcode) }
                val finalName = (info?.name ?: barcode).trim()
                if (existsByName(finalName)) {
                    _duplicate.value = DuplicatePrompt(finalName) { actuallyAddScanned(finalName, info?.imageUrl, listId) }
                } else {
                    actuallyAddScanned(finalName, info?.imageUrl, listId)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo añadir el artículo desde código."
            } finally { _loading.value = false }
        }
    }

    private fun actuallyAddScanned(name: String, imageUrl: String?, listId: String) {
        viewModelScope.launch {
            try {
                withTimeout(10_000) {
                    val newItem = ShoppingItem(
                        name = name,
                        inShoppingList = true,
                        addedByUid = _uid.value,
                        imageUrl = imageUrl,
                        listId = listId
                    )
                    repository.addItemToList(listId, newItem)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo añadir el artículo."
            }
        }
    }

    fun toggleItemStatus(item: ShoppingItem) {
        viewModelScope.launch {
            _loading.value = true
            try { withTimeout(10_000) { repository.updateItem(item.copy(inShoppingList = !item.inShoppingList)) } }
            catch (e: Exception) { _error.value = e.message ?: "No se pudo actualizar el estado." }
            finally { _loading.value = false }
        }
    }

    fun addImageToItem(item: ShoppingItem, imageUri: Uri) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val imageUrl = withTimeout(30_000) { repository.uploadImage(imageUri) }
                withTimeout(10_000) { repository.updateItem(item.copy(imageUrl = imageUrl)) }
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo subir la imagen."
            } finally { _loading.value = false }
        }
    }

    fun removeImageFromItem(item: ShoppingItem) {
        val url = item.imageUrl ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                withTimeout(20_000) { repository.deleteImageByUrl(url) }
                withTimeout(10_000) { repository.updateItem(item.copy(imageUrl = null)) }
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo eliminar la imagen."
            } finally { _loading.value = false }
        }
    }

    fun renameItem(item: ShoppingItem, newName: String) {
        if (newName.isBlank() || newName == item.name) return
        if (existsByName(newName)) {
            _duplicate.value = DuplicatePrompt(newName) { actuallyRename(item, newName) }
            return
        }
        actuallyRename(item, newName)
    }

    private fun actuallyRename(item: ShoppingItem, newName: String) {
        viewModelScope.launch {
            _loading.value = true
            try { withTimeout(10_000) { repository.updateItem(item.copy(name = newName.trim())) } }
            catch (e: Exception) { _error.value = e.message ?: "No se pudo renombrar el artículo." }
            finally { _loading.value = false }
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch {
            _loading.value = true
            try {
                item.imageUrl?.let { withTimeout(15_000) { repository.deleteImageByUrl(it) } }
                withTimeout(10_000) { repository.deleteItem(item.id) }
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo eliminar el artículo."
            } finally { _loading.value = false }
        }
    }

    fun updatePrice(item: ShoppingItem, newPrice: Double) {
        viewModelScope.launch {
            _loading.value = true
            try { withTimeout(10_000) { repository.updateItem(item.copy(previousPrice = item.price, price = newPrice)) } }
            catch (e: Exception) { _error.value = e.message ?: "No se pudo actualizar el precio." }
            finally { _loading.value = false }
        }
    }

    fun clearPrice(item: ShoppingItem) {
        viewModelScope.launch {
            _loading.value = true
            try {
                withTimeout(10_000) {
                    repository.updateItem(item.copy(previousPrice = item.price, price = null))
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo limpiar el precio."
            } finally {
                _loading.value = false
            }
        }
    }
}
