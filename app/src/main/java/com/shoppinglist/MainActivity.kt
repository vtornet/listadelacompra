package com.shoppinglist

import android.content.Intent
import android.net.Uri
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
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shoppinglist.ui.auth.AuthViewModel
import com.shoppinglist.ui.auth.LoginScreen
import com.shoppinglist.ui.home.HomeScreen
import com.shoppinglist.ui.shoppinglist.ShoppingListScreen
import com.shoppinglist.ui.statistics.StatisticsScreen
import com.shoppinglist.ui.theme.ShoppingListTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream
import java.io.InputStream

class MainActivity : ComponentActivity() {

    private lateinit var mainAuthViewModel: AuthViewModel
    private lateinit var mainShoppingListViewModel: com.shoppinglist.ui.shoppinglist.ShoppingListViewModel

    companion object {
        const val REQUEST_CODE_EXPORT = 1001
        const val REQUEST_CODE_IMPORT = 1002
    }

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

    @Deprecated("This method has been deprecated in favor of using the Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQUEST_CODE_EXPORT -> {
                if (resultCode == RESULT_OK && data != null) {
                    data.data?.let { uri ->
                        handleExportResult(uri)
                    }
                }
            }
            REQUEST_CODE_IMPORT -> {
                if (resultCode == RESULT_OK && data != null) {
                    data.data?.let { uri ->
                        handleImportResult(uri)
                    }
                }
            }
        }
    }

    private fun handleExportResult(uri: Uri) {
        lifecycleScope.launch {
            try {
                val exportData = mainShoppingListViewModel.generateExportData()
                if (exportData != null) {
                    val jsonContent = exportData.toJson()
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(jsonContent.toByteArray())
                            outputStream.flush()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleImportResult(uri: Uri) {
        lifecycleScope.launch {
            try {
                val jsonContent = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().readText()
                    }
                }

                if (jsonContent != null) {
                    val importData = com.shoppinglist.data.models.ExportableData.fromJson(jsonContent)
                    if (importData != null) {
                        val count = mainShoppingListViewModel.importFromData(importData)
                        // Aquí podrías mostrar un mensaje de éxito
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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
                onExitApp = { },
                onShowStatistics = { screen = "statistics" }
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
        "statistics" -> {
            BackHandler { screen = "home" }
            StatisticsScreen(
                shoppingListViewModel = shoppingListViewModel,
                onBack = { screen = "home" },
                onViewList = { listId ->
                    shoppingListViewModel.switchList(listId)
                    screen = "list"
                }
            )
        }
    }
}
