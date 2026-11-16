package com.railbite.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.railbite.core.ui.theme.Metrics
import com.railbite.core.ui.theme.Radii
import com.railbite.core.ui.theme.Space

/**
 * Shared RailBite UI scaffolds. Flat, token-driven, no decoration.
 * Buttons/inputs 8 dp radius, cards 10–12 dp, tap targets ≥ 44 dp,
 * primary buttons 56 dp tall. Loading skeletons, empty states and
 * user-readable error messages live here so every screen reuses them.
 */

/** Primary action: 56 dp tall, accent fill, 8 dp radius. */
@Composable
fun RailBiteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(Metrics.primaryButtonHeight),
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Secondary action: outline, ≥ 44 dp tall, 8 dp radius. */
@Composable
fun RailBiteSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.tapTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Labelled text field: 8 dp radius, ≥ 44 dp target, user-readable supporting text. */
@Composable
fun RailBiteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation =
        androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions =
        androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = hint?.let { h -> ({ Text(h) }) },
        supportingText = supportingText?.let { s -> ({ Text(s) }) },
        singleLine = singleLine,
        minLines = minLines,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.tapTarget),
    )
}

/** Checkbox with a label; the whole row is tappable and ≥ 44 dp tall. */
@Composable
fun RailBiteCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.tapTarget)
            .clickable { onCheckedChange(!checked) },
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Flat loading skeleton block — alpha pulse, no gradients. */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 20.dp,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    .compositeOver(MaterialTheme.colorScheme.surface),
                RoundedCornerShape(Radii.control),
            )
            .graphicsLayer { this.alpha = alpha },
    )
}

/** Skeleton list row placeholder. */
@Composable
fun SkeletonRow(modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = Space.s), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        SkeletonBlock(height = 16.dp, modifier = Modifier.fillMaxWidth(0.4f))
        SkeletonBlock(height = 14.dp, modifier = Modifier.fillMaxWidth(0.75f))
    }
}

/** Empty state: flat, centred, optional action. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.s),
        modifier = modifier
            .fillMaxWidth()
            .padding(Space.xl),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionText != null && onAction != null) {
            RailBiteSecondaryButton(actionText, onAction, modifier = Modifier.padding(top = Space.xs))
        }
    }
}

/** Error message: always human-readable, never a raw exception. */
@Composable
fun ErrorBar(message: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = Space.s, vertical = Space.xs),
        )
    }
}

/** Section headline (24). */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

/** Filter chip: ≥ 44 dp tap target, accent tint when selected. */
@Composable
fun RailBiteFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        modifier = modifier.heightIn(min = Metrics.tapTarget),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = Space.s),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Quantity stepper: two 44 dp controls on the accent fill, readable count. */
@Composable
fun RailBiteStepper(
    quantity: Int,
    itemName: String,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Metrics.tapTarget)
            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
    ) {
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .size(Metrics.tapTarget)
                .iconDescription("Remove one $itemName"),
        ) {
            Text(
                "−",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            "$quantity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onAdd,
            modifier = Modifier
                .size(Metrics.tapTarget)
                .iconDescription("Add one $itemName"),
        ) {
            Text(
                "+",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Icon-only buttons must expose a content description through this wrapper. */
fun Modifier.iconDescription(description: String): Modifier =
    this.then(
        Modifier.semantics { contentDescription = description },
    )
