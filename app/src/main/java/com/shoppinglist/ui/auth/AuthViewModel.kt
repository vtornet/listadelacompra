package com.shoppinglist.ui.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {

    private val TAG = "AuthViewModel"

    private val auth: FirebaseAuth = Firebase.auth
    private lateinit var credentialManager: CredentialManager
    private lateinit var activity: Activity

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        Log.d(TAG, "Auth state changed, user: ${firebaseAuth.currentUser?.email}")
        _user.value = firebaseAuth.currentUser
    }

    private val _user = MutableStateFlow(auth.currentUser)
    val user = _user.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    init {
        auth.addAuthStateListener(authListener)
    }

    fun initCredentialManager(
        context: Context,
        act: Activity
    ) {
        credentialManager = CredentialManager.create(context)
        activity = act
        Log.d(TAG, "CredentialManager initialized")
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                if (!::credentialManager.isInitialized || !::activity.isInitialized) {
                    _error.value = "CredentialManager no inicializado"
                    _loading.value = false
                    return@launch
                }

                Log.d(TAG, "Starting Google Sign In flow")
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId("894341793325-rm6tvk9lml6b4cf54gdld48nmc8lnddl.apps.googleusercontent.com")
                    .setFilterByAuthorizedAccounts(false)
                    .build()

                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()

                val result = credentialManager.getCredential(
                    request = request,
                    context = activity
                )

                Log.d(TAG, "Got credential result, type: ${result.credential.javaClass.simpleName}")
                handleCredentialResponse(result)

            } catch (e: Exception) {
                Log.e(TAG, "Error in signInWithGoogle", e)
                _error.value = e.message ?: "No se pudo iniciar sesión con Google."
                _loading.value = false
            }
        }
    }

    private fun handleCredentialResponse(response: GetCredentialResponse) {
        viewModelScope.launch {
            try {
                Log.d(TAG, "Handling credential response")
                when (val credential = response.credential) {
                    is androidx.credentials.CustomCredential -> {
                        Log.d(TAG, "CustomCredential, type: ${credential.type}")
                        if (credential.type == "com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN" ||
                            credential.type == "com.google.android.libraries.identity.googleid.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL") {
                            Log.d(TAG, "Is Google ID Token credential")
                            val googleIdTokenCredential = GoogleIdTokenCredential
                                .createFrom(credential.data)

                            val googleIdToken = googleIdTokenCredential.idToken
                            Log.d(TAG, "Got ID token, length: ${googleIdToken.length}")

                            val googleCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                            auth.signInWithCredential(googleCredential).await()
                            _error.value = null
                            _loading.value = false
                            Log.d(TAG, "Sign in successful, user: ${auth.currentUser?.email}")
                        } else {
                            Log.e(TAG, "Unknown custom credential type: ${credential.type}")
                            _error.value = "Tipo de credencial no reconocido: ${credential.type}"
                            _loading.value = false
                        }
                    }
                    else -> {
                        Log.e(TAG, "Not a custom credential: ${credential.javaClass.simpleName}")
                        _error.value = "Tipo de credencial no reconocido"
                        _loading.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in handleCredentialResponse", e)
                _error.value = e.message ?: "No se pudo completar el inicio de sesión."
                _loading.value = false
            }
        }
    }

    fun handleError(e: Exception, message: String) {
        _error.value = e.message ?: message
        _loading.value = false
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo iniciar sesión."
            } finally {
                _loading.value = false
            }
        }
    }

    fun signUp(email: String, password: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                _error.value = null
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo registrar."
            } finally {
                _loading.value = false
            }
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                auth.sendPasswordResetEmail(email).await()
                _error.value = "Correo de recuperación enviado a $email"
            } catch (e: Exception) {
                _error.value = e.message ?: "No se pudo enviar el correo de recuperación."
            } finally {
                _loading.value = false
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }

    fun clearError() {
        _error.value = null
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
    }
}
