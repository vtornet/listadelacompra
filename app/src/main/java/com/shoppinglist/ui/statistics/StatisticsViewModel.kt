package com.shoppinglist.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.data.models.ShoppingList
import com.shoppinglist.data.repository.ShoppingListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ViewModel para las estadísticas de la app.
 * Proporciona datos generales y por lista.
 */
class StatisticsViewModel : ViewModel() {

    private val repository = ShoppingListRepository()
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val _selectedListId = MutableStateFlow<String?>(null)
    val selectedListId: StateFlow<String?> = _selectedListId.asStateFlow()

    /**
     * Carga las estadísticas generales o de una lista específica.
     */
    fun loadStatistics(listId: String? = null) {
        _selectedListId.value = listId
        viewModelScope.launch {
            _uiState.value = StatisticsUiState.Loading
            try {
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    _uiState.value = StatisticsUiState.Error("Usuario no autenticado")
                    return@launch
                }

                // Obtener todas las listas del usuario
                val allLists = if (listId == null) {
                    repository.observeListsForUser(uid, auth.currentUser?.email)
                        .first() // Obtener solo el primer valor
                } else {
                    emptyList()
                }

                // Obtener items de todas las listas o de una específica
                val listIds = if (listId == null) allLists.map { it.id } else listOf(listId)
                val allItems = mutableListOf<ShoppingItem>()

                for (id in listIds) {
                    val items = repository.getItemsForListSync(id)
                    allItems.addAll(items)
                }

                // Calcular estadísticas
                val generalStats = calculateGeneralStats(allItems, allLists)
                val listStats = if (listId == null) {
                    calculateListStats(allLists, allItems)
                } else {
                    emptyList()
                }
                val categoryStats = calculateCategoryStats(allItems)

                _uiState.value = StatisticsUiState.Success(
                    generalStats = generalStats,
                    listStats = listStats,
                    categoryStats = categoryStats
                )
            } catch (e: Exception) {
                _uiState.value = StatisticsUiState.Error(e.message ?: "Error al cargar estadísticas")
            }
        }
    }

    private fun calculateGeneralStats(items: List<ShoppingItem>, lists: List<ShoppingList>): GeneralStats {
        val totalItems = items.size
        val pendingItems = items.count { it.inShoppingList }
        val purchasedItems = items.count { !it.inShoppingList }

        // Calcular precio total
        val totalPrice = items.filter { it.price != null }.sumOf { it.price!! * it.quantity }
        val previousTotalPrice = items.filter { it.previousPrice != null }.sumOf { it.previousPrice!! * it.quantity }

        // Items con precio
        val itemsWithPrice = items.count { it.price != null }
        val itemsWithImage = items.count { !it.imageUrl.isNullOrBlank() }

        // Items más frecuentes (por nombre, ignorando mayúsculas)
        val frequentItems = items
            .groupBy { it.name.lowercase() }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { FrequentItem(name = it.key, count = it.value) }

        // Última actividad (simulada - en una app real usaríamos timestamps)
        val lastActivity = "Hoy" // Se podría mejorar con timestamps reales

        return GeneralStats(
            totalLists = lists.size,
            totalItems = totalItems,
            pendingItems = pendingItems,
            purchasedItems = purchasedItems,
            estimatedTotal = totalPrice,
            itemsWithPrice = itemsWithPrice,
            itemsWithImage = itemsWithImage,
            frequentItems = frequentItems,
            lastActivity = lastActivity,
            savingsAmount = maxOf(0.0, previousTotalPrice - totalPrice)
        )
    }

    private fun calculateListStats(lists: List<ShoppingList>, allItems: List<ShoppingItem>): List<ListStats> {
        return lists.map { list ->
            val listItems = allItems.filter { it.listId == list.id }
            val pending = listItems.count { it.inShoppingList }
            val purchased = listItems.count { !it.inShoppingList }
            val total = listItems.sumOf { it.price ?: 0.0 }

            ListStats(
                listId = list.id,
                listName = list.name,
                totalItems = listItems.size,
                pendingItems = pending,
                purchasedItems = purchased,
                estimatedTotal = total,
                isOwner = list.ownerUid == auth.currentUser?.uid
            )
        }.sortedByDescending { it.estimatedTotal }
    }

    private fun calculateCategoryStats(items: List<ShoppingItem>): List<CategoryStat> {
        // Agrupar items por categoría (usando las primeras 2 letras como categoría simple)
        // En una app real, tendríamos un campo de categoría en el item
        return items
            .groupBy {
                val name = it.name.lowercase()
                when {
                    name.startsWith("lech") || name.startsWith("letuc") -> "Verduras"
                    name.startsWith("manz") || name.startsWith("plát") || name.startsWith("pera") -> "Frutas"
                    name.startsWith("lech") || name.startsWith("yogur") -> "Lácteos"
                    name.startsWith("pan") || name.startsWith("gall") -> "Panadería"
                    name.startsWith("carne") || name.startsWith("poll") || name.startsWith("cerd") -> "Carnes"
                    name.startsWith("pesc") || name.startsWith("merlu") || name.startsWith("atún") -> "Pescados"
                    name.startsWith("agua") || name.startsWith("refres") || name.startsWith("zum") -> "Bebidas"
                    name.startsWith("aceit") || name.startsWith("aceite") -> "Aceites"
                    else -> "Otros"
                }
            }
            .map { (category, items) ->
                CategoryStat(
                    category = category,
                    itemCount = items.size,
                    totalAmount = items.sumOf { it.price ?: 0.0 }
                )
            }
            .sortedByDescending { it.itemCount }
    }
}

// ===== DATA CLASSES PARA ESTADÍSTICAS =====

sealed class StatisticsUiState {
    object Loading : StatisticsUiState()
    data class Success(
        val generalStats: GeneralStats,
        val listStats: List<ListStats>,
        val categoryStats: List<CategoryStat>
    ) : StatisticsUiState()
    data class Error(val message: String) : StatisticsUiState()
}

data class GeneralStats(
    val totalLists: Int,
    val totalItems: Int,
    val pendingItems: Int,
    val purchasedItems: Int,
    val estimatedTotal: Double,
    val itemsWithPrice: Int,
    val itemsWithImage: Int,
    val frequentItems: List<FrequentItem>,
    val lastActivity: String,
    val savingsAmount: Double
)

data class ListStats(
    val listId: String,
    val listName: String,
    val totalItems: Int,
    val pendingItems: Int,
    val purchasedItems: Int,
    val estimatedTotal: Double,
    val isOwner: Boolean
)

data class CategoryStat(
    val category: String,
    val itemCount: Int,
    val totalAmount: Double
)

data class FrequentItem(
    val name: String,
    val count: Int
)
