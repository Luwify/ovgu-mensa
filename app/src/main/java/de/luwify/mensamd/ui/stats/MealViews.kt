package de.luwify.mensamd.ui.stats

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import de.luwify.mensamd.stats.CardEvent
import de.luwify.mensamd.stats.KnownDish
import de.luwify.mensamd.stats.OpenItem
import de.luwify.mensamd.stats.StatsData
import java.util.Locale

/** The meals overview (detailed statistics). Phase 3. */
@Suppress("UNUSED_PARAMETER")
internal fun LazyListScope.mealsSection(data: StatsData, vm: StatsViewModel, locale: Locale) = Unit

/** Which dish did a payment buy? Phase 3. */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun MealQuestionScreen(
    vm: StatsViewModel,
    question: OpenItem.MealQuestion,
    customDishes: List<KnownDish>,
    customExtras: List<String>,
    events: List<CardEvent>,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { onClose() }
}
