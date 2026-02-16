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
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * ViewModel mejorado con gestión de estado robusta.
 * - Usa Mutex para evitar condiciones de carrera en el estado de loading
 * - Contador de operaciones activas para gestionar el estado de carga correctamente
 * - Inicialización perezosa que solo se ejecuta una vez
 */
class ShoppingListViewModel : ViewModel() {

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 25_000L
        const val BARCODE_TIMEOUT_MS = 12_000L
        const val IMAGE_UPLOAD_TIMEOUT_MS = 45_000L
        const val IMAGE_DELETE_TIMEOUT_MS = 25_000L
    }

    private val repository = ShoppingListRepository()
    private val auth = Firebase.auth

    // Estado de autenticación
    private val _uid = MutableStateFlow(auth.currentUser?.uid ?: "")
    private val _email = MutableStateFlow(auth.currentUser?.email)

    private val authListener = FirebaseAuth.AuthStateListener { fb ->
        _uid.value = fb.currentUser?.uid ?: ""
        _email.value = fb.currentUser?.email
    }

    // Mutex para evitar condiciones de carrera en el estado de loading
    private val loadingMutex = Mutex()

    // Contador de operaciones activas para saber si hay algo en progreso
    private val activeOperations = AtomicInteger(0)

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

    // Flag para asegurar que la inicialización solo se ejecute una vez
    private var isInitialized = false
    private val initializationMutex = Mutex()

    init {
        auth.addAuthStateListener(authListener)
    }

    /**
     * Inicializa el ViewModel de forma segura. Solo se ejecuta una vez.
     */
    suspend fun ensureInitialized() {
        initializationMutex.withLock {
            if (isInitialized) return@withLock

            val uid = _uid.value
            if (uid.isNotBlank()) {
                try {
                    val listId = repository.getOrCreateDefaultListId(uid, _email.value)
                    _currentListId.value = listId
                } catch (e: Exception) {
                    handleError(e, "No se pudo inicializar tu lista.")
                }
            }

            isInitialized = true
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }

    // ===== STREAMS REACTIVOS =====

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

    // ===== GESTIÓN DE LOADING =====

    /**
     * Ejecuta una operación con indicador de loading.
     * Usa un contador para permitir múltiples operaciones concurrentes.
     */
    private suspend fun <T> withLoading(block: suspend () -> T): Result<T> {
        return loadingMutex.withLock {
            _loading.value = true
            activeOperations.incrementAndGet()
        }.let {
            try {
                Result.success(block())
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                if (activeOperations.decrementAndGet() == 0) {
                    loadingMutex.withLock {
                        _loading.value = false
                    }
                }
            }
        }
    }

    // ===== LISTAS =====

    fun switchList(listId: String) {
        if (listId.isNotBlank()) _currentListId.value = listId
    }

    fun inviteMember(email: String) {
        val listId = _currentListId.value
        if (listId.isBlank()) {
            _error.value = "No hay ninguna lista seleccionada"
            return
        }
        viewModelScope.launch {
            withLoading {
                repository.addMemberEmail(listId, email.trim())
            }.onSuccess { added ->
                _error.value = if (added) {
                    "$email ha sido añadido a esta lista. La persona deberá tener la app instalada e iniciar sesión con este email para ver la lista compartida."
                } else {
                    "$email ya tiene acceso a esta lista."
                }
            }.onFailure { e ->
                handleError(e, "No se pudo enviar la invitación.")
            }
        }
    }

    fun createList(name: String, onCreated: (String) -> Unit = {}) {
        val uid = _uid.value
        if (uid.isBlank()) return
        viewModelScope.launch {
            withLoading {
                val newId = repository.createList(name, uid)
                _currentListId.value = newId
                onCreated(newId)
            }.onFailure { e ->
                handleError(e, "No se pudo crear la lista.")
            }
        }
    }

    fun renameCurrentList(newName: String) {
        val id = _currentListId.value
        if (id.isBlank()) return
        viewModelScope.launch {
            withLoading {
                repository.renameList(id, newName)
            }.onFailure { e ->
                handleError(e, "No se pudo renombrar la lista.")
            }
        }
    }

    fun deleteListCascade(listId: String) {
        viewModelScope.launch {
            withLoading {
                repository.deleteListDeep(listId)
                if (_currentListId.value == listId) {
                    val remaining = lists.value.firstOrNull { it.id != listId }?.id ?: ""
                    _currentListId.value = remaining
                }
            }.onFailure { e ->
                handleError(e, "No se pudo eliminar la lista.")
            }
        }
    }

    fun leaveSharedList(listId: String, email: String?) {
        if (email.isNullOrBlank()) return
        viewModelScope.launch {
            withLoading {
                repository.removeMemberEmail(listId, email)
                if (_currentListId.value == listId) {
                    val remaining = lists.value.firstOrNull { it.id != listId }?.id ?: ""
                    _currentListId.value = remaining
                }
            }.onFailure { e ->
                handleError(e, "No se pudo abandonar la lista.")
            }
        }
    }

    // ===== ITEMS =====

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

    fun incrementQuantity(item: ShoppingItem) {
        viewModelScope.launch {
            withLoading {
                repository.updateItem(item.copy(quantity = (item.quantity + 1).coerceAtLeast(1)))
            }.onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun decrementQuantity(item: ShoppingItem) {
        viewModelScope.launch {
            withLoading {
                val newQ = (item.quantity - 1).coerceAtLeast(1)
                if (newQ != item.quantity) repository.updateItem(item.copy(quantity = newQ))
            }.onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun setQuantity(item: ShoppingItem, n: Int) {
        if (n < 1) return
        viewModelScope.launch {
            withLoading {
                repository.updateItem(item.copy(quantity = n))
            }.onFailure { e ->
                _error.value = e.message
            }
        }
    }

    fun mergeDuplicateByName(name: String) {
        viewModelScope.launch {
            withLoading {
                val existing = items.value.firstOrNull { it.name.equals(name, ignoreCase = true) }
                if (existing != null) {
                    repository.updateItem(existing.copy(quantity = existing.quantity + 1))
                }
            }.onFailure { e ->
                handleError(e, "No se pudo completar la operación.")
            }
            _duplicate.value = null
        }
    }

    fun markAllToBuyAsPurchased() {
        viewModelScope.launch {
            withLoading {
                val toBuy = items.value.filter { it.inShoppingList }
                if (toBuy.isEmpty()) return@withLoading
                repository.markItemsAsPurchased(toBuy.map { it.id }.filterNotNull())
            }.onFailure { e ->
                handleError(e, "No se pudieron marcar los artículos.")
            }
        }
    }

    /** Marca todos los artículos comprados como "por comprar". */
    fun markAllPurchasedToBuy() {
        viewModelScope.launch {
            withLoading {
                val purchased = items.value.filter { !it.inShoppingList }
                if (purchased.isEmpty()) return@withLoading
                repository.markItemsAsToBuy(purchased.map { it.id }.filterNotNull())
            }.onFailure { e ->
                handleError(e, "No se pudieron marcar los artículos.")
            }
        }
    }

    private fun actuallyAddItem(name: String, listId: String) {
        viewModelScope.launch {
            withLoading {
                val newItem = ShoppingItem(
                    name = name.trim(),
                    inShoppingList = true,
                    addedByUid = _uid.value,
                    listId = listId
                )
                repository.addItemToList(listId, newItem)
            }.onFailure { e ->
                handleError(e, "No se pudo añadir el artículo.")
            }
        }
    }

    fun addItemFromBarcode(barcode: String) {
        val listId = _currentListId.value
        viewModelScope.launch {
            withLoading {
                val info = repository.resolveBarcodeInfo(barcode)
                val finalName = (info?.name ?: barcode).trim()
                if (existsByName(finalName)) {
                    _duplicate.value = DuplicatePrompt(finalName) {
                        actuallyAddScanned(finalName, info?.imageUrl, listId)
                    }
                } else {
                    actuallyAddScanned(finalName, info?.imageUrl, listId)
                }
            }.onFailure { e ->
                handleError(e, "No se pudo añadir el artículo desde código.")
            }
        }
    }

    private fun actuallyAddScanned(name: String, imageUrl: String?, listId: String) {
        viewModelScope.launch {
            withLoading {
                val newItem = ShoppingItem(
                    name = name,
                    inShoppingList = true,
                    addedByUid = _uid.value,
                    imageUrl = imageUrl,
                    listId = listId
                )
                repository.addItemToList(listId, newItem)
            }.onFailure { e ->
                handleError(e, "No se pudo añadir el artículo.")
            }
        }
    }

    fun toggleItemStatus(item: ShoppingItem) {
        viewModelScope.launch {
            withLoading {
                repository.updateItem(item.copy(inShoppingList = !item.inShoppingList))
            }.onFailure { e ->
                handleError(e, "No se pudo actualizar el estado.")
            }
        }
    }

    fun addImageToItem(item: ShoppingItem, imageUri: Uri) {
        viewModelScope.launch {
            withLoading {
                val imageUrl = repository.uploadImage(imageUri)
                repository.updateItem(item.copy(imageUrl = imageUrl))
            }.onFailure { e ->
                handleError(e, "No se pudo subir la imagen.")
            }
        }
    }

    fun removeImageFromItem(item: ShoppingItem) {
        val url = item.imageUrl ?: return
        viewModelScope.launch {
            withLoading {
                repository.deleteImageByUrl(url)
                repository.updateItem(item.copy(imageUrl = null))
            }.onFailure { e ->
                handleError(e, "No se pudo eliminar la imagen.")
            }
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
            withLoading {
                repository.updateItem(item.copy(name = newName.trim()))
            }.onFailure { e ->
                handleError(e, "No se pudo renombrar el artículo.")
            }
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch {
            withLoading {
                item.imageUrl?.let { repository.deleteImageByUrl(it) }
                repository.deleteItem(item.id)
            }.onFailure { e ->
                handleError(e, "No se pudo eliminar el artículo.")
            }
        }
    }

    fun updatePrice(item: ShoppingItem, newPrice: Double) {
        viewModelScope.launch {
            withLoading {
                repository.updateItem(item.copy(previousPrice = item.price, price = newPrice))
            }.onFailure { e ->
                handleError(e, "No se pudo actualizar el precio.")
            }
        }
    }

    fun clearPrice(item: ShoppingItem) {
        viewModelScope.launch {
            withLoading {
                repository.updateItem(item.copy(previousPrice = item.price, price = null))
            }.onFailure { e ->
                handleError(e, "No se pudo limpiar el precio.")
            }
        }
    }

    // ===== EXPORTAR / IMPORTAR =====

    /**
     * Genera los datos exportables de todas las listas del usuario actual.
     * Solo exporta las listas donde el usuario es propietario.
     */
    suspend fun generateExportData(): com.shoppinglist.data.models.ExportableData? {
        val uid = _uid.value
        if (uid.isBlank()) return null

        return try {
            val myLists = lists.value.filter { it.ownerUid == uid }
            val exportableLists = myLists.map { list ->
                // Obtener items de cada lista individualmente desde Firestore
                val listItems = repository.getItemsForListSync(list.id)
                com.shoppinglist.data.models.ExportableList(
                    name = list.name,
                    items = listItems.map { item ->
                        com.shoppinglist.data.models.ExportableItem(
                            name = item.name,
                            inShoppingList = item.inShoppingList,
                            imageUrl = item.imageUrl,
                            price = item.price,
                            previousPrice = item.previousPrice,
                            quantity = item.quantity
                        )
                    }
                )
            }
            com.shoppinglist.data.models.ExportableData(
                version = 1,
                exportDate = System.currentTimeMillis(),
                lists = exportableLists
            )
        } catch (e: Exception) {
            handleError(e, "No se pudo generar los datos de exportación.")
            null
        }
    }

    /**
     * Importa listas desde datos exportados.
     * Crea nuevas listas con los items importados.
     * Devuelve el número de listas importadas.
     */
    suspend fun importFromData(data: com.shoppinglist.data.models.ExportableData): Int {
        val uid = _uid.value
        if (uid.isBlank()) return 0

        return try {
            var importedCount = 0
            data.lists.forEach { exportableList ->
                // Crear nueva lista
                val newListId = repository.createList(exportableList.name, uid)

                // Importar items
                exportableList.items.forEach { exportableItem ->
                    val newItem = ShoppingItem(
                        name = exportableItem.name,
                        inShoppingList = exportableItem.inShoppingList,
                        imageUrl = exportableItem.imageUrl,
                        price = exportableItem.price,
                        previousPrice = exportableItem.previousPrice,
                        quantity = exportableItem.quantity,
                        addedByUid = uid,
                        listId = newListId
                    )
                    repository.addItemToList(newListId, newItem)
                }
                importedCount++
            }
            importedCount
        } catch (e: Exception) {
            handleError(e, "No se pudo completar la importación.")
            0
        }
    }

    private fun handleError(e: Throwable, fallback: String) {
        val message = when (e) {
            is TimeoutCancellationException -> "La conexión está tardando más de lo esperado. Comprueba tu red e inténtalo de nuevo."
            else -> e.message ?: fallback
        }
        _error.value = message
    }
}
