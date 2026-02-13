package com.shoppinglist

import android.app.Application
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize

/**
 * Application principal que inicializa Firebase
 */
class ShoppingListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Firebase.initialize(this)
    }
}
