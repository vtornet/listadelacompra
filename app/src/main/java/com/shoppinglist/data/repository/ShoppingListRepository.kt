// Ruta: app/src/main/java/com/shoppinglist/data/repository/ShoppingListRepository.kt

package com.shoppinglist.data.repository

import android.net.Uri
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.shoppinglist.data.models.ShoppingItem
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.*

class ShoppingListRepository {

    private val db = Firebase.firestore
    private val storage = Firebase.storage
    private val listCollection = db.collection("shared_list")

    fun getItems(): Flow<List<ShoppingItem>> = callbackFlow {
        val listener = listCollection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val items = snapshot.toObjects(ShoppingItem::class.java)
                trySend(items).isSuccess
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun addItem(item: ShoppingItem) {
        val docRef = listCollection.document()
        val itemWithId = item.copy(id = docRef.id)
        docRef.set(itemWithId).await()
    }

    suspend fun updateItem(item: ShoppingItem) {
        listCollection.document(item.id).set(item).await()
    }

    suspend fun uploadImage(imageUri: Uri): String {
        val storageRef = storage.reference
        val imageFileName = "images/${UUID.randomUUID()}.jpg"
        val imageRef = storageRef.child(imageFileName)
        imageRef.putFile(imageUri).await()
        return imageRef.downloadUrl.await().toString()
    }
}