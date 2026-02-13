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
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
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
    val currentUser = FirebaseAuth.getInstance().currentUser

    var showOverflow by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newListName by rememberSaveable { mutableStateOf("") }

    // Renombrar / borrar
    var pendingDelete by remember { mutableStateOf<ShoppingList?>(null) }
    var pendingRename by remember { mutableStateOf<ShoppingList?>(null) }
    var renameText by rememberSaveable { mutableStateOf("") }

    // NUEVO: buscador de listas
    var query by rememberSaveable { mutableStateOf("") }

    // Estado para rastrear listas compartidas nuevas (se marcan como vistas una vez que el usuario las ve)
    var seenSharedListIds by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }
    val newSharedLists = remember(lists, seenSharedListIds, currentUser?.uid) {
        lists.filter { list -> list.ownerUid != currentUser?.uid && list.id !in seenSharedListIds }
    }
    val showSharedNotification = newSharedLists.isNotEmpty()

    BackHandler { onExitApp() }

    fun norm(s: String): String =
        Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.getDefault())

    val filteredSorted = remember(lists, query) {
        val q = norm(query)
        lists
            .filter { q.isBlank() || norm(it.name).contains(q) }
            .sortedWith(
                compareBy<ShoppingList> { list -> list.ownerUid != currentUser?.uid }
                    .thenBy { norm(it.name) }
            )
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

            // Notificación de listas compartidas
            if (showSharedNotification) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (newSharedLists.size == 1) {
                                    "Tienes 1 lista compartida nueva"
                                } else {
                                    "Tienes ${newSharedLists.size} listas compartidas nuevas"
                                },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            newSharedLists.firstOrNull()?.let { list ->
                                Text(
                                    text = "\"${list.name}\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Button(
                            onClick = {
                                seenSharedListIds = seenSharedListIds + newSharedLists.map { it.id }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text("Aceptar")
                        }
                    }
                }
            }

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
                    items(filteredSorted, key = { list -> list.id }) { list ->
                        val isOwner = list.ownerUid == currentUser?.uid
                        val isNew = !isOwner && list.id !in seenSharedListIds

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                if (value == SwipeToDismissBoxValue.EndToStart) {
                                    pendingDelete = list
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
                                        list = list,
                                        isOwner = isOwner,
                                        isNew = isNew,
                                        onClick = {
                                            shoppingListViewModel.switchList(list.id)
                                            onOpenList(list.id)
                                        },
                                        onLongClick = {
                                            // Solo permitir renombrar si eres propietario
                                            if (isOwner) {
                                                pendingRename = list
                                                renameText = list.name
                                            }
                                        }
                                    )
                                    HorizontalDivider()
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
        val isOwner = toDelete.ownerUid == currentUser?.uid
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (isOwner) "Eliminar lista" else "Abandonar lista") },
            text = {
                Text(
                    if (isOwner)
                        "¿Seguro que deseas eliminar «${toDelete.name.ifBlank { toDelete.id }}»? Esta acción no se puede deshacer."
                    else
                        "¿Seguro que deseas abandonar «${toDelete.name.ifBlank { toDelete.id }}»? Podrás volver a acceder si te vuelven a invitar."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isOwner) {
                        shoppingListViewModel.deleteListCascade(toDelete.id)
                    } else {
                        shoppingListViewModel.leaveSharedList(toDelete.id, currentUser?.email)
                    }
                    pendingDelete = null
                }) { Text(if (isOwner) "Eliminar" else "Abandonar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }

    // Renombrar (pulso largo) - solo para propietarios
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
    isOwner: Boolean,
    isNew: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = if (isOwner) onLongClick else null
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = list.name.ifBlank { "(Sin nombre)" },
                    style = MaterialTheme.typography.titleMedium
                )
                if (isNew) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "NUEVA",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isOwner) "Propietario" else "Invitado",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!isOwner && list.membersEmails.isNotEmpty()) {
                    Text(
                        text = " • ${list.membersEmails.size} miembro${if (list.membersEmails.size > 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
