package com.manisykh.screenrest.ui.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class ScreenRestTone {
    Neutral,
    Primary,
    Success,
    Warning,
    Blocked,
    Schedule,
}

@Composable
private fun toneColors(tone: ScreenRestTone): Pair<Color, Color> {
    val semantic = ScreenRestTheme.colors
    return when (tone) {
        ScreenRestTone.Neutral ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        ScreenRestTone.Primary ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        ScreenRestTone.Success -> semantic.successContainer to semantic.onSuccessContainer
        ScreenRestTone.Warning -> semantic.warningContainer to semantic.onWarningContainer
        ScreenRestTone.Blocked -> semantic.blockedContainer to semantic.onBlockedContainer
        ScreenRestTone.Schedule -> semantic.scheduleContainer to semantic.schedule
    }
}

@Composable
fun ScreenRestPageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = ScreenRestTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(ScreenRestTheme.spacing.sm))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf(String::isNotBlank)?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(ScreenRestTheme.spacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = it,
            )
        }
    }
}

@Composable
fun ScreenRestSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.xxs),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            supportingText?.takeIf(String::isNotBlank)?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.let {
            Spacer(Modifier.width(ScreenRestTheme.spacing.sm))
            it()
        }
    }
}

@Composable
fun ScreenRestCard(
    modifier: Modifier = Modifier,
    tone: ScreenRestTone = ScreenRestTone.Neutral,
    contentPadding: PaddingValues = PaddingValues(ScreenRestTheme.spacing.md),
    content: @Composable ColumnScope.() -> Unit,
) {
    val (toneContainer, _) = toneColors(tone)
    val container = if (tone == ScreenRestTone.Neutral) {
        MaterialTheme.colorScheme.surface
    } else {
        toneContainer.copy(alpha = 0.62f)
    }
    val border = if (tone == ScreenRestTone.Neutral) {
        MaterialTheme.colorScheme.outlineVariant
    } else {
        toneColors(tone).second.copy(alpha = 0.20f)
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ScreenRestTheme.radii.card),
        color = container,
        border = BorderStroke(1.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(ScreenRestTheme.spacing.sm),
            content = content,
        )
    }
}

@Composable
fun ScreenRestListRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    } else {
        Modifier
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .heightIn(min = ScreenRestTheme.sizes.minimumTouchTarget)
            .padding(horizontal = ScreenRestTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(ScreenRestTheme.spacing.sm))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            supportingText?.takeIf(String::isNotBlank)?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.let {
            Spacer(Modifier.width(ScreenRestTheme.spacing.sm))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = it,
            )
        }
    }
}

@Composable
fun ScreenRestIconContainer(
    tone: ScreenRestTone,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val (container, _) = toneColors(tone)
    Surface(
        modifier = modifier.size(ScreenRestTheme.sizes.listIconContainer),
        shape = CircleShape,
        color = container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
fun ScreenRestStatusPill(
    label: String,
    tone: ScreenRestTone,
    modifier: Modifier = Modifier,
) {
    val (container, content) = toneColors(tone)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(ScreenRestTheme.radii.chip),
        color = container,
        border = BorderStroke(1.dp, content.copy(alpha = 0.18f)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
fun ScreenRestPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ScreenRestTheme.sizes.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(ScreenRestTheme.radii.button),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(ScreenRestTheme.spacing.xs))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ScreenRestOutlinedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ScreenRestTone = ScreenRestTone.Primary,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val (_, content) = toneColors(tone)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(ScreenRestTheme.sizes.buttonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(ScreenRestTheme.radii.button),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
        border = BorderStroke(1.dp, content.copy(alpha = 0.52f)),
    ) {
        leading?.let {
            it()
            Spacer(Modifier.width(ScreenRestTheme.spacing.xs))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
