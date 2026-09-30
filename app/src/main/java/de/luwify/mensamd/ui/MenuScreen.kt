package de.luwify.mensamd.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import de.luwify.mensamd.model.LegendCode
import de.luwify.mensamd.model.CodeCategory
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.R
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.ui.theme.veganChipColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** The body of one Mensa page: the continuous menu list, or a message when there is none. */
@Composable
fun MenuBody(ui: MenuUiState, days: List<MenuDay>, today: LocalDate, onRetry: () -> Unit) {
    when {
        days.isNotEmpty() -> MenuList(ui, days, today)
        !ui.hasData && ui.refreshFailed -> Message(stringResource(R.string.error_no_data), onRetry)
        ui.hasData && !ui.isRefreshing -> Message(stringResource(R.string.empty_no_upcoming), null)
        // else: nothing cached yet and the first fetch is running. The progress bar says so; no spinner screen.
    }
}

@Composable
private fun Message(text: String, onRetry: (() -> Unit)?) {
    Column(
        // Scrollable so pull-to-refresh also works from the message screens.
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null) {
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
        }
    }
}

/**
 * All upcoming days in one scroll. The list starts at its first item, which is today or the next
 * open day, because past days are already filtered out.
 */
@Composable
private fun MenuList(ui: MenuUiState, days: List<MenuDay>, today: LocalDate) {
    val locale = LocalConfiguration.current.locales[0]
    val euro = remember(locale) { NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("EUR") } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val codeIndex = remember(ui.legend) { ui.legend.codes.associateBy { it.code } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Horizontal padding lives on the items so sticky headers can span the full width.
        contentPadding = PaddingValues(bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        days.forEachIndexed { index, day ->
            stickyHeader(key = "header-${day.date}") { DayHeader(day.date, today, locale) }
            val keys = dishKeys(day)
            items(day.dishes, key = { keys.getValue(it) }) { dish ->
                DishCard(dish, ui.legend, codeIndex, locale.language, euro, Modifier.padding(horizontal = 16.dp))
            }
            if (day.dishes.isEmpty()) {
                // Everything on this day is filtered out: keep the day visible and say why.
                item(key = "empty-${day.date}") {
                    Text(
                        stringResource(R.string.no_matching_dishes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (index < days.lastIndex) {
                item(key = "divider-${day.date}") { HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }
        }
        if (ui.lastUpdatedMillis != null) item(key = "footer") { UpdatedFooter(ui.lastUpdatedMillis, ui.refreshFailed) }
    }
}

/** Stable, unique lazy-list keys per dish: date + German name, numbered when a name repeats that day. */
private fun dishKeys(day: MenuDay): Map<Dish, String> {
    val seen = HashMap<String, Int>()
    return day.dishes.associateWith { dish ->
        val base = "${day.date}|${dish.name.de}"
        val n = seen.merge(base, 1, Int::plus)!!
        if (n == 1) base else "$base#$n"
    }
}

/** "Heute" / "Morgen" / weekday, with the date on the right. Opaque so cards scroll beneath it. */
@Composable
private fun DayHeader(date: LocalDate, today: LocalDate, locale: Locale) {
    val weekday = remember(locale) { DateTimeFormatter.ofPattern("EEEE", locale) }
    val shortDate = remember(locale) { DateTimeFormatter.ofPattern("d.M.", locale) }
    val weekdayDate = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d.M.", locale) }
    val relative = date == today || date == today.plusDays(1)
    val title = when (date) {
        today -> stringResource(R.string.today)
        today.plusDays(1) -> stringResource(R.string.tomorrow)
        else -> date.format(weekday)
    }
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            date.format(if (relative) weekdayDate else shortDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DishCard(
    dish: Dish,
    legend: Legend,
    codeIndex: Map<String, LegendCode>,
    language: String,
    euro: NumberFormat,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (dish.badges.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dish.badges.forEach { BadgeLabel(it.forLanguage(language)) }
                }
            }
            Text(dish.name.forLanguage(language), style = MaterialTheme.typography.titleMedium)
            if (dish.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dish.tags.forEach { TagLabel(it) }
                }
            }
            if (dish.prices.isNotEmpty()) {
                val fallback = listOf(R.string.price_students, R.string.price_staff, R.string.price_guests)
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    dish.prices.forEachIndexed { index, cents ->
                        val label = legend.priceGroups.getOrNull(index)?.forLanguage(language)
                            ?: fallback.getOrNull(index)?.let { stringResource(it) }.orEmpty()
                        Column {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(euro.format(cents / 100.0), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
            Footprint(dish)
            if (dish.codes.isNotEmpty()) CodeMeanings(dish.codes, codeIndex, language)
        }
    }
}

/** Prefix badge ("Save the meal", "Aktionstheke", ...): outlined with the tertiary color, unlike the filled tag chips. */
@Composable
private fun BadgeLabel(text: String) {
    val color = MaterialTheme.colorScheme.tertiary
    Surface(shape = MaterialTheme.shapes.extraSmall, color = Color.Transparent, border = BorderStroke(1.dp, color)) {
        Text(
            text.replaceFirstChar { it.uppercase() }, // the English side is lowercase ("pasta buffet")
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** CO2e and water per portion; nothing at all when the site has no data ("Keine Daten"). */
@Composable
private fun Footprint(dish: Dish) {
    val parts = listOfNotNull(
        dish.co2Grams?.let { stringResource(R.string.co2_value, it) },
        dish.waterLiters?.let { stringResource(R.string.water_value, it) },
    )
    if (parts.isEmpty()) return
    Text(
        stringResource(R.string.per_portion, parts.joinToString(" · ")),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Collapsed by default; the state is saved per lazy-list key, so it survives scrolling and rotation. */
@Composable
private fun CodeMeanings(codes: List<String>, codeIndex: Map<String, LegendCode>, language: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text(
        stringResource(R.string.codes_toggle, codes.distinct().size) + if (expanded) " ▴" else " ▾",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 4.dp),
    )
    if (expanded) {
        val groups = remember(codes, codeIndex, language) { groupCodes(codes, codeIndex, language) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(
                            when (group.category) {
                                CodeCategory.ADDITIVE -> R.string.code_group_additive
                                CodeCategory.ALLERGEN -> R.string.code_group_allergen
                                CodeCategory.OTHER -> R.string.code_group_other
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    group.codes.forEach { meaning ->
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(meaning.code) }
                                if (meaning.text != null) append("  ${meaning.text}")
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TagLabel(tag: Tag) {
    val label = tagLabelRes(tag.key)?.let { stringResource(it) } ?: tag.label
    // Vegan gets its own light-green pair per theme; every other chip keeps the theme's secondary colors.
    val vegan = if (tag.key == "vegan") veganChipColors(isSystemInDarkTheme()) else null
    Surface(
        shape = MaterialTheme.shapes.small,
        color = vegan?.container ?: MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = vegan?.content ?: MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun UpdatedFooter(updatedAt: Long, failed: Boolean) {
    val relative = DateUtils.getRelativeTimeSpanString(updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
    val text = stringResource(R.string.last_updated, relative) + if (failed) " · " + stringResource(R.string.update_failed) else ""
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
