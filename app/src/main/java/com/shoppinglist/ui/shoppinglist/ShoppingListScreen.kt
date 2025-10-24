@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.shoppinglist.ui.shoppinglist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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
    var renameDialogItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(renameDialogItem?.id) {
        renameText = renameDialogItem?.name ?: ""
    }

    LaunchedEffect(renameDialogItem, allItems) {
        val activeId = renameDialogItem?.id ?: return@LaunchedEffect
        if (allItems.none { it.id == activeId }) {
            renameDialogItem = null
            renameText = ""
        }
    }

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
                        onRenameClick = {
                            renameDialogItem = row
                            renameText = row.name
                        },
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
                            onRenameClick = {
                                renameDialogItem = row
                                renameText = row.name
                            },
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
            currencySymbol = currencySymbol,
            onDismiss = { priceDialogItem = null },
            onConfirm = { shoppingListViewModel.updatePrice(item, it) },
            onRemove = item.price?.let { { shoppingListViewModel.clearPrice(item) } }
        )
    }

    renameDialogItem?.let { item ->
        RenameItemDialog(
            value = renameText,
            onValueChange = { renameText = it },
            onDismiss = {
                renameDialogItem = null
                renameText = ""
            },
            onConfirm = {
                val trimmed = renameText.trim()
                if (trimmed.isNotEmpty()) {
                    shoppingListViewModel.renameItem(item, trimmed)
                }
                renameDialogItem = null
                renameText = ""
            }
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
