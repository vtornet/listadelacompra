@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.shoppinglist.ui.shoppinglist

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import coil.compose.SubcomposeAsyncImage
import com.shoppinglist.data.models.ShoppingItem
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

@Composable
internal fun AddItemRow(
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) { Text("Añadir") }
        FilledTonalButton(
            onClick = onScan,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) { Text("Escanear") }
    }
}

@Composable
internal fun ShoppingListSummaryCard(
    toBuyCount: Int,
    purchasedCount: Int,
    totalPurchased: Double,
    currencyFormatter: NumberFormat
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
internal fun ShoppingListSummaryStat(title: String, value: String, modifier: Modifier = Modifier) {
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
internal fun ShoppingListSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String
) {
    Surface(
        modifier = modifier
            .heightIn(min = 0.dp)
            .clip(RoundedCornerShape(12.dp)),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 34.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
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
internal fun ShoppingListInputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String
) {
    Surface(
        modifier = modifier
            .heightIn(min = 0.dp)
            .clip(RoundedCornerShape(12.dp)),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = 34.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp),
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

@Composable
internal fun ShoppingListSectionHeader(title: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
internal fun ShoppingListRow(
    item: ShoppingItem,
    currencyFormatter: NumberFormat,
    onToggleStatus: () -> Unit,
    onIncQty: () -> Unit,
    onDecQty: () -> Unit,
    onAddImageClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onRemoveImage: () -> Unit,
    onRenameClick: () -> Unit,
    onDelete: () -> Unit,
    onPriceClick: () -> Unit,
    onClearPrice: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }
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
        androidx.compose.material3.Checkbox(
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
                            onClick = { menuOpen = false; onRenameClick() }
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
}

@Composable
internal fun PriceDialog(
    itemName: String,
    initialPrice: Double?,
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
internal fun RenameItemDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renombrar artículo") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
internal fun FullscreenImageDialog(
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
internal fun BarcodeScannerDialog(
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
