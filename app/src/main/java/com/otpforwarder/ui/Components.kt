package com.otpforwarder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.otpforwarder.ui.theme.Lamp

/** Colours for the big hero surfaces: solid pine in light mode, a deeper pine in dark so it doesn't glare. */
data class Hero(val bg: Color, val on: Color, val accent: Color, val accentOn: Color)

@Composable
fun heroColors(): Hero {
    val c = MaterialTheme.colorScheme
    return if (c.background.luminance() < 0.5f) Hero(c.primaryContainer, c.onPrimaryContainer, c.primary, c.onPrimary)
    else Hero(c.primary, c.onPrimary, c.primaryContainer, c.onPrimaryContainer)
}

/** Large serif page title. Each screen opens with one. */
@Composable
fun ScreenTitle(text: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, hint: String? = null) {
    Column(Modifier.padding(top = 28.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Flat tonal surface: depth comes from colour, not from drop shadows. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = container) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
fun Avatar(name: String, size: Dp = 40.dp, container: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(Modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
fun Pill(
    text: String,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Surface(shape = CircleShape, color = container) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
        )
    }
}

/** The amber lamp: lit while sharing is on. A soft halo makes it read as light rather than a dot. */
@Composable
fun LampDot(lit: Boolean, size: Dp = 14.dp) {
    Box(Modifier.size(size * 2), contentAlignment = Alignment.Center) {
        if (lit) Box(Modifier.size(size * 2).clip(CircleShape).background(Lamp.copy(alpha = 0.28f)))
        Box(
            Modifier.size(size).clip(CircleShape)
                .background(if (lit) Lamp else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        )
    }
}

@Composable
fun EmptyNote(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "482913" -> "482 913" so a code is easy to read aloud and to compare. */
fun groupCode(code: String): String = when (code.length) {
    4 -> code
    5 -> code
    6 -> code.chunked(3).joinToString(" ")
    7 -> code.take(3) + " " + code.drop(3)
    8 -> code.chunked(4).joinToString(" ")
    else -> code
}

@Composable
fun RowLabel(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SpaceRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
