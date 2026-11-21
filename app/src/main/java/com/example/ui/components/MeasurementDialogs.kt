package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.HeartRateZone
import com.example.data.MeasurementContext
import com.example.data.PulseMeasurement

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SaveMeasurementDialog(
    bpm: Int,
    isSaving: Boolean,
    initialContext: MeasurementContext = MeasurementContext.RESTING,
    onDismiss: () -> Unit,
    onConfirm: (MeasurementContext, String) -> Unit,
) {
    var selectedContext by remember { mutableStateOf(initialContext) }
    var note by remember { mutableStateOf("") }
    val zone = remember(bpm) { HeartRateZone.forBpm(bpm) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.save_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = zone.color,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "$bpm ${stringResource(R.string.bpm_unit)}",
                                style = MaterialTheme.typography.headlineMedium,
                            )
                        }
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
                            modifier = Modifier.testTag("save_context_${ctx.name.lowercase()}"),
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
                        .testTag("save_note_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedContext, note) },
                enabled = !isSaving,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_save_button"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditMeasurementDialog(
    measurement: PulseMeasurement,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (MeasurementContext, String) -> Unit,
) {
    var selectedContext by remember(measurement.id) { mutableStateOf(measurement.contextEnum()) }
    var note by remember(measurement.id) { mutableStateOf(measurement.note.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = stringResource(R.string.edit_dialog_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "${measurement.bpm} ${stringResource(R.string.bpm_unit)}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = measurement.zone().color,
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
                        .testTag("edit_note_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedContext, note) },
                enabled = !isSaving,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_edit_button"),
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementGuideSheet(
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.guide_title),
                style = MaterialTheme.typography.headlineMedium,
            )

            Image(
                painter = painterResource(id = R.drawable.img_finger_guide_1795263413248),
                contentDescription = stringResource(R.string.cd_finger_guide),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(MaterialTheme.shapes.large),
            )

            Text(stringResource(R.string.guide_step_1), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.guide_step_2), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.guide_step_3), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.guide_step_4), style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .testTag("close_guide_button"),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.guide_close))
            }
        }
    }
}
