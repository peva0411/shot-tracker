package com.shottracker

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shottracker.ui.datacollection.DataCollectionScreen
import com.shottracker.ui.history.HistoryScreen
import com.shottracker.ui.home.HomeScreen
import com.shottracker.ui.library.LibraryScreen
import com.shottracker.ui.session.SessionScreen
import com.shottracker.ui.summary.SummaryScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Session : Screen("session")
    object Summary : Screen("summary/{sessionId}") {
        fun createRoute(sessionId: Long) = "summary/$sessionId"
    }
    object History : Screen("history")
    object Library : Screen("library")
    object DataCollection : Screen("data_collection")
}

@Composable
fun ShotTrackerApp(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartSession = {
                    navController.navigate(Screen.Session.route)
                },
                onViewHistory = {
                    navController.navigate(Screen.History.route)
                },
                onViewLibrary = {
                    navController.navigate(Screen.Library.route)
                },
                onDataCollection = {
                    navController.navigate(Screen.DataCollection.route)
                }
            )
        }

        composable(Screen.Session.route) {
            SessionScreen(
                onEndSession = { sessionId ->
                    navController.navigate(Screen.Summary.createRoute(sessionId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.Summary.route) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId")?.toLongOrNull() ?: 0L
            SummaryScreen(
                sessionId = sessionId,
                onSave = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onDiscard = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Library.route) {
            LibraryScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DataCollection.route) {
            DataCollectionScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
