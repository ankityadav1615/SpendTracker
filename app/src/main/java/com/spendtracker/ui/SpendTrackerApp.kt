package com.spendtracker.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendtracker.R
import com.spendtracker.model.ActivityFilter
import com.spendtracker.sms.SmsPermission
import com.spendtracker.ui.screens.ActivityScreen
import com.spendtracker.ui.screens.AppDocument
import com.spendtracker.ui.screens.DisclosureScreen
import com.spendtracker.ui.screens.DocumentScreen
import com.spendtracker.ui.screens.HomeScreen
import com.spendtracker.ui.screens.PeriodPanel
import com.spendtracker.ui.screens.PeriodPanels
import com.spendtracker.ui.screens.SettingsScreen
import com.spendtracker.ui.screens.TransactionSheet
import com.spendtracker.ui.screens.WelcomeScreen
import com.spendtracker.ui.theme.SpendTheme
import java.time.LocalDate

private enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    HOME("Home", R.drawable.ic_home),
    ACTIVITY("Activity", R.drawable.ic_list),
    SETTINGS("Settings", R.drawable.ic_sliders),
}

@Composable
fun SpendTrackerApp(viewModel: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(SmsPermission.granted(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = SmsPermission.granted(context)
        if (hasPermission) viewModel.importSms(userRequested = false)
    }
    // Also covers the user changing the permission in Android settings and coming back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = SmsPermission.granted(context)
        viewModel.onResume()
    }
    val requestPermission = { permissionLauncher.launch(SmsPermission.permissions) }
    val openAppSettings = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
        )
    }

    var document by rememberSaveable { mutableStateOf<AppDocument?>(null) }
    var disclosureOpen by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    LaunchedEffect(settings.introDone) {
        if (!settings.introDone) tab = Tab.HOME
    }

    val openDocument = document
    when {
        openDocument != null -> {
            BackHandler { document = null }
            DocumentScreen(openDocument, onBack = { document = null })
        }

        !settings.introDone -> Onboarding(
            viewModel = viewModel,
            onRequestPermission = requestPermission,
            onOpenPrivacy = { document = AppDocument.PRIVACY },
        )

        disclosureOpen -> {
            BackHandler { disclosureOpen = false }
            DisclosureScreen(
                showProgress = false,
                onAllow = {
                    viewModel.acceptSms()
                    disclosureOpen = false
                    requestPermission()
                },
                onNotNow = { disclosureOpen = false },
                onPrivacy = { document = AppDocument.PRIVACY },
            )
        }

        else -> MainScreens(
            viewModel = viewModel,
            tab = tab,
            onTabChange = { tab = it },
            hasPermission = hasPermission,
            onSetUpSms = { disclosureOpen = true },
            onRequestPermission = requestPermission,
            onOpenAppSettings = openAppSettings,
            onOpenDocument = { document = it },
        )
    }
}

@Composable
private fun Onboarding(viewModel: MainViewModel, onRequestPermission: () -> Unit, onOpenPrivacy: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableIntStateOf(if (settings.incomeType != null) 1 else 0) }
    var picked by rememberSaveable { mutableStateOf(settings.incomeType) }
    if (step == 0) {
        WelcomeScreen(
            selected = picked,
            onSelect = { picked = it },
            onContinue = {
                picked?.let(viewModel::chooseIncomeType)
                step = 1
            },
        )
    } else {
        BackHandler { step = 0 }
        DisclosureScreen(
            showProgress = true,
            onAllow = {
                viewModel.acceptSms()
                onRequestPermission()
            },
            onNotNow = viewModel::skipSms,
            onPrivacy = onOpenPrivacy,
        )
    }
}

@Composable
private fun MainScreens(
    viewModel: MainViewModel,
    tab: Tab,
    onTabChange: (Tab) -> Unit,
    hasPermission: Boolean,
    onSetUpSms: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenDocument: (AppDocument) -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val money by viewModel.money.collectAsStateWithLifecycle()
    val transactionsById by viewModel.transactionsById.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.messageShown()
    }

    var filterKey by rememberSaveable { mutableStateOf(ActivityFilter.All.key) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var periodPanel by rememberSaveable { mutableStateOf(PeriodPanel.NONE) }
    var budgetRequested by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = tab != Tab.HOME) { onTabChange(Tab.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { BottomBar(tab, onTabChange) },
    ) { padding ->
        when (tab) {
            Tab.HOME -> HomeScreen(
                state = money,
                settings = settings,
                hasPermission = hasPermission,
                importing = importing,
                contentPadding = padding,
                onOpenPeriods = { periodPanel = PeriodPanel.SHEET },
                onSelectPeriod = viewModel::selectPeriod,
                onSetUpSms = onSetUpSms,
                onRequestPermission = onRequestPermission,
                onOpenAppSettings = onOpenAppSettings,
                onSetBudget = {
                    budgetRequested = true
                    onTabChange(Tab.SETTINGS)
                },
                onOpenCategory = { category ->
                    filterKey = ActivityFilter.Of(category).key
                    onTabChange(Tab.ACTIVITY)
                },
            )

            Tab.ACTIVITY -> ActivityScreen(
                state = money,
                filter = ActivityFilter.fromKey(filterKey),
                searching = searching,
                query = query,
                contentPadding = padding,
                onOpenPeriods = { periodPanel = PeriodPanel.SHEET },
                onFilterChange = { filterKey = it.key },
                onSearchingChange = {
                    searching = it
                    if (!it) query = ""
                },
                onQueryChange = { query = it },
                onTransactionClick = { selectedId = it.id },
            )

            Tab.SETTINGS -> SettingsScreen(
                settings = settings,
                hasPermission = hasPermission,
                importing = importing,
                contentPadding = padding,
                onChooseIncomeType = viewModel::chooseIncomeType,
                onSetPeriodMode = viewModel::setPeriodMode,
                onSetBudget = viewModel::setBudget,
                onSetUpSms = onSetUpSms,
                onRequestPermission = onRequestPermission,
                onStopSms = viewModel::stopSms,
                onOpenAppSettings = onOpenAppSettings,
                onRescan = { viewModel.importSms(userRequested = true) },
                onOpenPrivacy = { onOpenDocument(AppDocument.PRIVACY) },
                onOpenLicences = { onOpenDocument(AppDocument.LICENCES) },
                onDeleteAll = viewModel::deleteAllData,
                budgetRequested = budgetRequested,
                onBudgetRequestHandled = { budgetRequested = false },
            )
        }
        // Lists scroll edge to edge; keep the status bar icons on a solid background.
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.background),
        )
    }

    money?.let { state ->
        PeriodPanels(
            panel = periodPanel,
            period = state.period,
            options = state.options,
            today = state.today,
            onPanelChange = { periodPanel = it },
            onSelect = viewModel::selectPeriod,
        )
    }

    val selected = selectedId?.let { transactionsById[it] }
    if (selected != null) {
        TransactionSheet(
            transaction = selected,
            today = money?.today ?: LocalDate.now(),
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

@Composable
private fun BottomBar(tab: Tab, onTabChange: (Tab) -> Unit) {
    val colors = SpendTheme.colors
    Column {
        HorizontalDivider(thickness = 1.dp, color = colors.line)
        NavigationBar(containerColor = colors.card, tonalElevation = 0.dp) {
            Tab.entries.forEach { item ->
                NavigationBarItem(
                    selected = item == tab,
                    onClick = { onTabChange(item) },
                    icon = { Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.primary,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}
