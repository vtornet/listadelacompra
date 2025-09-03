// Ruta: app/src/main/java/com/shoppinglist/ui/auth/AuthViewModel.kt

package com.shoppinglist.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val auth: FirebaseAuth = Firebase.auth

    private val _user = MutableStateFlow(auth.currentUser)
    val user = _user.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _user.value = auth.currentUser
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                _user.value = auth.currentUser
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    fun signOut() {
        auth.signOut()
        _user.value = null
    }

    fun clearError() {
        _error.value = null
    }
}