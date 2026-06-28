package com.preichert.blecustomscanner

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.navigation.toRoute
import com.preichert.blecustomscanner.ble.BleController
import com.preichert.blecustomscanner.ui.DeviceDetailRoot
import com.preichert.blecustomscanner.ui.DeviceDetailRoute
import com.preichert.blecustomscanner.ui.DeviceDetailViewModel
import com.preichert.blecustomscanner.ui.ScannerGraph
import com.preichert.blecustomscanner.ui.ScannerRoot
import com.preichert.blecustomscanner.ui.ScannerRoute
import com.preichert.blecustomscanner.ui.ScannerViewModel
import com.preichert.blecustomscanner.ui.theme.BleScannerTheme
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun App() {
    val controller = koinInject<BleController>()
    val systemDark = isSystemInDarkTheme()
    var isDarkMode by remember { mutableStateOf(systemDark) }

    BleScannerTheme(isDarkMode = isDarkMode) {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = ScannerGraph,
        ) {
            scannerGraph(
                controller = controller,
                navController = navController,
                isDarkMode = isDarkMode,
                onToggleTheme = { isDarkMode = !isDarkMode },
            )
            deviceDetailGraph(
                controller = controller,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

fun NavGraphBuilder.scannerGraph(
    controller: BleController,
    navController: NavController,
    isDarkMode: Boolean,
    onToggleTheme: () -> Unit,
) {
    navigation<ScannerGraph>(startDestination = ScannerRoute) {
        composable<ScannerRoute> {
            ScannerRoot(
                onNavigateToDevice = { device ->
                    navController.navigate(DeviceDetailRoute(deviceId = device.id))
                },
                isDarkMode = isDarkMode,
                onToggleTheme = onToggleTheme,
                viewModel = koinViewModel<ScannerViewModel>(
                    parameters = { parametersOf(controller) },
                )
            )
        }
    }
}

fun NavGraphBuilder.deviceDetailGraph(
    controller: BleController,
    onBack: () -> Unit,
) {
    composable<DeviceDetailRoute> { backStackEntry ->
        val route: DeviceDetailRoute = backStackEntry.toRoute()
        DeviceDetailRoot(
            onBack = onBack,
            viewModel = koinViewModel<DeviceDetailViewModel>(
                parameters = { parametersOf(controller, route.deviceId) },
            )
        )
    }
}
