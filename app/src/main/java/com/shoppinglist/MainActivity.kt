package com.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoppinglist.ui.auth.AuthViewModel
import com.shoppinglist.ui.auth.LoginScreen
import com.shoppinglist.ui.theme.ShoppingListTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShoppingListTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel = viewModel(),
    shoppingListViewModel: com.shoppinglist.ui.shoppinglist.ShoppingListViewModel = viewModel()
) {
    val user by authViewModel.user.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") } // "home" | "list"

    if (user == null) {
        screen = "home"
        LoginScreen(viewModel = authViewModel)
        return
    }

    when (screen) {
        "home" -> {
            com.shoppinglist.ui.home.HomeScreen(
                authViewModel = authViewModel,
                shoppingListViewModel = shoppingListViewModel,
                onOpenList = { screen = "list" },
                onExitApp = {
                    // Deja que el sistema maneje back por defecto (o cierra actividad si quieres).
                }
            )
        }
        "list" -> {
            // Back del sistema también vuelve a Home
            BackHandler { screen = "home" }
            com.shoppinglist.ui.shoppinglist.ShoppingListScreen(
                authViewModel = authViewModel,
                shoppingListViewModel = shoppingListViewModel,
                onBack = { screen = "home" }      // <-- Pasamos callback para ← Volver
            )
        }
    }
}
