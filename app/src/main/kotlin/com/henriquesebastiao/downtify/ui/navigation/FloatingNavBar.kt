package com.henriquesebastiao.downtify.ui.navigation

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henriquesebastiao.downtify.core.designsystem.component.PhonePreviews
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * The v3 navigation bar on phones: a pill floating 16 dp in from the screen's sides, with
 * Material's navigation items (the selected one's label in primary). The rail and the full-width
 * bar (Settings › Appearance) stay with NavigationSuiteScaffold.
 */
@Composable
fun FloatingNavBar(
    selected: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = DowntifyShape.FloatingNavBar,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    ) {
        BoxWithConstraints {
            // The item measures its label without a width limit: give it the item's own, so it can shrink.
            val labelWidth = maxWidth / TopLevelDestination.entries.size - Spacing.xs
            Row(
                Modifier.heightIn(min = BAR_HEIGHT).selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopLevelDestination.entries.forEach { tab ->
                    val isSelected = tab == selected
                    ShortNavigationBarItem(
                        selected = isSelected,
                        onClick = { onSelect(tab) },
                        icon = {
                            Icon(
                                painterResource(if (isSelected) tab.selectedIcon else tab.icon),
                                contentDescription = null,
                            )
                        },
                        label = { NavLabel(stringResource(tab.label), Modifier.widthIn(max = labelWidth)) },
                        colors = ShortNavigationBarItemDefaults.colors(
                            selectedTextColorTopIconPosition = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * A destination's label: one line that steps down from labelMedium to 10 sp to fit a long word
 * ("Bibliothèque") on a narrow phone; past that it ends in an ellipsis ("Téléchargements" at
 * 360 dp), never in a cut letter.
 */
@Composable
fun NavLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(
            minFontSize = 10.sp,
            maxFontSize = MaterialTheme.typography.labelMedium.fontSize,
            stepSize = 0.5.sp,
        ),
    )
}

private val BAR_HEIGHT = 80.dp

@PhonePreviews
@Composable
private fun FloatingNavBarPreview() {
    DowntifyTheme {
        Surface { FloatingNavBar(selected = TopLevelDestination.Library, onSelect = {}) }
    }
}
