package com.mj.spendwise.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mj.spendwise.backend.SyncStatus
import com.mj.spendwise.ui.components.OfflineBanner
import com.mj.spendwise.ui.components.SyncChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mj.spendwise.viewmodel.ExpenseViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.mj.spendwise.ui.screens.alerts.AlertsScreen
import com.mj.spendwise.ui.screens.analytics.AnalyticsScreen
import com.mj.spendwise.ui.screens.chat.ChatScreen
import com.mj.spendwise.ui.screens.dashboard.DashboardScreen
import com.mj.spendwise.ui.screens.expenses.AddEditExpenseScreen
import com.mj.spendwise.ui.screens.expenses.ExpenseDetailScreen
import com.mj.spendwise.ui.screens.expenses.ExpenseListScreen
import com.mj.spendwise.ui.screens.map.MapScreen
import com.mj.spendwise.ui.screens.settings.SettingsScreen
import com.mj.spendwise.ui.screens.splash.SplashScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    Tab(Routes.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
    Tab(Routes.EXPENSES, "Expenses", Icons.Default.Receipt),
    Tab(Routes.ANALYTICS, "Analytics", Icons.Default.BarChart),
    Tab(Routes.MAP, "Map", Icons.Default.Map),
    Tab(Routes.CHAT, "Assistant", Icons.AutoMirrored.Filled.Chat)
)

/** Title shown in the top bar for each destination. */
private fun titleFor(route: String?): String = when {
    route == null -> ""
    route.startsWith("add_expense") -> "Add / Edit expense"
    route.startsWith("expense_detail") -> "Expense"
    else -> when (route) {
        Routes.DASHBOARD -> "SpendWise"
        Routes.EXPENSES -> "Expenses"
        Routes.ANALYTICS -> "Analytics"
        Routes.MAP -> "Map"
        Routes.CHAT -> "AI Assistant (demo)"
        Routes.ALERTS -> "Alerts"
        Routes.SETTINGS -> "Settings"
        else -> ""
    }
}

/**
 * Root composable: one Scaffold (top bar, bottom bar, FAB) wrapped around the NavHost.
 * The bars are shown/hidden based on the current route.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendWiseNavHost(navController: NavHostController = rememberNavController()) {
    // Activity-scoped: one instance (and one Firestore listener) shared by every screen.
    val expenseVm: ExpenseViewModel = viewModel()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val isTab = route in Routes.tabs
    val showTopBar = route != null && route != Routes.SPLASH
    val syncStatus by expenseVm.syncStatus.collectAsStateWithLifecycle()
    val pending by expenseVm.pendingCount.collectAsStateWithLifecycle()
    val syncPaused by expenseVm.syncPaused.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(titleFor(route)) },
                    navigationIcon = {
                        // Tabs are top-level; every other screen gets a back arrow.
                        if (!isTab) {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    },
                    actions = {
                        if (isTab) {
                            SyncChip(syncStatus, pending, compact = true)
                            IconButton(onClick = { navController.navigate(Routes.ALERTS) }) {
                                // Unread count is a placeholder until alerts exist (Phase 8).
                                BadgedBox(badge = { Badge { Text("2") } }) {
                                    Icon(Icons.Default.Notifications, contentDescription = "Alerts")
                                }
                            }
                            IconButton(onClick = { navController.navigate(Routes.SETTINGS) }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (route == Routes.DASHBOARD || route == Routes.EXPENSES) {
                FloatingActionButton(onClick = { navController.navigate(Routes.addExpense()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add expense")
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding)) {
        OfflineBanner(
            visible = showTopBar && syncStatus == SyncStatus.OFFLINE,
            message = if (syncPaused) "Sync paused (Wi-Fi only) — changes will sync on Wi-Fi."
            else "You're offline — changes will sync automatically."
        )
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
            modifier = Modifier.weight(1f)
        ) {
            // 1. Splash -> Main. popUpTo(splash, inclusive) removes splash so Back exits the app.
            composable(Routes.SPLASH) {
                SplashScreen(
                    awaitReady = {
                        // Wait for sign-in + seeding, but never more than 6 s (e.g. offline first launch).
                        withTimeoutOrNull(6_000) {
                            expenseVm.startup.first { it !is ExpenseViewModel.Startup.Loading }
                        }
                    },
                    onDone = {
                        navController.navigate(Routes.MAIN_GRAPH) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            // 3. Nested graph #1: the five bottom-nav tabs.
            navigation(route = Routes.MAIN_GRAPH, startDestination = Routes.DASHBOARD) {
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        expenseVm,
                        onScanReceipt = { navController.navigate(Routes.addExpense(scan = true)) },
                        onOpenExpense = { navController.navigate(Routes.expenseDetail(it)) }
                    )
                }
                composable(Routes.EXPENSES) {
                    ExpenseListScreen(expenseVm, onOpenExpense = { navController.navigate(Routes.expenseDetail(it)) })
                }
                composable(Routes.ANALYTICS) { AnalyticsScreen(expenseVm) }
                composable(Routes.MAP) { MapScreen() }
                composable(Routes.CHAT) { ChatScreen() }
            }

            // 3. Nested graph #2: add/edit + detail, with 4. arguments.
            navigation(route = Routes.EXPENSE_FLOW, startDestination = Routes.ADD_EXPENSE) {
                composable(
                    route = Routes.ADD_EXPENSE,
                    arguments = listOf(
                        navArgument(Routes.ARG_SCAN) { type = NavType.BoolType; defaultValue = false },
                        navArgument(Routes.ARG_EDIT_ID) { type = NavType.StringType; nullable = true; defaultValue = null }
                    )
                ) { entry ->
                    AddEditExpenseScreen(
                        vm = expenseVm,
                        scan = entry.arguments?.getBoolean(Routes.ARG_SCAN) ?: false,
                        editId = entry.arguments?.getString(Routes.ARG_EDIT_ID),
                        onSaved = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Routes.EXPENSE_DETAIL,
                    arguments = listOf(navArgument(Routes.ARG_EXPENSE_ID) { type = NavType.StringType })
                ) { entry ->
                    val id = entry.arguments?.getString(Routes.ARG_EXPENSE_ID).orEmpty()
                    ExpenseDetailScreen(
                        vm = expenseVm,
                        expenseId = id,
                        onEdit = { navController.navigate(Routes.addExpense(editId = id)) },
                        onDeleted = { navController.popBackStack() }
                    )
                }
            }

            composable(Routes.ALERTS) { AlertsScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(expenseVm) }
        }
        }
    }
}

/** Standard bottom-nav behaviour: one copy of each tab, state saved/restored when switching. */
private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
