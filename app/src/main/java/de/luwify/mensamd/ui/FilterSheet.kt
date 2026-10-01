package de.luwify.mensamd.ui

import androidx.compose.foundation.layout.Arrangement
import de.luwify.mensamd.ui.theme.LocalMensaColors
import de.luwify.mensamd.ui.theme.LocalThemeMode
import de.luwify.mensamd.ui.theme.MensaFilterChip
import de.luwify.mensamd.data.settings.ThemeMode
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.R
import de.luwify.mensamd.data.settings.ViewPrefs
import de.luwify.mensamd.model.CodeCategory
import de.luwify.mensamd.model.Legend
import de.luwify.mensamd.model.LegendCode

/**
 * Bottom sheet with the settings that apply to all Mensen: the price group to highlight and the
 * additives/allergens to exclude. Names and meanings come from the parsed legend.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    legend: Legend,
    prefs: ViewPrefs,
    onPriceGroup: (Int) -> Unit,
    onCompact: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onSetCodes: (Set<String>, Boolean) -> Unit,
    onClearCodes: () -> Unit,
    onDismiss: () -> Unit,
) {
    val language = LocalConfiguration.current.locales[0].language
    val sections = remember(legend) { codeSections(legend.codes) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            item(key = "title") {
                Text(stringResource(R.string.sheet_title), style = MaterialTheme.typography.titleLarge)
            }
            item(key = "price-title") { SheetHeading(stringResource(R.string.price_group_title)) }
            item(key = "price-picker") {
                val fallback = listOf(R.string.price_students, R.string.price_staff, R.string.price_guests)
                val count = if (legend.priceGroups.isEmpty()) fallback.size else legend.priceGroups.size
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (index in 0 until count) {
                        val label = legend.priceGroups.getOrNull(index)?.forLanguage(language)
                            ?: fallback.getOrNull(index)?.let { stringResource(it) }.orEmpty()
                        MensaFilterChip(selected = prefs.priceGroup == index, onClick = { onPriceGroup(index) }, label = { Text(label) })
                    }
                }
            }

            item(key = "display-title") { SheetHeading(stringResource(R.string.display_title)) }
            item(key = "display-picker") {
                val theme = LocalThemeMode.current
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MensaFilterChip(selected = !prefs.compact, onClick = { onCompact(false) }, label = { Text(stringResource(R.string.display_normal)) })
                        MensaFilterChip(selected = prefs.compact, onClick = { onCompact(true) }, label = { Text(stringResource(R.string.display_compact)) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MensaFilterChip(selected = theme == ThemeMode.SYSTEM, onClick = { onThemeMode(ThemeMode.SYSTEM) }, label = { Text(stringResource(R.string.theme_system)) })
                        MensaFilterChip(selected = theme == ThemeMode.SOFT, onClick = { onThemeMode(ThemeMode.SOFT) }, label = { Text(stringResource(R.string.theme_soft)) })
                    }
                }
            }

            item(key = "exclude-title") { SheetHeading(stringResource(R.string.exclude_title)) }
            item(key = "exclude-hint") {
                Text(
                    stringResource(R.string.exclude_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "exclude-note") {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.allergen_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            if (prefs.excludedCodes.isNotEmpty()) {
                item(key = "clear") {
                    TextButton(onClick = onClearCodes) { Text(stringResource(R.string.clear_selection)) }
                }
            }

            for (section in sections) {
                item(key = "section-${section.category}") {
                    SheetHeading(
                        stringResource(
                            when (section.category) {
                                CodeCategory.ADDITIVE -> R.string.code_group_additive
                                CodeCategory.ALLERGEN -> R.string.code_group_allergen
                                CodeCategory.OTHER -> R.string.code_group_other
                            },
                        ),
                    )
                }
                for (group in section.groups) {
                    if (group.family != null) {
                        item(key = "family-${group.family}") {
                            FamilyHeader(group, prefs.excludedCodes, onSetCodes)
                        }
                    }
                    items(group.codes, key = { it.code }) { code ->
                        CodeRow(code, language, code.code in prefs.excludedCodes, indented = group.family != null) {
                            onSetCodes(setOf(code.code), code.code !in prefs.excludedCodes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = LocalMensaColors.current.headingText,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun FamilyHeader(group: ExcludeGroup, excluded: Set<String>, onSetCodes: (Set<String>, Boolean) -> Unit) {
    val title = group.family?.let(::codeFamilyRes)?.let { stringResource(it) }
        ?: "${group.codes.first().code}–${group.codes.last().code}"
    val state = groupState(group.codes, excluded)
    Row(
        Modifier
            .fillMaxWidth()
            .triStateToggleable(state = state, role = Role.Checkbox) {
                onSetCodes(group.codes.map { it.code }.toSet(), groupToggleSelects(group.codes, excluded))
            }
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TriStateCheckbox(state = state, onClick = null)
        Text(
            "  $title (${group.codes.first().code}–${group.codes.last().code})",
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun CodeRow(code: LegendCode, language: String, checked: Boolean, indented: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = if (indented) 28.dp else 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Box(Modifier.padding(start = 12.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(code.code) }
                    append("  ${code.text.forLanguage(language)}")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
