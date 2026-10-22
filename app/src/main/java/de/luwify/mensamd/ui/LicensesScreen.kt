package de.luwify.mensamd.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import de.luwify.mensamd.R
import de.luwify.mensamd.ui.theme.BackIcon

/** Where the source code and the license text are published (GPL: offer the source, show how to get the license). */
const val SOURCE_URL = "https://github.com/Luwify/ovgu-mensa"

/** A third-party component with its copyright and license; [url] is its home page. */
private class Credit(val name: String, val copyright: String, val license: String, val url: String)

private val cardReaderCredits = listOf(
    Credit("MensaGuthaben", "Copyright (C) 2014 Jakob Wenzel", "GPL-3.0-or-later", "https://github.com/jakobwenzel/MensaGuthaben"),
    Credit("FareBot", "Copyright (C) 2011 Eric Butler", "GPL-3.0-or-later", "https://github.com/codebutler/farebot"),
)

private val libraryCredits = listOf(
    Credit("AndroidX, Jetpack Compose, Material 3", "Copyright The Android Open Source Project", "Apache License 2.0", "https://developer.android.com/jetpack/androidx"),
    Credit("Kotlin, kotlinx.coroutines, kotlinx.serialization", "Copyright JetBrains s.r.o. and contributors", "Apache License 2.0", "https://kotlinlang.org"),
    Credit("jsoup", "Copyright (c) 2009-2026 Jonathan Hedley", "MIT License (full text below)", "https://jsoup.org"),
    Credit("Material Symbols (icons)", "Copyright Google LLC", "Apache License 2.0", "https://fonts.google.com/icons"),
)

private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"
private const val APACHE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

/**
 * "Lizenzen und Quellcode" (settings page, section "Über die App"): the app's license with the no-warranty note and
 * where the source is, the card reader's origin (MensaGuthaben, FareBot), the libraries and the jsoup MIT text.
 * An overlay in [SettingsScreen], closed with back.
 */
@Composable
fun LicensesScreen(versionName: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val resources = LocalResources.current
    // The file keeps the original's hard line breaks; on screen each paragraph flows (blank lines stay paragraph breaks).
    val jsoupLicense = remember(resources) {
        resources.openRawResource(R.raw.license_jsoup).bufferedReader().use { it.readText() }.trim()
            .split(Regex("\n\\s*\n")).joinToString("\n\n") { it.replace(Regex("\\s*\n\\s*"), " ") }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).height(56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(BackIcon, contentDescription = stringResource(R.string.back)) }
                Text(stringResource(R.string.about_licenses), style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                    .padding(start = 16.dp, end = 16.dp, bottom = bottom + 24.dp),
            ) {
                Text("${stringResource(R.string.app_name)} $versionName", style = MaterialTheme.typography.titleMedium)
                Text("Copyright (C) 2026 Luwify", style = MaterialTheme.typography.bodyMedium)
                Paragraph(stringResource(R.string.licenses_free_software))
                Paragraph(stringResource(R.string.licenses_source))
                Link(SOURCE_URL)
                Link(GPL_URL)
                Paragraph(stringResource(R.string.licenses_not_affiliated))

                SheetHeading(stringResource(R.string.licenses_card_title))
                Text(stringResource(R.string.licenses_card_text), style = MaterialTheme.typography.bodyMedium)
                cardReaderCredits.forEach { CreditBlock(it) }

                SheetHeading(stringResource(R.string.licenses_libraries_title))
                libraryCredits.forEach { CreditBlock(it) }
                Paragraph("Apache License 2.0:")
                Link(APACHE_URL)

                SheetHeading("jsoup: MIT License")
                Text(jsoupLicense, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Paragraph(text: String) {
    Text(text, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun CreditBlock(credit: Credit) {
    Column(Modifier.padding(top = 12.dp)) {
        Text(credit.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            "${credit.copyright}\n${credit.license}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Link(credit.url)
    }
}

/** A URL that opens in the browser. */
@Composable
private fun Link(url: String) {
    val style = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline))
    val text = remember(url, style) { buildAnnotatedString { withLink(LinkAnnotation.Url(url, style)) { append(url) } } }
    Text(text, Modifier.padding(vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
}
