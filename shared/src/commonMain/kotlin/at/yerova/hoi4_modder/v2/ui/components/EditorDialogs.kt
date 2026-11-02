package at.yerova.hoi4_modder.v2.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import at.yerova.hoi4_modder.v2.ui.*
import hoi4_modder.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun NewProvinceDialogV2(
    onDismiss: () -> Unit,
    onCreate: (type: String, coastal: Boolean, terrain: String, continent: Int) -> Unit
) {
    var type by remember { mutableStateOf("land") }
    var coastal by remember { mutableStateOf(false) }
    var terrain by remember { mutableStateOf("plains") }
    var continent by remember { mutableStateOf("1") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = HMDefaults.PanelShape,
        title = { Text(stringResource(Res.string.dialog_new_prov_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.dialog_new_prov_desc), style = MaterialTheme.typography.bodySmall)
                HMTextField(value = type, onValueChange = { type = it }, label = stringResource(Res.string.field_type))
                HMTextField(
                    value = terrain,
                    onValueChange = { terrain = it },
                    label = stringResource(Res.string.field_terrain)
                )
                HMTextField(
                    value = continent,
                    onValueChange = { continent = it.filter { c -> c.isDigit() } },
                    label = stringResource(Res.string.field_continent)
                )
                HMCheckbox(
                    checked = coastal,
                    onCheckedChange = { coastal = it },
                    label = stringResource(Res.string.field_coastal)
                )
            }
        },
        confirmButton = {
            HMButton(onClick = { onCreate(type.trim(), coastal, terrain.trim(), continent.toIntOrNull() ?: 1) }) {
                Text(stringResource(Res.string.dialog_create_btn))
            }
        },
        dismissButton = {
            HMTextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel_btn)) }
        }
    )
}

@Composable
fun NewStateDialogV2(
    onDismiss: () -> Unit,
    onCreate: (name: String, manpower: Int, category: String) -> Unit
) {
    var name by remember { mutableStateOf("STATE_NEW") }
    var manpower by remember { mutableStateOf("150000") }
    var category by remember { mutableStateOf("town") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = HMDefaults.PanelShape,
        title = { Text(stringResource(Res.string.dialog_new_state_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HMTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(Res.string.field_state_name)
                )
                HMTextField(
                    value = manpower,
                    onValueChange = { manpower = it.filter { c -> c.isDigit() } },
                    label = stringResource(Res.string.field_manpower)
                )
                HMTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = stringResource(Res.string.field_state_cat)
                )
            }
        },
        confirmButton = {
            HMButton(onClick = { onCreate(name.trim(), manpower.toIntOrNull() ?: 10000, category.trim()) }) {
                Text(stringResource(Res.string.dialog_create_generic_btn))
            }
        },
        dismissButton = {
            HMTextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel_btn)) }
        }
    )
}

@Composable
fun NewStrategicRegionDialogV2(
    onDismiss: () -> Unit,
    onCreate: (name: String) -> Unit
) {
    var name by remember { mutableStateOf("STRATEGICREGION_NEW") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = HMDefaults.PanelShape,
        title = { Text(stringResource(Res.string.dialog_new_region_title)) },
        text = {
            HMTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(Res.string.field_region_name)
            )
        },
        confirmButton = {
            HMButton(onClick = { onCreate(name.trim()) }) {
                Text(stringResource(Res.string.dialog_create_generic_btn))
            }
        },
        dismissButton = {
            HMTextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel_btn)) }
        }
    )
}

@Composable
fun AddAdjacencyDialogV2(
    onDismiss: () -> Unit,
    onAdd: (fromId: Int, toId: Int, throughSeaId: Int, ruleName: String, comment: String) -> Unit
) {
    var fromProv by remember { mutableStateOf("") }
    var toProv by remember { mutableStateOf("") }
    var throughProv by remember { mutableStateOf("8657") }
    var ruleName by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("Custom Strait") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = HMDefaults.PanelShape,
        title = { Text(stringResource(Res.string.dialog_add_adjacency_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HMTextField(
                    value = fromProv,
                    onValueChange = { fromProv = it.filter { c -> c.isDigit() } },
                    label = stringResource(Res.string.field_from_province_id)
                )
                HMTextField(
                    value = toProv,
                    onValueChange = { toProv = it.filter { c -> c.isDigit() } },
                    label = stringResource(Res.string.field_to_province_id)
                )
                HMTextField(
                    value = throughProv,
                    onValueChange = { throughProv = it.filter { c -> c.isDigit() || c == '-' } },
                    label = stringResource(Res.string.field_through_sea_province_id)
                )
                HMTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = stringResource(Res.string.field_adjacency_rule_optional)
                )
                HMTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = stringResource(Res.string.field_comment)
                )
            }
        },
        confirmButton = {
            HMButton(
                onClick = {
                    val f = fromProv.toIntOrNull()
                    val t = toProv.toIntOrNull()
                    if (f != null && t != null) {
                        onAdd(f, t, throughProv.toIntOrNull() ?: -1, ruleName, comment)
                    }
                }
            ) {
                Text(stringResource(Res.string.add_button))
            }
        },
        dismissButton = {
            HMTextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel_btn)) }
        }
    )
}

@Composable
fun UnsavedChangesExitDialog(
    modName: String,
    unsavedSummary: String,
    isSaving: Boolean,
    onSaveAndExit: () -> Unit,
    onDiscardAndExit: () -> Unit,
    onCancel: () -> Unit
) {
    val modifiedItems = remember(unsavedSummary) {
        unsavedSummary.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onCancel() },
        shape = HMDefaults.PanelShape,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HMIcon("warning", size = 20.sp, color = Hoi4ModderTheme.extendedColors.warning)
                Text("Save Modified Resources?")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Project '$modName' has unsaved modifications. Do you want to save your changes before exiting?",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (modifiedItems.isNotEmpty()) {
                    HMPanel(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentPadding = PaddingValues(10.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Modified resources in memory:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            modifiedItems.forEach { item ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    HMIcon(
                                        iconName = "description",
                                        size = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = item,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HMDangerButton(
                    onClick = onDiscardAndExit,
                    enabled = !isSaving
                ) {
                    HMIcon("delete_forever", size = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Don't Save")
                }

                HMButton(
                    onClick = onSaveAndExit,
                    enabled = !isSaving
                ) {
                    HMIcon("save", size = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSaving) "Saving..." else "Save & Exit")
                }
            }
        },
        dismissButton = {
            HMOutlinedButton(
                onClick = onCancel,
                enabled = !isSaving
            ) {
                Text(stringResource(Res.string.dialog_cancel_btn))
            }
        }
    )
}