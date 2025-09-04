package com.shoppinglist.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shoppinglist.data.models.ShoppingList
import com.shoppinglist.ui.auth.AuthViewModel
import com.shoppinglist.ui.shoppinglist.ShoppingListViewModel
import java.text.Normalizer
import java.util.Locale

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
    var newListName by rememberSaveable { mutableStateOf("") }

    // Renombrar / borrar
    var pendingDelete by remember { mutableStateOf<ShoppingList?>(null) }
    var pendingRename by remember { mutableStateOf<ShoppingList?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    // NUEVO: buscador de listas
    var query by rememberSaveable { mutableStateOf("") }

    BackHandler { onExitApp() }

    fun norm(s: String): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.getDefault())

    val filteredSorted = remember(lists, query) {
        val q = norm(query)
        lists
            .filter { q.isBlank() || norm(it.name).contains(q) }
            .sortedBy { norm(it.name) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis listas", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menú")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Cerrar sesión") },
                                onClick = { showOverflow = false; authViewModel.signOut() }
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
        Column(Modifier.fillMaxSize().padding(padding)) {

            // Hint + Buscador
            Text(
                text = "Desplaza a la izquierda para eliminar una lista",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Buscar listas") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))

            if (filteredSorted.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No hay listas que coincidan.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    items(filteredSorted, key = { it.id }) { l ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    pendingDelete = l
                                    false
                                } else true
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            enableDismissFromStartToEnd = false,
                            backgroundContent = {},
                            content = {
                                Column {
                                    ListRow(
                                        list = l,
                                        onClick = {
                                            shoppingListViewModel.switchList(l.id)
                                            onOpenList(l.id)
                                        },
                                        onLongClick = {
                                            pendingRename = l
                                            renameText = l.name
                                        }
                                    )
                                    Divider()
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
        }
    }

    // Crear lista
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Crear nueva lista") },
            text = {
                OutlinedTextField(
                    value = newListName,
                    onValueChange = { newListName = it },
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

    // Confirmar eliminación
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

    // Renombrar (pulso largo)
    pendingRename?.let { toRename ->
        AlertDialog(
            onDismissRequest = { pendingRename = null },
            title = { Text("Renombrar lista") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    placeholder = { Text("Nuevo nombre") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = renameText.trim()
                    if (name.isNotEmpty()) {
                        shoppingListViewModel.switchList(toRename.id)
                        shoppingListViewModel.renameCurrentList(name)
                    }
                    pendingRename = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { pendingRename = null }) { Text("Cancelar") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    list: ShoppingList,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
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
