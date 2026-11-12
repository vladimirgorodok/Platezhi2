package ru.platezhi2.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.platezhi2.app.core.AppContainer
import ru.platezhi2.app.ui.data.DataScreen
import ru.platezhi2.app.ui.edit.PaymentEditScreen
import ru.platezhi2.app.ui.home.HomeScreen
import ru.platezhi2.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val EDIT = "edit"
    const val DATA = "data"
    const val SETTINGS = "settings"
    const val ARG_ID = "id"

    fun edit(id: Long): String = "$EDIT/$id"
    val editPattern: String = "$EDIT/{$ARG_ID}"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onAddPayment = { navController.navigate(Routes.edit(0L)) },
                onEditPayment = { id -> navController.navigate(Routes.edit(id)) },
                onOpenData = { navController.navigate(Routes.DATA) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.editPattern,
            arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType }),
        ) {
            PaymentEditScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.DATA) {
            DataScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
