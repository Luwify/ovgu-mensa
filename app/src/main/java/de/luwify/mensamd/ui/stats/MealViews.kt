package de.luwify.mensamd.ui.stats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.luwify.mensamd.R
import de.luwify.mensamd.card.formatCardAmount
import de.luwify.mensamd.favorites.Favorites
import de.luwify.mensamd.model.MensaCatalog
import de.luwify.mensamd.model.MenuPage
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.stats.CardEvent
import de.luwify.mensamd.stats.DishSearch
import de.luwify.mensamd.stats.EatenDish
import de.luwify.mensamd.stats.Extras
import de.luwify.mensamd.stats.KnownDish
import de.luwify.mensamd.stats.MealMatcher
import de.luwify.mensamd.stats.MealRecord
import de.luwify.mensamd.stats.MealSummary
import de.luwify.mensamd.stats.MenuDishes
import de.luwify.mensamd.stats.OpenItem
import de.luwify.mensamd.stats.SnapDish
import de.luwify.mensamd.stats.StatsData
import de.luwify.mensamd.ui.SheetHeading
import de.luwify.mensamd.ui.forLanguage
import de.luwify.mensamd.ui.tagLabelRes
import de.luwify.mensamd.ui.theme.PlusIcon
import java.text.NumberFormat
import java.util.Locale

internal fun mensaName(id: String?, language: String, unknown: String): String =
    id?.let { MensaCatalog.byId(it)?.name?.forLanguage(language) ?: it } ?: unknown

/** "Gericht bearbeiten" for one dish: a hand-named dish can be renamed and its change reaches every payment with it. */
@Composable
internal fun EditDishDialog(
    dish: EatenDish,
    language: String,
    onConfirm: (name: String, mensaId: String?, tags: List<Tag>, price: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    DishDetailsDialog(
        title = stringResource(R.string.dish_edit_title),
        name = dish.name.forLanguage(language),
        nameEditable = dish.own,
        mensaId = dish.mensaId,
        tags = dish.tags,
        note = if (dish.own) stringResource(R.string.dish_own_note) else null,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The meals part of the statistics overview (detailed statistics). */
internal fun LazyListScope.mealsSection(data: StatsData, vm: StatsViewModel, locale: Locale, onUnassigned: () -> Unit) {
    val summary = MealSummary.summarize(data.events)
    val language = locale.language
    item(key = "meals-title") { SheetHeading(stringResource(R.string.meals_title)) }
    if (summary.servings == 0) {
        item(key = "meals-empty") { Hint(stringResource(R.string.meals_empty)) }
        return
    }
    item(key = "meals-tiles") {
        val percent = NumberFormat.getPercentInstance(locale)
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            summary.averagePerMeal?.let { Tile(stringResource(R.string.meals_average), formatCardAmount(it, locale)) }
            summary.veganShare?.let { Tile(stringResource(R.string.meals_vegan), percent.format(it)) }
            summary.vegetarianShare?.let { Tile(stringResource(R.string.meals_vegetarian), percent.format(it)) }
        }
    }
    if (summary.unknownTags > 0) item(key = "meals-unknown-tags") {
        Hint(pluralStringResource(R.plurals.meals_unknown_tags, summary.unknownTags, summary.unknownTags), Modifier.padding(top = 8.dp))
    }
    item(key = "meals-top-title") {
        Text(stringResource(R.string.meals_top), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    }
    items(summary.top, key = { "top-${it.key}" }) { d ->
        val favorites by vm.favorites.collectAsStateWithLifecycle()
        val favorite = d.key in favorites
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${d.count}×", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(44.dp))
            Text(d.name.forLanguage(language), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.toggleFavorite(EatenDish(d.key, d.name, null, d.lastPrice, 1, d.tags)) }) {
                Icon(
                    painterResource(if (favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
                    stringResource(if (favorite) R.string.meals_unfavorite else R.string.meals_favorite),
                    tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    item(key = "meals-mensa-title") {
        Text(stringResource(R.string.meals_by_mensa), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
    }
    item(key = "meals-mensa") {
        val unknown = stringResource(R.string.meal_unknown_mensa)
        val max = summary.byMensa.maxOf { it.second }.coerceAtLeast(1)
        val barColor = MaterialTheme.colorScheme.primary
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            summary.byMensa.forEach { (id, n) ->
                Column {
                    // "Unbekannte Mensa" opens the list of those dishes, to assign a Mensa.
                    Row(
                        Modifier.fillMaxWidth().then(if (id == null) Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onUnassigned) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(mensaName(id, language, unknown), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("$n×", style = MaterialTheme.typography.bodyMedium)
                        if (id == null) Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // A thin horizontal bar: 8 dp, rounded ends, length = share of the most-visited Mensa.
                    Box(Modifier.fillMaxWidth(n.toFloat() / max).height(8.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * "Unbekannte Mensa": every dish of the meals without a Mensa, newest first, to assign one (and tags, and for a
 * hand-named dish a new name) with the same dialog as in the history. Full-screen overlay; the list shrinks as dishes
 * get a Mensa.
 */
@Composable
internal fun UnassignedDishesScreen(vm: StatsViewModel, events: List<CardEvent>, onClose: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val language = locale.language
    val rows = remember(events) { MealSummary.withoutMensa(events) }
    var editing by rememberSaveable { mutableStateOf<Pair<Long, Int>?>(null) }
    BackHandler(onBack = onClose)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            TopBar(stringResource(R.string.meal_unknown_mensa), onBack = onClose)
            Hint(stringResource(R.string.unassigned_hint), Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
            if (rows.isEmpty()) Text(stringResource(R.string.unassigned_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
            LazyColumn(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp),
            ) {
                items(rows, key = { "${it.eventId}-${it.index}" }) { r ->
                    Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { editing = r.eventId to r.index }.padding(vertical = 6.dp)) {
                        Text((if (r.dish.count > 1) "${r.dish.count}× " else "") + r.dish.name.forLanguage(language), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            shortDate(r.date, locale) + " · " + formatCardAmount(r.dish.price * r.dish.count, locale),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    val target = editing?.let { (eventId, index) -> rows.firstOrNull { it.eventId == eventId && it.index == index } }
    if (target != null) {
        EditDishDialog(target.dish, language, onConfirm = { name, mensaId, tags, _ ->
            editing = null
            vm.editDish(target.eventId, target.index, name, mensaId, tags)
        }, onDismiss = { editing = null })
    }
}
