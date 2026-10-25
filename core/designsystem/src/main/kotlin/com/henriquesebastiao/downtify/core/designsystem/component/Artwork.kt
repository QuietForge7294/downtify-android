package com.henriquesebastiao.downtify.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyShape
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.designsystem.theme.Spacing

/** The placeholder's colors: neutral, or one of the scheme's container tones. */
enum class ArtworkTone { Neutral, Primary, Secondary, Tertiary }

/**
 * A cover from the server (or anything Coil loads), clipped to [shape]: a squircle by default,
 * [CircleShape] for artists, a Material shape for a highlight. Without a cover, while it loads or
 * when it fails, an honest placeholder: a tonal container with [placeholderIcon].
 */
@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = DowntifyShape.ArtworkThumb,
    placeholderIcon: Int = DowntifyIcons.Song,
    tone: ArtworkTone = ArtworkTone.Neutral,
) {
    val (container, content) = tone.colors()
    Box(modifier.clip(shape).background(container)) {
        if (url == null) {
            ArtworkPlaceholder(placeholderIcon, content)
        } else {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { ArtworkPlaceholder(placeholderIcon, content) },
                error = { ArtworkPlaceholder(placeholderIcon, content) },
            )
        }
    }
}

@Composable
private fun ArtworkTone.colors(): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (this) {
        ArtworkTone.Neutral -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
        ArtworkTone.Primary -> scheme.primaryContainer to scheme.onPrimaryContainer
        ArtworkTone.Secondary -> scheme.secondaryContainer to scheme.onSecondaryContainer
        ArtworkTone.Tertiary -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

@Composable
private fun ArtworkPlaceholder(icon: Int, tint: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.fillMaxSize(PLACEHOLDER_ICON_FRACTION).alpha(PLACEHOLDER_ICON_ALPHA),
        )
    }
}

private const val PLACEHOLDER_ICON_FRACTION = 0.42f
private const val PLACEHOLDER_ICON_ALPHA = 0.8f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@PhonePreviews
@Composable
private fun ArtworkPreview() {
    DowntifyTheme {
        Surface {
            Row(Modifier.padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Artwork(url = null, contentDescription = null, modifier = Modifier.size(64.dp))
                Artwork(
                    url = null,
                    contentDescription = null,
                    shape = CircleShape,
                    placeholderIcon = DowntifyIcons.Artist,
                    tone = ArtworkTone.Secondary,
                    modifier = Modifier.size(64.dp),
                )
                Artwork(
                    url = null,
                    contentDescription = null,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                    placeholderIcon = DowntifyIcons.Album,
                    tone = ArtworkTone.Tertiary,
                    modifier = Modifier.size(96.dp),
                )
            }
        }
    }
}
