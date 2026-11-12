package de.luwify.mensamd.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.R
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.stats.Amounts
import de.luwify.mensamd.stats.Extras
import de.luwify.mensamd.stats.KnownDish
import de.luwify.mensamd.stats.MealMatcher
import de.luwify.mensamd.stats.MealPart
import de.luwify.mensamd.stats.MealParts
import de.luwify.mensamd.stats.SnapDish
import de.luwify.mensamd.ui.SheetHeading
import de.luwify.mensamd.ui.forLanguage
import java.text.DecimalFormatSymbols
import java.util.Locale

/** A part being edited: what it is ([part], price ignored) and the price as typed. */
private data class PartDraft(val part: MealPart, val priceText: String)

private enum class AddKind { MENU, KNOWN, NEW, EXTRA }

/**
 * "Mehrere Gerichte": the payment split into any number of parts, each a dish of that day's menu, a known dish, a new dish
 * (name, Mensa, tags; saved with its price) or an extra, each with its price. The parts must add up to the payment; the
 * rest is shown as it changes. A new part starts with its menu price, or else with what is still open.
 */
@Composable
internal fun MealPartsStep(
    amount: Int,
    dishes: List<SnapDish>,
    known: List<KnownDish>,
    customExtras: List<String>,
    group: Int,
    defaultMensaId: String?,
    language: String,
    locale: Locale,
    onSave: (List<MealPart>) -> Unit,
) {
    val comma = remember(locale) { DecimalFormatSymbols.getInstance(locale).decimalSeparator == ',' }
    val drafts = remember { mutableStateListOf<PartDraft>() }
    var adding by remember { mutableStateOf<AddKind?>(null) }
    val unknownMensa = stringResource(R.string.meal_unknown_mensa)
    val prices = drafts.map { Amounts.parseEuro(it.priceText) }
    val rest = MealParts.rest(amount, prices)
    val parts = drafts.zip(prices).mapNotNull { (d, p) -> p?.let { d.part.withPrice(it) } }
    val valid = parts.size == drafts.size && MealParts.valid(amount, parts)
    fun add(part: MealPart, price: Int?) {
        val start = price ?: rest.takeIf { it > 0 }
        drafts.add(PartDraft(part, start?.let { Amounts.toInput(it, comma) }.orEmpty()))
        adding = null
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        LazyColumn(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            item(key = "parts-title") { SheetHeading(stringResource(R.string.parts_title)) }
            item(key = "parts-hint") { Hint(stringResource(R.string.parts_hint)) }
            items(drafts.size, key = { "part-$it" }) { i ->
                val d = drafts[i]
                PartRow(d, prices[i], language, unknownMensa, onPrice = { drafts[i] = d.copy(priceText = it.take(10)) }, onRemove = { drafts.removeAt(i) })
            }
            item(key = "add-title") { SheetHeading(stringResource(R.string.parts_add)) }
            item(key = "add-menu") { AddRow(stringResource(R.string.parts_add_menu)) { adding = AddKind.MENU } }
            item(key = "add-known") { AddRow(stringResource(R.string.parts_add_known)) { adding = AddKind.KNOWN } }
            item(key = "add-new") { AddRow(stringResource(R.string.parts_add_new)) { adding = AddKind.NEW } }
            item(key = "add-extra") { AddRow(stringResource(R.string.parts_add_extra)) { adding = AddKind.EXTRA } }
        }
        HorizontalDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    drafts.isEmpty() -> stringResource(R.string.parts_none)
                    rest > 0 -> stringResource(R.string.q_split_rest, formatCardAmount(rest, locale))
                    rest < 0 -> stringResource(R.string.parts_too_much, formatCardAmount(-rest, locale))
                    else -> stringResource(R.string.q_split_ok)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (valid) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            Button(onClick = { onSave(parts) }, enabled = valid, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.q_save)) }
        }
    }

    when (adding) {
        AddKind.MENU -> {
            val offered = remember(dishes, defaultMensaId) {
                dishes.distinctBy { Triple(it.mensaId, it.name.de, it.prices) }.sortedBy { it.mensaId != defaultMensaId }
            }
            PickDialog(
                stringResource(R.string.parts_add_menu), offered.size, stringResource(R.string.meal_no_menu),
                label = { offered[it].name.forLanguage(language) },
                detail = { i ->
                    val d = offered[i]
                    (listOf(mensaName(d.mensaId, language, unknownMensa)) +
                        MealMatcher.prices(d, group).map { formatCardAmount(it, locale) + if (d.byWeight) " " + stringResource(R.string.price_per_100g) else "" }
                        ).joinToString(" · ")
                },
                onPick = { i ->
                    val d = offered[i]
                    add(MealPart.Dish(d.name, d.mensaId, d.tags, d.own, 0), MealMatcher.prices(d, group).firstOrNull()?.takeUnless { d.byWeight })
                },
                onDismiss = { adding = null },
            )
        }
        AddKind.KNOWN -> PickDialog(
            stringResource(R.string.parts_add_known), known.size, stringResource(R.string.meal_search_none),
            label = { known[it].name.forLanguage(language) },
            detail = { i -> listOf(mensaName(known[i].mensaId, language, unknownMensa), formatCardAmount(known[i].price, locale)).joinToString(" · ") },
            onPick = { i ->
                val k = known[i]
                add(MealPart.Dish(k.name, k.mensaId, k.tags, k.own, 0), k.price)
            },
            onDismiss = { adding = null },
        )
        AddKind.NEW -> DishDetailsDialog(
            title = stringResource(R.string.meal_new_title),
            name = "",
            nameEditable = true,
            mensaId = defaultMensaId,
            tags = emptyList(),
            note = null,
            onConfirm = { name, mensaId, tags -> add(MealPart.NewDish(name, mensaId, tags, 0), null) },
            onDismiss = { adding = null },
        )
        AddKind.EXTRA -> {
            val extras = Extras.builtIn + customExtras
            val names = extras.map { extraName(it) }
            PickDialog(
                stringResource(R.string.parts_add_extra), extras.size, "",
                label = { names[it] },
                detail = { null },
                onPick = { add(MealPart.Extra(extras[it], 0), null) },
                onDismiss = { adding = null },
            )
        }
        null -> Unit
    }
}

private fun MealPart.withPrice(price: Int): MealPart = when (this) {
    is MealPart.Dish -> copy(price = price)
    is MealPart.NewDish -> copy(price = price)
    is MealPart.Extra -> copy(price = price)
}

@Composable
private fun PartRow(d: PartDraft, price: Int?, language: String, unknownMensa: String, onPrice: (String) -> Unit, onRemove: () -> Unit) {
    val (name, detail) = when (val p = d.part) {
        is MealPart.Dish -> p.name.forLanguage(language) to mensaName(p.mensaId, language, unknownMensa)
        is MealPart.NewDish -> p.name to mensaName(p.mensaId, language, unknownMensa) + " · " + stringResource(R.string.parts_new_dish)
        is MealPart.Extra -> extraName(p.key) to stringResource(R.string.parts_extra)
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = d.priceText,
            onValueChange = onPrice,
            singleLine = true,
            suffix = { Text("€") },
            isError = price == null,
            label = { Text(stringResource(R.string.parts_price)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.padding(start = 8.dp).width(120.dp),
        )
        IconButton(onClick = onRemove) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.parts_remove), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+  $label", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** A list to pick one entry from ([count] entries; [empty] shown when there are none). */
@Composable
private fun PickDialog(
    title: String,
    count: Int,
    empty: String,
    label: @Composable (Int) -> String,
    detail: @Composable (Int) -> String?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (count == 0) {
                Text(empty)
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(count, key = { it }) { i ->
                        Column(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button) { onPick(i) }.padding(vertical = 6.dp)) {
                            Text(label(i), style = MaterialTheme.typography.bodyLarge)
                            detail(i)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_action)) } },
    )
}
