package com.shoppinglist.ui.statistics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoppinglist.ui.shoppinglist.ShoppingListViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    shoppingListViewModel: ShoppingListViewModel,
    onBack: () -> Unit,
    onViewList: (String) -> Unit
) {
    val viewModel: StatisticsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()

    var showMonthPicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadStatistics()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Estadísticas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        when (val state = uiState) {
            is StatisticsUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is StatisticsUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Button(onClick = { viewModel.loadStatistics() }) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            is StatisticsUiState.Success -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Selector de período
                    item {
                        PeriodFilterSelector(
                            selectedPeriod = selectedPeriod,
                            onPeriodSelected = {
                                viewModel.setPeriod(it)
                                if (it == PeriodFilter.SPECIFIC_MONTH) {
                                    showMonthPicker = true
                                }
                            },
                            periodInfo = state.periodInfo
                        )
                    }

                    // Selector de mes específico
                    if (selectedPeriod == PeriodFilter.SPECIFIC_MONTH) {
                        item {
                            MonthYearSelector(
                                selectedMonth = selectedMonth.first,
                                selectedYear = selectedMonth.second,
                                onMonthSelected = { month, year -> viewModel.setMonth(month, year) }
                            )
                        }
                    }

                    // Resumen general
                    item {
                        GeneralStatsCard(stats = state.generalStats)
                    }

                    // Estadísticas por lista
                    if (state.listStats.isNotEmpty()) {
                        item {
                            SectionTitle(
                                text = "Estadísticas por lista",
                                icon = Icons.AutoMirrored.Filled.List
                            )
                        }
                        items(state.listStats) { listStats ->
                            ListStatsCard(
                                stats = listStats,
                                onClick = { onViewList(listStats.listId) }
                            )
                        }
                    }

                    // Categorías
                    if (state.categoryStats.isNotEmpty()) {
                        item {
                            SectionTitle(
                                text = "Distribución por categorías",
                                icon = Icons.Default.Category
                            )
                        }
                        item {
                            CategoryStatsRow(
                                categoryStats = state.categoryStats,
                                totalItems = state.generalStats.totalItems
                            )
                        }
                    }

                    // Items más frecuentes
                    if (state.generalStats.frequentItems.isNotEmpty()) {
                        item {
                            SectionTitle(
                                text = "Productos más frecuentes",
                                icon = Icons.AutoMirrored.Filled.TrendingUp
                            )
                        }
                        item {
                            FrequentItemsCard(
                                items = state.generalStats.frequentItems
                            )
                        }
                    }

                    // Espacio final
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PeriodFilterSelector(
    selectedPeriod: PeriodFilter,
    onPeriodSelected: (PeriodFilter) -> Unit,
    periodInfo: PeriodInfo
) {
    val periods = listOf(
        PeriodFilter.TODAY to "Hoy",
        PeriodFilter.THIS_WEEK to "Semana",
        PeriodFilter.LAST_7_DAYS to "7 días",
        PeriodFilter.THIS_MONTH to "Mes actual",
        PeriodFilter.SPECIFIC_MONTH to "Elegir mes",
        PeriodFilter.LAST_30_DAYS to "30 días",
        PeriodFilter.THIS_YEAR to "Año",
        PeriodFilter.ALL to "Todo"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Información del período
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Período: ${periodInfo.label}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = formatDateRange(periodInfo.startDate, periodInfo.endDate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }

            // Chips de selección
            FilterChipsRow(
                selectedPeriod = selectedPeriod,
                onPeriodSelected = onPeriodSelected,
                periods = periods
            )
        }
    }
}

@Composable
private fun FilterChipsRow(
    selectedPeriod: PeriodFilter,
    onPeriodSelected: (PeriodFilter) -> Unit,
    periods: List<Pair<PeriodFilter, String>>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        periods.forEach { (period, label) ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = { Text(label) }
            )
        }
    }
}

private fun formatDateRange(start: Long, end: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return if (start == 0L) {
        "Histórico"
    } else {
        "${sdf.format(Date(start))} - ${sdf.format(Date(end))}"
    }
}

@Composable
private fun GeneralStatsCard(stats: GeneralStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Resumen General",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // Primera fila: listas y items
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "Listas",
                    value = stats.totalLists.toString()
                )
                StatColumn(
                    icon = Icons.Default.ShoppingCart,
                    label = "Total Items",
                    value = stats.totalItems.toString()
                )
                StatColumn(
                    icon = Icons.Default.CheckCircle,
                    label = "Comprados",
                    value = stats.purchasedItems.toString()
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
            )

            // Segunda fila: precios
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatColumn(
                    icon = Icons.Default.Euro,
                    label = "Estimado Total",
                    value = String.format("%.2f€", stats.estimatedTotal)
                )
                if (stats.savingsAmount > 0) {
                    StatColumn(
                        icon = Icons.Default.Savings,
                        label = "Ahorros",
                        value = String.format("%.2f€", stats.savingsAmount),
                        tint = Color(0xFF4CAF50)
                    )
                }
            }

            // Barra de progreso
            if (stats.totalItems > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Progreso de compras",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    LinearProgressIndicator(
                        progress = { stats.purchasedItems.toFloat() / stats.totalItems.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${stats.purchasedItems} de ${stats.totalItems} productos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun StatColumn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun ListStatsCard(
    stats: ListStats,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stats.listName.ifBlank { "(Sin nombre)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${stats.totalItems} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${stats.pendingItems} pendientes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = String.format("%.2f€", stats.estimatedTotal),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryStatsRow(
    categoryStats: List<CategoryStat>,
    totalItems: Int
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            categoryStats.forEach { category ->
                CategoryBarItem(
                    category = category.category,
                    count = category.itemCount,
                    total = totalItems
                )
            }
        }
    }
}

@Composable
private fun CategoryBarItem(
    category: String,
    count: Int,
    total: Int
) {
    val percentage = if (total > 0) count.toFloat() / total.toFloat() else 0f

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = category,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "$count (${(percentage * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier.fillMaxWidth(),
            color = when (category) {
                "Frutas", "Verduras" -> Color(0xFF4CAF50)
                "Carnes", "Pescados" -> Color(0xFFF44336)
                "Lácteos" -> Color(0xFF2196F3)
                "Panadería" -> Color(0xFFFF9800)
                "Bebidas" -> Color(0xFF9C27B0)
                else -> MaterialTheme.colorScheme.primary
            }
        )
    }
}

@Composable
private fun FrequentItemsCard(items: List<FrequentItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items.forEachIndexed { index, item ->
                if (index > 0) HorizontalDivider()
                FrequentItemRow(
                    position = index + 1,
                    name = item.name,
                    count = item.count
                )
            }
        }
    }
}

@Composable
private fun FrequentItemRow(
    position: Int,
    name: String,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Posición con medalla
        Box(
            modifier = Modifier.size(32.dp),
            contentAlignment = Alignment.Center
        ) {
            when (position) {
                1 -> {
                    Text("🥇", style = MaterialTheme.typography.titleLarge)
                }
                2 -> {
                    Text("🥈", style = MaterialTheme.typography.titleLarge)
                }
                3 -> {
                    Text("🥉", style = MaterialTheme.typography.titleLarge)
                }
                else -> {
                    Text(
                        text = position.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "$count",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MonthYearSelector(
    selectedMonth: Int,
    selectedYear: Int,
    onMonthSelected: (month: Int, year: Int) -> Unit
) {
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val years = (currentYear - 5)..(currentYear + 1)
    val months = listOf(
        0 to "Enero", 1 to "Febrero", 2 to "Marzo", 3 to "Abril",
        4 to "Mayo", 5 to "Junio", 6 to "Julio", 7 to "Agosto",
        8 to "Septiembre", 9 to "Octubre", 10 to "Noviembre", 11 to "Diciembre"
    )

    var expandedMonth by remember { mutableStateOf(false) }
    var expandedYear by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Seleccionar mes:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Selectores de mes y año
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selector de mes
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { expandedMonth = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(months[selectedMonth].second)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = expandedMonth,
                        onDismissRequest = { expandedMonth = false }
                    ) {
                        months.forEach { (month, name) ->
                            DropdownMenuItem(
                                text = { Text(name) },
                                onClick = {
                                    onMonthSelected(month, selectedYear)
                                    expandedMonth = false
                                },
                                leadingIcon = if (month == selectedMonth) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }

                // Selector de año
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { expandedYear = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedYear.toString())
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                    DropdownMenu(
                        expanded = expandedYear,
                        onDismissRequest = { expandedYear = false }
                    ) {
                        years.toList().forEach { year ->
                            DropdownMenuItem(
                                text = { Text(year.toString()) },
                                onClick = {
                                    onMonthSelected(selectedMonth, year)
                                    expandedYear = false
                                },
                                leadingIcon = if (year == selectedYear) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
        }
    }
}
