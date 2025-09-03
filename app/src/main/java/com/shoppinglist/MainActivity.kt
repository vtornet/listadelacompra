package com.shoppinglist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoppinglist.ui.auth.AuthViewModel
import com.shoppinglist.ui.auth.LoginScreen
import com.shoppinglist.ui.shoppinglist.ShoppingListScreen
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
    authViewModel: AuthViewModel = viewModel()
) {
    val user by authViewModel.user.collectAsState()

    if (user == null) {
        LoginScreen(viewModel = authViewModel)
    } else {
        ShoppingListScreen(authViewModel = authViewModel)
    }
}