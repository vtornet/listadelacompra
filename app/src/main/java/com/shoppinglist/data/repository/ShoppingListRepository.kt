package com.shoppinglist.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.data.models.ShoppingList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    private val storage = Firebase.storage

    private val listsCol = firestore.collection("lists")
    private val itemsCol = firestore.collection("shoppingItems")

    /* ===================== LISTAS ===================== */

    /** Observa en tiempo real todas las listas del usuario (propietario o invitado por email). */
    fun observeListsForUser(uid: String, email: String?): Flow<List<ShoppingList>> = callbackFlow {
        // Mantenemos dos mapas independientes y fusionamos en cada evento.
        val ownerMap = linkedMapOf<String, ShoppingList>()
        val memberMap = linkedMapOf<String, ShoppingList>()

        fun sendMerged() {
            val merged = LinkedHashMap<String, ShoppingList>()
            // Si una lista aparece en ambos, preferimos la de propietario.
            ownerMap.forEach { (k, v) -> merged[k] = v }
            memberMap.forEach { (k, v) -> merged[k] = merged[k] ?: v }
            trySend(merged.values.toList())
        }

        var regOwner: ListenerRegistration? = null
        var regMember: ListenerRegistration? = null

        // Propietario
        regOwner = listsCol.whereEqualTo("ownerUid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "observeListsForUser(owner) error", err)
                    return@addSnapshotListener
                }
                ownerMap.clear()
                snap?.documents?.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    ownerMap[doc.id] = ShoppingList(
                        id = doc.id,
                        name = data["name"] as? String ?: "",
                        ownerUid = data["ownerUid"] as? String ?: "",
                        membersEmails = (data["membersEmails"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    )
                }
                sendMerged()
            }

        // Miembro por email
        if (!email.isNullOrBlank()) {
            regMember = listsCol.whereArrayContains("membersEmails", email)
                .addSnapshotListener { snap, err ->
                    if (err != null) {
                        Log.w(TAG, "observeListsForUser(member) error", err)
                        return@addSnapshotListener
                    }
                    memberMap.clear()
                    snap?.documents?.forEach { doc ->
                        val data = doc.data ?: return@forEach
                        memberMap[doc.id] = ShoppingList(
                            id = doc.id,
                            name = data["name"] as? String ?: "",
                            ownerUid = data["ownerUid"] as? String ?: "",
                            membersEmails = (data["membersEmails"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        )
                    }
                    sendMerged()
                }
        }

        awaitClose {
            regOwner?.remove()
            regMember?.remove()
        }
    }

    /** Crea lista, devuelve su id. */
    suspend fun createList(name: String, ownerUid: String): String {
        val ref = listsCol.document()
        val data = hashMapOf(
            "name" to name.ifBlank { "Sin nombre" },
            "ownerUid" to ownerUid,
            "membersEmails" to emptyList<String>()
        )
        ref.set(data).await()
        return ref.id
    }

    /** Renombra lista. */
    suspend fun renameList(id: String, newName: String) {
        listsCol.document(id).update("name", newName.ifBlank { "Sin nombre" }).await()
    }

    /** Elimina lista y (opcionalmente) sus items (borrado por reglas: permitido a miembros). */
    suspend fun deleteListDeep(id: String) {
        // Borrado de items en cliente (mejor usar funciones/Batch en backend si crece):
        val items = itemsCol.whereEqualTo("listId", id).get().await()
        for (doc in items.documents) {
            doc.reference.delete().await()
        }
        // Borrar lista al final
        listsCol.document(id).delete().await()
    }

    /** Añade email a membersEmails si no existía ya. */
    suspend fun addMemberEmail(listId: String, email: String) {
        val ref = listsCol.document(listId)
        firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val current = (snap.get("membersEmails") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val target = email.trim()
            if (current.any { it.equals(target, ignoreCase = true) }) return@runTransaction
            tx.update(ref, "membersEmails", current + target)
        }.await()
    }

    /** Elimina email de membersEmails si existe. */
    suspend fun removeMemberEmail(listId: String, email: String) {
        val ref = listsCol.document(listId)
        firestore.runTransaction { tx ->
            val snap = tx.get(ref)
            val current = (snap.get("membersEmails") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val target = email.trim()
            val updated = current.filter { !it.equals(target, ignoreCase = true) }
            if (updated.size != current.size) tx.update(ref, "membersEmails", updated)
        }.await()
    }

    /** Obtiene (o crea) id de la lista por defecto del usuario. */
    suspend fun getOrCreateDefaultListId(uid: String, email: String?): String {
        // 1) Propietaria
        val owned = listsCol.whereEqualTo("ownerUid", uid).limit(1).get().await()
        if (!owned.isEmpty) return owned.documents.first().id

        // 2) Miembro por email
        if (!email.isNullOrBlank()) {
            val member = listsCol.whereArrayContains("membersEmails", email).limit(1).get().await()
            if (!member.isEmpty) return member.documents.first().id
        }

        // 3) Crear una por defecto
        return createList("Mi lista", uid)
    }

    /* ===================== ÍTEMS ===================== */

    /** Observa items de una lista (ordenados por nombre). */
    fun getItemsForList(listId: String?): Flow<List<ShoppingItem>> = callbackFlow {
        if (listId.isNullOrBlank()) {
            trySend(emptyList())
            awaitClose { }
            return@callbackFlow
        }
        val reg = itemsCol.whereEqualTo("listId", listId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.w(TAG, "getItemsForList error", err)
                    return@addSnapshotListener
                }
                val items = snap?.documents?.mapNotNull { it.toShoppingItem() }?.sortedWith(
                    compareBy<ShoppingItem> { !it.inShoppingList }.thenBy { it.name?.lowercase() ?: "" }
                ).orEmpty()
                trySend(items)
            }
        awaitClose { reg.remove() }
    }

    /** Añadir item a lista (si existe por nombre, VM decidirá si suma o crea). */
    suspend fun addItemToList(listId: String, item: ShoppingItem): String {
        val id = (item.id.takeIf { !it.isNullOrBlank() } ?: itemsCol.document().id)
        val data = item.toMap(listId, id)
        itemsCol.document(id).set(data).await()
        return id
    }

    /** Actualizar item. */
    suspend fun updateItem(item: ShoppingItem) {
        val id = item.id ?: return
        itemsCol.document(id).update(item.toMap(item.listId, id)).await()
    }

    /** Borrar item. */
    suspend fun deleteItem(itemId: String) {
        itemsCol.document(itemId).delete().await()
    }

    /** Marca múltiples items como comprados (no en lista) en una sola operación batch. */
    suspend fun markItemsAsPurchased(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        firestore.runBatch { batch ->
            for (id in itemIds) {
                batch.update(itemsCol.document(id), "inShoppingList", false)
            }
        }.await()
    }

    /** Obtiene todos los items de una lista de forma síncrona (para exportación). */
    suspend fun getItemsForListSync(listId: String): List<ShoppingItem> {
        if (listId.isBlank()) return emptyList()
        return try {
            itemsCol.whereEqualTo("listId", listId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toShoppingItem() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting items for list $listId", e)
            emptyList()
        }
    }

    /** Marca múltiples items como "por comprar" (en lista) en una sola operación batch. */
    suspend fun markItemsAsToBuy(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        firestore.runBatch { batch ->
            for (id in itemIds) {
                batch.update(itemsCol.document(id), "inShoppingList", true)
            }
        }.await()
    }

    /* ===================== IMÁGENES (Firebase Storage) ===================== */

    /** Sube imagen a /images/{random}.jpg y devuelve su URL de descarga. */
    suspend fun uploadImage(uri: Uri): String {
        val fileName = "images/${UUID.randomUUID()}.jpg"
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

    /** Intenta resolver nombre+imagen de OpenFoodFacts (v2 → v0 fallback).
     *  - Corre en Dispatchers.IO.
     *  - Normaliza el código a solo dígitos y valida longitud (8..14).
     *  - Construye nombre combinando product_name(_es)/generic_name(_es)+brand+quantity cuando procede.
     */
    suspend fun resolveBarcodeInfo(barcode: String): BarcodeInfo? = withContext(Dispatchers.IO) {
        val code = barcode.filter { it.isDigit() }.takeIf { it.length in 8..14 } ?: return@withContext null

        fun HttpURLConnection.setup() {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "ShoppingList/1.0 (Android)")
            setRequestProperty("Accept-Language", "es,es-ES;q=0.9,en;q=0.8")
        }

        // 1) API v2 (preferida)
        runCatching {
            val url = URL(
                "https://world.openfoodfacts.org/api/v2/product/$code" +
                        "?fields=product_name,product_name_es,generic_name,generic_name_es,brands,quantity,image_small_url,image_url"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply { setup() }
            conn.inputStream.use { `in` ->
                val body = `in`.readBytes().toString(Charsets.UTF_8)
                val json = JSONObject(body)
                val product = json.optJSONObject("product")
                if (json.optInt("status", 0) == 1 && product != null) {
                    return@withContext BarcodeInfo(
                        name = buildProductName(product),
                        imageUrl = chooseImage(product)
                    )
                }
            }
        }.onFailure { Log.w(TAG, "resolveBarcodeInfo v2 fail", it) }

        // 2) API v0 (respaldo)
        return@withContext runCatching {
            val url = URL("https://world.openfoodfacts.org/api/v0/product/$code.json")
            val conn = (url.openConnection() as HttpURLConnection).apply { setup() }
            conn.inputStream.use { `in` ->
                val body = `in`.readBytes().toString(Charsets.UTF_8)
                val json = JSONObject(body)
                if (json.optInt("status", 0) != 1) null
                else {
                    val product = json.optJSONObject("product") ?: return@runCatching null
                    BarcodeInfo(
                        name = buildProductName(product),
                        imageUrl = chooseImage(product)
                    )
                }
            }
        }.getOrElse {
            Log.w(TAG, "resolveBarcodeInfo v0 fail", it)
            null
        }
    }

    /* ===================== Helpers ===================== */

    private fun buildProductName(prod: JSONObject): String? {
        // Preferencias de nombre
        val nEs = prod.optString("product_name_es").trim()
        val nEn = prod.optString("product_name").trim()
        val gEs = prod.optString("generic_name_es").trim()
        val gEn = prod.optString("generic_name").trim()
        val base = when {
            nEs.isNotBlank() -> nEs
            nEn.isNotBlank() -> nEn
            gEs.isNotBlank() -> gEs
            gEn.isNotBlank() -> gEn
            else -> ""
        }

        // Marca(s)
        val brands = prod.optString("brands").split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val brand = brands.firstOrNull() ?: ""

        // Cantidad
        val qty = prod.optString("quantity").trim()

        // Composición final
        val name = when {
            base.isNotBlank() && brand.isNotBlank() && qty.isNotBlank() -> "$brand $base • $qty"
            base.isNotBlank() && brand.isNotBlank() -> "$brand $base"
            base.isNotBlank() && qty.isNotBlank() -> "$base • $qty"
            base.isNotBlank() -> base
            brand.isNotBlank() && qty.isNotBlank() -> "$brand • $qty"
            brand.isNotBlank() -> brand
            else -> null
        }
        return name
    }

    private fun chooseImage(prod: JSONObject): String? {
        val small = prod.optString("image_small_url").trim()
        val big = prod.optString("image_url").trim()
        return when {
            small.isNotBlank() -> small
            big.isNotBlank() -> big
            else -> null
        }
    }

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
            name = (data["name"] as? String)?.takeIf { it.isNotBlank() } ?: "",
            inShoppingList = data["inShoppingList"] as? Boolean ?: true,
            listId = data["listId"] as? String,
            addedByUid = data["addedByUid"] as? String,
            imageUrl = data["imageUrl"] as? String,
            price = (data["price"] as? Number)?.toDouble()?.takeIf { it >= 0 },
            previousPrice = (data["previousPrice"] as? Number)?.toDouble()?.takeIf { it >= 0 },
            quantity = (data["quantity"] as? Number)?.toInt()?.coerceIn(1, 9999) ?: 1
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
