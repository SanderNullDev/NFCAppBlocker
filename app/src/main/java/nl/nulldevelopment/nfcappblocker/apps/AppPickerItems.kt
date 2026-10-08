package nl.nulldevelopment.nfcappblocker.apps

import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** One row in the app picker. */
data class AppPickerItem(
    val packageName: String,
    /** The app's name; for an app that's no longer installed, its package name. */
    val label: String,
    val selected: Boolean,
    val installed: Boolean,
    val neverBlockReason: NeverBlockReason?,
) {
    val selectable: Boolean get() = neverBlockReason == null
}

/**
 * Builds the picker rows: installed launcher apps plus selected apps that are no longer installed
 * (so they can be removed), filtered by [query] on name or package and sorted by name, with the
 * apps in [pinnedFirst] on top. Callers pin what was selected when the screen opened, not the
 * live selection, so a row doesn't jump away while it's being ticked.
 */
fun appPickerItems(
    installed: List<InstalledApp>,
    selected: Set<String>,
    policy: NeverBlockPolicy,
    query: String,
    locale: Locale = Locale.getDefault(),
    pinnedFirst: Set<String> = emptySet(),
): List<AppPickerItem> {
    val installedPackages = installed.mapTo(mutableSetOf()) { it.packageName }
    val rows = installed.map { app ->
        AppPickerItem(app.packageName, app.label, app.packageName in selected, true, policy.reasonFor(app.packageName))
    } + (selected - installedPackages).map { pkg ->
        AppPickerItem(pkg, pkg, selected = true, installed = false, neverBlockReason = policy.reasonFor(pkg))
    }
    val needle = normalize(query.trim(), locale)
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    return rows
        .filter { needle.isEmpty() || needle in normalize(it.label, locale) || needle in it.packageName.lowercase(locale) }
        .sortedWith(compareBy<AppPickerItem> { it.packageName !in pinnedFirst }.thenBy(collator) { it.label })
}

/** Lower case without accents, so "ecole" finds "École". */
private fun normalize(text: String, locale: Locale): String =
    Normalizer.normalize(text.lowercase(locale), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
