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
import de.luwify.mensamd.data.settings.hueToAccent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType

/** Quick picks for the accent hue, with names for TalkBack. The slider below reaches every other hue. */
private val SWATCHES = listOf(
    0 to R.string.hue_red, 25 to R.string.hue_orange, 45 to R.string.hue_yellow, 90 to R.string.hue_lime,
    140 to R.string.hue_green, 175 to R.string.hue_teal, 200 to R.string.hue_cyan, 220 to R.string.hue_blue,
    265 to R.string.hue_violet, 300 to R.string.hue_magenta, 335 to R.string.hue_pink,
)

/** A swatch is the accent the old hue-only setting meant: HSL saturation 62 %, lightness 52 % of its hue. */
private fun swatchRgb(hue: Int) = hueToAccent(hue)

private fun rgbColor(rgb: Int) = Color(0xFF000000.toInt() or (rgb and 0xFFFFFF))

/**
 * The editor for the user's own theme (shown on the settings page while "Eigenes" is selected): background
 * (drop-down), accent color (swatches, a hue slider that keeps saturation and lightness, or typed as hex / RGB)
 * and the glow border. [onChange] gets (spec, commit): commit false only previews (slider moving).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CustomThemeEditor(spec: CustomThemeSpec, onChange: (CustomThemeSpec, Boolean) -> Unit) {
    val latest by rememberUpdatedState(spec)
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SettingsDropdown(
            label = stringResource(R.string.custom_base),
            options = listOf(
                CustomBase.SYSTEM to stringResource(R.string.custom_base_system),
                CustomBase.LIGHT to stringResource(R.string.custom_base_light),
                CustomBase.DARK to stringResource(R.string.custom_base_dark),
                CustomBase.BLACK to stringResource(R.string.custom_base_black),
            ),
            selected = spec.base,
            onSelect = { onChange(latest.copy(base = it), true) },
            modifier = Modifier.fillMaxWidth(),
        )

        Label(R.string.custom_accent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for ((hue, name) in SWATCHES) {
                Swatch(swatchRgb(hue), name, selected = spec.accent == swatchRgb(hue)) { onChange(latest.copy(accent = swatchRgb(hue)), true) }
            }
        }
        var dragging by remember { mutableFloatStateOf(-1f) }
        val hueLabel = stringResource(R.string.custom_hue)
        val hue = ColorInput.hue(spec.accent)
        Slider(
            value = if (dragging >= 0f) dragging else hue.toFloat(),
            onValueChange = {
                dragging = it
                onChange(latest.copy(accent = ColorInput.withHue(latest.accent, it)), false)
            },
            onValueChangeFinished = {
                if (dragging >= 0f) onChange(latest.copy(accent = ColorInput.withHue(latest.accent, dragging)), true)
                dragging = -1f
            },
            valueRange = 0f..359f,
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.horizontalGradient((0..360 step 30).map { rgbColor(swatchRgb(it)) })),
                )
            },
            modifier = Modifier.semantics {
                contentDescription = hueLabel
                stateDescription = "$hue°"
            },
        )
        ColorFields(spec.accent) { onChange(latest.copy(accent = it), true) }
        // The theme keeps text readable: say so when the accent had to be darkened/lightened for that.
        if (MaterialTheme.colorScheme.primary != rgbColor(spec.accent)) {
            Text(
                stringResource(R.string.custom_accent_adjusted),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = spec.glow, role = Role.Switch, onValueChange = { onChange(latest.copy(glow = it), true) })
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

/**
 * The accent as text: a hex field and R/G/B fields, kept in sync. A field applies its value as soon as it is valid;
 * it is only rewritten from outside (swatch, slider, another field) when it no longer matches the color, so typing
 * is never interrupted.
 */
@Composable
private fun ColorFields(accent: Int, onAccent: (Int) -> Unit) {
    val latest by rememberUpdatedState(accent)
    var hex by remember { mutableStateOf(ColorInput.formatHex(accent)) }
    LaunchedEffect(accent) { if (ColorInput.parseHex(hex) != accent) hex = ColorInput.formatHex(accent) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(rgbColor(accent)).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { text ->
                hex = text.take(7)
                ColorInput.parseHex(hex)?.let(onAccent)
            },
            label = { Text(stringResource(R.string.custom_hex)) },
            singleLine = true,
            isError = ColorInput.parseHex(hex) == null,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            modifier = Modifier.weight(1f),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((channel, label) in listOf(0 to R.string.custom_red, 1 to R.string.custom_green, 2 to R.string.custom_blue)) {
            val current = when (channel) {
                0 -> ColorInput.red(accent)
                1 -> ColorInput.green(accent)
                else -> ColorInput.blue(accent)
            }
            var text by remember(channel) { mutableStateOf(current.toString()) }
            LaunchedEffect(current) { if (ColorInput.parseChannel(text) != current) text = current.toString() }
            OutlinedTextField(
                value = text,
                onValueChange = { new ->
                    text = new.filter { it.isDigit() }.take(3)
                    ColorInput.parseChannel(text)?.let { onAccent(ColorInput.withChannel(latest, channel, it)) }
                },
                label = { Text(stringResource(label)) },
                singleLine = true,
                isError = ColorInput.parseChannel(text) == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Label(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A 32dp color dot in a 48dp touch target; the selected one gets a ring in the text color. */
@Composable
private fun Swatch(rgb: Int, @StringRes name: Int, selected: Boolean, onClick: () -> Unit) {
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
                .background(rgbColor(rgb))
                .semantics { contentDescription = label },
        )
    }
}
