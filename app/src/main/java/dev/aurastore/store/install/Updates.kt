package dev.aurastore.store.install

/**
 * Whether the catalogue's build is newer than the one on this device.
 *
 * There are two questions here and they need two rules, because the strings available differ.
 *
 * [isAvailable] is for an app this store installed. The two strings have to come from the same
 * world, and the honest pair is *label vs label*: the label of the build that went in, recorded at
 * install time, against the label of the build on offer now. The alternative — the APK's own
 * `versionName` against a catalogue label — answers "update available" forever, because a post
 * labelled "v5.0" ships an APK reading 1.0.6, and an enabled auto-update then re-downloads and
 * reinstalls the same file. That is what happened on the Redmi Note 8 Pro with Win Slot: the update
 * sheet opened by itself minutes after the install finished.
 *
 * [isNewer] is for an app that came from elsewhere and has no recorded label; see its own doc for why
 * that pair is compared by order instead of by equality.
 *
 * [installed] falls back to the version name only for a package recorded before labels were kept.
 */
object Updates {

    fun isAvailable(installed: String?, catalogue: String?): Boolean {
        val have = versionOf(installed)
        val wanted = versionOf(catalogue)
        // Only two known versions can be compared; "unknown" must not read as "out of date".
        return wanted.isNotBlank() && have.isNotBlank() && wanted != have
    }

    /**
     * True when the catalogue's version is *strictly newer* than the installed one.
     *
     * This is the rule for an app that arrived from somewhere else, where the pair that means
     * something is the APK's own `versionName` against a version the catalogue offers — the newest
     * build on the post, or its `lastest_version` when the question is only whether the post is worth
     * opening. There, "not equal" is the wrong question: the catalogue lags behind the Play Store as
     * often as it leads it, and an inequality would offer an "update" that installs an older build,
     * then offer it again forever.
     *
     * [installed] with no number in it is unknown, not old, so it never reads as out of date.
     */
    fun isNewer(installed: String?, catalogue: String?): Boolean {
        val have = numbersOf(installed)
        val offered = numbersOf(catalogue)
        return have.isNotEmpty() && offered.isNotEmpty() && compare(have, offered) < 0
    }

    /** -1, 0 or 1 as [left]'s version is below, equal to or above [right]'s. Missing numbers read as zero. */
    fun compare(left: String?, right: String?): Int = compare(numbersOf(left), numbersOf(right))

    private fun compare(left: List<Long>, right: List<Long>): Int {
        val length = maxOf(left.size, right.size)
        for (i in 0 until length) {
            val a = left.getOrElse(i) { 0L }
            val b = right.getOrElse(i) { 0L }
            if (a != b) return if (a < b) -1 else 1
        }
        return 0
    }

    /** The leading dotted number of a version label, or "" when the label has none. */
    fun versionOf(label: String?): String =
        Regex("""\d+(\.\d+)+""").find(label.orEmpty())?.value ?: ""

    /** That number split, so "9.1.94" and "9.1.94.1912" are ordered rather than merely different. */
    private fun numbersOf(label: String?): List<Long> =
        versionOf(label).split('.').mapNotNull { it.toLongOrNull() }
}
