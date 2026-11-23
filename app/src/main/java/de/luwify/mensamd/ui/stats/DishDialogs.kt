package de.luwify.mensamd.ui.stats

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.material3.Switch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.text.DecimalFormatSymbols
import de.luwify.mensamd.R
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.stats.Amounts
import de.luwify.mensamd.stats.DishTags
import de.luwify.mensamd.ui.SettingsDropdown
import de.luwify.mensamd.ui.forLanguage
import de.luwify.mensamd.ui.tagLabelRes
import de.luwify.mensamd.ui.theme.MensaFilterChip

/** What [DishDetailsDialog] confirms; [price] null without a price field, [per100] only [byWeight] and when entered. */
internal data class DishDetails(
    val name: String,
    val mensaId: String?,
    val tags: List<Tag>,
    val price: Int?,
    val byWeight: Boolean = false,
    val per100: Int? = null,
)

/**
 * Name, Mensa and tags of a dish, for "Als neues Gericht speichern" and for editing a dish of a payment. The Mensa is
 * one of the catalog or "Andere / unbekannt" (null); the tags are the menu's tag chips, multiple choice, only those in
 * [tags] selected at the start. [nameEditable] false: a menu dish keeps its name. [note] explains what the change
 * reaches. Without a tag the dish counts as "no information" in the vegan/vegetarian shares. [price] (mills) non-null
 * adds a price field, starting with it; the confirmed price is then passed on (null without the field). [weightOption]
 * adds "Nach Gewicht": the price is then what was paid this time, with an optional price per 100 g.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DishDetailsDialog(
    title: String,
    name: String,
    nameEditable: Boolean,
    mensaId: String?,
    tags: List<Tag>,
    note: String?,
    price: Int? = null,
    weightOption: Boolean = false,
    onConfirm: (DishDetails) -> Unit,
    onDismiss: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.language
    val comma = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',' }
    var text by rememberSaveable { mutableStateOf(name) }
    var priceText by rememberSaveable { mutableStateOf(price?.takeIf { it > 0 }?.let { Amounts.toInput(it, comma) }.orEmpty()) }
    val parsedPrice = Amounts.parseEuro(priceText)?.takeIf { it > 0 }
    var byWeight by rememberSaveable { mutableStateOf(false) }
    var per100Text by rememberSaveable { mutableStateOf("") }
    val per100 = Amounts.parseEuro(per100Text)?.takeIf { it > 0 }
    val per100Valid = per100Text.isBlank() || per100 != null
    // A MensaCatalog id, or "" for "Andere / unbekannt" (keeps the saved state a plain String).
    var mensa by rememberSaveable { mutableStateOf(mensaId.orEmpty()) }
    // Tag keys never contain a comma.
    var selected by rememberSaveable { mutableStateOf(tags.joinToString(",") { it.key }) }
    val keys = selected.split(',').filter { it.isNotEmpty() }.toSet()
    val offered = remember(tags) { DishTags.offered(tags) }
    val other = stringResource(R.string.dish_mensa_other)
    val options = remember(language, other) {
        MensaCatalog.all.map { it.id to it.name.forLanguage(language) } + ("" to other)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    singleLine = true,
                    enabled = nameEditable,
                    label = { Text(stringResource(R.string.meal_new_label)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (weightOption) {
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = byWeight, role = Role.Switch, onValueChange = { byWeight = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.dish_by_weight), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = byWeight, onCheckedChange = null)
                    }
                }
                if (price != null) {
                    PriceField(
                        priceText, parsedPrice, per100.takeIf { byWeight },
                        label = stringResource(if (byWeight) R.string.dish_paid_now else R.string.meal_price),
                    ) { priceText = it }
                }
                if (byWeight) {
                    PriceField(per100Text, per100, null, label = stringResource(R.string.dish_per_100g), optional = true) { per100Text = it }
                }
                SettingsDropdown(stringResource(R.string.dish_mensa_label), options, mensa, { mensa = it }, Modifier.fillMaxWidth())
                Text(stringResource(R.string.dish_tags_title), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    offered.forEach { tag ->
                        MensaFilterChip(
                            selected = tag.key in keys,
                            onClick = { selected = (if (tag.key in keys) keys - tag.key else keys + tag.key).joinToString(",") },
                            label = { Text(tagLabelRes(tag.key)?.let { stringResource(it) } ?: tag.label) },
                        )
                    }
                }
                Hint(stringResource(R.string.dish_tags_hint))
                if (note != null) Hint(note)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(DishDetails(text.trim(), mensa.ifEmpty { null }, DishTags.pick(keys, tags), parsedPrice, byWeight, per100.takeIf { byWeight })) },
                enabled = text.isNotBlank() && (price == null || parsedPrice != null) && (!byWeight || per100Valid),
            ) { Text(stringResource(R.string.q_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) } },
    )
}
