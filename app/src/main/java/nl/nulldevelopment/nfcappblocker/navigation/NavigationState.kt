package nl.nulldevelopment.nfcappblocker.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/** One back stack with the home screen at the bottom (D-043); Back always ends up home. */
class Navigator(private val backStack: NavBackStack<NavKey>) {
    fun navigate(route: NavKey) {
        backStack.add(route)
    }

    /** Replaces the current screen, e.g. "add tag" with the tag it found. */
    fun replace(route: NavKey) {
        backStack.removeLastOrNull()
        backStack.add(route)
    }

    fun goBack() {
        if (backStack.size > 1) backStack.removeLastOrNull()
    }

    /** Opens [routes] in order on top of the home screen, e.g. after a scan asked for a screen. */
    fun openFromHome(vararg routes: NavKey) {
        while (backStack.size > 1) backStack.removeLastOrNull()
        routes.forEach(backStack::add)
    }
}
