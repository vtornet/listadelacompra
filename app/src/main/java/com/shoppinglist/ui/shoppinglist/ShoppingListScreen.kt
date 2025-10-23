@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.shoppinglist.ui.shoppinglist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.ui.auth.AuthViewModel
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

@Composable
fun ShoppingListScreen(
    authViewModel: AuthViewModel,
    shoppingListViewModel: ShoppingListViewModel = viewModel(),
    onBack: () -> Unit = {}
) {
    // Estado del VM
    val allItems by shoppingListViewModel.items.collectAsState()
    val lists by shoppingListViewModel.lists.collectAsState()
    val currentListName by shoppingListViewModel.currentListName.collectAsState()
    val isLoading by shoppingListViewModel.loading.collectAsState()
    val errorMsg by shoppingListViewModel.error.collectAsState()
    val duplicate by shoppingListViewModel.duplicate.collectAsState()

    // Estado UI
    val snackbar = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var showListPicker by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var inviteEmail by rememberSaveable { mutableStateOf("") }

    var query by rememberSaveable { mutableStateOf("") }
    var showPurchased by rememberSaveable { mutableStateOf(true) }

    var showScanner by remember { mutableStateOf(false) }
    var confirmMarkAll by remember { mutableStateOf(false) }

    var itemForImage by remember { mutableStateOf<ShoppingItem?>(null) }
    var fullImageUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var priceDialogItem by remember { mutableStateOf<ShoppingItem?>(null) }

    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) { delay(300); showProgress = shoppingListViewModel.loading.value }
        else showProgress = false
    }
    LaunchedEffect(errorMsg) {
        errorMsg?.let { snackbar.showSnackbar(it); shoppingListViewModel.clearError() }
    }

    // Image picker
    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            itemForImage?.let { item ->
                if (uri != null) shoppingListViewModel.addImageToItem(item, uri)
            }
        }
    )

    // Filtro + orden alfabético
    val filteredSorted = remember(allItems, query) {
        val q = query.trim().lowercase(Locale.getDefault())
        val base = if (q.isEmpty()) allItems
        else allItems.filter { it.name.lowercase(Locale.getDefault()).contains(q) }
        base.sortedBy { it.name.lowercase(Locale.getDefault()) }
    }
    val (toBuy, purchased) = remember(filteredSorted) {
        filteredSorted.partition { it.inShoppingList }
    }

    val currencyFormatter = remember { NumberFormat.getCurrencyInstance() }
    val currencySymbol = remember(currencyFormatter) {
        runCatching { currencyFormatter.currency?.getSymbol(Locale.getDefault()) }
            .getOrNull() ?: "€"
    }
    val totalPurchasedAmount = remember(purchased) {
        purchased.sumOf { (it.price ?: 0.0) * max(1, it.quantity) }
    }
    val pendingCount = remember(toBuy) { toBuy.sumOf { max(1, it.quantity) } }
    val purchasedCount = remember(purchased) { purchased.sumOf { max(1, it.quantity) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentListName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menú") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Cambiar lista") },
                                onClick = { menuOpen = false; showListPicker = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Compartir") },
                                onClick = { menuOpen = false; showInvite = true }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Marcar todo como comprado") },
                                onClick = { menuOpen = false; confirmMarkAll = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Cerrar sesión") },
                                onClick = { menuOpen = false; authViewModel.signOut() }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxSize()
        ) {
            if (showProgress) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            // Buscador + switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShoppingListSearchBar(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "Buscar en la lista"
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Ver comprados", style = MaterialTheme.typography.labelSmall)
                    Switch(checked = showPurchased, onCheckedChange = { showPurchased = it })
                }
            }

            Spacer(Modifier.height(4.dp))

            AddItemRow(
                onAddItem = { name -> shoppingListViewModel.addItem(name) },
                onScan = { showScanner = true }
            )

            Spacer(Modifier.height(4.dp))

            ShoppingListSummaryCard(
                toBuyCount = pendingCount,
                purchasedCount = purchasedCount,
                totalPurchased = totalPurchasedAmount,
                currencyFormatter = currencyFormatter
            )

            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item { ShoppingListSectionHeader("POR COMPRAR", Color(0xFFB00020)) }

                items(toBuy, key = { it.id }) { row ->
                    ShoppingListRow(
                        item = row,
                        currencyFormatter = currencyFormatter,
                        onToggleStatus = { shoppingListViewModel.toggleItemStatus(row) },
                        onIncQty = { shoppingListViewModel.incrementQuantity(row) },
                        onDecQty = { shoppingListViewModel.decrementQuantity(row) },
                        onAddImageClick = {
                            itemForImage = row
                            pickImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onImageClick = { url -> fullImageUrl = url },
                        onRemoveImage = { shoppingListViewModel.removeImageFromItem(row) },
                        onRename = { newName -> shoppingListViewModel.renameItem(row, newName) },
                        onDelete = { shoppingListViewModel.deleteItem(row) },
                        onPriceClick = { priceDialogItem = row },
                        onClearPrice = { shoppingListViewModel.clearPrice(row) }
                    )
                }

                if (showPurchased) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item { ShoppingListSectionHeader("COMPRADO", Color(0xFF008000)) }

                    items(purchased, key = { it.id }) { row ->
                        ShoppingListRow(
                            item = row,
                            currencyFormatter = currencyFormatter,
                            onToggleStatus = { shoppingListViewModel.toggleItemStatus(row) },
                            onIncQty = { shoppingListViewModel.incrementQuantity(row) },
                            onDecQty = { shoppingListViewModel.decrementQuantity(row) },
                            onAddImageClick = {
                                itemForImage = row
                                pickImageLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onImageClick = { url -> fullImageUrl = url },
                            onRemoveImage = { shoppingListViewModel.removeImageFromItem(row) },
                            onRename = { newName -> shoppingListViewModel.renameItem(row, newName) },
                            onDelete = { shoppingListViewModel.deleteItem(row) },
                            onPriceClick = { priceDialogItem = row },
                            onClearPrice = { shoppingListViewModel.clearPrice(row) }
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // Cambiar lista
    if (showListPicker) {
        AlertDialog(
            onDismissRequest = { showListPicker = false },
            title = { Text("Cambiar lista") },
            text = {
                if (lists.isEmpty()) Text("No tienes listas aún")
                else Column {
                    lists.forEach { l ->
                        TextButton(
                            onClick = { shoppingListViewModel.switchList(l.id); showListPicker = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(l.name.ifBlank { l.id }) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showListPicker = false }) { Text("Cerrar") } }
        )
    }

    // Compartir
    if (showInvite) {
        AlertDialog(
            onDismissRequest = { showInvite = false },
            title = { Text("Compartir lista") },
            text = {
                Column {
                    Text("Introduce el email de la persona a invitar.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inviteEmail,
                        onValueChange = { inviteEmail = it },
                        singleLine = true,
                        placeholder = { Text("usuario@dominio.com") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val e = inviteEmail.trim()
                    if (e.isNotEmpty()) shoppingListViewModel.inviteMember(e)
                    inviteEmail = ""
                    showInvite = false
                }) { Text("Invitar") }
            },
            dismissButton = { TextButton(onClick = { showInvite = false }) { Text("Cancelar") } }
        )
    }

    // Escáner (entrada manual, sin dependencias de cámara)
    if (showScanner) {
        BarcodeScannerDialog(
            onResult = { code -> shoppingListViewModel.addItemFromBarcode(code) },
            onClose = { showScanner = false }
        )
    }

    // Imagen completa
    fullImageUrl?.let { url ->
        FullscreenImageDialog(imageUrl = url, onDismiss = { fullImageUrl = null })
    }

    priceDialogItem?.let { item ->
        PriceDialog(
            itemName = item.name,
            initialPrice = item.price,
            currencyFormatter = currencyFormatter,
            currencySymbol = currencySymbol,
            onDismiss = { priceDialogItem = null },
            onConfirm = { shoppingListViewModel.updatePrice(item, it) },
            onRemove = item.price?.let { { shoppingListViewModel.clearPrice(item) } }
        )
    }

    // Confirmación "marcar todo"
    if (confirmMarkAll) {
        AlertDialog(
            onDismissRequest = { confirmMarkAll = false },
            title = { Text("Marcar todo como comprado") },
            text = { Text("¿Quieres mover todos los artículos pendientes a «Comprado»?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmMarkAll = false
                    shoppingListViewModel.markAllToBuyAsPurchased()
                }) { Text("Sí, marcar todo") }
            },
            dismissButton = { TextButton(onClick = { confirmMarkAll = false }) { Text("Cancelar") } }
        )
    }
}
@Composable
private fun AddItemRow(
    onAddItem: (String) -> Unit,
    onScan: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ShoppingListInputField(
            value = text,
            onValueChange = { text = it },
            placeholder = "Nuevo artículo",
            modifier = Modifier.weight(1f)
        )
        FilledTonalButton(
            enabled = text.isNotBlank(),
            onClick = {
                onAddItem(text.trim())
                text = ""
            },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) { Text("Añadir") }
        FilledTonalButton(
            onClick = onScan,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
        ) { Text("Escanear") }
    }
}

@Composable
private fun ShoppingListSummaryCard(
    toBuyCount: Int,
    purchasedCount: Int,
    totalPurchased: Double,
    currencyFormatter: NumberFormat
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShoppingListSummaryStat(
                    title = "Pendientes",
                    value = "$toBuyCount uds",
                    modifier = Modifier.weight(1f)
                )
                ShoppingListSummaryStat(
                    title = "Comprados",
                    value = "$purchasedCount uds",
                    modifier = Modifier.weight(1f)
                )
                ShoppingListSummaryStat(
                    title = "Total",
                    value = "${toBuyCount + purchasedCount} uds",
                    modifier = Modifier.weight(1f)
                )
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Importe comprado",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = currencyFormatter.format(totalPurchased),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun ShoppingListSummaryStat(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ShoppingListSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String
) {
    Surface(
        modifier = modifier.heightIn(min = 0.dp),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotEmpty()) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = { onValueChange("") },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Limpiar búsqueda")
                }
            }
        }
    }
}

@Composable
private fun ShoppingListInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String
) {
    Surface(
        modifier = modifier.heightIn(min = 0.dp),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable private fun ShoppingListSectionHeader(title: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ShoppingListRow(
    item: ShoppingItem,
    currencyFormatter: NumberFormat,
    onToggleStatus: () -> Unit,
    onIncQty: () -> Unit,
    onDecQty: () -> Unit,
    onAddImageClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onRemoveImage: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onPriceClick: () -> Unit,
    onClearPrice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by rememberSaveable(item.id) { mutableStateOf(item.name) }
    val hasImage = !item.imageUrl.isNullOrBlank()
    val hasPrice = item.price != null
    val quantity = max(1, item.quantity)
    val totalPrice = item.price?.times(quantity)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = !item.inShoppingList,
            onCheckedChange = { onToggleStatus() }
        )

        SubcomposeAsyncImage(
            model = item.imageUrl,
            contentDescription = item.name,
            loading = { CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) },
            error = {},
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .padding(start = 8.dp, end = 12.dp)
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    if (hasImage) onImageClick(item.imageUrl!!) else onAddImageClick()
                }
        )

        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onDecQty,
                    enabled = quantity > 1
                ) { Icon(Icons.Filled.Remove, contentDescription = "Restar") }

                Text("x$quantity", style = MaterialTheme.typography.bodyMedium)

                IconButton(onClick = onIncQty) {
                    Icon(Icons.Filled.Add, contentDescription = "Sumar")
                }

                Spacer(Modifier.weight(1f))

                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Más opciones")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (hasImage) "Ver foto" else "Añadir foto") },
                            leadingIcon = { Icon(Icons.Filled.AddAPhoto, null) },
                            onClick = {
                                menuOpen = false
                                if (hasImage) onImageClick(item.imageUrl!!) else onAddImageClick()
                            }
                        )
                        if (hasImage) {
                            DropdownMenuItem(
                                text = { Text("Quitar foto") },
                                onClick = { menuOpen = false; onRemoveImage() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (hasPrice) "Actualizar precio" else "Añadir precio") },
                            leadingIcon = { Icon(Icons.Filled.AttachMoney, null) },
                            onClick = { menuOpen = false; onPriceClick() }
                        )
                        if (hasPrice) {
                            DropdownMenuItem(
                                text = { Text("Quitar precio") },
                                onClick = { menuOpen = false; onClearPrice() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Renombrar") },
                            leadingIcon = { Icon(Icons.Filled.Edit, null) },
                            onClick = { menuOpen = false; showRename = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Eliminar") },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                            onClick = { menuOpen = false; onDelete() }
                        )
                    }
                }
            }

            if (hasPrice) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append("Precio: ")
                        append(currencyFormatter.format(item.price!!))
                        totalPrice?.takeIf { quantity > 1 }?.let {
                            append(" · Total ")
                            append(currencyFormatter.format(it))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                item.previousPrice?.takeIf { it != item.price }?.let { previous ->
                    Text(
                        text = "Anterior: ${currencyFormatter.format(previous)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }

    if (showRename) {
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Renombrar artículo") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val txt = renameText.trim()
                    if (txt.isNotEmpty()) onRename(txt)
                    showRename = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { showRename = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PriceDialog(
    itemName: String,
    initialPrice: Double?,
    currencyFormatter: NumberFormat,
    currencySymbol: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
    onRemove: (() -> Unit)?
) {
    var text by rememberSaveable(initialPrice) {
        mutableStateOf(
            initialPrice?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: ""
        )
    }
    var error by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    fun handleConfirm() {
        val sanitized = text.replace(',', '.').trim()
        val price = sanitized.toDoubleOrNull()
        if (price != null) {
            onConfirm(price)
            onDismiss()
        } else {
            error = "Introduce un número válido"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Precio del producto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Introduce el precio unitario para \"$itemName\".",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        error = null
                    },
                    singleLine = true,
                    label = { Text("Precio") },
                    prefix = { Text(currencySymbol) },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = KeyboardType.Decimal
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            handleConfirm()
                        }
                    ),
                    isError = error != null,
                    supportingText = error?.let { err ->
                        { Text(err, color = MaterialTheme.colorScheme.error) }
                    }
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { handleConfirm() }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onRemove?.let {
                    TextButton(onClick = {
                        it()
                        onDismiss()
                    }) { Text("Quitar precio") }
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

@Composable
private fun FullscreenImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            SubcomposeAsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
            }
        }
    }
}

@Composable
private fun BarcodeScannerDialog(
    onResult: (String) -> Unit,
    onClose: () -> Unit
) {
    var manual by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = Color.Black.copy(alpha = 0.88f)) {
            Box(Modifier.fillMaxSize()) {

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Escanear código de barras",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manual,
                        onValueChange = { manual = it },
                        singleLine = true,
                        label = { Text("Pegar/introducir código") },
                        modifier = Modifier.fillMaxWidth(0.9f)
                    )

                    Spacer(Modifier.height(12.dp))

                    Row {
                        TextButton(onClick = onClose) { Text("Cancelar") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            enabled = manual.isNotBlank(),
                            onClick = {
                                onResult(manual.trim())
                                onClose()
                            }
                        ) { Text("Aceptar") }
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
        }
    }
}
