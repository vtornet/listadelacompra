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
import java.util.Calendar

/**
 * ViewModel para las estadísticas de la app.
 * Proporciona datos generales y por lista con filtros por período.
 */
class StatisticsViewModel : ViewModel() {

    private val repository = ShoppingListRepository()
    private val auth = Firebase.auth

    private val _uiState = MutableStateFlow<StatisticsUiState>(StatisticsUiState.Loading)
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(PeriodFilter.ALL)
    val selectedPeriod: StateFlow<PeriodFilter> = _selectedPeriod.asStateFlow()

    // Caché de items para evitar recargas innecesarias
    private var cachedItems: List<ShoppingItem> = emptyList()
    private var cachedLists: List<ShoppingList> = emptyList()
    private var cacheTimestamp = 0L
    private val CACHE_DURATION = 5 * 60 * 1000 // 5 minutos

    /**
     * Carga las estadísticas con el filtro de período actual.
     */
    fun loadStatistics(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = StatisticsUiState.Loading
            try {
                val uid = auth.currentUser?.uid
                if (uid == null) {
                    _uiState.value = StatisticsUiState.Error("Usuario no autenticado")
                    return@launch
                }

                val now = System.currentTimeMillis()
                val useCache = !forceRefresh && cachedItems.isNotEmpty() && (now - cacheTimestamp) < CACHE_DURATION

                val allLists = if (useCache) cachedLists else {
                    repository.observeListsForUser(uid, auth.currentUser?.email).first().also {
                        cachedLists = it
                    }
                }

                val listIds = allLists.map { it.id }

                // Obtener items - usar caché si es posible y el filtro no ha cambiado
                val allItems = if (useCache && _selectedPeriod.value == PeriodFilter.ALL) {
                    cachedItems
                } else {
                    // Para filtros de fecha, obtener desde Firestore con filtro
                    val minDate = getMinDateForPeriod(_selectedPeriod.value)
                    if (minDate != null && listIds.isNotEmpty()) {
                        // Usar consulta filtrada por fecha para mejor rendimiento
                        repository.getItemsForListsFilteredByDate(listIds, minDate)
                    } else {
                        // Obtener todos los items (con paginación para muchas listas)
                        mutableListOf<ShoppingItem>().apply {
                            for (listId in listIds) {
                                addAll(repository.getItemsForListSync(listId))
                            }
                        }
                    }.also {
                        cachedItems = it
                        cacheTimestamp = now
                    }
                }

                // Filtrar items por el período seleccionado (doble filtro por seguridad)
                val filteredItems = if (useCache && _selectedPeriod.value != PeriodFilter.ALL) {
                    filterItemsByPeriod(cachedItems, _selectedPeriod.value)
                } else {
                    allItems
                }

                // Calcular estadísticas
                val generalStats = calculateGeneralStats(filteredItems, allLists)
                val listStats = calculateListStats(allLists, filteredItems)
                val categoryStats = calculateCategoryStats(filteredItems)
                val periodInfo = getPeriodInfo(_selectedPeriod.value)

                _uiState.value = StatisticsUiState.Success(
                    generalStats = generalStats,
                    listStats = listStats,
                    categoryStats = categoryStats,
                    periodInfo = periodInfo
                )
            } catch (e: Exception) {
                _uiState.value = StatisticsUiState.Error(e.message ?: "Error al cargar estadísticas")
            }
        }
    }

    /**
     * Cambia el filtro de período y recarga las estadísticas.
     */
    fun setPeriod(period: PeriodFilter) {
        _selectedPeriod.value = period

        // Si cambiamos de un filtro a otro y tenemos caché, intentar usarla
        if (cachedItems.isNotEmpty() && period != PeriodFilter.ALL) {
            viewModelScope.launch {
                _uiState.value = StatisticsUiState.Loading
                try {
                    val filteredItems = filterItemsByPeriod(cachedItems, period)
                    val generalStats = calculateGeneralStats(filteredItems, cachedLists)
                    val listStats = calculateListStats(cachedLists, filteredItems)
                    val categoryStats = calculateCategoryStats(filteredItems)
                    val periodInfo = getPeriodInfo(period)

                    _uiState.value = StatisticsUiState.Success(
                        generalStats = generalStats,
                        listStats = listStats,
                        categoryStats = categoryStats,
                        periodInfo = periodInfo
                    )
                } catch (e: Exception) {
                    _uiState.value = StatisticsUiState.Error(e.message ?: "Error al cargar estadísticas")
                }
            }
        } else {
            loadStatistics(forceRefresh = true)
        }
    }

    private fun getMinDateForPeriod(period: PeriodFilter): Long? {
        if (period == PeriodFilter.ALL) return null

        val calendar = Calendar.getInstance()
        return when (period) {
            PeriodFilter.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            PeriodFilter.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            PeriodFilter.LAST_7_DAYS -> {
                calendar.add(Calendar.DAY_OF_MONTH, -7)
                calendar.timeInMillis
            }
            PeriodFilter.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            PeriodFilter.LAST_30_DAYS -> {
                calendar.add(Calendar.DAY_OF_MONTH, -30)
                calendar.timeInMillis
            }
            PeriodFilter.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                calendar.timeInMillis
            }
            PeriodFilter.ALL -> null
        }
    }

    private fun filterItemsByPeriod(items: List<ShoppingItem>, period: PeriodFilter): List<ShoppingItem> {
        if (period == PeriodFilter.ALL) return items

        val now = Calendar.getInstance()
        val startTime = Calendar.getInstance()

        when (period) {
            PeriodFilter.TODAY -> {
                // Inicio del día actual
                startTime.set(Calendar.HOUR_OF_DAY, 0)
                startTime.set(Calendar.MINUTE, 0)
                startTime.set(Calendar.SECOND, 0)
                startTime.set(Calendar.MILLISECOND, 0)
            }
            PeriodFilter.THIS_WEEK -> {
                // Inicio de la semana (lunes)
                startTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                startTime.set(Calendar.HOUR_OF_DAY, 0)
                startTime.set(Calendar.MINUTE, 0)
                startTime.set(Calendar.SECOND, 0)
                startTime.set(Calendar.MILLISECOND, 0)
            }
            PeriodFilter.LAST_7_DAYS -> {
                // Últimos 7 días
                startTime.add(Calendar.DAY_OF_MONTH, -7)
            }
            PeriodFilter.THIS_MONTH -> {
                // Inicio del mes
                startTime.set(Calendar.DAY_OF_MONTH, 1)
                startTime.set(Calendar.HOUR_OF_DAY, 0)
                startTime.set(Calendar.MINUTE, 0)
                startTime.set(Calendar.SECOND, 0)
                startTime.set(Calendar.MILLISECOND, 0)
            }
            PeriodFilter.LAST_30_DAYS -> {
                // Últimos 30 días
                startTime.add(Calendar.DAY_OF_MONTH, -30)
            }
            PeriodFilter.THIS_YEAR -> {
                // Inicio del año
                startTime.set(Calendar.DAY_OF_YEAR, 1)
                startTime.set(Calendar.HOUR_OF_DAY, 0)
                startTime.set(Calendar.MINUTE, 0)
                startTime.set(Calendar.SECOND, 0)
                startTime.set(Calendar.MILLISECOND, 0)
            }
            PeriodFilter.ALL -> {
                // No filtrar
            }
        }

        return items.filter { it.createdAt >= startTime.timeInMillis }
    }

    private fun getPeriodInfo(period: PeriodFilter): PeriodInfo {
        val now = Calendar.getInstance()
        val startTime = Calendar.getInstance()

        val label = when (period) {
            PeriodFilter.TODAY -> {
                startTime.set(Calendar.HOUR_OF_DAY, 0)
                startTime.set(Calendar.MINUTE, 0)
                "Hoy"
            }
            PeriodFilter.THIS_WEEK -> {
                startTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                "Esta semana"
            }
            PeriodFilter.LAST_7_DAYS -> {
                startTime.add(Calendar.DAY_OF_MONTH, -7)
                "Últimos 7 días"
            }
            PeriodFilter.THIS_MONTH -> {
                startTime.set(Calendar.DAY_OF_MONTH, 1)
                "Este mes"
            }
            PeriodFilter.LAST_30_DAYS -> {
                startTime.add(Calendar.DAY_OF_MONTH, -30)
                "Últimos 30 días"
            }
            PeriodFilter.THIS_YEAR -> {
                startTime.set(Calendar.DAY_OF_YEAR, 1)
                "Este año"
            }
            PeriodFilter.ALL -> {
                startTime.timeInMillis = 0
                "Histórico"
            }
        }

        return PeriodInfo(
            label = label,
            startDate = startTime.timeInMillis,
            endDate = now.timeInMillis
        )
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

        // Items más frecuentes
        val frequentItems = items
            .groupBy { it.name.lowercase() }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { FrequentItem(name = it.key, count = it.value) }

        return GeneralStats(
            totalLists = lists.size,
            totalItems = totalItems,
            pendingItems = pendingItems,
            purchasedItems = purchasedItems,
            estimatedTotal = totalPrice,
            itemsWithPrice = itemsWithPrice,
            itemsWithImage = itemsWithImage,
            frequentItems = frequentItems,
            lastActivity = "Hoy",
            savingsAmount = maxOf(0.0, previousTotalPrice - totalPrice)
        )
    }

    private fun calculateListStats(lists: List<ShoppingList>, allItems: List<ShoppingItem>): List<ListStats> {
        return lists.map { list ->
            val listItems = allItems.filter { it.listId == list.id }
            if (listItems.isEmpty()) return@map null
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
        }.filterNotNull().sortedByDescending { it.estimatedTotal }
    }

    private fun calculateCategoryStats(items: List<ShoppingItem>): List<CategoryStat> {
        return items
            .groupBy {
                val name = it.name.lowercase()
                when {
                    name.startsWith("lech") || name.startsWith("letuc") -> "Verduras"
                    name.startsWith("manz") || name.startsWith("plát") || name.startsWith("pera") -> "Frutas"
                    name.startsWith("yogur") || name.startsWith("leche") || name.startsWith("queso") -> "Lácteos"
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

// ===== ENUM PARA FILTROS DE PERÍODO =====

enum class PeriodFilter {
    TODAY,
    THIS_WEEK,
    LAST_7_DAYS,
    THIS_MONTH,
    LAST_30_DAYS,
    THIS_YEAR,
    ALL
}

data class PeriodInfo(
    val label: String,
    val startDate: Long,
    val endDate: Long
)

// ===== DATA CLASSES PARA ESTADÍSTICAS =====

sealed class StatisticsUiState {
    object Loading : StatisticsUiState()
    data class Success(
        val generalStats: GeneralStats,
        val listStats: List<ListStats>,
        val categoryStats: List<CategoryStat>,
        val periodInfo: PeriodInfo
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
