package com.shoppinglist.ui.shoppinglist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.ui.auth.AuthViewModel
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    authViewModel: AuthViewModel,
    shoppingListViewModel: ShoppingListViewModel = viewModel(),
    onBack: () -> Unit = {}             // botón Volver (←)
) {
    val items by shoppingListViewModel.items.collectAsState()
    val (itemsToBuy, itemsPurchased) = items.partition { it.inShoppingList }

    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "ES")) }
    val purchasedTotal by remember(itemsPurchased) { mutableStateOf(itemsPurchased.sumOf { it.price ?: 0.0 }) }
    val pricedCount by remember(itemsPurchased) { mutableStateOf(itemsPurchased.count { it.price != null }) }

    val isLoading by shoppingListViewModel.loading.collectAsState()
    val errorMsg by shoppingListViewModel.error.collectAsState()
    val duplicate by shoppingListViewModel.duplicate.collectAsState()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val lists by shoppingListViewModel.lists.collectAsState()
    val currentListName by shoppingListViewModel.currentListName.collectAsState()
    var showOverflow by remember { mutableStateOf(false) }
    var showListPicker by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var inviteEmail by rememberSaveable { mutableStateOf("") }

    var itemToUpdateImage by remember { mutableStateOf<ShoppingItem?>(null) }
    var fullImageUrl by rememberSaveable { mutableStateOf<String?>(null) }

    var priceTarget by remember { mutableStateOf<ShoppingItem?>(null) }
    var priceInput by rememberSaveable { mutableStateOf("") }

    var showScanner by remember { mutableStateOf(false) }

    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) { if (isLoading) { delay(400); showProgress = shoppingListViewModel.loading.value } else showProgress = false }
    LaunchedEffect(errorMsg) { errorMsg?.let { snackbar.showSnackbar(it); shoppingListViewModel.clearError() } }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let { u -> itemToUpdateImage?.let { item -> shoppingListViewModel.addImageToItem(item, u) } }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentListName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Volver") } },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) { Icon(Icons.Filled.MoreVert, "Menú") }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(text = { Text("Cambiar lista") }, onClick = { showOverflow = false; showListPicker = true })
                            DropdownMenuItem(text = { Text("Compartir") }, onClick = { showOverflow = false; showInviteDialog = true })
                            Divider()
                            DropdownMenuItem(text = { Text("Cerrar sesión") }, onClick = { showOverflow = false; authViewModel.signOut() })
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(8.dp)
                .fillMaxSize()
        ) {
            if (showProgress) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            AddItemRow(
                onAddItem = { name -> shoppingListViewModel.addItem(name) },
                onScan = { showScanner = true }
            )
            Spacer(Modifier.height(8.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                item { ListHeader("POR COMPRAR", Color(0xFFB00020)) }

                items(itemsToBuy, key = { it.id }) { item ->
                    ShoppingListRow(
                        item = item,
                        onToggleStatus = { shoppingListViewModel.toggleItemStatus(item) },
                        onAddImageClick = {
                            itemToUpdateImage = item
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onImageClick = { url -> fullImageUrl = url },
                        onEditPrice = {
                            priceTarget = item
                            priceInput = item.price?.let { String.format(Locale.US, "%.2f", it) } ?: ""
                        },
                        onRemoveImage = { shoppingListViewModel.removeImageFromItem(item) },
                        onRename = { newName -> shoppingListViewModel.renameItem(item, newName) },
                        onDelete = { shoppingListViewModel.deleteItem(item) }
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }

                item { ListHeader("COMPRADO", Color(0xFF008000)) }

                item {
                    PurchasedSummaryRow(
                        totalFormatted = currency.format(purchasedTotal),
                        pricedCount = pricedCount,
                        totalCount = itemsPurchased.size
                    )
                }

                items(itemsPurchased, key = { it.id }) { item ->
                    ShoppingListRow(
                        item = item,
                        onToggleStatus = { shoppingListViewModel.toggleItemStatus(item) },
                        onAddImageClick = {
                            itemToUpdateImage = item
                            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onImageClick = { url -> fullImageUrl = url },
                        onEditPrice = {
                            priceTarget = item
                            priceInput = item.price?.let { String.format(Locale.US, "%.2f", it) } ?: ""
                        },
                        onRemoveImage = { shoppingListViewModel.removeImageFromItem(item) },
                        onRename = { newName -> shoppingListViewModel.renameItem(item, newName) },
                        onDelete = { shoppingListViewModel.deleteItem(item) }
                    )
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
    if (showInviteDialog) {
        AlertDialog(
            onDismissRequest = { showInviteDialog = false },
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
                    showInviteDialog = false
                }) { Text("Invitar") }
            },
            dismissButton = { TextButton(onClick = { showInviteDialog = false }) { Text("Cancelar") } }
        )
    }

    // Escáner (overlay)
    if (showScanner) {
        BarcodeScannerOverlay(
            onResult = { code -> shoppingListViewModel.addItemFromBarcode(code) },
            onClose = { showScanner = false }
        )
    }

    // Imagen completa
    fullImageUrl?.let { url ->
        FullscreenImageDialog(imageUrl = url, onDismiss = { fullImageUrl = null })
    }

    // Edición de precio
    priceTarget?.let { target ->
        PriceDialog(
            title = "Precio para \"${target.name}\"",
            priceText = priceInput,
            onChangeText = { priceInput = it },
            onDismiss = { priceTarget = null },
            onConfirm = {
                val parsed = priceInput.trim().replace(',', '.').toDoubleOrNull()
                if (parsed != null && parsed >= 0.0) {
                    shoppingListViewModel.updatePrice(target, parsed)
                    priceTarget = null
                } else {
                    scope.launch { snackbar.showSnackbar("Introduce un precio válido (ej. 2.35)") } // ✅
                }
            }
        )
    }


    // Duplicado (insertar o cancelar)
    duplicate?.let { dup ->
        AlertDialog(
            onDismissRequest = { shoppingListViewModel.dismissDuplicate() },
            title = { Text("Producto duplicado") },
            text = { Text("«${dup.name}» ya existe en la lista. ¿Quieres añadirlo igualmente?") },
            confirmButton = { TextButton(onClick = { shoppingListViewModel.confirmDuplicate() }) { Text("Insertar") } },
            dismissButton = { TextButton(onClick = { shoppingListViewModel.dismissDuplicate() }) { Text("Cancelar") } }
        )
    }
}
@Composable
private fun AddItemRow(
    onAddItem: (String) -> Unit,
    onScan: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Nuevo artículo") },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(onClick = {
            val t = text.trim()
            if (t.isNotEmpty()) { onAddItem(t); text = "" }
        }) { Text("Añadir") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onScan) { Text("Escanear") }
    }
}

@Composable
private fun ListHeader(title: String, color: Color) {
    Text(text = title, fontSize = 14.sp, color = color, modifier = Modifier.padding(vertical = 8.dp))
    Divider()
}

@Composable
private fun PurchasedSummaryRow(
    totalFormatted: String,
    pricedCount: Int,
    totalCount: Int
) {
    Surface(
        tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Total", style = TextStyle(fontSize = 14.sp), modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = totalFormatted,
                    style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "Precios en $pricedCount de $totalCount",
                    style = TextStyle(fontSize = 11.sp, color = Color.Gray)
                )
            }
        }
    }
}
@Composable
private fun ShoppingListRow(
    item: ShoppingItem,
    onToggleStatus: () -> Unit,
    onAddImageClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onEditPrice: () -> Unit,
    onRemoveImage: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "ES")) }
    var menuOpen by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }
    var askRename by remember { mutableStateOf(false) }
    var renameText by rememberSaveable { mutableStateOf(item.name) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = !item.inShoppingList, onCheckedChange = { onToggleStatus() })

        if (!item.imageUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = item.imageUrl, contentDescription = item.name,
                loading = {
                    Box(
                        Modifier.size(48.dp).padding(start = 8.dp).clip(CircleShape)
                            .background(Color.LightGray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) }
                },
                modifier = Modifier
                    .size(48.dp)
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .clickable { onImageClick(item.imageUrl!!) },
                contentScale = ContentScale.Crop
            )
        } else {
            Spacer(Modifier.width(56.dp)) // hueco de la miniatura
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name)

            val hasCurrent = item.price != null
            val hasPrev = item.previousPrice != null

            if (hasCurrent || hasPrev) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (hasPrev) {
                        Text(
                            text = currency.format(item.previousPrice),
                            style = TextStyle(textDecoration = TextDecoration.LineThrough, fontSize = 12.sp),
                            color = Color.Gray
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (hasCurrent) {
                        Text(
                            text = currency.format(item.price),
                            style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
                // % debajo del precio (pequeño) — evita que se amontone en vertical
                if (hasPrev && hasCurrent && item.previousPrice != 0.0) {
                    val diff = ((item.price!! - item.previousPrice!!) / item.previousPrice!!) * 100.0
                    val sign = if (diff >= 0) "+" else ""
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "$sign${"%.1f".format(diff)}%",
                        style = TextStyle(fontSize = 11.sp),
                        color = if (diff <= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }
            } else {
                Text(text = "— sin precio —", fontSize = 12.sp, color = Color.Gray)
            }
        }

        IconButton(onClick = onAddImageClick) { Icon(Icons.Filled.AddAPhoto, "Añadir foto") }
        IconButton(onClick = onEditPrice) { Text("€", fontSize = 18.sp) }

        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Más opciones") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Renombrar") }, onClick = { menuOpen = false; renameText = item.name; askRename = true })
                if (!item.imageUrl.isNullOrBlank()) {
                    DropdownMenuItem(text = { Text("Eliminar foto") }, onClick = { menuOpen = false; onRemoveImage() })
                }
                DropdownMenuItem(text = { Text("Eliminar artículo") }, onClick = { menuOpen = false; askDelete = true })
            }
        }
    }

    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Eliminar \"${item.name}\"") },
            text = { Text("Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { askDelete = false; onDelete() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { askDelete = false }) { Text("Cancelar") } }
        )
    }

    if (askRename) {
        AlertDialog(
            onDismissRequest = { askRename = false },
            title = { Text("Renombrar artículo") },
            text = { OutlinedTextField(value = renameText, onValueChange = { renameText = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val t = renameText.trim()
                    if (t.isNotEmpty() && t != item.name) onRename(t)
                    askRename = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { askRename = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PriceDialog(
    title: String,
    priceText: String,
    onChangeText: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text("Introduce el precio (usa punto o coma decimal):")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = priceText, onValueChange = onChangeText, singleLine = true, placeholder = { Text("Ej.: 2.35") })
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun FullscreenImageDialog(imageUrl: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(tonalElevation = 0.dp, color = Color.Black) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                var scale by remember { mutableStateOf(1f) }
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }
                val transformState = rememberTransformableState { zoom: Float, pan: Offset, _: Float ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    val canPan = newScale > 1f
                    scale = newScale
                    if (canPan) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
                }
                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "Imagen",
                    loading = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } },
                    error = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No se pudo cargar la imagen", color = Color.White) } },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY }
                        .transformable(transformState),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp)
                        .background(color = Color.Black.copy(alpha = 0.4f), shape = CircleShape)
                ) { Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color.White) }
            }
        }
    }
}
