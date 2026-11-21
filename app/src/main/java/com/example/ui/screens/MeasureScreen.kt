package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.HeartRateZone
import com.example.data.MeasurementContext
import com.example.data.MeasurementSource
import com.example.ppg.CameraPpgPreview
import com.example.ppg.PpgPhase
import com.example.ppg.PpgSnapshot
import com.example.ui.TapTempoState
import com.example.ui.components.MeasurementGuideSheet
import com.example.ui.components.PulseWaveformCanvas
import com.example.ui.components.SaveMeasurementDialog
import kotlinx.coroutines.delay

@Composable
fun MeasureScreen(
    selectedMode: MeasurementSource,
    isCameraScanning: Boolean,
    torchEnabled: Boolean,
    ppgSnapshot: PpgSnapshot,
    tapState: TapTempoState,
    isSaving: Boolean,
    onSelectMode: (MeasurementSource) -> Unit,
    onStartCameraScan: () -> Unit,
    onStopCameraScan: () -> Unit,
    onToggleTorch: () -> Unit,
    onCameraFrameSample: (Double, Double, Double, Long) -> Unit,
    onCameraError: (String) -> Unit,
    onRecordTap: () -> Unit,
    onResetTap: () -> Unit,
    onSaveMeasurement: (bpm: Int, context: MeasurementContext, source: MeasurementSource, confidence: Int, note: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedOnce by remember { mutableStateOf(false) }
    var showGuideSheet by remember { mutableStateOf(false) }
    var pendingSaveBpm by remember { mutableStateOf<Triple<Int, MeasurementSource, Int>?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            permissionDeniedOnce = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header row with title and guide button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.measure_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                IconButton(
                    onClick = { showGuideSheet = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("help_guide_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                        contentDescription = stringResource(R.string.cd_help),
                    )
                }
            }

            // Mode switcher
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth(),
            ) {
                MeasurementSource.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = selectedMode == mode,
                        onClick = { onSelectMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = MeasurementSource.entries.size,
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("mode_${mode.name.lowercase()}"),
                    ) {
                        Text(
                            text = stringResource(mode.labelRes),
                            maxLines = 1,
                        )
                    }
                }
            }

            when (selectedMode) {
                MeasurementSource.CAMERA_PPG -> {
                    if (!hasCameraPermission) {
                        CameraPermissionCard(
                            permissionDenied = permissionDeniedOnce,
                            onRequestPermission = {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            onSwitchToTap = { onSelectMode(MeasurementSource.TAP_TEMPO) },
                        )
                    } else {
                        CameraPpgSection(
                            isScanning = isCameraScanning,
                            torchEnabled = torchEnabled,
                            snapshot = ppgSnapshot,
                            onStartScan = onStartCameraScan,
                            onStopScan = onStopCameraScan,
                            onToggleTorch = onToggleTorch,
                            onFrameSample = onCameraFrameSample,
                            onCameraError = onCameraError,
                            onSaveRequest = { bpm, confidence ->
                                pendingSaveBpm = Triple(bpm, MeasurementSource.CAMERA_PPG, confidence)
                            },
                        )
                    }
                }

                MeasurementSource.TAP_TEMPO -> {
                    TapTempoSection(
                        tapState = tapState,
                        onTap = onRecordTap,
                        onReset = onResetTap,
                        onSaveRequest = { bpm, confidence ->
                            pendingSaveBpm = Triple(bpm, MeasurementSource.TAP_TEMPO, confidence)
                        },
                    )
                }

                MeasurementSource.MANUAL -> {
                    ManualEntrySection(
                        isSaving = isSaving,
                        onSave = { bpm, ctx, note ->
                            onSaveMeasurement(bpm, ctx, MeasurementSource.MANUAL, 100, note)
                        },
                    )
                }
            }

            // Non-medical disclaimer
            Text(
                text = stringResource(R.string.app_disclaimer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
            )
        }
    }

    if (showGuideSheet) {
        MeasurementGuideSheet(onDismiss = { showGuideSheet = false })
    }

    pendingSaveBpm?.let { (bpm, source, confidence) ->
        SaveMeasurementDialog(
            bpm = bpm,
            isSaving = isSaving,
            onDismiss = { pendingSaveBpm = null },
            onConfirm = { ctx, note ->
                onSaveMeasurement(bpm, ctx, source, confidence, note)
                pendingSaveBpm = null
            },
        )
    }
}

@Composable
private fun CameraPermissionCard(
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onSwitchToTap: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_finger_guide_1795263413248),
                contentDescription = stringResource(R.string.cd_finger_guide),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Icon(
                imageVector = Icons.Rounded.CameraAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.camera_permission_title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    if (permissionDenied) R.string.camera_permission_denied
                    else R.string.camera_permission_desc
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("grant_camera_permission_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.camera_permission_grant))
            }
            if (permissionDenied) {
                OutlinedButton(
                    onClick = onSwitchToTap,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.mode_tap_tempo))
                }
            }
        }
    }
}

@Composable
private fun CameraPpgSection(
    isScanning: Boolean,
    torchEnabled: Boolean,
    snapshot: PpgSnapshot,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onToggleTorch: () -> Unit,
    onFrameSample: (Double, Double, Double, Long) -> Unit,
    onCameraError: (String) -> Unit,
    onSaveRequest: (bpm: Int, confidence: Int) -> Unit,
) {
    var beatPulse by remember { mutableStateOf(false) }
    LaunchedEffect(snapshot.beatTick) {
        if (snapshot.beatTick > 0L) {
            beatPulse = true
            delay(130L)
            beatPulse = false
        }
    }
    val heartScale by animateFloatAsState(
        targetValue = if (beatPulse) 1.24f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 450f),
        label = "heartBeatScale",
    )

    val displayedBpm = snapshot.finalBpm ?: snapshot.liveBpm
    val activeZone = displayedBpm?.let { HeartRateZone.forBpm(it) }
    val ringColor = activeZone?.color ?: MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val animatedProgress by animateFloatAsState(
        targetValue = snapshot.progress,
        label = "scanProgress",
    )

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Circular PPG Sensor Ring + Live Camera Feed / Readout
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }

                Box(
                    modifier = Modifier
                        .size(182.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isScanning) {
                        CameraPpgPreview(
                            isScanning = isScanning,
                            torchEnabled = torchEnabled,
                            onFrameSample = onFrameSample,
                            onCameraError = onCameraError,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = stringResource(R.string.cd_heart_icon),
                            tint = ringColor,
                            modifier = Modifier
                                .size(30.dp)
                                .scale(heartScale),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayedBpm?.toString() ?: "--",
                            style = MaterialTheme.typography.displayLarge,
                            color = if (isScanning) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.bpm_unit),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isScanning) Color.White.copy(alpha = 0.85f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Status prompt + zone pill
            val statusText = when (snapshot.phase) {
                PpgPhase.IDLE -> stringResource(R.string.status_idle)
                PpgPhase.NO_FINGER -> stringResource(R.string.status_no_finger)
                PpgPhase.CALIBRATING -> stringResource(R.string.status_calibrating)
                PpgPhase.MEASURING -> stringResource(R.string.status_measuring, snapshot.remainingSeconds)
                PpgPhase.COMPLETED -> stringResource(R.string.status_complete)
            }

            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium,
                color = if (snapshot.phase == PpgPhase.NO_FINGER) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val qualityText = when {
                    !snapshot.fingerDetected -> "—"
                    snapshot.signalQuality >= 75 -> stringResource(R.string.signal_quality_good)
                    snapshot.signalQuality >= 50 -> stringResource(R.string.signal_quality_medium)
                    else -> stringResource(R.string.signal_quality_weak)
                }
                Text(
                    text = "${stringResource(R.string.signal_quality_label)}: $qualityText",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                activeZone?.let { zone ->
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = zone.color.copy(alpha = 0.16f),
                    ) {
                        Text(
                            text = stringResource(zone.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            color = zone.color,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            // Live PPG waveform monitor
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.background,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PulseWaveformCanvas(
                    samples = snapshot.waveform,
                    activeColor = ringColor,
                    modifier = Modifier.padding(8.dp),
                )
            }

            // Action controls
            AnimatedVisibility(visible = snapshot.phase == PpgPhase.COMPLETED && snapshot.finalBpm != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onStartScan,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("measure_again_button"),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.btn_measure_again))
                    }
                    Button(
                        onClick = {
                            snapshot.finalBpm?.let { bpm ->
                                onSaveRequest(bpm, snapshot.signalQuality)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("save_ppg_result_button"),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(stringResource(R.string.btn_save_result))
                    }
                }
            }

            if (snapshot.phase != PpgPhase.COMPLETED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = if (isScanning) onStopScan else onStartScan,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanning) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            contentColor = if (isScanning) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onPrimary,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .testTag("start_stop_scan_button"),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            imageVector = if (isScanning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(
                                if (isScanning) R.string.btn_stop_measure else R.string.btn_start_measure
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (torchEnabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onToggleTorch)
                            .testTag("torch_toggle_button"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (torchEnabled) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                contentDescription = stringResource(R.string.cd_torch_toggle),
                                tint = if (torchEnabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TapTempoSection(
    tapState: TapTempoState,
    onTap: () -> Unit,
    onReset: () -> Unit,
    onSaveRequest: (bpm: Int, confidence: Int) -> Unit,
) {
    var pulse by remember { mutableStateOf(false) }
    LaunchedEffect(tapState.beatTick) {
        if (tapState.beatTick > 0L) {
            pulse = true
            delay(100L)
            pulse = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pulse) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "tapScale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val zone = tapState.liveBpm?.let { HeartRateZone.forBpm(it) }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.tap_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = tapState.liveBpm?.toString() ?: "--",
                    style = MaterialTheme.typography.displayLarge,
                    color = zone?.color ?: MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.bpm_unit),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            Box(
                modifier = Modifier
                    .size(188.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.primaryContainer,
                            )
                        )
                    )
                    .border(3.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = ripple(bounded = true),
                        onClick = onTap,
                    )
                    .testTag("tap_tempo_circle_button"),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.TouchApp,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.tap_button_label),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Text(
                        text = stringResource(R.string.tap_count_format, tapState.tapCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onReset,
                    enabled = tapState.tapCount > 0,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("tap_reset_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.tap_reset))
                }
                Button(
                    onClick = {
                        tapState.liveBpm?.let { bpm ->
                            onSaveRequest(bpm, tapState.confidence)
                        }
                    },
                    enabled = tapState.isReadyToSave,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("tap_save_button"),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.btn_save_result))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualEntrySection(
    isSaving: Boolean,
    onSave: (bpm: Int, context: MeasurementContext, note: String?) -> Unit,
) {
    var bpmText by remember { mutableStateOf("") }
    var selectedContext by remember { mutableStateOf(MeasurementContext.RESTING) }
    var note by remember { mutableStateOf("") }

    val parsedBpm = bpmText.toIntOrNull()
    val isValidBpm = parsedBpm != null && parsedBpm in 30..240
    val showError = bpmText.isNotEmpty() && !isValidBpm

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = bpmText,
                onValueChange = { input ->
                    if (input.length <= 3 && input.all { it.isDigit() }) {
                        bpmText = input
                    }
                },
                label = { Text(stringResource(R.string.manual_bpm_label)) },
                isError = showError,
                supportingText = if (showError) {
                    { Text(stringResource(R.string.manual_bpm_error)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_bpm_input"),
            )

            Text(
                text = stringResource(R.string.context_selector_label),
                style = MaterialTheme.typography.labelLarge,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MeasurementContext.entries.forEach { ctx ->
                    val selected = selectedContext == ctx
                    FilterChip(
                        selected = selected,
                        onClick = { selectedContext = ctx },
                        label = { Text(stringResource(ctx.labelRes)) },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 200) note = it },
                label = { Text(stringResource(R.string.note_field_label)) },
                placeholder = { Text(stringResource(R.string.note_field_placeholder)) },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manual_note_input"),
            )

            Button(
                onClick = {
                    if (parsedBpm != null && isValidBpm) {
                        onSave(parsedBpm, selectedContext, note)
                        bpmText = ""
                        note = ""
                    }
                },
                enabled = isValidBpm && !isSaving,
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("manual_save_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    text = stringResource(R.string.btn_save_result),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}
