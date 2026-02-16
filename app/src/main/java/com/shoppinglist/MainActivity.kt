package com.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoppinglist.ui.auth.AuthViewModel
import com.shoppinglist.ui.auth.LoginScreen
import com.shoppinglist.ui.home.HomeScreen
import com.shoppinglist.ui.shoppinglist.ShoppingListScreen
import com.shoppinglist.ui.theme.ShoppingListTheme

class MainActivity : ComponentActivity() {

    private lateinit var mainAuthViewModel: AuthViewModel
    private lateinit var mainShoppingListViewModel: com.shoppinglist.ui.shoppinglist.ShoppingListViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            // Crear ViewModels al nivel de Activity para que sobrevivan a recomposiciones
            mainAuthViewModel = viewModel()
            mainShoppingListViewModel = viewModel()

            LaunchedEffect(Unit) {
                mainAuthViewModel.initCredentialManager(
                    context = this@MainActivity,
                    act = this@MainActivity
                )
            }

            ShoppingListTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(
                        authViewModel = mainAuthViewModel,
                        shoppingListViewModel = mainShoppingListViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    shoppingListViewModel: com.shoppinglist.ui.shoppinglist.ShoppingListViewModel
) {
    val user by authViewModel.user.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }

    if (user == null) {
        screen = "home"
        LoginScreen(viewModel = authViewModel)
        return
    }

    when (screen) {
        "home" -> {
            HomeScreen(
                authViewModel = authViewModel,
                shoppingListViewModel = shoppingListViewModel,
                onOpenList = { screen = "list" },
                onExitApp = { }
            )
        }
        "list" -> {
            BackHandler { screen = "home" }
            ShoppingListScreen(
                authViewModel = authViewModel,
                shoppingListViewModel = shoppingListViewModel,
                onBack = { screen = "home" }
            )
        }
    }
}
