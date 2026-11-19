package com.triforge.app.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.triforge.app.ui.theme.SportColors
import com.triforge.app.ui.theme.TriColors
import com.triforge.app.ui.theme.TriIcons
import com.triforge.app.ui.theme.numberStyle
import com.triforge.domain.Formatters

/** The three discipline colors around the photo, the way the wordmark has them. */
private val RingBrush = Brush.sweepGradient(
    listOf(SportColors.swim, SportColors.bike, SportColors.run, SportColors.swim),
)

/**
 * The athlete's photo, or their initials when there is none. With [ring] it
 * sits inside a thin ring in the three sport colors.
 */
@Composable
fun Avatar(
    jpeg: ByteArray?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    ring: Boolean = false,
    contentDescription: String? = null,
) {
    val image = remember(jpeg) { jpeg?.let(::decodeImage) }
    val initials = remember(name) { Formatters.initials(name) }
    val ringWidth = if (ring) (size.value / 30f).coerceAtLeast(2f).dp else 0.dp
    val gap = if (ring) ringWidth + 1.dp else 0.dp

    Box(
        modifier = modifier
            .size(size)
            .then(if (ring) Modifier.border(ringWidth, RingBrush, CircleShape) else Modifier)
            .padding(gap)
            .clip(CircleShape)
            .background(TriColors.avatar),
        contentAlignment = Alignment.Center,
    ) {
        when {
            image != null -> Image(
                bitmap = image,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
            initials.isNotEmpty() -> Text(
                text = initials,
                style = numberStyle((size.value * 0.34f).sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
            else -> Icon(
                imageVector = TriIcons.Person,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}
