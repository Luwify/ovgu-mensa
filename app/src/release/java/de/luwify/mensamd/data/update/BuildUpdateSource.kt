package de.luwify.mensamd.data.update

import android.content.Context
import de.luwify.mensamd.update.UpdateSource

/** Release builds: always the release repo of the build (the debug build has an override, see src/debug). */
@Suppress("UNUSED_PARAMETER")
fun buildUpdateSource(context: Context): UpdateSource = releaseSource
