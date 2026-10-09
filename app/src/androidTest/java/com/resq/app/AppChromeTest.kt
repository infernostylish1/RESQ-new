package com.resq.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.resq.app.data.database.PreloadedDisasterData
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies shared app chrome and restores the user's saved filter and theme afterward. */
@RunWith(AndroidJUnit4::class)
class AppChromeTest {
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
    private var originalDisasterShowAll: Boolean? = null

    @Before
    fun rememberTheme() {
        originalNightMode = AppCompatDelegate.getDefaultNightMode()
        val preferences = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("disaster_screen", Context.MODE_PRIVATE)
        originalDisasterShowAll = if (preferences.contains("show_all_disasters")) {
            preferences.getBoolean("show_all_disasters", false)
        } else null
        assertTrue(preferences.edit().remove("show_all_disasters").commit())
    }

    @After
    fun restoreTheme() {
        scenario?.close()
        val edit = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("disaster_screen", Context.MODE_PRIVATE).edit()
        originalDisasterShowAll?.let { edit.putBoolean("show_all_disasters", it) }
            ?: edit.remove("show_all_disasters")
        assertTrue(edit.commit())
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(originalNightMode)
        }
    }

    @Test
    fun lightTheme_primaryModules_haveRecognizableAccessibleSettingsAndMatchingBars() {
        verifyPrimaryModules(AppCompatDelegate.MODE_NIGHT_NO)
    }

    @Test
    fun darkTheme_primaryModules_haveRecognizableAccessibleSettingsAndMatchingBars() {
        verifyPrimaryModules(AppCompatDelegate.MODE_NIGHT_YES)
    }

    @Test
    fun lightTheme_detailAndInformationHeaders_remainFlatReadableAndNavigable() {
        verifySecondaryModules(AppCompatDelegate.MODE_NIGHT_NO)
    }

    @Test
    fun darkTheme_detailAndInformationHeaders_remainFlatReadableAndNavigable() {
        verifySecondaryModules(AppCompatDelegate.MODE_NIGHT_YES)
    }

    @Test
    fun disasterFilter_updatesContentAndKeepsSelectionAcrossInformationAndTips() {
        launchHome(AppCompatDelegate.MODE_NIGHT_NO)
        tapView { it.findViewById(R.id.nav_disasters) }
        awaitToolbar(R.id.nav_disasters)
        awaitUi("recommended disasters to load") {
            (it.findViewById<RecyclerView>(R.id.rv_content).adapter?.itemCount ?: 0) > 0 &&
                it.findViewById<View>(R.id.progress_indicator).visibility != View.VISIBLE
        }
        var recommendedCount = 0
        scenario!!.onActivity {
            recommendedCount = it.findViewById<RecyclerView>(R.id.rv_content).adapter!!.itemCount
        }
        assertFilterLabel(R.string.disasters_tab_recommended)
        chooseFilter(R.string.disasters_tab_all)
        awaitUi("all bundled disasters") {
            it.findViewById<RecyclerView>(R.id.rv_content).adapter?.itemCount ==
                PreloadedDisasterData.getDisasters().size
        }
        assertFilterLabel(R.string.disasters_tab_all)
        scenario!!.onActivity {
            assertTrue(it.findViewById<RecyclerView>(R.id.rv_content).adapter!!.itemCount >= recommendedCount)
        }

        clickTab(R.string.nav_tips)
        awaitUi("all tip groups") {
            (it.findViewById<RecyclerView>(R.id.rv_content).adapter?.itemCount ?: 0) > 0
        }
        assertFilterLabel(R.string.disasters_tab_all)
        chooseFilter(R.string.disasters_tab_recommended)
        assertFilterLabel(R.string.disasters_tab_recommended)
        clickTab(R.string.disasters_tab_information)
        awaitUi("recommended information after switching tabs") {
            it.findViewById<RecyclerView>(R.id.rv_content).adapter?.itemCount == recommendedCount
        }
        assertFilterLabel(R.string.disasters_tab_recommended)

        // Change only these views' text sizes to exercise larger text without saving a preference.
        scenario!!.onActivity { activity ->
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            val title = descendants(toolbar).filterIsInstance<TextView>()
                .first { it.text.toString() == toolbar.title.toString() }
            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, title.textSize * 1.3f)
            val filter = activity.findViewById<MaterialButton>(R.id.btn_filter)
            filter.setTextSize(TypedValue.COMPLEX_UNIT_PX, filter.textSize * 1.3f)
        }
        awaitToolbar(R.id.nav_disasters)
        assertToolbar()
        assertSettingsAction()
        awaitDisasterFilterLayout()
        scenario!!.onActivity { activity ->
            val filter = activity.findViewById<MaterialButton>(R.id.btn_filter)
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            val fab = activity.findViewById<View>(R.id.fab_main)
            assertTrue("The fixed filter should remain inside the list viewport with larger text",
                screenBounds(list).contains(screenBounds(filter)))
            assertEquals("The filter should stay aligned with the fixed floating menu with larger text",
                screenBounds(fab).exactCenterY(), screenBounds(filter).exactCenterY(), 1f)
            assertFalse("The filter should not overlap the floating menu with larger text",
                Rect.intersects(screenBounds(filter), screenBounds(fab)))
            assertTrue("Filter label should stay readable with larger text",
                (0 until filter.layout.lineCount).all { filter.layout.getEllipsisCount(it) == 0 })
        }
        chooseFilter(R.string.disasters_tab_all)
        assertFilterLabel(R.string.disasters_tab_all)
        awaitDisasterFilterLayout()
        captureScreenshot("light-disasters-large-text")
    }

    @Test
    fun hiddenNavigationAndRecreation_keepSystemBarsInSyncInBothThemes() {
        for (mode in listOf(AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)) {
            launchHome(mode)
            navigate(R.id.nav_global_search)
            awaitUi("search controls") {
                it.findViewById<View>(R.id.search_controls)?.isShown == true
            }
            assertSystemBars(mode, bottomNavigationVisible = false)
            captureScreenshot("${themeName(mode)}-search")

            returnHome()
            assertSystemBars(mode, bottomNavigationVisible = true)

            // Opening onboarding is read-only: do not skip, complete, or edit its profile pages.
            navigate(R.id.nav_onboarding)
            awaitUi("onboarding") { it.findViewById<View>(R.id.view_pager)?.isShown == true }
            assertSystemBars(mode, bottomNavigationVisible = false)
            captureScreenshot("${themeName(mode)}-onboarding")

            returnHome()
            scenario!!.recreate()
            awaitToolbar(R.id.nav_home)
            assertToolbar()
            assertSettingsAction()
            assertSystemBars(mode, bottomNavigationVisible = true)
            captureScreenshot("${themeName(mode)}-recreated-home")
            scenario!!.close()
            scenario = null
        }
    }

    private fun verifyPrimaryModules(mode: Int) {
        launchHome(mode)
        val modules = listOf(
            R.id.nav_home to "home",
            R.id.nav_disasters to "disasters",
            R.id.nav_first_aid to "first-aid",
            R.id.nav_map to "map"
        )
        for ((destination, name) in modules) {
            onView(withId(destination)).perform(click())
            awaitToolbar(destination)
            assertToolbar()
            assertSettingsAction()
            assertSystemBars(mode, bottomNavigationVisible = true)
            captureScreenshot("${themeName(mode)}-$name")

            // Exercise the visible control, rather than navigating straight to Settings.
            onView(withId(R.id.btn_toolbar_settings)).perform(click())
            awaitToolbar(R.id.nav_settings)
            assertToolbar()
            assertSystemBars(mode, bottomNavigationVisible = true)
            scenario!!.onActivity {
                assertEquals("Settings should hide the floating menu", View.GONE,
                    it.findViewById<View>(R.id.fab_main).visibility)
                assertTrue(navHost(it).navController.popBackStack())
            }
            awaitToolbar(destination)
            assertSystemBars(mode, bottomNavigationVisible = true)
        }
    }

    private fun verifySecondaryModules(mode: Int) {
        launchHome(mode)
        val routes = listOf(
            Route(R.id.nav_settings, "settings"),
            Route(R.id.nav_emergency_contacts, "contacts"),
            Route(R.id.nav_about, "about"),
            Route(R.id.nav_terms, "terms"),
            Route(R.id.nav_privacy, "privacy"),
            Route(R.id.nav_data_sources, "data-sources"),
            Route(R.id.nav_disaster_detail, "disaster-detail", disasterArguments()),
            Route(R.id.nav_first_aid_detail, "first-aid-detail", Bundle().apply {
                putLong("categoryId", 1L)
                putString("categoryName", "CPR & Choking")
            }),
            Route(R.id.nav_tip_detail, "tip-detail", disasterArguments())
        )
        for (route in routes) {
            navigate(route.destination, route.arguments)
            awaitToolbar(route.destination)
            assertToolbar()
            assertSystemBars(mode, bottomNavigationVisible = true)
            if (route.arguments != null) {
                verifyDetailHeaderStaysVisible()
            }
            captureScreenshot("${themeName(mode)}-${route.name}")
            var backDescription = ""
            scenario!!.onActivity {
                val toolbar = it.findViewById<Toolbar>(R.id.toolbar)
                assertNotNull("Secondary screens need a visible back icon", toolbar.navigationIcon)
                backDescription = toolbar.navigationContentDescription?.toString().orEmpty()
                assertTrue("Back must have an accessibility label", backDescription.isNotBlank())
            }
            onView(allOf(
                isDescendantOfA(withId(R.id.toolbar)),
                withContentDescription(backDescription)
            )).perform(click())
            awaitToolbar(R.id.nav_home)
        }
    }

    private fun verifyDetailHeaderStaysVisible() {
        var toolbarBounds = Rect()
        scenario!!.onActivity { activity ->
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            toolbarBounds = screenBounds(toolbar)
            val illustration = activity.findViewById<ImageView>(R.id.iv_header)
            if (illustration?.isShown == true) {
                assertTrue("Illustrations should sit below the flat header",
                    screenBounds(illustration).top >= toolbarBounds.bottom)
            }
            activity.findViewById<NestedScrollView>(R.id.nested_scroll_view).fullScroll(View.FOCUS_DOWN)
        }
        awaitUi("detail content to finish scrolling") {
            !it.findViewById<NestedScrollView>(R.id.nested_scroll_view).canScrollVertically(1) &&
                !it.findViewById<View>(R.id.toolbar).isLayoutRequested
        }
        scenario!!.onActivity {
            assertEquals("Scrolling guidance must keep the toolbar in place", toolbarBounds,
                screenBounds(it.findViewById(R.id.toolbar)))
            assertTrue(it.findViewById<View>(R.id.toolbar).isShown)
        }
    }

    private fun assertToolbar() {
        scenario!!.onActivity { activity ->
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            val background = activity.getColor(R.color.background)
            assertTrue("Toolbar should be visible", toolbar.isShown)
            assertEquals("Toolbar must blend with the app background", background,
                drawableCenterColor(toolbar.background))
            assertEquals("Toolbar should be flat", 0f, toolbar.elevation, 0.01f)
            assertEquals("Toolbar should not be translucent", 1f, toolbar.alpha, 0.01f)
            val title = toolbar.title?.toString().orEmpty()
            assertTrue("Every module should have a readable title", title.isNotBlank())
            val titleView = descendants(toolbar).filterIsInstance<TextView>()
                .firstOrNull { it.text.toString() == title }
            assertNotNull("Toolbar title should be rendered", titleView)
            assertTrue("Toolbar title contrast should meet normal-text accessibility",
                ColorUtils.calculateContrast(titleView!!.currentTextColor, background) >= 4.5)
            assertTrue("Title should fit inside the toolbar",
                screenBounds(toolbar).contains(screenBounds(titleView)))
        }
    }

    private fun assertSettingsAction() {
        scenario!!.onActivity { activity ->
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            val container = activity.findViewById<ViewGroup>(R.id.toolbar_actions)
            val settings = activity.findViewById<ImageView>(R.id.btn_toolbar_settings)
            assertTrue("Settings container should be visible", container.isShown)
            assertEquals("Settings container should be flat", 0f, container.elevation, 0.01f)
            val fill = drawableCenterColor(container.background)
            assertEquals("Settings container must have an opaque fill", 255, Color.alpha(fill))
            assertTrue("Settings container should stand out against the toolbar",
                fill != activity.getColor(R.color.background))
            assertTrue("Settings must accept taps and keyboard focus", settings.isClickable && settings.isFocusable)
            assertTrue("Settings must have an accessibility label", !settings.contentDescription.isNullOrBlank())
            assertFalse("Settings must remain exposed to accessibility services",
                settings.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO ||
                    settings.importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS)
            val minimumTouchSize = (48 * activity.resources.displayMetrics.density).roundToInt()
            assertTrue("Settings needs at least a 48dp touch target",
                settings.width >= minimumTouchSize && settings.height >= minimumTouchSize)
            assertTrue("Settings should fit inside its container",
                screenBounds(container).contains(screenBounds(settings)))
            assertTrue("The action container should fit inside the toolbar",
                screenBounds(toolbar).contains(screenBounds(container)))
            val titleView = descendants(toolbar).filterIsInstance<TextView>()
                .first { it.text.toString() == toolbar.title.toString() }
            assertTrue("The settings container should not overlap the title",
                !Rect.intersects(screenBounds(titleView), screenBounds(container)))

            val actual = renderDrawable(settings.drawable)
            try {
                var opaquePixels = 0
                var minimumContrast = Double.POSITIVE_INFINITY
                for (y in 0 until actual.height) {
                    for (x in 0 until actual.width) {
                        val pixel = actual.getPixel(x, y)
                        if (Color.alpha(pixel) == 255) {
                            opaquePixels++
                            minimumContrast = minOf(minimumContrast, ColorUtils.calculateContrast(pixel, fill))
                        }
                    }
                }
                assertTrue("Settings gear must have a visible opaque fill", opaquePixels > 0)
                assertTrue("The rendered settings gear must contrast against its container", minimumContrast >= 3.0)
            } finally {
                actual.recycle()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun assertSystemBars(mode: Int, bottomNavigationVisible: Boolean) {
        scenario!!.onActivity { activity ->
            val dark = mode == AppCompatDelegate.MODE_NIGHT_YES
            assertEquals("Requested theme should be active", dark,
                activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES)
            val background = activity.getColor(R.color.background)
            val bottomNavigation = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
            assertEquals("Bottom navigation visibility", if (bottomNavigationVisible) View.VISIBLE else View.GONE,
                bottomNavigation.visibility)
            assertEquals("Status bar must match the app background", background, activity.window.statusBarColor)
            val navigationColor = if (bottomNavigationVisible) activity.getColor(R.color.nav_background) else background
            assertEquals("System navigation should match the adjacent app surface", navigationColor,
                activity.window.navigationBarColor)
            if (bottomNavigationVisible) {
                assertEquals("Bottom navigation must match the system navigation bar", navigationColor,
                    drawableCenterColor(bottomNavigation.background))
                assertEquals("Bottom navigation should not tint its surface with an elevation overlay",
                    0f, bottomNavigation.elevation, 0.01f)
            }
            val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            assertEquals("Status icons should remain visible in the current theme", !dark,
                controller.isAppearanceLightStatusBars)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                assertEquals("Navigation icons should remain visible in the current theme", !dark,
                    controller.isAppearanceLightNavigationBars)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                assertEquals("System navigation divider should blend with its surface", navigationColor,
                    activity.window.navigationBarDividerColor)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                assertFalse("Android should not apply a separate navigation contrast scrim",
                    activity.window.isNavigationBarContrastEnforced)
            }
        }
    }

    private fun launchHome(mode: Int) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitUi("navigation graph") { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
                ?.navController?.currentDestination != null
        }
        scenario!!.onActivity {
            if (navHost(it).navController.currentDestination?.id != R.id.nav_home) {
                navHost(it).navController.navigate(R.id.nav_home)
            }
        }
        awaitToolbar(R.id.nav_home)
    }

    private fun chooseFilter(labelResource: Int) {
        awaitDisasterFilterLayout()
        tapView { it.findViewById(R.id.btn_filter) }
        assertFilterLabel(labelResource)
    }

    private fun awaitDisasterFilterLayout() {
        awaitUi("fixed Disaster filter layout") { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            val filter = activity.findViewById<View>(R.id.btn_filter)
            !list.isLayoutRequested && !filter.isLayoutRequested && list.itemAnimator?.isRunning != true &&
                filter.isShown && screenBounds(list).contains(screenBounds(filter))
        }
        scenario!!.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_content)
            val filter = activity.findViewById<View>(R.id.btn_filter)
            val fab = activity.findViewById<View>(R.id.fab_main)
            val root = navHost(activity).childFragmentManager.primaryNavigationFragment!!.requireView()
            assertEquals("The list should extend to the content bottom without a rectangular controls strip",
                screenBounds(root).bottom, screenBounds(list).bottom)
            assertEquals("The state filter should align with the fixed floating menu",
                screenBounds(fab).exactCenterY(), screenBounds(filter).exactCenterY(), 1f)
            assertFalse("The state filter should remain clear of the floating menu",
                Rect.intersects(screenBounds(filter), screenBounds(fab)))
            assertTrue("The scrolling list should have no rectangular backdrop",
                list.background == null || Color.alpha(drawableCenterColor(list.background)) == 0)
        }
    }

    private fun assertFilterLabel(labelResource: Int) {
        val label = localizedString(labelResource)
        awaitUi("filter label $label") {
            it.findViewById<MaterialButton>(R.id.btn_filter)?.text?.toString() == label
        }
    }

    private fun clickTab(labelResource: Int) {
        val label = localizedString(labelResource)
        tapView { activity ->
            descendants(activity.findViewById(R.id.tab_layout)).filterIsInstance<TextView>()
                .first { it.text.toString() == label }
        }
    }

    private fun tapView(findView: (MainActivity) -> View) {
        var target = Rect()
        scenario!!.onActivity { activity ->
            val view = findView(activity)
            assertTrue("Tap target should be visible", view.isShown && view.getGlobalVisibleRect(target))
            assertTrue("Tap target should have a measured size", target.width() > 0 && target.height() > 0)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                target.exactCenterX(), target.exactCenterY(), 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            try {
                assertTrue("Native touch event should be injected", instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun localizedString(resource: Int): String {
        var result = ""
        scenario!!.onActivity { result = it.getString(resource) }
        return result
    }

    private fun navigate(destination: Int, arguments: Bundle? = null) {
        scenario!!.onActivity { navHost(it).navController.navigate(destination, arguments) }
    }

    private fun returnHome() {
        scenario!!.onActivity {
            assertTrue("The prior Home screen should remain on the back stack",
                navHost(it).navController.popBackStack())
        }
        awaitToolbar(R.id.nav_home)
    }

    private fun awaitToolbar(destination: Int) {
        var previousBounds: Rect? = null
        var unchangedSince = SystemClock.uptimeMillis()
        awaitUi("settled toolbar for destination $destination") { activity ->
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            if (navHost(activity).navController.currentDestination?.id != destination ||
                toolbar?.isShown != true || toolbar.width == 0 || toolbar.title.isNullOrBlank()) {
                false
            } else {
                val bounds = screenBounds(toolbar)
                if (previousBounds != bounds || toolbar.isLayoutRequested) {
                    previousBounds = bounds
                    unchangedSince = SystemClock.uptimeMillis()
                    false
                } else {
                    SystemClock.uptimeMillis() - unchangedSince >= 250
                }
            }
        }
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

    private fun navHost(activity: MainActivity) = activity.supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

    private fun descendants(parent: ViewGroup): List<View> = buildList {
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            add(child)
            if (child is ViewGroup) addAll(descendants(child))
        }
    }

    private fun drawableCenterColor(drawable: Drawable?): Int {
        val bitmap = renderDrawable(drawable)
        return try {
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        } finally {
            bitmap.recycle()
        }
    }

    private fun renderDrawable(drawable: Drawable?): Bitmap {
        assertNotNull("Expected a visible drawable", drawable)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val previousBounds = Rect(drawable!!.bounds)
        try {
            drawable.setBounds(0, 0, bitmap.width, bitmap.height)
            drawable.draw(Canvas(bitmap))
        } finally {
            drawable.bounds = previousBounds
        }
        return bitmap
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
            val target = File(instrumentation.targetContext.getExternalFilesDir(null), "app-chrome-$name.png")
            FileOutputStream(target).use { output ->
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            screenshot.recycle()
        }
    }

    private fun disasterArguments() = Bundle().apply {
        putLong("disasterId", 1L)
        putString("disasterName", "Earthquake")
    }

    private fun themeName(mode: Int) = if (mode == AppCompatDelegate.MODE_NIGHT_YES) "dark" else "light"

    private data class Route(val destination: Int, val name: String, val arguments: Bundle? = null)
}
