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
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.ui.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    authViewModel: AuthViewModel,
    shoppingListViewModel: ShoppingListViewModel = viewModel()
) {
    val items by shoppingListViewModel.items.collectAsState()
    val (itemsToBuy, itemsPurchased) = items.partition { it.inShoppingList }

    // Ítem al que se le añadirá la imagen
    var itemToUpdateImage by remember { mutableStateOf<ShoppingItem?>(null) }
    // URL a visualizar a pantalla completa
    var fullImageUrl by rememberSaveable { mutableStateOf<String?>(null) }

    // Previews locales inmediatas (URI del dispositivo) -> clave: item.id
    val localPreviews = remember { mutableStateMapOf<String, String>() }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let { picked ->
                itemToUpdateImage?.let { item ->
                    // 1) Preview inmediato en UI
                    localPreviews[item.id] = picked.toString()
                    // 2) Subida + guardado en repositorio/BD
                    shoppingListViewModel.addImageToItem(item, picked)
                }
            }
        }
    )

    // Si el item ya trae imageUrl desde BD, retiramos el preview local
    LaunchedEffect(items) {
        items.forEach { item ->
            if (!item.imageUrl.isNullOrBlank()) {
                localPreviews.remove(item.id)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mi Lista de la Compra") },
                actions = {
                    TextButton(onClick = { authViewModel.signOut() }) {
                        Text("Cerrar Sesión")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(8.dp)
                .fillMaxSize()
        ) {
            AddItemInput(
                onAddItem = { name -> shoppingListViewModel.addItem(name) }
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // POR COMPRAR
                item { ListHeader(title = "POR COMPRAR", color = Color(0xFFB00020)) }
                items(itemsToBuy, key = { it.id }) { item: ShoppingItem ->
                    val previewOrRemote = localPreviews[item.id] ?: item.imageUrl
                    ShoppingListItem(
                        item = item,
                        previewUrl = previewOrRemote,
                        onToggleStatus = { shoppingListViewModel.toggleItemStatus(item) },
                        onAddImageClick = {
                            itemToUpdateImage = item
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onImageClick = { imageUrl -> fullImageUrl = imageUrl }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // COMPRADO
                item { ListHeader(title = "COMPRADO", color = Color(0xFF008000)) }
                items(itemsPurchased, key = { it.id }) { item: ShoppingItem ->
                    val previewOrRemote = localPreviews[item.id] ?: item.imageUrl
                    ShoppingListItem(
                        item = item,
                        previewUrl = previewOrRemote,
                        onToggleStatus = { shoppingListViewModel.toggleItemStatus(item) },
                        onAddImageClick = {
                            itemToUpdateImage = item
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onImageClick = { imageUrl -> fullImageUrl = imageUrl }
                    )
                }
            }
        }
    }

    // Diálogo de imagen a pantalla completa
    fullImageUrl?.let { url ->
        FullscreenImageDialog(
            imageUrl = url,
            onDismiss = { fullImageUrl = null }
        )
    }
}

@Composable
private fun AddItemInput(onAddItem: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Nuevo artículo") },
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = {
                val trimmed = text.trim()
                if (trimmed.isNotEmpty()) {
                    onAddItem(trimmed)
                    text = ""
                }
            }
        ) { Text("Añadir") }
    }
}

@Composable
private fun ListHeader(title: String, color: Color) {
    Text(
        text = title,
        fontSize = 14.sp,
        color = color,
        modifier = Modifier.padding(vertical = 8.dp)
    )
    Divider()
}

@Composable
private fun ShoppingListItem(
    item: ShoppingItem,
    previewUrl: String?,
    onToggleStatus: () -> Unit,
    onAddImageClick: () -> Unit,
    onImageClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = !item.inShoppingList,
            onCheckedChange = { onToggleStatus() }
        )

        // Previsualización redonda (usa preview local si existe; si no, la remota)
        if (!previewUrl.isNullOrBlank() && !previewUrl.startsWith("gs://")) {
            SubcomposeAsyncImage(
                model = previewUrl,
                contentDescription = item.name,
                loading = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                },
                error = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(start = 8.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray.copy(alpha = 0.4f)),
                    )
                },
                modifier = Modifier
                    .size(48.dp)
                    .padding(start = 8.dp)
                    .clip(CircleShape)
                    .clickable { onImageClick(previewUrl) },
                contentScale = ContentScale.Crop
            )
        } else {
            // Reservamos hueco para que la fila no "salte"
            Spacer(
                modifier = Modifier
                    .width(48.dp)
                    .padding(start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = item.name,
            modifier = Modifier.weight(1f)
        )

        IconButton(onClick = onAddImageClick) {
            Icon(imageVector = Icons.Default.AddAPhoto, contentDescription = "Añadir foto")
        }
    }
}

/** Diálogo a pantalla completa con zoom y arrastre */
@Composable
private fun FullscreenImageDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(tonalElevation = 0.dp, color = Color.Black) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                var scale by remember { mutableStateOf(1f) }
                var offsetX by remember { mutableStateOf(0f) }
                var offsetY by remember { mutableStateOf(0f) }

                val transformState = rememberTransformableState { zoomChange: Float, panChange: Offset, _: Float ->
                    val newScale = (scale * zoomChange).coerceIn(1f, 5f)
                    val canPan = newScale > 1f
                    scale = newScale
                    if (canPan) {
                        offsetX += panChange.x
                        offsetY += panChange.y
                    } else {
                        offsetX = 0f
                        offsetY = 0f
                    }
                }

                SubcomposeAsyncImage(
                    model = imageUrl,
                    contentDescription = "Imagen",
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    },
                    error = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(text = "No se pudo cargar la imagen", color = Color.White)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
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
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                }
            }
        }
    }
}
