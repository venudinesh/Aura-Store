package dev.aurastore.store.version

/**
 * The app's own numbering: `major.minor.patch`, with the Android version code derived from it.
 *
 * One number, read two ways. [code] is what Android compares when an update is handed to the
 * installer, and the same three parts are what a GitHub tag carries, so a release cannot publish a
 * name the installer will refuse or a code that disagrees with its name. The derivation lives here
 * rather than in the build file alone so the running app can show and compare it.
 *
 * A pre-release (`1.2.0-beta.2`) sorts below the release it leads to, which is semver's rule and the
 * reason a beta on GitHub is never offered as an upgrade to the build it precedes. Suffixes after
 * `+` are build metadata and do not change the order.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<AppVersion> {

    /**
     * The Android version code this number maps to: `major × 10 000 + minor × 100 + patch`.
     *
     * The three fields are bounded by [PATTERN] (major 0–999, minor and patch 0–99), so the result
     * stays inside Android's 2 100 000 000 limit and always grows with the name. `1.1.0` is 10 100.
     */
    val code: Int
        get() = major * 10_000 + minor * 100 + patch

    /** The name without the `v` a tag carries, and without build metadata. */
    val text: String
        get() = "$major.$minor.$patch" + (preRelease?.let { "-$it" } ?: "")

    override fun toString(): String = text

    override fun compareTo(other: AppVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        return when {
            preRelease == null && other.preRelease == null -> 0
            // A release outranks any pre-release of the same number.
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> comparePreRelease(preRelease, other.preRelease)
        }
    }

    companion object {
        /**
         * `v1.1.0`, `1.2.3`, `2.0.0-rc.1`, `1.0.0+ci.7`.
         *
         * The bounds are the version-code bounds: a tag outside them could not be turned into a code
         * that fits in an Android `versionCode`, so it is not a version this app can install over.
         */
        private val PATTERN =
            Regex("""^[vV]?(\d{1,3})\.(\d{1,2})\.(\d{1,2})(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?$""")

        /** Reads a version name or a release tag, or null when the text is not one. */
        fun parse(text: String?): AppVersion? {
            val match = PATTERN.matchEntire(text?.trim().orEmpty()) ?: return null
            return AppVersion(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                preRelease = match.groupValues[4].takeIf { it.isNotBlank() },
            )
        }

        /** The version code [text] would carry, or null when it is not a version. */
        fun codeOf(text: String?): Int? = parse(text)?.code

        /**
         * Semver's pre-release order: dot-separated identifiers, numeric ones by value and below
         * alphanumeric ones, and a longer set above its own prefix (`rc.1` before `rc.1.1`).
         */
        private fun comparePreRelease(left: String, right: String): Int {
            val a = left.split('.')
            val b = right.split('.')
            for (index in 0 until minOf(a.size, b.size)) {
                val x = a[index]
                val y = b[index]
                val xn = x.toIntOrNull()
                val yn = y.toIntOrNull()
                val order = when {
                    xn != null && yn != null -> xn.compareTo(yn)
                    xn != null -> -1
                    yn != null -> 1
                    else -> x.compareTo(y)
                }
                if (order != 0) return order
            }
            return a.size.compareTo(b.size)
        }
    }
}
