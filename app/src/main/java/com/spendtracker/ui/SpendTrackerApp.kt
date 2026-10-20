package com.spendtracker.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendtracker.model.Category
import com.spendtracker.sms.SmsPermission
import com.spendtracker.ui.screens.DashboardScreen
import com.spendtracker.ui.screens.TransactionSheet
import com.spendtracker.ui.screens.TransactionsScreen

private enum class Screen { DASHBOARD, TRANSACTIONS }

@Composable
fun SpendTrackerApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(SmsPermission.granted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = SmsPermission.granted(context)
        if (hasPermission) viewModel.importSms(userRequested = false)
    }
    // Also covers the user granting the permission from system settings and coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = SmsPermission.granted(context)
        if (hasPermission) viewModel.importSms(userRequested = false)
    }

    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val transactionsById by viewModel.transactionsById.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.messageShown()
    }

    var screen by rememberSaveable { mutableStateOf(Screen.DASHBOARD) }
    var filter by rememberSaveable { mutableStateOf<Category?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }

    when (screen) {
        Screen.DASHBOARD -> DashboardScreen(
            state = dashboard,
            hasPermission = hasPermission,
            importing = importing,
            snackbarHostState = snackbarHostState,
            onRequestPermission = { permissionLauncher.launch(SmsPermission.permissions) },
            onOpenSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            onRescan = { viewModel.importSms(userRequested = true) },
            onPreviousPeriod = viewModel::previousPeriod,
            onNextPeriod = viewModel::nextPeriod,
            onOpenTransactions = { category ->
                filter = category
                screen = Screen.TRANSACTIONS
            },
            onTransactionClick = { selectedId = it.id },
        )

        Screen.TRANSACTIONS -> {
            BackHandler { screen = Screen.DASHBOARD }
            TransactionsScreen(
                state = dashboard,
                filter = filter,
                snackbarHostState = snackbarHostState,
                onFilterChange = { filter = it },
                onBack = { screen = Screen.DASHBOARD },
                onTransactionClick = { selectedId = it.id },
            )
        }
    }

    val selected = selectedId?.let { transactionsById[it] }
    if (selected != null) {
        TransactionSheet(
            transaction = selected,
            onDismiss = { selectedId = null },
            onCategorySelected = { category, applyToSimilar ->
                viewModel.updateCategory(selected, category, applyToSimilar)
            },
            onHide = {
                viewModel.hide(selected)
                selectedId = null
            },
        )
    }
}
