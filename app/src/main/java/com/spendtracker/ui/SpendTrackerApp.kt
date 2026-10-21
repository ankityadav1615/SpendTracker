package com.spendtracker.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.spendtracker.R
import com.spendtracker.export.ExportFormat
import com.spendtracker.export.ReportDocument
import com.spendtracker.model.ActivityFilter
import com.spendtracker.model.Category
import com.spendtracker.model.ReportSide
import com.spendtracker.model.TransactionType
import com.spendtracker.sms.SmsPermission
import com.spendtracker.ui.screens.ActivityScreen
import com.spendtracker.ui.screens.AppDocument
import com.spendtracker.ui.screens.DisclosureScreen
import com.spendtracker.ui.screens.DocumentScreen
import com.spendtracker.ui.screens.EntrySheet
import com.spendtracker.ui.screens.ExportSheet
import com.spendtracker.ui.screens.HomeScreen
import com.spendtracker.ui.screens.PeriodPanel
import com.spendtracker.ui.screens.PeriodPanels
import com.spendtracker.ui.screens.ReportChart
import com.spendtracker.ui.screens.ReportsScreen
import com.spendtracker.ui.screens.SettingsScreen
import com.spendtracker.ui.screens.TransactionSheet
import com.spendtracker.ui.screens.WelcomeScreen
import com.spendtracker.ui.screens.exportRange
import com.spendtracker.ui.theme.SpendTheme
import java.time.LocalDate

private enum class Tab(val label: String, @param:DrawableRes val icon: Int) {
    HOME("Home", R.drawable.ic_home),
    ACTIVITY("Activity", R.drawable.ic_list),
    REPORTS("Reports", R.drawable.ic_reports),
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
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        val action = current.action
        val result = snackbarHostState.showSnackbar(
            current.text,
            actionLabel = action?.label,
            duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) {
            when (action) {
                is UiMessage.Undo -> viewModel.undo(action)
                is UiMessage.Open -> openFile(context, action)
                null -> Unit
            }
        }
        viewModel.messageShown()
    }

    var filterKey by rememberSaveable { mutableStateOf(ActivityFilter.All.key) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var periodPanel by rememberSaveable { mutableStateOf(PeriodPanel.NONE) }
    var budgetRequested by rememberSaveable { mutableStateOf(false) }
    var reportSide by rememberSaveable { mutableStateOf(ReportSide.EXPENSES) }
    var reportChart by rememberSaveable { mutableStateOf(ReportChart.RING) }
    // The add sheet: closed, adding (editId null) or changing an added entry.
    var entryOpen by rememberSaveable { mutableStateOf(false) }
    var entryType by rememberSaveable { mutableStateOf(TransactionType.DEBIT) }
    var editId by rememberSaveable { mutableStateOf<Long?>(null) }
    var exportOpen by rememberSaveable { mutableStateOf(false) }
    // Categories of the download waiting for Android's "save as" screen; null means all.
    var exportCategories by rememberSaveable { mutableStateOf<List<String>?>(null) }

    val saveReport: (ExportFormat, Uri?) -> Unit = { format, uri ->
        val state = money
        if (uri != null && state != null) {
            val (first, last) = exportRange(state)
            val categories = exportCategories?.mapNotNull { name -> Category.entries.firstOrNull { it.name == name } }?.toSet()
            viewModel.export(uri, format, first, last, categories)
            exportOpen = false
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType)) {
        saveReport(ExportFormat.PDF, it)
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportFormat.CSV.mimeType)) {
        saveReport(ExportFormat.CSV, it)
    }
    val openEntry = { type: TransactionType ->
        editId = null
        entryType = type
        entryOpen = true
    }

    BackHandler(enabled = tab != Tab.HOME) { onTabChange(Tab.HOME) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { BottomBar(tab, onTabChange, onAdd = { openEntry(TransactionType.DEBIT) }) },
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

            Tab.REPORTS -> ReportsScreen(
                state = money,
                side = reportSide,
                chart = reportChart,
                contentPadding = padding,
                onSideChange = { reportSide = it },
                onChartChange = { reportChart = it },
                onOpenPeriods = { periodPanel = PeriodPanel.SHEET },
                onSelectPeriod = viewModel::selectPeriod,
                onDownload = { exportOpen = true },
                onOpenCategory = { category ->
                    filterKey = ActivityFilter.Of(category).key
                    onTabChange(Tab.ACTIVITY)
                },
                onAdd = { openEntry(if (reportSide == ReportSide.INCOME) TransactionType.CREDIT else TransactionType.DEBIT) },
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
            onEdit = {
                selectedId = null
                editId = selected.id
                entryOpen = true
            },
            onDelete = {
                viewModel.deleteEntry(selected)
                selectedId = null
            },
            coveredPaise = money?.cashCovered?.get(selected.id) ?: 0,
        )
    }

    if (entryOpen) {
        val editing = editId?.let { transactionsById[it] }
        EntrySheet(
            existing = editing,
            today = money?.today ?: LocalDate.now(),
            initialType = entryType,
            onSave = { entry ->
                viewModel.saveEntry(entry, editing)
                entryOpen = false
                editId = null
            },
            onDismiss = {
                entryOpen = false
                editId = null
            },
        )
    }

    val exportState = money
    if (exportOpen && exportState != null) {
        ExportSheet(
            state = exportState,
            exporting = exporting,
            onOpenPeriods = { periodPanel = PeriodPanel.SHEET },
            onExport = { format, categories ->
                exportCategories = categories?.map { it.name }
                val (first, last) = exportRange(exportState)
                val name = ReportDocument.fileName(first, last, format)
                try {
                    if (format == ExportFormat.PDF) pdfLauncher.launch(name) else csvLauncher.launch(name)
                } catch (_: ActivityNotFoundException) {
                    viewModel.say("This phone has no app for saving files")
                }
            },
            onDismiss = { exportOpen = false },
        )
    }

    // Last, so the period picker opened from the download sheet sits above it.
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
}

private fun openFile(context: Context, action: UiMessage.Open) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(action.uri, action.mimeType)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this phone can open this file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun BottomBar(tab: Tab, onTabChange: (Tab) -> Unit, onAdd: () -> Unit) {
    val colors = SpendTheme.colors
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column {
        HorizontalDivider(thickness = 1.dp, color = colors.line)
        NavigationBar(containerColor = colors.card, tonalElevation = 0.dp) {
            Tab.entries.forEach { item ->
                if (item == Tab.REPORTS) AddButton(onAdd)
                NavigationBarItem(
                    selected = item == tab,
                    onClick = { onTabChange(item) },
                    icon = { Icon(painterResource(item.icon), contentDescription = null, modifier = Modifier.size(24.dp)) },
                    label = { Text(item.label, style = MaterialTheme.typography.labelMedium) },
                    colors = itemColors,
                )
            }
        }
    }
}

/** The centre "+" in the bottom bar: add a cash or any other spend by hand. */
@Composable
private fun RowScope.AddButton(onAdd: () -> Unit) {
    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(56.dp)
                .shadow(6.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .clickable(role = Role.Button, onClickLabel = "Add expense", onClick = onAdd)
                .semantics { contentDescription = "Add expense" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_plus),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
