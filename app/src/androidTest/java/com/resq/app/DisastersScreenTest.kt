package com.resq.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.resq.app.ui.adapter.DisasterAdapter
import com.resq.app.ui.adapter.TipDisasterGroupAdapter
import com.resq.app.ui.fragment.DisastersFragment
import com.resq.app.ui.viewmodel.DisasterTipsViewModel
import com.resq.app.ui.viewmodel.DisasterViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real Disaster controls while restoring the user's saved filter and theme afterward. */
@RunWith(AndroidJUnit4::class)
class DisastersScreenTest {
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
    private var originalShowAll: Boolean? = null

    @Before
    fun isolateFilterPreference() {
        originalNightMode = AppCompatDelegate.getDefaultNightMode()
        val preferences = filterPreferences()
        originalShowAll = if (preferences.contains(FILTER_KEY)) preferences.getBoolean(FILTER_KEY, false) else null
        assertTrue(preferences.edit().remove(FILTER_KEY).commit())
    }

    @After
    fun restorePreferences() {
        scenario?.close()
        val edit = filterPreferences().edit()
        originalShowAll?.let { edit.putBoolean(FILTER_KEY, it) } ?: edit.remove(FILTER_KEY)
        assertTrue(edit.commit())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(originalNightMode)
        }
    }

    @Test
    fun freshSelectionIsRecommended_andSingleTapsFilterBothTabs() {
        launchDisasters()
        awaitContent(tab = 0, showAll = false)
        scenario!!.onActivity {
            assertNull("The state button should not present a dropdown arrow",
                it.findViewById<MaterialButton>(R.id.btn_filter).icon)
        }

        toggleFilter(showAll = true)
        awaitContent(tab = 0, showAll = true)
        selectTab(1)
        awaitContent(tab = 1, showAll = true)
        toggleFilter(showAll = false)
        awaitContent(tab = 1, showAll = false)
        selectTab(0)
        awaitContent(tab = 0, showAll = false)
        toggleFilter(showAll = true)
        awaitContent(tab = 0, showAll = true)
        assertTrue("One-tap changes should save the selection", filterPreferences().getBoolean(FILTER_KEY, false))
    }

    @Test
    fun selectionSurvivesLeavingScreen_activityRecreation_andRelaunch() {
        launchDisasters()
        toggleFilter(showAll = true)
        selectTab(1)
        awaitContent(tab = 1, showAll = true)

        tapView { it.findViewById(R.id.nav_home) }
        awaitDestination(R.id.nav_home)
        tapView { it.findViewById(R.id.nav_disasters) }
        awaitDestination(R.id.nav_disasters)
        awaitFilter(showAll = true)
        var returnedTab = 0
        scenario!!.onActivity { returnedTab = it.findViewById<TabLayout>(R.id.tab_layout).selectedTabPosition }
        awaitContent(returnedTab, showAll = true)

        selectTab(1)
        awaitContent(tab = 1, showAll = true)
        tapView { it.findViewById(R.id.et_search) }
        awaitUi("global search to open") {
            navHost(it).navController.currentDestination?.id == R.id.nav_global_search
        }
        scenario!!.onActivity { assertTrue(navHost(it).navController.popBackStack()) }
        awaitDestination(R.id.nav_disasters)
        awaitContent(tab = 1, showAll = true)
        scenario!!.onActivity { assertAdaptiveFilterLayout(it) }
        scenario!!.recreate()
        awaitDestination(R.id.nav_disasters)
        awaitContent(tab = 1, showAll = true)

        scenario!!.close()
        scenario = null
        launchDisasters()
        awaitContent(tab = 0, showAll = true)
        toggleFilter(showAll = false)
        scenario!!.close()
        scenario = null
        launchDisasters()
        awaitContent(tab = 0, showAll = false)
    }

    @Test
    fun longLists_keepFilterAndFloatingMenuFixed_whileCardsScroll() {
        launchDisasters()
        toggleFilter(showAll = true)
        for (tab in listOf(0, 1)) {
            selectTab(tab)
            awaitContent(tab, showAll = true)
            var initialFilter = Rect()
            var initialFab = Rect()
            var initialOffset = 0
            scenario!!.onActivity { activity ->
                activity.findViewById<RecyclerView>(R.id.rv_content).scrollToPosition(0)
            }
            awaitUi("Disaster content to return to the top") {
                val list = it.findViewById<RecyclerView>(R.id.rv_content)
                !list.canScrollVertically(-1) && !list.isLayoutRequested
            }
            scenario!!.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rv_content)
                initialFilter = screenBounds(activity.findViewById(R.id.btn_filter))
                initialFab = screenBounds(activity.findViewById(R.id.fab_main))
                initialOffset = list.computeVerticalScrollOffset()
                assertTrue("Bundled Disaster content should exercise scrolling", list.canScrollVertically(1))
                assertStickyFilterLayout(activity)
            }
            swipeListUp()
            awaitUi("Disaster list to scroll from a real gesture") {
                it.findViewById<RecyclerView>(R.id.rv_content).computeVerticalScrollOffset() > initialOffset
            }
            scenario!!.onActivity { activity ->
                assertEquals("The filter must stay fixed at the bottom while cards scroll", initialFilter,
                    screenBounds(activity.findViewById(R.id.btn_filter)))
                assertEquals("The floating menu must stay fixed while cards scroll", initialFab,
                    screenBounds(activity.findViewById(R.id.fab_main)))
                assertStickyFilterLayout(activity)
            }

            scrollToFinalCard()
            scenario!!.onActivity { activity ->
                assertEquals("The filter must stay fixed when the final card is reached", initialFilter,
                    screenBounds(activity.findViewById(R.id.btn_filter)))
                assertEquals("The floating menu must stay fixed when the final card is reached", initialFab,
                    screenBounds(activity.findViewById(R.id.fab_main)))
                assertFinalCardClearance(activity)
            }
            captureScreenshot("all-${if (tab == 0) "information" else "tips"}-scrolled")
        }
    }

    @Test
    fun shortLists_placeFilterDirectlyBeneathFinalCard_withoutScrolling() {
        launchDisasters()
        toggleFilter(showAll = true)
        for (tab in listOf(0, 1)) {
            selectTab(tab)
            awaitContent(tab, showAll = true)
            for (count in listOf(3, 1)) {
                submitShortCards(count)
                var initialFilter = Rect()
                scenario!!.onActivity { activity ->
                    assertInlineFilterLayout(activity)
                    initialFilter = screenBounds(activity.findViewById(R.id.btn_filter))
                }
                swipeListUp()
                scenario!!.onActivity { activity ->
                    assertEquals("A swipe should leave the inline filter beneath short content", initialFilter,
                        screenBounds(activity.findViewById(R.id.btn_filter)))
                    assertInlineFilterLayout(activity)
                }
                captureScreenshot("short-$count-${if (tab == 0) "information" else "tips"}")
            }
            toggleFilter(showAll = false)
            awaitContent(tab, showAll = false)
            scenario!!.onActivity { assertAdaptiveFilterLayout(it) }
            toggleFilter(showAll = true)
            awaitContent(tab, showAll = true)
            scenario!!.onActivity { assertStickyFilterLayout(it) }
        }
    }

    @Test
    fun filterRepositions_whenAvailableViewportShrinksAndGrows() {
        launchDisasters()
        toggleFilter(showAll = true)
        for (tab in listOf(0, 1)) {
            selectTab(tab)
            awaitContent(tab, showAll = true)
            submitShortCards(3)
            var originalTopMargin = 0
            var initialFilter = Rect()
            scenario!!.onActivity { activity ->
                assertInlineFilterLayout(activity)
                initialFilter = screenBounds(activity.findViewById(R.id.btn_filter))
                val list = activity.findViewById<RecyclerView>(R.id.rv_content)
                val cards = (0 until list.adapter!!.itemCount).map {
                    list.findViewHolderForAdapterPosition(it)!!.itemView
                }
                val params = list.layoutParams as ViewGroup.MarginLayoutParams
                originalTopMargin = params.topMargin
                // Shrink the same content viewport without changing the device's saved display settings.
                // A complete card and the controls' clearance must still fit after scrolling to the end.
                val tallestDecoratedCard = cards.maxOf { card ->
                    val margins = card.layoutParams as ViewGroup.MarginLayoutParams
                    card.height + margins.topMargin + margins.bottomMargin
                }
                val smallerHeight = tallestDecoratedCard + list.paddingTop + list.paddingBottom +
                    activity.resources.getDimensionPixelSize(R.dimen.spacing_md)
                assertTrue("The test needs room to shrink the content viewport", smallerHeight < list.height)
                assertTrue("Three cards should still overflow the reduced viewport",
                    cards.sumOf { card ->
                        val margins = card.layoutParams as ViewGroup.MarginLayoutParams
                        card.height + margins.topMargin + margins.bottomMargin
                    } + list.paddingTop + list.paddingBottom > smallerHeight)
                params.topMargin += list.height - smallerHeight
                list.layoutParams = params
            }
            awaitUi("short cards to overflow the smaller viewport") { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rv_content)
                !list.isLayoutRequested && list.canScrollVertically(1) &&
                    !activity.findViewById<View>(R.id.btn_filter).isLayoutRequested
            }
            scenario!!.onActivity { assertStickyFilterLayout(it) }
            scrollToFinalCard()
            scenario!!.onActivity { assertFinalCardClearance(it) }
            scenario!!.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rv_content)
                val params = list.layoutParams as ViewGroup.MarginLayoutParams
                params.topMargin = originalTopMargin
                list.layoutParams = params
            }
            awaitUi("cards to fit the restored viewport") { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rv_content)
                !list.isLayoutRequested && !list.canScrollVertically(-1) && !list.canScrollVertically(1) &&
                    !activity.findViewById<View>(R.id.btn_filter).isLayoutRequested
            }
            scenario!!.onActivity { activity ->
                assertInlineFilterLayout(activity)
                assertEquals("Growing the viewport should return the filter beneath the same cards", initialFilter,
                    screenBounds(activity.findViewById(R.id.btn_filter)))
            }
        }
    }

    @Test
    fun recommendedLists_useAvailableSpace_whenScrollingAndChangingState() {
        launchDisasters()
        for (tab in listOf(0, 1)) {
            selectTab(tab)
            awaitContent(tab, showAll = false)
            var initialFilter = Rect()
            var initialFab = Rect()
            var initialOffset = 0
            var scrollable = false
            scenario!!.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rv_content)
                list.scrollToPosition(0)
            }
            awaitUi("recommended cards to return to the top") {
                val list = it.findViewById<RecyclerView>(R.id.rv_content)
                !list.canScrollVertically(-1) && !list.isLayoutRequested
            }
            scenario!!.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rv_content)
                initialFilter = screenBounds(activity.findViewById(R.id.btn_filter))
                initialFab = screenBounds(activity.findViewById(R.id.fab_main))
                initialOffset = list.computeVerticalScrollOffset()
                scrollable = list.canScrollVertically(1)
                assertAdaptiveFilterLayout(activity)
            }
            swipeListUp()
            scenario!!.onActivity { activity ->
                val list = activity.findViewById<RecyclerView>(R.id.rv_content)
                if (scrollable) assertTrue("A real swipe should scroll recommended cards",
                    list.computeVerticalScrollOffset() > initialOffset)
                assertEquals("Scrolling must keep the current filter placement", initialFilter,
                    screenBounds(activity.findViewById(R.id.btn_filter)))
                assertEquals("Recommended cards must not move the floating menu", initialFab,
                    screenBounds(activity.findViewById(R.id.fab_main)))
            }
            scrollToFinalCard()
            scenario!!.onActivity { assertFinalCardClearance(it) }
            captureScreenshot("recommended-${if (tab == 0) "information" else "tips"}-scrolled")
            toggleFilter(showAll = true)
            awaitContent(tab, showAll = true)
            scenario!!.onActivity { activity ->
                assertAdaptiveFilterLayout(activity)
            }
            toggleFilter(showAll = false)
            awaitContent(tab, showAll = false)
            scenario!!.onActivity { assertAdaptiveFilterLayout(it) }
        }
    }

    @Test
    fun lightTheme_tabsMatchContent_andFilterHasAnAccessibleTarget() {
        verifyThemeAndLargeText(AppCompatDelegate.MODE_NIGHT_NO)
    }

    @Test
    fun darkTheme_tabsMatchContent_andFilterHasAnAccessibleTarget() {
        verifyThemeAndLargeText(AppCompatDelegate.MODE_NIGHT_YES)
    }

    private fun verifyThemeAndLargeText(mode: Int) {
        launchDisasters(mode)
        for (tab in listOf(0, 1)) {
            selectTab(tab)
            awaitContent(tab, showAll = false)
            scrollToFinalCard()
            scenario!!.onActivity { activity ->
                assertEquals("Requested theme should be active", mode == AppCompatDelegate.MODE_NIGHT_YES,
                    activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                        Configuration.UI_MODE_NIGHT_YES)
                val background = activity.getColor(R.color.background)
                val root = disastersFragment(activity).requireView()
                val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
                val tabs = activity.findViewById<TabLayout>(R.id.tab_layout)
                for (view in listOf(root, toolbar, tabs)) {
                    assertEquals("Disaster tabs, header, and content should share the theme background",
                        background, drawableCenterColor(view.background))
                    assertEquals("Disaster chrome should not create an elevated seam", 0f, view.elevation, 0.01f)
                }
                assertFinalCardClearance(activity)
                val filter = activity.findViewById<MaterialButton>(R.id.btn_filter)
                assertTrue("The filter should accept taps and keyboard focus", filter.isClickable && filter.isFocusable)
                assertFalse("The filter should have readable text", filter.text.isNullOrBlank())
                assertNull("A one-tap filter does not need a dropdown icon", filter.icon)
            }
            captureScreenshot("${themeName(mode)}-${if (tab == 0) "information" else "tips"}")
        }
        scenario!!.onActivity { activity ->
            val filter = activity.findViewById<MaterialButton>(R.id.btn_filter)
            filter.setTextSize(TypedValue.COMPLEX_UNIT_PX, filter.textSize * 2f)
        }
        awaitUi("larger filter text layout") { activity ->
            val filter = activity.findViewById<MaterialButton>(R.id.btn_filter)
            !filter.isLayoutRequested && filter.layout != null
        }
        scrollToFinalCard()
        scenario!!.onActivity { activity ->
            val filter = activity.findViewById<MaterialButton>(R.id.btn_filter)
            assertFinalCardClearance(activity)
            assertTrue("Larger filter text should remain complete", (0 until filter.layout.lineCount).all {
                filter.layout.getEllipsisCount(it) == 0
            })
            assertEquals("Larger filter text should not be truncated", filter.text.length,
                filter.layout.getLineEnd(filter.layout.lineCount - 1))
            assertTrue("Larger filter text should fit vertically", filter.height >= filter.layout.height +
                filter.compoundPaddingTop + filter.compoundPaddingBottom)
        }
        toggleFilter(showAll = true)
        awaitContent(tab = 1, showAll = true)
        scrollToFinalCard()
        scenario!!.onActivity { activity ->
            val filter = activity.findViewById<MaterialButton>(R.id.btn_filter)
            assertFinalCardClearance(activity)
            assertTrue("All Disasters should stay readable at twice the text size", (0 until filter.layout.lineCount).all {
                filter.layout.getEllipsisCount(it) == 0
            })
        }
        captureScreenshot("${themeName(mode)}-large-text")
    }

    private fun assertFilterLayout(activity: MainActivity) {
        val filter = activity.findViewById<MaterialButton>(R.id.btn_filter)
        val fab = activity.findViewById<View>(R.id.fab_main)
        val root = disastersFragment(activity).requireView()
        val list = activity.findViewById<RecyclerView>(R.id.rv_content)
        val minimumSize = (48 * activity.resources.displayMetrics.density).roundToInt()
        assertTrue("The filter needs a 48dp touch target", filter.width >= minimumSize && filter.height >= minimumSize)
        assertEquals("The filter should align with the app's left content margin",
            screenBounds(root).left + activity.resources.getDimensionPixelSize(R.dimen.screen_margin_horizontal),
            screenBounds(filter).left)
        assertTrue("The filter should remain left of the floating menu", screenBounds(filter).right < screenBounds(fab).left)
        assertEquals("Cards should scroll through the full content viewport without a rectangular footer",
            screenBounds(root).bottom, screenBounds(list).bottom)
        assertTrue("The scrolling list should have no rectangular backdrop",
            list.background == null || Color.alpha(drawableCenterColor(list.background)) == 0)
        assertEquals("The list should not create a raised panel", 0f, list.elevation, 0.01f)
    }

    private fun assertVisibleFilterLayout(activity: MainActivity) {
        assertFilterLayout(activity)
        val filter = screenBounds(activity.findViewById(R.id.btn_filter))
        val list = screenBounds(activity.findViewById(R.id.rv_content))
        assertTrue("The filter should fit inside the content viewport", list.contains(filter))
        assertTrue("The filter must remain clear of the floating menu",
            !Rect.intersects(filter, screenBounds(activity.findViewById(R.id.fab_main))))
    }

    private fun assertStickyFilterLayout(activity: MainActivity) {
        assertVisibleFilterLayout(activity)
        assertEquals("An overflowing list should keep the filter level with the fixed floating menu",
            screenBounds(activity.findViewById(R.id.fab_main)).exactCenterY(),
            screenBounds(activity.findViewById(R.id.btn_filter)).exactCenterY(), 1f)
    }

    private fun assertInlineFilterLayout(activity: MainActivity) {
        assertVisibleFilterLayout(activity)
        val list = activity.findViewById<RecyclerView>(R.id.rv_content)
        assertFalse("Cards that fit should not scroll upward", list.canScrollVertically(-1))
        assertFalse("Cards that fit should not scroll downward", list.canScrollVertically(1))
        val lastPosition = list.adapter!!.itemCount - 1
        val finalCard = list.findViewHolderForAdapterPosition(lastPosition)?.itemView
        assertNotNull("Every card should be laid out when the content fits", finalCard)
        assertEquals("The filter should follow the final card with the regular content gap",
            screenBounds(finalCard!!).bottom + activity.resources.getDimensionPixelSize(R.dimen.spacing_md),
            screenBounds(activity.findViewById(R.id.btn_filter)).top)
        for (position in 0..lastPosition) {
            val card = list.findViewHolderForAdapterPosition(position)?.itemView
            assertNotNull("Card $position should be present when content fits", card)
            assertTrue("Card $position should be entirely readable", screenBounds(list).contains(screenBounds(card!!)))
            assertFalse("The floating menu should not obscure card $position",
                Rect.intersects(screenBounds(card), screenBounds(activity.findViewById(R.id.fab_main))))
        }
    }

    private fun assertAdaptiveFilterLayout(activity: MainActivity) {
        val list = activity.findViewById<RecyclerView>(R.id.rv_content)
        if (list.canScrollVertically(-1) || list.canScrollVertically(1)) {
            assertStickyFilterLayout(activity)
        } else {
            assertInlineFilterLayout(activity)
        }
    }

    private fun assertFinalCardClearance(activity: MainActivity) {
        assertVisibleFilterLayout(activity)
        val list = activity.findViewById<RecyclerView>(R.id.rv_content)
        val card = list.findViewHolderForAdapterPosition(list.adapter!!.itemCount - 1)!!.itemView
        val bounds = screenBounds(card)
        assertTrue("The final card should fit completely inside the list viewport", screenBounds(list).contains(bounds))
        assertTrue("The final card should remain above the filter",
            bounds.bottom <= screenBounds(activity.findViewById(R.id.btn_filter)).top)
        assertTrue("The final card should remain above the floating menu",
            bounds.bottom <= screenBounds(activity.findViewById(R.id.fab_main)).top)
    }

    private fun launchDisasters(mode: Int = AppCompatDelegate.MODE_NIGHT_NO) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { AppCompatDelegate.setDefaultNightMode(mode) }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitUi("navigation graph") { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
                ?.navController?.currentDestination != null
        }
        scenario!!.onActivity { activity ->
            val controller = navHost(activity).navController
            if (controller.currentDestination?.id != R.id.nav_home) controller.navigate(R.id.nav_home)
        }
        awaitDestination(R.id.nav_home)
        tapView { it.findViewById(R.id.nav_disasters) }
        awaitDestination(R.id.nav_disasters)
        awaitContent(tab = 0, showAll = filterPreferences().getBoolean(FILTER_KEY, false))
    }

    private fun toggleFilter(showAll: Boolean) {
        awaitFilter(!showAll)
        awaitFilterLayout()
        tapView { it.findViewById(R.id.btn_filter) }
        awaitFilter(showAll)
    }

    private fun awaitFilterLayout() {
        awaitUi("Disaster filter layout") { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            val filter = activity.findViewById<View>(R.id.btn_filter)
            !list.isLayoutRequested && !filter.isLayoutRequested && list.itemAnimator?.isRunning != true &&
                filter.isShown && screenBounds(list).contains(screenBounds(filter))
        }
    }

    private fun scrollToFinalCard() {
        awaitFilterLayout()
        scenario!!.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            list.scrollToPosition(list.adapter!!.itemCount - 1)
        }
        awaitUi("final Disaster card to lay out") { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            !list.isLayoutRequested && list.itemAnimator?.isRunning != true &&
                list.findViewHolderForAdapterPosition(list.adapter!!.itemCount - 1) != null
        }
        scenario!!.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            list.scrollBy(0, list.computeVerticalScrollRange())
        }
        awaitUi("complete final card and controls") { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            !list.isLayoutRequested && list.scrollState == RecyclerView.SCROLL_STATE_IDLE &&
                list.itemAnimator?.isRunning != true && !list.canScrollVertically(1)
        }
    }

    private fun submitShortCards(count: Int) {
        scenario!!.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            when (val adapter = list.adapter) {
                is DisasterAdapter -> adapter.submitList(adapter.currentList.take(count).map {
                    it.copy(description = "A short description.")
                })
                is TipDisasterGroupAdapter -> adapter.submitList(adapter.currentList.take(count))
                else -> throw AssertionError("Expected a Disaster card adapter")
            }
            list.scrollToPosition(0)
        }
        awaitUi("$count short cards and filter to settle") { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            val filter = activity.findViewById<View>(R.id.btn_filter)
            list.adapter?.itemCount == count && !list.isLayoutRequested && !filter.isLayoutRequested &&
                list.itemAnimator?.isRunning != true && !list.canScrollVertically(-1)
        }
    }

    private fun awaitFilter(showAll: Boolean) = awaitUi("filter label for showAll=$showAll") { activity ->
        activity.findViewById<MaterialButton>(R.id.btn_filter)?.text?.toString() ==
            activity.getString(if (showAll) R.string.disasters_tab_all else R.string.disasters_tab_recommended)
    }

    private fun selectTab(position: Int) {
        var selected = -1
        scenario!!.onActivity { selected = it.findViewById<TabLayout>(R.id.tab_layout).selectedTabPosition }
        if (selected != position) tapView { activity ->
            val tabs = activity.findViewById<TabLayout>(R.id.tab_layout)
            val strip = tabs.getChildAt(0) as ViewGroup
            strip.getChildAt(position)
        }
    }

    private fun awaitContent(tab: Int, showAll: Boolean) = awaitUi("Disaster tab $tab and showAll=$showAll") { activity ->
        if (navHost(activity).navController.currentDestination?.id != R.id.nav_disasters) return@awaitUi false
        val list = activity.findViewById<RecyclerView>(R.id.rv_content)
        val disasters = disasterModel(activity)
        val tips = tipsModel(activity)
        val allLoaded = disasters.disasters.value.isNotEmpty() && disasters.recommendedDisasters.value.isNotEmpty() &&
            tips.allDisasterGroups.value.isNotEmpty() && tips.recommendedDisasterGroups.value.isNotEmpty() &&
            !disasters.isLoading.value
        val matching = when (tab) {
            0 -> (list.adapter as? DisasterAdapter)?.currentList ==
                (if (showAll) disasters.disasters.value else disasters.recommendedDisasters.value)
            else -> (list.adapter as? TipDisasterGroupAdapter)?.currentList ==
                (if (showAll) tips.allDisasterGroups.value else tips.recommendedDisasterGroups.value)
        }
        allLoaded && matching && activity.findViewById<TabLayout>(R.id.tab_layout).selectedTabPosition == tab &&
            activity.findViewById<MaterialButton>(R.id.btn_filter).text.toString() ==
                activity.getString(if (showAll) R.string.disasters_tab_all else R.string.disasters_tab_recommended) &&
            !list.isLayoutRequested && !activity.findViewById<View>(R.id.btn_filter).isLayoutRequested &&
            list.itemAnimator?.isRunning != true
    }

    private fun awaitDestination(destination: Int) = awaitUi("destination $destination to settle") { activity ->
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        navHost(activity).navController.currentDestination?.id == destination &&
            toolbar?.isShown == true && toolbar.width > 0 && !toolbar.isLayoutRequested
    }

    private fun awaitUi(description: String, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var fulfilled = false
            scenario!!.onActivity { fulfilled = condition(it) }
            if (fulfilled) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for $description")
    }

    /** Real touch input avoids Espresso's legacy InputManager reflection on newer Android. */
    private fun tapView(findView: (MainActivity) -> View) {
        var target = Rect()
        scenario!!.onActivity { activity ->
            val view = findView(activity)
            assertTrue("Tap target should be visible", view.isShown && view.getGlobalVisibleRect(target))
            assertTrue("Tap target should have a measured size", target.width() > 0 && target.height() > 0)
        }
        val downTime = SystemClock.uptimeMillis()
        injectTouch(downTime, MotionEvent.ACTION_DOWN, target.exactCenterX(), target.exactCenterY())
        injectTouch(downTime, MotionEvent.ACTION_UP, target.exactCenterX(), target.exactCenterY())
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    private fun swipeListUp() {
        var bounds = Rect()
        scenario!!.onActivity { activity ->
            bounds = screenBounds(activity.findViewById(R.id.rv_content))
            bounds.bottom = minOf(bounds.bottom, screenBounds(activity.findViewById(R.id.btn_filter)).top)
        }
        val downTime = SystemClock.uptimeMillis()
        val x = bounds.exactCenterX()
        val start = bounds.top + bounds.height() * 0.8f
        val end = bounds.top + bounds.height() * 0.2f
        injectTouch(downTime, MotionEvent.ACTION_DOWN, x, start)
        for (step in 1..12) {
            SystemClock.sleep(20)
            injectTouch(downTime, MotionEvent.ACTION_MOVE, x, start + (end - start) * step / 12f)
        }
        injectTouch(downTime, MotionEvent.ACTION_UP, x, end)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        var previousOffset = -1
        var unchangedSince = SystemClock.uptimeMillis()
        awaitUi("list gesture to settle") { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            val offset = list.computeVerticalScrollOffset()
            if (offset != previousOffset || list.isLayoutRequested || list.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                previousOffset = offset
                unchangedSince = SystemClock.uptimeMillis()
                false
            } else {
                SystemClock.uptimeMillis() - unchangedSince >= 250
            }
        }
    }

    private fun injectTouch(downTime: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
        }
        try {
            assertTrue("Native touch event should be injected",
                InstrumentationRegistry.getInstrumentation().uiAutomation.injectInputEvent(event, true))
        } finally {
            event.recycle()
        }
    }

    private fun navHost(activity: MainActivity) = activity.supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

    private fun disastersFragment(activity: MainActivity) = navHost(activity).childFragmentManager
        .primaryNavigationFragment as DisastersFragment

    private fun disasterModel(activity: MainActivity) =
        ViewModelProvider(disastersFragment(activity))[DisasterViewModel::class.java]

    private fun tipsModel(activity: MainActivity) =
        ViewModelProvider(disastersFragment(activity))[DisasterTipsViewModel::class.java]

    private fun filterPreferences() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("disaster_screen", Context.MODE_PRIVATE)

    private fun drawableCenterColor(drawable: Drawable?): Int {
        assertNotNull("Expected a visible background", drawable)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val oldBounds = Rect(drawable!!.bounds)
        return try {
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        } finally {
            drawable.bounds = oldBounds
            bitmap.recycle()
        }
    }

    private fun screenBounds(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun captureScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val renderedFrames = CountDownLatch(1)
        scenario!!.onActivity { activity ->
            val decor = activity.window.decorView
            decor.postOnAnimation {
                decor.postOnAnimation { renderedFrames.countDown() }
            }
        }
        assertTrue("The screen should render before capturing $name",
            renderedFrames.await(2, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Could not capture $name screenshot")
        try {
            val target = File(instrumentation.targetContext.getExternalFilesDir(null), "disasters-test-$name.png")
            FileOutputStream(target).use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            screenshot.recycle()
        }
    }

    private fun themeName(mode: Int) = if (mode == AppCompatDelegate.MODE_NIGHT_YES) "dark" else "light"

    private companion object {
        const val FILTER_KEY = "show_all_disasters"
    }
}
