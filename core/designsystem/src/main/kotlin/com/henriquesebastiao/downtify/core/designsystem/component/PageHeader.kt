package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/**
 * A screen's large header (v3): circular 48 dp buttons in the top row (back at the start,
 * [actions] at the end) over a bold 48 sp title that shrinks to fit one line. The title collapses
 * into the top row as the content scrolls under [scrollBehavior]. [tint] washes the header with a
 * soft gradient of that color, fading into the surface (the library's header).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    tint: Color? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val surface = MaterialTheme.colorScheme.surface
    val background = tint?.let {
        Modifier.background(Brush.verticalGradient(listOf(it.copy(alpha = TINT_ALPHA).compositeOver(surface), surface)))
    } ?: Modifier
    val typography = MaterialTheme.typography
    TwoRowsTopAppBar(
        title = { expanded ->
            Text(
                title,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = if (expanded) typography.displayLarge else typography.titleLarge,
                autoSize = if (expanded) PageTitleAutoSize else null,
            )
        },
        subtitle = subtitle?.let { text ->
            { expanded ->
                Text(
                    text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = if (expanded) typography.bodyLarge else typography.bodyMedium,
                )
            }
        },
        navigationIcon = {
            if (onBack != null) {
                CircleButton(DowntifyIcons.Back, backLabel, onBack, Modifier.padding(start = Spacing.xs))
            }
        },
        actions = {
            actions()
            Spacer(Modifier.width(Spacing.xs))
        },
        collapsedHeight = COLLAPSED_HEIGHT,
        expandedHeight = if (subtitle == null) EXPANDED_HEIGHT else EXPANDED_HEIGHT_WITH_SUBTITLE,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = if (tint == null) surface else Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        scrollBehavior = scrollBehavior,
        modifier = modifier.then(background),
    )
}

/**
 * A circular 48 dp tonal button: a header's back and actions, a list row's "more". Squarer while
 * pressed, as Material 3 Expressive's icon buttons are.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CircleButton(
    icon: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        modifier = modifier.size(CIRCLE_BUTTON_SIZE),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription)
    }
}

// Long words in 48 sp don't fit a 360 dp phone: the title steps down instead of breaking or cutting.
private val PageTitleAutoSize = TextAutoSize.StepBased(
    minFontSize = 28.sp,
    maxFontSize = 48.sp,
    stepSize = 2.sp,
)

private val CIRCLE_BUTTON_SIZE = 48.dp
private val COLLAPSED_HEIGHT = 64.dp

// The top row, the title's 56 dp line with 20 dp around it, and the subtitle's 24 dp line.
private val EXPANDED_HEIGHT = 64.dp + 96.dp
private val EXPANDED_HEIGHT_WITH_SUBTITLE = EXPANDED_HEIGHT + 28.dp
private const val TINT_ALPHA = 0.18f

@PhonePreviews
@Composable
private fun PageHeaderPreview() {
    DowntifyTheme {
        Surface {
            Column {
                PageHeader(title = "Settings", onBack = {}, backLabel = "Back")
                PageHeader(
                    title = "Library",
                    subtitle = "1,204 songs",
                    titleColor = MaterialTheme.colorScheme.primary,
                    tint = MaterialTheme.colorScheme.primary,
                    actions = { CircleButton(DowntifyIcons.Settings, "Settings", {}) },
                )
            }
        }
    }
}
