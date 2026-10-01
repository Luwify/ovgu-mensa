package de.luwify.mensamd.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import de.luwify.mensamd.data.settings.ViewPrefs
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Box
import de.luwify.mensamd.ui.theme.Sunflower
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.unit.sp
import de.luwify.mensamd.R
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.ui.theme.footprintColor
import de.luwify.mensamd.ui.theme.veganChipColors
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

/** The body of one Mensa page: the continuous menu list, or a message when there is none. */
@Composable
fun MenuBody(
    ui: MenuUiState,
    days: List<MenuDay>,
    today: LocalDate,
    prefs: ViewPrefs,
    footprints: Map<LocalDate, DayFootprint>,
    onRetry: () -> Unit,
) {
    when {
        days.isNotEmpty() -> MenuList(ui, days, today, prefs, footprints)
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
private fun MenuList(ui: MenuUiState, days: List<MenuDay>, today: LocalDate, prefs: ViewPrefs, footprints: Map<LocalDate, DayFootprint>) {
    val locale = LocalConfiguration.current.locales[0]
    val euro = remember(locale) { NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("EUR") } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val codeIndex = remember(ui.legend) { ui.legend.codes.associateBy { it.code } }
    val rows = remember(days, ui.lastUpdatedMillis, ui.refreshFailed) { buildRows(days, ui.lastUpdatedMillis, ui.refreshFailed) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Horizontal padding lives on the items so sticky headers can span the full width.
        contentPadding = PaddingValues(bottom = bottom + 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (prefs.compact) 8.dp else 12.dp),
    ) {
        for (row in rows) {
            when (row) {
                is ListRow.Header -> stickyHeader(key = row.key, contentType = "header") { DayHeader(row.date, today, locale) }
                is ListRow.SubHeader -> item(key = row.key, contentType = "subheader") { SubHeader(row.dinner) }
                is ListRow.DishRow -> item(key = row.key, contentType = "dish") {
                    DishCard(
                        row.dish, ui.legend, codeIndex, locale.language, euro, prefs.priceGroup, footprints[row.date],
                        prefs.compact, Modifier.padding(horizontal = 16.dp),
                    )
                }
                is ListRow.NoMatch -> item(key = row.key, contentType = "empty") {
                    Text(
                        stringResource(R.string.no_matching_dishes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                is ListRow.SubDivider -> item(key = row.key, contentType = "subdivider") {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
                is ListRow.DayDivider -> item(key = row.key, contentType = "divider") {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                is ListRow.Footer -> item(key = row.key, contentType = "footer") { UpdatedFooter(row.updatedAt, row.failed) }
            }
        }
    }
}

/** Small label that tells the lunch part from the dinner part of a day. */
@Composable
private fun SubHeader(dinner: Boolean) {
    Text(
        stringResource(if (dinner) R.string.dinner else R.string.lunch),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
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

/**
 * Normal mode: the full card. Compact mode: a short card (name, small price, tag markers) that
 * expands to exactly the normal layout when tapped, and back. The expanded state is saved per
 * lazy-list key, like the other card toggles.
 */
@Composable
private fun DishCard(
    dish: Dish,
    legend: Legend,
    codeIndex: Map<String, LegendCode>,
    language: String,
    euro: NumberFormat,
    priceGroup: Int,
    footprint: DayFootprint?,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val full = !compact || open
    val body: @Composable () -> Unit = {
        if (full) {
            FullCardBody(dish, legend, codeIndex, language, euro, priceGroup, footprint)
        } else {
            CompactCardBody(dish, legend, language, euro, priceGroup)
        }
    }
    if (compact) {
        Card(onClick = { open = !open }, modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Card(modifier.fillMaxWidth()) { body() }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FullCardBody(
    dish: Dish,
    legend: Legend,
    codeIndex: Map<String, LegendCode>,
    language: String,
    euro: NumberFormat,
    priceGroup: Int,
    footprint: DayFootprint?,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        if (dish.badges.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dish.badges.forEach { BadgeLabel(it.forLanguage(language)) }
            }
        }
        Text(dish.name.forLanguage(language), style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp))
        if (dish.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dish.tags.forEach { TagLabel(it) }
            }
        }
        Prices(dish.prices, priceGroup, legend, language, euro)
        Footprint(dish, footprint)
        if (dish.codes.isNotEmpty()) CodeMeanings(dish.codes, codeIndex, language)
    }
}

/** Name (at most two lines) with the selected price on the same row, then badges and tag markers. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactCardBody(dish: Dish, legend: Legend, language: String, euro: NumberFormat, priceGroup: Int) {
    val split = splitPrices(dish.prices, priceGroup)
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Text(
                dish.name.forLanguage(language),
                style = MaterialTheme.typography.titleSmall.copy(lineHeight = 18.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (split != null) {
                Surface(shape = MaterialTheme.shapes.extraSmall, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        euro.format(dish.prices[split.selected] / 100.0),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        if (dish.badges.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dish.badges.forEach { BadgeLabel(it.forLanguage(language)) }
            }
        }
        if (dish.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { dish.tags.forEach { TagMarker(it) } }
        }
    }
}

/**
 * Icon-only tag marker for compact cards. Tap or long-press shows the tag's name. The sunflower
 * marks vegan; others use an emoji glyph, or the label's first letter when the font lacks it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagMarker(tag: Tag) {
    val label = tagLabelRes(tag.key)?.let { stringResource(it) } ?: tag.label
    val dark = isSystemInDarkTheme()
    val vegan = if (tag.key == "vegan") veganChipColors(dark) else null
    val glyph = remember(tag.key, label) {
        tagMarkerGlyph(tag.key)?.takeIf { android.graphics.Paint().hasGlyph(it) } ?: tagMarkerLetter(label, tag.key)
    }
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltip,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(MaterialTheme.shapes.small)
                .background(vegan?.container ?: MaterialTheme.colorScheme.secondaryContainer)
                .clickable(role = Role.Button, onClickLabel = label) { scope.launch { tooltip.show() } }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            if (vegan != null) {
                Image(rememberVectorPainter(Sunflower.vector(dark)), contentDescription = null, modifier = Modifier.size(18.dp))
            } else {
                Text(glyph, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

/**
 * Collapsed: only the selected group's price in a tonal pill, with an arrow to its right. Expanded:
 * the pill gets its group label and the other prices appear inline to the right, labeled.
 * The expanded state is saved per lazy-list key like the code meanings.
 */
@Composable
private fun Prices(prices: List<Int>, priceGroup: Int, legend: Legend, language: String, euro: NumberFormat) {
    val split = splitPrices(prices, priceGroup) ?: return
    val fallback = listOf(R.string.price_students, R.string.price_staff, R.string.price_guests)
    @Composable
    fun label(index: Int) = legend.priceGroups.getOrNull(index)?.forLanguage(language)
        ?: fallback.getOrNull(index)?.let { stringResource(it) }.orEmpty()

    var expanded by rememberSaveable { mutableStateOf(false) }
    // One row: [selected price] [arrow] [other prices...]. If it is too wide for a narrow screen
    // it scrolls sideways instead of wrapping below.
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                if (expanded) {
                    Text(label(split.selected), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Text(
                    euro.format(prices[split.selected] / 100.0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        if (split.others.isNotEmpty()) {
            val description = stringResource(if (expanded) R.string.hide_other_prices else R.string.show_other_prices)
            Text(
                if (expanded) "◂" else "▸",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = description) { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (expanded) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    split.others.forEach { index ->
                        Column {
                            Text(label(index), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(euro.format(prices[index] / 100.0), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
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

/**
 * CO2e and water per portion; nothing at all when the site has no data ("Keine Daten"). Each value
 * is colored green to red relative to the other dishes of the day ([scale]); the number stays text,
 * and values without a meaningful scale stay neutral.
 */
@Composable
private fun Footprint(dish: Dish, scale: DayFootprint?) {
    val co2 = dish.co2Grams
    val water = dish.waterLiters
    if (co2 == null && water == null) return
    val dark = isSystemInDarkTheme()
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val parts = buildList {
        if (co2 != null) add(stringResource(R.string.co2_value, co2) to scale?.co2?.level(co2))
        if (water != null) add(stringResource(R.string.water_value, water) to scale?.water?.level(water))
    }
    // "%1$s per portion": split around the placeholder so the values can be colored individually.
    val (before, after) = stringResource(R.string.per_portion, "\u0001").split("\u0001").let { it[0] to it.getOrElse(1) { "" } }
    Text(
        buildAnnotatedString {
            append(before)
            parts.forEachIndexed { index, (text, level) ->
                if (index > 0) append(" · ")
                if (level != null) {
                    withStyle(SpanStyle(color = footprintColor(level, dark), fontWeight = FontWeight.SemiBold)) { append(text) }
                } else {
                    append(text)
                }
            }
            append(after)
        },
        style = MaterialTheme.typography.labelMedium,
        color = neutral,
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
        modifier = Modifier.clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 2.dp),
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
    val dark = isSystemInDarkTheme()
    // Vegan gets its own light-green pair per theme and a sunflower; every other chip keeps the theme's secondary colors.
    val vegan = if (tag.key == "vegan") veganChipColors(dark) else null
    Surface(
        shape = MaterialTheme.shapes.small,
        color = vegan?.container ?: MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(start = if (vegan != null) 6.dp else 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (vegan != null) {
                Image(rememberVectorPainter(Sunflower.vector(dark)), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = vegan?.content ?: MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
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
