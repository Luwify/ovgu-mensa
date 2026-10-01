package de.luwify.mensamd.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.R
import de.luwify.mensamd.data.settings.CustomBase
import de.luwify.mensamd.data.settings.CustomThemeSpec
import de.luwify.mensamd.ui.theme.MensaFilterChip
import de.luwify.mensamd.ui.theme.hsl

/** Quick picks for the accent hue, with names for TalkBack. The slider below reaches every other hue. */
private val SWATCHES = listOf(
    0 to R.string.hue_red, 25 to R.string.hue_orange, 45 to R.string.hue_yellow, 90 to R.string.hue_lime,
    140 to R.string.hue_green, 175 to R.string.hue_teal, 200 to R.string.hue_cyan, 220 to R.string.hue_blue,
    265 to R.string.hue_violet, 300 to R.string.hue_magenta, 335 to R.string.hue_pink,
)

/** A swatch shows the accent the way the theme would: a mid tone of the hue. */
private fun swatchColor(hue: Int) = hsl(hue.toFloat(), 0.62f, 0.52f)

/**
 * The editor for the user's own theme (shown on the settings page while "Eigenes" is selected): base brightness,
 * accent hue (swatches + slider, live while dragging, saved on release) and the glow border.
 * [onChange] gets (spec, commit): commit false only previews.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeEditor(spec: CustomThemeSpec, onChange: (CustomThemeSpec, Boolean) -> Unit) {
    val latest by rememberUpdatedState(spec)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Label(R.string.custom_base)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((base, label) in listOf(
                CustomBase.SYSTEM to R.string.custom_base_system, CustomBase.LIGHT to R.string.custom_base_light,
                CustomBase.DARK to R.string.custom_base_dark, CustomBase.BLACK to R.string.custom_base_black,
            )) {
                MensaFilterChip(selected = spec.base == base, onClick = { onChange(spec.copy(base = base), true) }, label = { Text(stringResource(label)) })
            }
        }

        Label(R.string.custom_accent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((hue, name) in SWATCHES) Swatch(hue, name, selected = spec.hue == hue) { onChange(spec.copy(hue = hue), true) }
        }
        var dragging by remember { mutableFloatStateOf(-1f) }
        val hueLabel = stringResource(R.string.custom_hue)
        Slider(
            value = if (dragging >= 0f) dragging else spec.hue.toFloat(),
            onValueChange = {
                dragging = it
                onChange(latest.copy(hue = it.toInt()), false)
            },
            onValueChangeFinished = {
                if (dragging >= 0f) onChange(latest.copy(hue = dragging.toInt()), true)
                dragging = -1f
            },
            valueRange = 0f..359f,
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.horizontalGradient((0..360 step 30).map { swatchColor(it) })),
                )
            },
            modifier = Modifier.semantics {
                contentDescription = hueLabel
                stateDescription = "${spec.hue}°"
            },
        )

        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = spec.glow, role = Role.Switch, onValueChange = { onChange(spec.copy(glow = it), true) })
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.custom_glow), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.custom_glow_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = spec.glow, onCheckedChange = null)
        }
    }
}

@Composable
private fun Label(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A 32dp color dot in a 48dp touch target; the selected one gets a ring in the text color. */
@Composable
private fun Swatch(hue: Int, @StringRes name: Int, selected: Boolean, onClick: () -> Unit) {
    val ring = MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val label = stringResource(name)
        Box(
            Modifier
                .size(32.dp)
                .then(if (selected) Modifier.border(BorderStroke(2.dp, ring), CircleShape).padding(4.dp) else Modifier)
                .clip(CircleShape)
                .background(swatchColor(hue))
                .semantics { contentDescription = label },
        )
    }
}
