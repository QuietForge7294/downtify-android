package com.henriquesebastiao.downtify.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.henriquesebastiao.downtify.core.designsystem.component.DowntifyIcons
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.ListStyle
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/** What tapping a [GroupedRow] does, and what it shows at the end. */
sealed interface RowAction {
    /** Opens something; [trailingIcon] hints at what (a chevron for a screen, "open in new" for a link). */
    data class Click(val onClick: () -> Unit, @DrawableRes val trailingIcon: Int? = null) : RowAction

    /** A switch: the whole row toggles it. */
    data class Toggle(val checked: Boolean, val onCheckedChange: (Boolean) -> Unit) : RowAction
}

/** One row of a [GroupedList]. */
data class GroupedRow(
    val title: String,
    val action: RowAction,
    val summary: String? = null,
    @DrawableRes val icon: Int? = null,
    val enabled: Boolean = true,
    val titleColor: Color = Color.Unspecified,
)

/**
 * A group of rows as Material 3 Expressive draws a grouped list: each row its own surface, the ends
 * of the group with large outer corners, a small gap between rows. [style] sets the corners and the
 * gap: segmented (settings, About), separated or compact. Rows never change shape or tint.
 */
@Composable
fun GroupedList(rows: List<GroupedRow>, modifier: Modifier = Modifier, style: ListStyle = ListStyle.Segmented) {
    Column(
        modifier.padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(style.gap),
    ) {
        rows.forEachIndexed { index, row -> GroupedListItem(row, index, rows.size, style = style) }
    }
}

/** A row that is the [index]th of [count] in its group, which decides its corners. */
@Composable
fun GroupedListItem(
    row: GroupedRow,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    style: ListStyle = ListStyle.Segmented,
) {
    // A row looks the same on, off, pressed or focused: the stock item rounds a checked row all the
    // way and tints it, which made the switches stand out from the rest of the group.
    val shape = style.shapeOf(index, count)
    val shapes = ListItemShapes(shape, shape, shape, shape, shape, shape)
    val scheme = MaterialTheme.colorScheme
    val colors = ListItemDefaults.segmentedColors(
        containerColor = scheme.surfaceContainer,
        selectedContainerColor = scheme.surfaceContainer,
        selectedContentColor = scheme.onSurface,
        selectedLeadingContentColor = scheme.onSurfaceVariant,
        selectedTrailingContentColor = scheme.onSurfaceVariant,
        selectedSupportingContentColor = scheme.onSurfaceVariant,
    )
    val leading: @Composable (() -> Unit)? = row.icon?.let { icon ->
        { Icon(painterResource(icon), contentDescription = null) }
    }
    val supporting: @Composable (() -> Unit)? = row.summary?.let { summary -> { Text(summary) } }
    val title: @Composable () -> Unit = { Text(row.title, color = row.titleColor) }
    when (val action = row.action) {
        is RowAction.Click -> SegmentedListItem(
            onClick = action.onClick,
            shapes = shapes,
            modifier = modifier,
            enabled = row.enabled,
            leadingContent = leading,
            trailingContent = action.trailingIcon?.let { icon ->
                {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            supportingContent = supporting,
            colors = colors,
            content = title,
        )

        is RowAction.Toggle -> SegmentedListItem(
            checked = action.checked,
            onCheckedChange = action.onCheckedChange,
            shapes = shapes,
            modifier = modifier,
            enabled = row.enabled,
            leadingContent = leading,
            trailingContent = { Switch(checked = action.checked, onCheckedChange = null, enabled = row.enabled) },
            supportingContent = supporting,
            colors = colors,
            content = title,
        )
    }
}

/** A group's title, in the accent color. */
@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .padding(start = Spacing.screen + Spacing.lg, end = Spacing.screen, top = Spacing.xl, bottom = Spacing.sm)
            .semantics { heading() },
    )
}

@PhonePreviews
@Composable
private fun GroupedListPreview() {
    DowntifyTheme {
        Surface {
            Column {
                GroupTitle("Appearance")
                GroupedList(
                    listOf(
                        GroupedRow("Theme", RowAction.Click({}), "Follow system", DowntifyIcons.DarkMode),
                        GroupedRow(
                            "Pure black",
                            RowAction.Toggle(true) {
                            },
                            "True black in the dark theme",
                            DowntifyIcons.Contrast,
                        ),
                        GroupedRow("About", RowAction.Click({}, DowntifyIcons.ChevronRight), null, DowntifyIcons.Info),
                    ),
                )
            }
        }
    }
}
