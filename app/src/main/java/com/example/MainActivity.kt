package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.PulseDatabase
import com.example.data.PulseRepository
import com.example.ui.PulseViewModel
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MeasureScreen
import com.example.ui.theme.PulseTheme
import kotlinx.coroutines.launch

private enum class PulseTab(
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
) {
    MEASURE(R.string.nav_measure, Icons.Rounded.Favorite, Icons.Outlined.FavoriteBorder, "nav_measure"),
    HISTORY(R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History, "nav_history"),
    ANALYTICS(R.string.nav_analytics, Icons.Rounded.Analytics, Icons.Outlined.Analytics, "nav_analytics"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PulseTheme {
                PulseAppShell()
            }
        }
    }
}

@Composable
private fun PulseAppShell() {
    val viewModel: PulseViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val database = PulseDatabase.getInstance(app)
                PulseViewModel(PulseRepository(database.pulseDao()))
            }
        },
    )

    val measurementsState by viewModel.measurementsState.collectAsStateWithLifecycle()
    val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
    val isCameraScanning by viewModel.isCameraScanning.collectAsStateWithLifecycle()
    val torchEnabled by viewModel.torchEnabled.collectAsStateWithLifecycle()
    val ppgSnapshot by viewModel.ppgSnapshot.collectAsStateWithLifecycle()
    val tapState by viewModel.tapState.collectAsStateWithLifecycle()
    val filterContext by viewModel.filterContext.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(PulseTab.MEASURE) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val savedMsg = stringResource(R.string.saved_snackbar)
    val deletedMsg = stringResource(R.string.deleted_snackbar)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (currentTab != PulseTab.MEASURE) {
        BackHandler {
            currentTab = PulseTab.MEASURE
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                    ) {
                        PulseTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    if (tab != PulseTab.MEASURE) {
                                        viewModel.stopCameraScan()
                                    }
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.navigationBars),
                    ) {
                        PulseTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (tab != PulseTab.MEASURE) {
                                        viewModel.stopCameraScan()
                                    }
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes),
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag(tab.tag),
                            )
                        }
                    }
                }

                when (currentTab) {
                    PulseTab.MEASURE -> MeasureScreen(
                        selectedMode = selectedMode,
                        isCameraScanning = isCameraScanning,
                        torchEnabled = torchEnabled,
                        ppgSnapshot = ppgSnapshot,
                        tapState = tapState,
                        isSaving = isSaving,
                        onSelectMode = viewModel::selectMode,
                        onStartCameraScan = viewModel::startCameraScan,
                        onStopCameraScan = viewModel::stopCameraScan,
                        onToggleTorch = viewModel::toggleTorch,
                        onCameraFrameSample = viewModel::onCameraFrameSample,
                        onCameraError = viewModel::onCameraError,
                        onRecordTap = { viewModel.recordTap() },
                        onResetTap = viewModel::resetTapTempo,
                        onSaveMeasurement = { bpm, ctx, source, confidence, note ->
                            viewModel.saveMeasurement(
                                bpm = bpm,
                                contextTag = ctx,
                                source = source,
                                confidence = confidence,
                                note = note,
                                onSaved = {
                                    currentTab = PulseTab.HISTORY
                                    scope.launch { snackbarHostState.showSnackbar(savedMsg) }
                                },
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )

                    PulseTab.HISTORY -> HistoryScreen(
                        measurementsState = measurementsState,
                        selectedFilter = filterContext,
                        isSaving = isSaving,
                        onSelectFilter = viewModel::setFilterContext,
                        onUpdateMeasurement = { id, ctx, note ->
                            viewModel.updateMeasurement(id, ctx, note) {}
                        },
                        onDeleteMeasurement = { id ->
                            viewModel.deleteMeasurement(id, deletedMsg)
                        },
                        modifier = Modifier.weight(1f),
                    )

                    PulseTab.ANALYTICS -> AnalyticsScreen(
                        measurementsState = measurementsState,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
