package com.shoppinglist.data.repository

import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.shoppinglist.data.models.ShoppingItem
import com.shoppinglist.data.models.ShoppingList
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ShoppingListRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val listsCol = firestore.collection("lists")
    private val itemsCol = firestore.collection("shoppingItems")

    // Storage: usa el bucket de google-services.json
    private val storage: FirebaseStorage by lazy {
        val bucketFromOptions: String? = FirebaseApp.getInstance().options.storageBucket
        val gsUrl = when {
            bucketFromOptions.isNullOrBlank() -> null
            bucketFromOptions.startsWith("gs://") -> bucketFromOptions
            else -> "gs://$bucketFromOptions"
        }
        val s = if (gsUrl != null) FirebaseStorage.getInstance(gsUrl) else FirebaseStorage.getInstance()
        Log.d("StorageDebug", "Using bucket=${s.reference.bucket} (gsUrl=$gsUrl)")
        s
    }

    /* ============== LISTAS ============== */

    suspend fun getOrCreateDefaultListId(ownerUid: String, ownerEmail: String?): String {
        val owned = listsCol.whereEqualTo("ownerUid", ownerUid).limit(1).get().await()
        if (!owned.isEmpty) {
            val d = owned.documents.first()
            return d.id
        }
        val doc = listsCol.document()
        val data = hashMapOf(
            "name" to "Mi lista",
            "ownerUid" to ownerUid,
            "membersEmails" to emptyList<String>()
        )
        doc.set(data).await()
        return doc.id
    }

    fun observeListsForUser(uid: String, email: String?): Flow<List<ShoppingList>> = callbackFlow {
        if (uid.isBlank()) { trySend(emptyList()); awaitClose {}; return@callbackFlow }

        val map = mutableMapOf<String, ShoppingList>()

        val regOwned = listsCol.whereEqualTo("ownerUid", uid)
            .addSnapshotListener { snap, _ ->
                snap?.documents?.forEach { d ->
                    map[d.id] = ShoppingList(
                        id = d.id,
                        name = d.getString("name") ?: "",
                        ownerUid = d.getString("ownerUid"),
                        membersEmails = (d.get("membersEmails") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                    )
                }
                trySend(map.values.sortedBy { it.name })
            }

        var regMember: com.google.firebase.firestore.ListenerRegistration? = null
        if (!email.isNullOrBlank()) {
            regMember = listsCol.whereArrayContains("membersEmails", email)
                .addSnapshotListener { snap, _ ->
                    snap?.documents?.forEach { d ->
                        map[d.id] = ShoppingList(
                            id = d.id,
                            name = d.getString("name") ?: "",
                            ownerUid = d.getString("ownerUid"),
                            membersEmails = (d.get("membersEmails") as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        )
                    }
                    trySend(map.values.sortedBy { it.name })
                }
        }

        awaitClose {
            regOwned.remove()
            regMember?.remove()
        }
    }

    suspend fun addMemberEmail(listId: String, email: String) {
        listsCol.document(listId).update("membersEmails", FieldValue.arrayUnion(email)).await()
    }

    /** Crear nueva lista (devuelve id). */
    suspend fun createList(name: String, ownerUid: String): String {
        val doc = listsCol.document()
        val data = hashMapOf(
            "name" to name.trim(),
            "ownerUid" to ownerUid,
            "membersEmails" to emptyList<String>()
        )
        doc.set(data).await()
        return doc.id
    }

    /** Renombrar lista (dueño). */
    suspend fun renameList(listId: String, newName: String) {
        listsCol.document(listId).update("name", newName.trim()).await()
    }

    /** Eliminar lista (no borra items). */
    suspend fun deleteList(listId: String) {
        listsCol.document(listId).delete().await()
    }

    /* ============== ITEMS ============== */

    fun getItemsForList(listId: String): Flow<List<ShoppingItem>> = callbackFlow {
        if (listId.isBlank()) { trySend(emptyList()); awaitClose {}; return@callbackFlow }
        val reg = itemsCol.whereEqualTo("listId", listId)
            .addSnapshotListener { snap, err ->
                if (err != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { d ->
                    d.toObject(ShoppingItem::class.java)?.copy(id = d.id)
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { reg.remove() }
    }

    suspend fun addItemToList(listId: String, item: ShoppingItem) {
        val doc = if (item.id.isBlank()) itemsCol.document() else itemsCol.document(item.id)
        doc.set(item.copy(id = doc.id, listId = listId)).await()
    }

    suspend fun updateItem(item: ShoppingItem) {
        require(item.id.isNotBlank()) { "Item sin id" }
        itemsCol.document(item.id).set(item).await()
    }

    suspend fun deleteItem(itemId: String) {
        itemsCol.document(itemId).delete().await()
    }

    suspend fun migrateMyItemsToList(ownerUid: String, listId: String) {
        val snap = itemsCol.whereEqualTo("addedByUid", ownerUid).get().await()
        for (d in snap.documents) {
            val hasList = d.getString("listId")
            if (hasList.isNullOrBlank()) {
                d.reference.update("listId", listId).await()
            }
        }
    }

    /* ============== IMÁGENES (STORAGE) ============== */

    suspend fun uploadImage(uri: Uri): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anon"
        val fileName = "${System.currentTimeMillis()}.jpg"
        val ref = storage.reference.child("items/$uid/$fileName")
        Log.d("StorageDebug", "putFile to bucket=${storage.reference.bucket} path=${ref.path}")
        ref.putFile(uri).await()

        val delays = listOf(150L, 300L, 600L, 1200L, 2000L)
        var lastError: Exception? = null
        for (attempt in 0..delays.size) {
            try {
                val url = ref.downloadUrl.await().toString()
                Log.d("StorageDebug", "downloadUrl OK: $url")
                return url
            } catch (e: Exception) {
                lastError = e
                val notFound = (e as? StorageException)?.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND ||
                        (e.message?.contains("Object does not exist", true) == true)
                Log.w("StorageDebug", "downloadUrl attempt#$attempt failed: ${e.message}")
                if (!notFound || attempt == delays.size) break
                delay(delays[attempt])
            }
        }
        throw lastError ?: IllegalStateException("No se pudo obtener la URL de descarga")
    }

    suspend fun deleteImageByUrl(url: String) {
        try { storage.getReferenceFromUrl(url).delete().await() } catch (_: Exception) { }
    }

    /* ============== Open Food Facts (nombre + imagen) ============== */

    data class BarcodeInfo(val name: String?, val imageUrl: String?)

    suspend fun resolveBarcodeInfo(barcode: String): BarcodeInfo? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "ListaDeLaCompra/1.0 (Android)")
            }
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            if (root.optInt("status") != 1) return@withContext null
            val p = root.optJSONObject("product") ?: return@withContext null

            fun pick(vararg keys: String): String? {
                for (k in keys) {
                    val v = p.optString(k, null)
                    if (!v.isNullOrBlank()) return v
                }
                return null
            }

            val name = pick("product_name_es", "product_name", "generic_name_es", "generic_name")
            val brand = p.optString("brands", null)?.split(',')?.firstOrNull()?.trim()
                ?.takeIf { it.isNotBlank() }
            val finalName = when {
                name != null && brand != null -> "$name ($brand)"
                name != null -> name
                brand != null -> brand
                else -> null
            }
            val imageUrl = pick("image_front_url", "image_highres_url", "image_url")

            BarcodeInfo(finalName, imageUrl)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
