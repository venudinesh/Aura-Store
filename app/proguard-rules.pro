# Rules beyond the Android default file.
#
# R8 is on for release: `isMinifyEnabled` and `isShrinkResources` are both true in build.gradle.kts.
# The APK is installed and exercised on a handset, so a rule the shrinker eats fails that pass rather
# than a user's install.
#
# The app's whole reflective surface is two enum lookups, both reading a name back out of persisted
# state — a value written by an older build, or hand-edited, that no longer matches a constant:
#   data/Settings.kt      AutoUpdateMode.valueOf(prefs.getString(KEY_AUTO_UPDATE, …))
#   MainActivity.kt       SourceId.valueOf(sourceOpen)
# Both are wrapped in runCatching, so a stripped constant degrades to a default rather than crashing —
# but it degrades silently, which is the failure worth preventing. The default Android file already
# keeps `values()` and `valueOf()` for every enum; this pins the two by name so a future rule change
# cannot quietly take them away.

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class dev.aurastore.store.data.AutoUpdateMode { *; }
-keep class dev.aurastore.store.data.sources.SourceId { *; }

# WorkManager discovers AutoUpdateWorker by class name from its own database, which survives a process
# death into a build whose class names have been rewritten. The library ships a consumer rule for this;
# it is repeated here because the failure mode — a scheduled job that silently never runs — is invisible
# to every test in this project.
-keep class dev.aurastore.store.auto.AutoUpdateWorker { *; }
