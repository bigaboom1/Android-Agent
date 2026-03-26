package com.example.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.agent.ui.ChatScreen
import com.example.agent.ui.LoginScreen
import com.example.agent.ui.RemoteScreen
import com.example.agent.ui.SettingsScreen
import com.example.agent.ui.theme.AgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AgentTheme {
                val navController = rememberNavController()
                val vm: RemoteViewModel = viewModel()

                // Check if credentials are already saved
                val savedCreds = remember {
                    CredentialStore.load(applicationContext)
                }

                // Auto-connect if credentials exist
                LaunchedEffect(Unit) {
                    savedCreds?.let { (url, token) ->
                        vm.connect(url, token)
                    }
                }

                NavHost(
                    navController    = navController,
                    startDestination = if (savedCreds != null) "remote" else "login"
                ) {
                    composable("login") {
                        LoginScreen(
                            onConnected = {
                                navController.navigate("remote") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            vm = vm
                        )
                    }

                    composable("remote") {
                        RemoteScreen(
                            onOpenChat     = { navController.navigate("chat") },
                            onOpenSettings = { navController.navigate("settings") },
                            vm = vm
                        )
                    }

                    composable("chat") {
                        ChatScreen(
                            onBack = { navController.popBackStack() },
                            vm = vm
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onBack = { navController.popBackStack() },
                            onLogout = {
                                CredentialStore.clear(applicationContext)
                                vm.disconnect()
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            vm = vm
                        )
                    }
                }
            }
        }
    }
}