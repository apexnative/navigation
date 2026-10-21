package dev.apexnative.navigation

import android.os.Build

/**
 * Whether a navigation activity draws edge to edge, by Android's rules: the app asks for it (`edgeToEdgeEnabled=true`
 * in its gradle.properties) — always; Android 16 and later with a target of 36 or more — always, Android enforces it;
 * Android 15 and later with a target of 35 or more — unless the theme opts out (`windowOptOutEdgeToEdgeEnforcement`
 * true); Android 15 and later with a lower target — only when the theme sets that attribute to `false` itself; below
 * Android 15 — never without the app's flag.
 */
internal object EdgeToEdgeDecision {
    /**
     * The decision for an app that asks for it or not ([appEnabled]), on Android [sdk], with [targetSdk], and the
     * theme's `windowOptOutEdgeToEdgeEnforcement` ([themeOptOut]; null when the theme doesn't set it).
     */
    fun isEnabled(appEnabled: Boolean, sdk: Int, targetSdk: Int, themeOptOut: Boolean?): Boolean = when {
        appEnabled -> true
        sdk >= Build.VERSION_CODES.BAKLAVA && targetSdk >= Build.VERSION_CODES.BAKLAVA -> true
        sdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM && targetSdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
            themeOptOut != true
        sdk >= Build.VERSION_CODES.VANILLA_ICE_CREAM -> themeOptOut == false
        else -> false
    }
}
