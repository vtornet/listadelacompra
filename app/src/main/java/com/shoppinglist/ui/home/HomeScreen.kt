package com.shoppinglist.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shoppinglist.data.models.ShoppingList
import com.shoppinglist.ui.auth.AuthViewModel
import com.shoppinglist.ui.shoppinglist.ShoppingListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    authViewModel: AuthViewModel,
    shoppingListViewModel: ShoppingListViewModel,
    onOpenList: (String) -> Unit,
    onExitApp: () -> Unit
) {
    val lists by shoppingListViewModel.lists.collectAsState()

    var showOverflow by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<ShoppingList?>(null) }

    BackHandler { onExitApp() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis listas", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menú")
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Cerrar sesión") },
                                onClick = {
                                    showOverflow = false
                                    authViewModel.signOut()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva lista")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                text = "Desplaza a la izquierda para eliminar una lista",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (lists.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aún no tienes listas. Crea una con el botón +")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(lists, key = { it.id }) { l ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    pendingDelete = l
                                    false // pedimos confirmación, no borramos aún
                                } else true
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false, // solo izquierda
                            backgroundContent = {
                                val showBg =
                                    dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
                                            dismissState.targetValue != SwipeToDismissBoxValue.Settled
                                if (showBg) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            content = {
                                Surface(color = MaterialTheme.colorScheme.surface) {
                                    Column {
                                        ListRow(
                                            list = l,
                                            onClick = {
                                                shoppingListViewModel.switchList(l.id)
                                                onOpenList(l.id)
                                            }
                                        )
                                        Divider()
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    // Diálogo: crear lista (usa OutlinedTextField totalmente cualificado y lambda con parámetro nombrado)
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Crear nueva lista") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = newListName,
                    onValueChange = { value -> newListName = value },
                    singleLine = true,
                    placeholder = { Text("Ej. Compra semanal") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newListName.trim()
                    if (name.isNotEmpty()) {
                        shoppingListViewModel.createList(name) { newId ->
                            showCreateDialog = false
                            newListName = ""
                            onOpenList(newId)
                        }
                    }
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancelar") } }
        )
    }

    // Confirmación de borrado
    pendingDelete?.let { toDelete ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar lista") },
            text = { Text("¿Seguro que deseas eliminar «${toDelete.name.ifBlank { toDelete.id }}»? Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    shoppingListViewModel.deleteList(toDelete.id)
                    pendingDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ListRow(list: ShoppingList, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = list.name.ifBlank { "(Sin nombre)" }, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Propietario",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
