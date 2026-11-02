package at.yerova.hoi4_modder.v2.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Central design tokens and default shapes for the HoI4 Modder UI ("HM" component suite).
 * Ensures a consistent, compact studio/editor look across all screens.
 */
object HMDefaults {
    /** Compact corner shape used for buttons, chips, and inputs. */
    val ComponentShape: Shape = RoundedCornerShape(4.dp)

    /** Slightly larger corner shape used for panels, cards, and containers. */
    val PanelShape: Shape = RoundedCornerShape(6.dp)

    /** Standard border thickness for outlined studio components. */
    val BorderThickness: Dp = 1.dp

    /** Helper to create a consistent border stroke respecting enabled states. */
    @Composable
    fun outlineBorder(enabled: Boolean = true, color: Color = MaterialTheme.colorScheme.outline): BorderStroke {
        return BorderStroke(
            width = BorderThickness,
            color = if (enabled) color else color.copy(alpha = 0.4f)
        )
    }
}

/** Kept for backwards compatibility with existing views. */
val AppButtonShape = HMDefaults.ComponentShape

/**
 * Primary filled action button with compact studio padding and [HMDefaults.ComponentShape].
 *
 * @param onClick Called when the user clicks the button.
 * @param modifier Modifier to be applied to the button.
 * @param enabled Controls the enabled state of the button.
 * @param containerColor Background color of the button when enabled.
 * @param contentColor Text and icon color inside the button when enabled.
 * @param contentPadding Internal spacing between the button border and its content.
 * @param content The composable content displayed inside the button row.
 */
@Composable
fun HMButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        enabled = enabled,
        shape = HMDefaults.ComponentShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        ),
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Convenience wrapper around [HMButton] styled specifically for destructive actions (e.g., Delete, Reset).
 */
@Composable
fun HMDangerButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    HMButton(
        onClick = onClick,
        modifier = modifier.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        enabled = enabled,
        containerColor = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Secondary outlined button with a subtle [MaterialTheme.colorScheme.outline] border.
 * Ideal for secondary actions like "Cancel", "Export", or "Browse...".
 */
@Composable
fun HMOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        enabled = enabled,
        shape = HMDefaults.ComponentShape,
        border = HMDefaults.outlineBorder(enabled),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        ),
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Low-emphasis text button without background or border.
 * Suitable for inline actions, toolbar links, or dialog dismissals.
 */
@Composable
fun HMTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        enabled = enabled,
        shape = HMDefaults.ComponentShape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        ),
        contentPadding = contentPadding,
        content = content
    )
}

/**
 * Compact icon button for toolbars and action headers.
 * Can optionally render a subtle outline border via [bordered].
 */
@Composable
fun HMIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = false,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit
) {
    if (bordered) {
        OutlinedIconButton(
            onClick = onClick,
            modifier = modifier.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
            enabled = enabled,
            shape = HMDefaults.ComponentShape,
            border = HMDefaults.outlineBorder(enabled),
            colors = IconButtonDefaults.outlinedIconButtonColors(
                contentColor = contentColor,
                disabledContentColor = contentColor.copy(alpha = 0.4f)
            ),
            content = content
        )
    } else {
        IconButton(
            onClick = onClick,
            modifier = modifier.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = contentColor,
                disabledContentColor = contentColor.copy(alpha = 0.4f)
            ),
            content = content
        )
    }
}

/**
 * Standard studio text input field styled with [HMDefaults.ComponentShape] and theme outline colors.
 *
 * @param value The input text to be shown in the text field.
 * @param onValueChange The callback that is triggered when the input service updates the text.
 * @param modifier Modifier to be applied to this text field.
 * @param label Optional label displayed inside or above the border.
 * @param placeholder Optional placeholder text displayed when the field is empty.
 * @param leadingIcon Optional icon at the start of the text field.
 * @param trailingIcon Optional icon at the end of the text field (e.g., clear button).
 * @param supportingText Optional helper or error message displayed below the input.
 * @param isError Indicates if the text field's current value is in an error state.
 * @param singleLine When true, restricts the input to a single horizontally scrolling line.
 */
@Composable
fun HMTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        enabled = enabled,
        readOnly = readOnly,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        supportingText = supportingText,
        isError = isError,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = HMDefaults.ComponentShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
            errorBorderColor = MaterialTheme.colorScheme.error
        )
    )
}

/**
 * Compact filter chip for toggling tags, categories, or filter states.
 */
@Composable
fun HMFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier.pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = HMDefaults.ComponentShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outline,
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    )
}

/**
 * Studio-styled checkbox with an optional clickable text label next to it.
 *
 * @param checked Whether the checkbox is currently checked.
 * @param onCheckedChange Called when the user clicks the checkbox or its label.
 * @param modifier Modifier applied to the outer row (if labeled) or the checkbox itself.
 * @param enabled Controls the enabled state of the checkbox.
 * @param label Optional text label displayed to the right of the checkbox.
 */
@Composable
fun HMCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null
) {
    val checkboxColors = CheckboxDefaults.colors(
        checkedColor = MaterialTheme.colorScheme.primary,
        uncheckedColor = MaterialTheme.colorScheme.outline,
        checkmarkColor = MaterialTheme.colorScheme.onPrimary
    )

    if (label == null) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            colors = checkboxColors
        )
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        Row(
            modifier = modifier
                .clip(HMDefaults.ComponentShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = { onCheckedChange(!checked) }
                )
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = checkboxColors
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.5f
                )
            )
        }
    }
}

/**
 * Studio-styled toggle switch for binary settings.
 */
@Composable
fun HMSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    thumbContent: (@Composable () -> Unit)? = null
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        thumbContent = thumbContent,
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline
        )
    )
}

/**
 * Generic dropdown selector built on [ExposedDropdownMenuBox].
 * Ideal for selecting enums, ideologies, scopes, or tags in modding forms.
 *
 * @param T The type of items in the dropdown list.
 * @param items The list of available options.
 * @param selectedItem The currently selected item.
 * @param onItemSelected Callback invoked when the user selects an option.
 * @param itemLabel Function converting an item of type [T] into a human-readable string.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> HMDropdownSelector(
    items: List<T>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    itemLabel: (T) -> String = { it.toString() }
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = modifier
    ) {
        HMTextField(
            value = itemLabel(selectedItem),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = label,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            border = HMDefaults.outlineBorder()
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = itemLabel(item),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}

/**
 * Outlined studio panel/card used to group related modding properties or editor sections.
 * Uses [HMDefaults.PanelShape] and a subtle 1.dp outline border.
 */
@Composable
fun HMPanel(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = HMDefaults.PanelShape,
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
        border = HMDefaults.outlineBorder()
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

/**
 * Pre-styled horizontal 1.dp divider using the theme's outline color.
 */
@Composable
fun HMHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = HMDefaults.BorderThickness,
    color: Color = MaterialTheme.colorScheme.outline
) {
    HorizontalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

/**
 * Pre-styled vertical 1.dp divider for separating split-panes or toolbar groups.
 */
@Composable
fun HMVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = HMDefaults.BorderThickness,
    color: Color = MaterialTheme.colorScheme.outline
) {
    VerticalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

/**
 * Shows a material symbol by using the theme icon resolver.
 *
 * @param iconName the icon name (Ligatur), e.g. "factory", "military_tech", "train"
 * @param modifier Compose Modifier
 * @param color Icon color
 * @param size sizc  Icons (default 24.sp)
 */
@Composable
fun HMIcon(
    iconName: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    size: TextUnit = 24.sp
) {
    Text(
        text = iconName,
        fontFamily = Hoi4ModderTheme.symbolFont,
        color = color,
        fontSize = size,
        modifier = modifier
    )
}