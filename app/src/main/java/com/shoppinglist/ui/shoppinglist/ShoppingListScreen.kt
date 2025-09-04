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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.ui.auth.AuthViewModel
import kotlinx.coroutines.delay
import java.util.Locale

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
                .padding(8.dp)
                .fillMaxSize()
        ) {
            if (showProgress) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }

            // Buscador + switch
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    label = { Text("Buscar en la lista") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ver comprados", style = MaterialTheme.typography.labelSmall)
                    Switch(checked = showPurchased, onCheckedChange = { showPurchased = it })
                }
            }

            Spacer(Modifier.height(8.dp))

            AddItemRow(
                onAddItem = { name -> shoppingListViewModel.addItem(name) },
                onScan = { showScanner = true }
            )

            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxSize()) {
                item { ListHeader("POR COMPRAR", Color(0xFFB00020)) }

                items(toBuy, key = { it.id }) { row ->
                    ShoppingListRow(
                        item = row,
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
                        onDelete = { shoppingListViewModel.deleteItem(row) }
                    )
                }

                if (showPurchased) {
                    item { Spacer(Modifier.height(16.dp)) }
                    item { ListHeader("COMPRADO", Color(0xFF008000)) }

                    items(purchased, key = { it.id }) { row ->
                        ShoppingListRow(
                            item = row,
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
                            onDelete = { shoppingListViewModel.deleteItem(row) }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            label = { Text("Nuevo artículo") },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(
            enabled = text.isNotBlank(),
            onClick = { onAddItem(text.trim()); text = "" }
        ) { Text("Añadir") }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onScan) { Text("Escanear") }
    }
}

@Composable private fun ListHeader(title: String, color: Color) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = color,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
    Divider()
}

@Composable
private fun ShoppingListRow(
    item: ShoppingItem,
    onToggleStatus: () -> Unit,
    onIncQty: () -> Unit,
    onDecQty: () -> Unit,
    onAddImageClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onRemoveImage: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var renameText by rememberSaveable(item.id) { mutableStateOf(item.name) }
    val hasImage = !item.imageUrl.isNullOrBlank()

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
                    enabled = (item.quantity ?: 1) > 1
                ) { Icon(Icons.Filled.Remove, contentDescription = "Restar") }

                Text("x${item.quantity ?: 1}", style = MaterialTheme.typography.bodyMedium)

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
