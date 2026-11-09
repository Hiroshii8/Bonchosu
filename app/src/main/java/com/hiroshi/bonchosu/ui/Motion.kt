package com.hiroshi.bonchosu.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry

/**
 * Screen transitions, following Material 3 motion:
 * - Bottom-bar tabs: **fade through**. Unrelated top-level pages, so nothing slides; the old page fades out
 *   quickly and the new one fades in with a slight zoom.
 * - Opening a page from another (Settings → Wallets): **shared axis X**. A short slide with a cross-fade,
 *   forwards to the left and back to the right, so direction always matches the back button.
 * - The add/edit transaction screen: **shared axis Y**. The page lifts away and the editor rises in from below,
 *   like a full-screen sheet; closing it reverses that.
 * Durations and easing are Material's "emphasized" set (300 ms, decelerate in, accelerate out).
 */
object Motion {
    private const val DURATION = 300
    private const val OUT = 90 // the outgoing page fades fast...
    private const val IN = DURATION - OUT // ...and the incoming one fades in after it

    private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Slide distance: a tenth of the screen (~40dp), enough to show direction without whipping the page across. */
    private fun shift(size: Int) = size / 10

    private enum class Kind { TABS, SHEET, AXIS }

    private fun AnimatedContentTransitionScope<NavBackStackEntry>.kind(sheetRoute: String): Kind {
        val from = initialState.destination.route
        val to = targetState.destination.route
        return when {
            from in Routes.tabs && to in Routes.tabs -> Kind.TABS
            from == sheetRoute || to == sheetRoute -> Kind.SHEET
            else -> Kind.AXIS
        }
    }

    // ---------- Building blocks ----------

    private fun fadeThroughIn(): EnterTransition =
        fadeIn(tween(IN, delayMillis = OUT, easing = LinearEasing)) +
            scaleIn(tween(IN, delayMillis = OUT, easing = EmphasizedDecelerate), initialScale = 0.94f)

    private fun fadeThroughOut(): ExitTransition = fadeOut(tween(OUT, easing = LinearEasing))

    private fun axisIn(forward: Boolean): EnterTransition =
        slideInHorizontally(tween(DURATION, easing = EmphasizedDecelerate)) { if (forward) shift(it) else -shift(it) } +
            fadeIn(tween(IN, delayMillis = OUT, easing = LinearEasing))

    private fun axisOut(forward: Boolean): ExitTransition =
        slideOutHorizontally(tween(DURATION, easing = Emphasized)) { if (forward) -shift(it) else shift(it) } +
            fadeOut(tween(OUT, easing = LinearEasing))

    // Shared axis Y: the page underneath fades out first, then the sheet rises in, so the two never overlap.
    private fun sheetIn(): EnterTransition =
        slideInVertically(tween(DURATION, easing = EmphasizedDecelerate)) { shift(it) } +
            fadeIn(tween(IN, delayMillis = OUT, easing = LinearEasing))

    private fun sheetOut(): ExitTransition =
        slideOutVertically(tween(DURATION, easing = EmphasizedAccelerate)) { shift(it) } +
            fadeOut(tween(OUT, easing = LinearEasing))

    private fun underSheetOut(): ExitTransition =
        slideOutVertically(tween(DURATION, easing = Emphasized)) { -shift(it) / 2 } + fadeOut(tween(OUT, easing = LinearEasing))

    private fun underSheetIn(): EnterTransition =
        slideInVertically(tween(DURATION, easing = EmphasizedDecelerate)) { -shift(it) / 2 } +
            fadeIn(tween(IN, delayMillis = OUT, easing = LinearEasing))

    // ---------- What NavHost uses ----------

    fun enter(sheetRoute: String): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when (kind(sheetRoute)) {
            Kind.TABS -> fadeThroughIn()
            Kind.SHEET -> if (targetState.destination.route == sheetRoute) sheetIn() else underSheetIn()
            Kind.AXIS -> axisIn(forward = true)
        }
    }

    fun exit(sheetRoute: String): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when (kind(sheetRoute)) {
            Kind.TABS -> fadeThroughOut()
            Kind.SHEET -> if (initialState.destination.route == sheetRoute) sheetOut() else underSheetOut()
            Kind.AXIS -> axisOut(forward = true)
        }
    }

    fun popEnter(sheetRoute: String): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        when (kind(sheetRoute)) {
            Kind.TABS -> fadeThroughIn()
            Kind.SHEET -> if (targetState.destination.route == sheetRoute) sheetIn() else underSheetIn()
            Kind.AXIS -> axisIn(forward = false)
        }
    }

    fun popExit(sheetRoute: String): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        when (kind(sheetRoute)) {
            Kind.TABS -> fadeThroughOut()
            Kind.SHEET -> if (initialState.destination.route == sheetRoute) sheetOut() else underSheetOut()
            Kind.AXIS -> axisOut(forward = false)
        }
    }
}
