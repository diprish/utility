package com.diprish.utilitymeter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.diprish.utilitymeter.ui.detail.MeterDetailScreen
import com.diprish.utilitymeter.ui.meters.MetersListScreen
import com.diprish.utilitymeter.ui.reading.AddReadingScreen

private object Routes {
    const val METERS = "meters"
    const val METER_DETAIL = "meter/{meterId}"
    const val ADD_READING = "meter/{meterId}/add"

    fun detail(meterId: Long) = "meter/$meterId"
    fun addReading(meterId: Long) = "meter/$meterId/add"
}

@Composable
fun UtilityMeterNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.METERS) {
        composable(Routes.METERS) {
            MetersListScreen(
                onOpenMeter = { meterId -> navController.navigate(Routes.detail(meterId)) },
            )
        }

        composable(
            route = Routes.METER_DETAIL,
            arguments = listOf(navArgument("meterId") { type = NavType.LongType }),
        ) {
            MeterDetailScreen(
                onBack = { navController.popBackStack() },
                onAddReading = { meterId -> navController.navigate(Routes.addReading(meterId)) },
            )
        }

        composable(
            route = Routes.ADD_READING,
            arguments = listOf(navArgument("meterId") { type = NavType.LongType }),
        ) {
            AddReadingScreen(
                onDone = { navController.popBackStack() },
            )
        }
    }
}
