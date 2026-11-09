package de.luwify.mensamd.ui.stats

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
import de.luwify.mensamd.R
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.stats.DishTags
import de.luwify.mensamd.ui.SettingsDropdown
import de.luwify.mensamd.ui.forLanguage
import de.luwify.mensamd.ui.tagLabelRes
import de.luwify.mensamd.ui.theme.MensaFilterChip

/**
 * Name, Mensa and tags of a dish, for "Als neues Gericht speichern" and for editing a dish of a payment. The Mensa is
 * one of the catalog or "Andere / unbekannt" (null); the tags are the menu's tag chips, multiple choice, only those in
 * [tags] selected at the start. [nameEditable] false: a menu dish keeps its name. [note] explains what the change
 * reaches. Without a tag the dish counts as "no information" in the vegan/vegetarian shares.
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
    onConfirm: (name: String, mensaId: String?, tags: List<Tag>) -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalConfiguration.current.locales[0].language
    var text by rememberSaveable { mutableStateOf(name) }
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
                onClick = { onConfirm(text.trim(), mensa.ifEmpty { null }, DishTags.pick(keys, tags)) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.q_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) } },
    )
}
