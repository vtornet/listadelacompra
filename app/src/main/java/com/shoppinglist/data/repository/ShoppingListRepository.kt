package com.shoppinglist.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.storage.FirebaseStorage
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.data.models.ShoppingList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Repositorio central de Firestore/Storage para listas e items.
 * Colecciones:
 *  - lists (raíz) -> documentos: { name, ownerUid, membersEmails[] }
 *  - shoppingItems (raíz) -> documentos: { name, inShoppingList, listId, addedByUid, imageUrl, price, previousPrice }
 */
class ShoppingListRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val listsCol = firestore.collection("lists")
    private val itemsCol = firestore.collection("shoppingItems")

    /* ===================== LISTAS ===================== */

    /** Observa en tiempo real todas las listas del usuario (propietario o invitado por email). */
    /** Observa en tiempo real todas las listas del usuario (propietario o invitado por email). */
    fun observeListsForUser(uid: String, email: String?): Flow<List<ShoppingList>> = callbackFlow {
        // Mantenemos dos mapas independientes y fusionamos en cada evento.
        val ownerMap = linkedMapOf<String, ShoppingList>()
        val memberMap = linkedMapOf<String, ShoppingList>()

        fun sendMerged() {
            val merged = LinkedHashMap<String, ShoppingList>()
            // Si una lista aparece en ambos, preferimos la de propietario.
            ownerMap.forEach { (k, v) -> merged[k] = v }
            memberMap.forEach { (k, v) -> merged[k] = v }
            trySend(merged.values.toList())
        }

        var regOwner: ListenerRegistration? = null
        var regMember: ListenerRegistration? = null

        if (uid.isNotBlank()) {
            regOwner = listsCol.whereEqualTo("ownerUid", uid)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.w(TAG, "observeListsForUser(owner) error", err)
                        return@addSnapshotListener
                    }
                    ownerMap.clear()
                    if (snap != null) {
                        for (d in snap.documents) {
                            ownerMap[d.id] = ShoppingList(
                                id = d.id,
                                name = d.getString("name") ?: "",
                                ownerUid = d.getString("ownerUid"),
                                membersEmails = (d.get("membersEmails") as? List<*>)?.filterIsInstance<String>()
                                    ?: emptyList()
                            )
                        }
                    }
                    sendMerged()
                }
        }

        if (!email.isNullOrBlank()) {
            regMember = listsCol.whereArrayContains("membersEmails", email)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.w(TAG, "observeListsForUser(member) error", err)
                        return@addSnapshotListener
                    }
                    memberMap.clear()
                    if (snap != null) {
                        for (d in snap.documents) {
                            memberMap[d.id] = ShoppingList(
                                id = d.id,
                                name = d.getString("name") ?: "",
                                ownerUid = d.getString("ownerUid"),
                                membersEmails = (d.get("membersEmails") as? List<*>)?.filterIsInstance<String>()
                                    ?: emptyList()
                            )
                        }
                    }
                    sendMerged()
                }
        }

        awaitClose {
            regOwner?.remove()
            regMember?.remove()
        }
    }

    /** Crea una lista y devuelve su ID. (Escribe los campos que esperan las reglas) */
    suspend fun createList(name: String, ownerUid: String): String {
        val doc = listsCol.document()
        val data = hashMapOf(
            "name" to name,
            "ownerUid" to ownerUid,
            "membersEmails" to emptyList<String>()
        )
        doc.set(data).await()
        return doc.id
    }

    /** Renombra una lista. */
    suspend fun renameList(listId: String, newName: String) {
        listsCol.document(listId).update("name", newName).await()
    }

    /**
     * Elimina una lista + TODOS sus items + sus imágenes en Storage.
     * Compatible con lotes de Firestore (500 por batch).
     */
    suspend fun deleteListDeep(listId: String) {
        // 1) Recuperar items de la lista
        val itemsSnap = itemsCol.whereEqualTo("listId", listId).get().await()
        val docs = itemsSnap.documents

        // 1.1) Borrar imágenes si las hay
        for (d in docs) {
            val url = d.getString("imageUrl")
            if (!url.isNullOrBlank()) {
                try { deleteImageByUrl(url) } catch (e: Exception) {
                    Log.w(TAG, "delete image fail", e)
                }
            }
        }

        // 2) Borrar items por lotes de 500
        var idx = 0
        while (idx < docs.size) {
            val batch = firestore.batch()
            for (i in idx until kotlin.math.min(idx + 500, docs.size)) {
                batch.delete(docs[i].reference)
            }
            batch.commit().await()
            idx += 500
        }

        // 3) Borrar documento de lista
        listsCol.document(listId).delete().await()
    }

    /** Devuelve/crea una lista "Mi lista" para el usuario al iniciar. */
    suspend fun getOrCreateDefaultListId(uid: String, email: String?): String {
        val existing = listsCol.whereEqualTo("ownerUid", uid).limit(1).get().await()
        if (!existing.isEmpty) return existing.documents.first().id
        return createList("Mi lista", uid)
    }

    /** Invitar miembro por email a una lista. */
    suspend fun addMemberEmail(listId: String, email: String) {
        val ref = listsCol.document(listId)
        firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val current = (snap.get("membersEmails") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            if (!current.contains(email)) tx.update(ref, "membersEmails", current + email)
        }.await()
    }

    /* ===================== ITEMS ===================== */

    /** Items en tiempo real de una lista. */
    fun getItemsForList(listId: String): Flow<List<ShoppingItem>> = callbackFlow {
        val registration = itemsCol
            .whereEqualTo("listId", listId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "getItemsForList error", err)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val out = snap.documents.map { d -> d.toShoppingItem() }
                        .sortedWith(
                            compareBy<ShoppingItem> { !it.inShoppingList }
                                .thenBy { it.name.lowercase() }
                        )
                    trySend(out)
                }
            }
        awaitClose { registration.remove() }
    }

    /** Añade un item a una lista. */
    suspend fun addItemToList(listId: String, item: ShoppingItem): String {
        val doc = itemsCol.document()
        val data = item.toMap(listId = listId, id = doc.id)
        doc.set(data).await()
        return doc.id
    }

    /** Actualiza un item (usa el id del propio item). */
    suspend fun updateItem(item: ShoppingItem) {
        val id = item.id
        require(id.isNotBlank()) { "item.id vacío" }
        val data = item.toMap(listId = item.listId, id = id)
        itemsCol.document(id).set(data).await()
    }

    /** Elimina un item por ID. */
    suspend fun deleteItem(itemId: String) {
        itemsCol.document(itemId).delete().await()
    }

    /* ===================== STORAGE ===================== */

    suspend fun uploadImage(uri: Uri): String {
        val fileName = "images/${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"
        val ref = storage.reference.child(fileName)
        ref.putFile(uri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun deleteImageByUrl(url: String) {
        try {
            val ref = storage.getReferenceFromUrl(url)
            ref.delete().await()
        } catch (e: Exception) {
            Log.w(TAG, "deleteImageByUrl: $url", e)
        }
    }

    /* ===================== CÓDIGOS DE BARRA ===================== */

    data class BarcodeInfo(val name: String?, val imageUrl: String?)

    /** Consulta OpenFoodFacts. Si falla, devuelve null y se usará el número del código. */
    suspend fun resolveBarcodeInfo(barcode: String): BarcodeInfo? {
        return try {
            val url = URL("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 7000
                readTimeout = 7000
            }
            conn.inputStream.use { `in` ->
                val body = `in`.readBytes().toString(Charsets.UTF_8)
                val json = JSONObject(body)
                val status = json.optInt("status", 0)
                if (status != 1) return null
                val prod = json.optJSONObject("product") ?: return null
                val name = prod.optString("product_name_es")
                    .ifBlank { prod.optString("product_name") }
                val image = prod.optString("image_small_url")
                    .ifBlank { prod.optString("image_url") }
                BarcodeInfo(name.ifBlank { null }, image.ifBlank { null })
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveBarcodeInfo fail", e)
            null
        }
    }

    /* ===================== Helpers ===================== */

    private fun ShoppingItem.toMap(listId: String?, id: String): Map<String, Any?> = hashMapOf(
        "id" to id,
        "name" to name,
        "inShoppingList" to inShoppingList,
        "listId" to (listId ?: this.listId),
        "addedByUid" to addedByUid,
        "imageUrl" to imageUrl,
        "price" to price,
        "previousPrice" to previousPrice,
        "quantity" to quantity
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toShoppingItem(): ShoppingItem {
        val data = this.data ?: emptyMap()
        return ShoppingItem(
            id = this.id,
            name = data["name"] as? String ?: "",
            inShoppingList = data["inShoppingList"] as? Boolean ?: true,
            listId = data["listId"] as? String,
            addedByUid = data["addedByUid"] as? String,
            imageUrl = data["imageUrl"] as? String,
            price = (data["price"] as? Number)?.toDouble(),
            previousPrice = (data["previousPrice"] as? Number)?.toDouble(),
            quantity = (data["quantity"] as? Number)?.toInt() ?: 1
        )
    }

    companion object {
        private const val TAG = "ShoppingListRepo"
    }

    /* ======= Migración opcional (no-op si no existe) ======= */
    suspend fun migrateMyItemsToList(uid: String, listId: String) {
        // Si tuvieras una colección antigua ("myItems"), migra aquí.
        // En este proyecto no es necesaria: lo dejamos como no-op.
        return
    }
}
