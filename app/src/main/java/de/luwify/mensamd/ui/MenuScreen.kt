package de.luwify.mensamd.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import de.luwify.mensamd.data.settings.ViewPrefs
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ripple
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import de.luwify.mensamd.model.Dish
import de.luwify.mensamd.model.FixedSection
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.MenuDay
import de.luwify.mensamd.model.Tag
import de.luwify.mensamd.ui.theme.footprintColor
import de.luwify.mensamd.ui.theme.LocalMensaColors
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
    fixed: List<FixedSection>,
    today: LocalDate,
    prefs: ViewPrefs,
    footprints: Map<LocalDate, DayFootprint>,
    onToggleFavorite: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        // The favorites filter before the first favorite: say how to add one instead of "no matching dishes" per day.
        DishFilters.FAVORITES in prefs.tagFilters && prefs.favorites.isEmpty() && (days.isNotEmpty() || fixed.isNotEmpty()) ->
            Message(stringResource(R.string.favorites_empty), null)
        days.isNotEmpty() -> {
            val rows = remember(days, ui.lastUpdatedMillis, ui.refreshFailed) { buildRows(days, ui.lastUpdatedMillis, ui.refreshFailed) }
            MenuList(ui, rows, today, prefs, footprints, onToggleFavorite)
        }
        fixed.isNotEmpty() -> {
            val rows = remember(fixed, ui.lastUpdatedMillis, ui.refreshFailed) { buildFixedRows(fixed, ui.lastUpdatedMillis, ui.refreshFailed) }
            MenuList(ui, rows, today, prefs, footprints, onToggleFavorite)
        }
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
 * The rows of one Mensa in one scroll. For dated Mensen the list starts at its first item, which is
 * today or the next open day, because past days are already filtered out; a fixed menu starts at
 * its "Festes Angebot" label.
 */
@Composable
private fun MenuList(
    ui: MenuUiState,
    rows: List<ListRow>,
    today: LocalDate,
    prefs: ViewPrefs,
    footprints: Map<LocalDate, DayFootprint>,
    onToggleFavorite: (String) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val euro = remember(locale) { NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("EUR") } }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val codeIndex = remember(ui.legend) { ui.legend.codes.associateBy { it.code } }
    // "Favoriten funkeln": the favorites' aura (SuperAuraOverlay). The aura also gets the jelly drag
    // while scrolling (ScrollDrag).
    val drag = if (prefs.legendary) rememberScrollDrag() else null
    val listState = rememberLazyListState()
    // The aura is drawn in one layer above the list (see SuperAuraOverlay), not by the cards themselves.
    val favoriteKeys = remember(rows, prefs.favorites, prefs.legendary) {
        if (!prefs.legendary) emptySet()
        else rows.filterIsInstance<ListRow.DishRow>().filter { it.favoriteKey in prefs.favorites }.mapTo(HashSet()) { it.key }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().then(if (drag != null) Modifier.nestedScroll(drag.connection) else Modifier),
            // Horizontal padding lives on the items so sticky headers can span the full width.
            contentPadding = PaddingValues(bottom = bottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (prefs.compact) 8.dp else 12.dp),
        ) {
            for (row in rows) {
                when (row) {
                    is ListRow.Header -> stickyHeader(key = row.key, contentType = "header") { DayHeader(row.date, today, locale) }
                    is ListRow.SubHeader -> item(key = row.key, contentType = "subheader") { SubHeader(row.dinner) }
                    ListRow.FixedHeader -> stickyHeader(key = row.key, contentType = "header") { FixedHeader() }
                    is ListRow.SectionTitle -> item(key = row.key, contentType = "subheader") { SectionLabel(row.title.forLanguage(locale.language)) }
                    is ListRow.SectionDivider -> item(key = row.key, contentType = "divider") {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    }
                    is ListRow.DishRow -> item(key = row.key, contentType = "dish") {
                        DishCard(
                            row.dish, ui.legend, codeIndex, locale.language, euro, prefs.priceGroup, row.date?.let { footprints[it] },
                            prefs.compact, row.favoriteKey in prefs.favorites, { onToggleFavorite(row.favoriteKey) },
                            Modifier.padding(horizontal = 16.dp),
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
        if (favoriteKeys.isNotEmpty()) {
            SuperAuraOverlay(listState, favoriteKeys, headerKeys = { it is String && (it.startsWith("header-") || it == ListRow.FixedHeader.key) }, drag, Modifier.fillMaxSize())
        }
    }
}

/** Replaces the day header on a fixed menu: it has no days, only this label. Opaque like [DayHeader]. */
@Composable
private fun FixedHeader() {
    Text(
        stringResource(R.string.fixed_offer),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Title of a section of a fixed menu (e.g. a category the page may add). */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
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
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val full = !compact || open
    val burst = remember { FavoriteBurst() }
    val body: @Composable () -> Unit = {
        if (full) {
            FullCardBody(dish, legend, codeIndex, language, euro, priceGroup, footprint, favorite, onToggleFavorite, burst)
        } else {
            CompactCardBody(dish, legend, language, euro, priceGroup, favorite, onToggleFavorite, burst)
        }
    }
    val theme = LocalMensaColors.current
    val colors = theme.cardContainer?.let { CardDefaults.cardColors(containerColor = it) } ?: CardDefaults.cardColors()
    val elevation = CardDefaults.cardElevation(defaultElevation = theme.cardElevation)
    // Hairline border (one physical pixel) so the card edge doesn't depend on the shadow.
    val border = theme.cardBorder?.let { BorderStroke(theme.cardBorderWidth, it) }
    val shape = CardDefaults.shape
    val cardModifier = theme.cardGlow?.let { glow -> Modifier.cardGlow(glow, shape) } ?: Modifier
    FavoriteEffects(
        burst = burst,
        favoriteColor = theme.favorite,
        background = theme.cardContainer ?: MaterialTheme.colorScheme.surface,
        // Where the heart sits (see FullCardBody / CompactCardBody): its 24dp icon at the end of the first row.
        heartCenter = { size ->
            val end = if (full) 16.dp else 12.dp
            val top = if (full) 14.dp else 8.dp
            Offset(size.width - (end + 12.dp).toPx(), (top + 12.dp).toPx())
        },
        modifier = modifier,
    ) {
        if (compact) {
            // At least 48dp tall so the heart's touch area (see FavoriteButton) is never clipped by the card.
            Card(
                onClick = { open = !open }, modifier = cardModifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = colors, elevation = elevation, border = border,
            ) { body() }
        } else {
            Card(cardModifier.fillMaxWidth(), colors = colors, elevation = elevation, border = border) { body() }
        }
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
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    burst: FavoriteBurst,
) {
    val density = LocalDensity.current
    // The window, not the screen: correct in split-screen too.
    val windowWidthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value.toInt() }
    var pricesExpanded by rememberSaveable { mutableStateOf(false) }
    val tags: @Composable () -> Unit = {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            dish.tags.forEach { TagLabel(it) }
        }
    }
    val prices: @Composable () -> Unit = {
        if (dish.priceGroupsKnown) {
            Prices(dish.prices, priceGroup, legend, language, euro, pricesExpanded) { pricesExpanded = !pricesExpanded }
        } else {
            UnlabeledPrices(dish.prices, euro, MaterialTheme.typography.titleMedium)
        }
    }

    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        val name: @Composable (Modifier) -> Unit = {
            Text(dish.name.forLanguage(language), style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp), modifier = it)
        }
        // The heart sits at the end of the first row: the badges if there are any (the name then keeps the full
        // card width below), otherwise the name.
        Row(verticalAlignment = Alignment.Top) {
            if (dish.badges.isNotEmpty()) {
                FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    dish.badges.forEach { BadgeLabel(it.forLanguage(language)) }
                }
            } else {
                name(Modifier.weight(1f))
            }
            Spacer(Modifier.width(HEART_GAP))
            FavoriteButton(favorite, onToggleFavorite, above = 14.dp, burst = burst)
        }
        if (dish.badges.isNotEmpty()) name(Modifier)
        when {
            dish.tags.isEmpty() -> prices()
            dish.prices.isEmpty() -> tags()
            // Collapsed pill on a normal window: tags right-aligned on the price row, or wrapped
            // below it if they don't fit. Expanded prices, narrow windows and large fonts: below.
            tagsBesidePrice(windowWidthDp, density.fontScale) && !pricesExpanded -> PriceTagsRow(prices, tags)
            else -> {
                prices()
                tags()
            }
        }
        Footprint(dish, footprint)
        if (dish.codes.isNotEmpty()) CodeMeanings(dish.codes, codeIndex, language)
    }
}

/**
 * Price pill on the left, [tags] right-aligned on the same row if pill, gap and the tags in one
 * line fit; otherwise the tags go on a row of their own below. The pill is never squeezed.
 */
@Composable
private fun PriceTagsRow(price: @Composable () -> Unit, tags: @Composable () -> Unit) {
    Layout(content = { price(); tags() }) { measurables, constraints ->
        val maxWidth = constraints.maxWidth
        val gap = 12.dp.roundToPx()
        val rowGap = 5.dp.roundToPx()
        val pricePlaceable = measurables[0].measure(Constraints(maxWidth = maxWidth))
        val naturalTagsWidth = measurables[1].maxIntrinsicWidth(10_000)
        if (fitsBesidePrice(pricePlaceable.width, gap, naturalTagsWidth, maxWidth)) {
            val tagsPlaceable = measurables[1].measure(Constraints(minWidth = naturalTagsWidth, maxWidth = naturalTagsWidth))
            val height = maxOf(pricePlaceable.height, tagsPlaceable.height)
            layout(maxWidth, height) {
                pricePlaceable.placeRelative(0, (height - pricePlaceable.height) / 2)
                tagsPlaceable.placeRelative(maxWidth - tagsPlaceable.width, (height - tagsPlaceable.height) / 2)
            }
        } else {
            val tagsPlaceable = measurables[1].measure(Constraints(maxWidth = maxWidth))
            layout(maxWidth, pricePlaceable.height + rowGap + tagsPlaceable.height) {
                pricePlaceable.placeRelative(0, 0)
                tagsPlaceable.placeRelative(0, pricePlaceable.height + rowGap)
            }
        }
    }
}

/** Name (at most two lines) with the selected price on the same row, then badges and small text tag chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactCardBody(
    dish: Dish,
    legend: Legend,
    language: String,
    euro: NumberFormat,
    priceGroup: Int,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    burst: FavoriteBurst,
) {
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
            if (split != null && !dish.priceGroupsKnown) {
                UnlabeledPrices(dish.prices, euro, MaterialTheme.typography.labelLarge)
            } else if (split != null) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = LocalMensaColors.current.price.container,
                    border = LocalMensaColors.current.price.border?.let { BorderStroke(1.dp, it) },
                ) {
                    Text(
                        euro.format(dish.prices[split.selected] / 100.0),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = LocalMensaColors.current.price.content,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            // Row spacing 10 + 4: the heart's touch area (12dp beyond the icon) stays clear of the price pill.
            Spacer(Modifier.width(HEART_GAP - 10.dp))
            FavoriteButton(favorite, onToggleFavorite, above = 8.dp, burst = burst)
        }
        if (dish.badges.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                dish.badges.forEach { BadgeLabel(it.forLanguage(language)) }
            }
        }
        if (dish.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                dish.tags.forEach { TagLabel(it, dense = true) }
            }
        }
    }
}

/**
 * A soft halo around a card: two plain strokes just outside the card's own border, the inner one 2dp at the given
 * alpha, the outer one 5dp much fainter, so it fades outward. Drawn behind the card in the same layer (no blur, no
 * shadow, no offscreen buffer): scrolling costs two more outlines per card.
 */
private fun Modifier.cardGlow(color: Color, shape: androidx.compose.ui.graphics.Shape): Modifier =
    // One smooth gradient (EdgeGlow) instead of two stacked rings; about as strong near the edge and as wide.
    edgeGlow(color, shape, reach = 6.dp, strength = 1.35f, power = 1.8f)

/** Space between the card text and the heart: more than the 12dp its touch area reaches beyond the icon. */
private val HEART_GAP = 14.dp

/**
 * The favorite toggle: a 24dp heart whose 48x48dp touch area is larger than its layout box, so the card's rows
 * don't grow. The area reaches [above] up into the card padding (the rest of the overhang goes down) and 12dp to
 * either side, into the card's end padding and the [HEART_GAP]; the card must leave that room (compact cards have
 * a 48dp minimum height for this).
 */
@Composable
private fun FavoriteButton(favorite: Boolean, onToggle: () -> Unit, above: Dp, burst: FavoriteBurst) {
    val icon = 24.dp
    val target = 48.dp
    // Only a heart the user just filled bursts (the same dish on other days just turns red).
    LaunchedEffect(favorite) {
        if (favorite && burst.pending) {
            burst.pending = false
            burst.play()
        }
    }
    Box(
        Modifier
            .layout { measurable, _ ->
                val placeable = measurable.measure(Constraints.fixed(target.roundToPx(), target.roundToPx()))
                layout(icon.roundToPx(), icon.roundToPx()) {
                    placeable.place((icon.roundToPx() - placeable.width) / 2, -above.roundToPx())
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 20.dp),
                role = Role.Button,
                onClickLabel = null,
                onClick = {
                    if (!favorite) burst.pending = true
                    onToggle()
                },
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Icon(
            painterResource(if (favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite),
            contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
            tint = if (favorite) LocalMensaColors.current.favorite else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = above).size(icon).graphicsLayer {
                // The pop of the burst; 1 the rest of the time. Read here, so it only re-draws this layer.
                val scale = SparkleMath.heartScale(burst.progress.value)
                scaleX = scale
                scaleY = scale
            },
        )
    }
}

/**
 * Prices whose groups the page doesn't name (PIER 16 sometimes lists only two): shown as plain
 * amounts, without a group label and without picking one as "the selected group's" price.
 */
@Composable
private fun UnlabeledPrices(prices: List<Int>, euro: NumberFormat, style: androidx.compose.ui.text.TextStyle) {
    Text(
        prices.joinToString("  ·  ") { euro.format(it / 100.0) },
        style = style,
        fontWeight = FontWeight.Bold,
    )
}

/**
 * Collapsed: only the selected group's price in a tonal pill, with an arrow to its right. Expanded:
 * the pill gets its group label and the other prices appear inline to the right, labeled.
 * The expanded state is owned by the card (it moves the tags) and saved per lazy-list key.
 */
@Composable
private fun Prices(
    prices: List<Int>,
    priceGroup: Int,
    legend: Legend,
    language: String,
    euro: NumberFormat,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val split = splitPrices(prices, priceGroup) ?: return
    val fallback = listOf(R.string.price_students, R.string.price_staff, R.string.price_guests)
    @Composable
    fun label(index: Int) = legend.priceGroups.getOrNull(index)?.forLanguage(language)
        ?: fallback.getOrNull(index)?.let { stringResource(it) }.orEmpty()

    // One row: [selected price] [arrow] [other prices...]. If it is too wide for a narrow screen
    // it scrolls sideways instead of wrapping below.
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = LocalMensaColors.current.price.container,
            border = LocalMensaColors.current.price.border?.let { BorderStroke(1.dp, it) },
        ) {
            Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
                if (expanded) {
                    Text(label(split.selected), style = MaterialTheme.typography.labelSmall, color = LocalMensaColors.current.price.content)
                }
                Text(
                    euro.format(prices[split.selected] / 100.0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = LocalMensaColors.current.price.content,
                )
            }
        }
        if (split.others.isNotEmpty()) {
            val description = stringResource(if (expanded) R.string.hide_other_prices else R.string.show_other_prices)
            Text(
                if (expanded) "◂" else "▸",
                style = MaterialTheme.typography.titleSmall,
                color = LocalMensaColors.current.linkText,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = description, onClick = onToggle)
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
    val stops = LocalMensaColors.current.footprint
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
                    withStyle(SpanStyle(color = footprintColor(level, stops), fontWeight = FontWeight.SemiBold)) { append(text) }
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
        color = LocalMensaColors.current.linkText,
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

/**
 * A tag as a text chip; [dense] is the smaller variant for compact cards. Every tag is text only,
 * except vegan: it has its own light-green pair per theme and the sprout icon, tinted with the chip's text
 * color so it stays readable on both the light and the deep green chip.
 */
@Composable
private fun TagLabel(tag: Tag, dense: Boolean = false) {
    val label = tagLabelRes(tag.key)?.let { stringResource(it) } ?: tag.label
    val colors = LocalMensaColors.current
    val isVegan = tag.key == "vegan"
    val content = if (isVegan) colors.veganContent else colors.tag.content
    Surface(
        shape = if (dense) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.small,
        color = if (isVegan) colors.veganContainer else colors.tag.container,
    ) {
        Row(
            if (dense) Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            else Modifier.padding(start = if (isVegan) 6.dp else 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isVegan) {
                Icon(
                    painterResource(R.drawable.ic_sprout),
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(if (dense) 14.dp else 18.dp),
                )
                Spacer(Modifier.width(if (dense) 3.dp else 4.dp))
            }
            Text(
                label,
                style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                color = content,
            )
        }
    }
}

@Composable
private fun UpdatedFooter(updatedAt: Long, failed: Boolean) {
    // The system text starts with a capital ("Vor 5 Minuten"); it follows "Aktualisiert", so lowercase it.
    val relative = DateUtils.getRelativeTimeSpanString(updatedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
        .toString().replaceFirstChar { it.lowercase() }
    val text = stringResource(R.string.last_updated, relative) + if (failed) " · " + stringResource(R.string.update_failed) else ""
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}
