package com.resq.app

import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.pressImeActionButton
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputLayout
import com.resq.app.data.repository.SearchHistoryRepository
import com.resq.app.ui.search.GlobalSearchFragment
import com.resq.app.ui.search.GlobalSearchViewModel
import java.io.File
import java.io.FileOutputStream
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual search screen against the app's bundled search content. */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED
    private var originalHistory: List<String> = emptyList()

    @Before
    fun rememberUserState() {
        originalNightMode = AppCompatDelegate.getDefaultNightMode()
        originalHistory = SearchHistoryRepository(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).history.value
    }

    @After
    fun restoreUserState() {
        // Restore only the dedicated search history; contacts and settings are untouched.
        scenario?.onActivity { activity ->
            val host = activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            val fragment = host.childFragmentManager.primaryNavigationFragment
            if (host.navController.currentDestination?.id == R.id.nav_global_search && fragment != null) {
                val model = ViewModelProvider(fragment)[GlobalSearchViewModel::class.java]
                model.clearHistory()
                originalHistory.asReversed().forEach { query ->
                    model.onQueryChanged(query)
                    model.recordSearch()
                }
                model.onQueryChanged("")
            }
        }
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(originalNightMode)
        }
    }

    @Test
    fun lightTheme_hasFilledBottomControlsWithoutAppNavigation() {
        launchSearch(AppCompatDelegate.MODE_NIGHT_NO)
        assertSearchChrome()
        captureScreenshot("light-idle")
    }

    @Test
    fun darkTheme_hasFilledBottomControlsWithoutAppNavigation() {
        launchSearch(AppCompatDelegate.MODE_NIGHT_YES)
        assertSearchChrome()
        captureScreenshot("dark-idle")
    }

    @Test
    fun suggestions_submitHistory_clearQuery_reuseHistory_andClearHistory() {
        launchSearch()
        var suggestedQuery = ""
        scenario!!.onActivity { activity ->
            val suggestions = activity.findViewById<ChipGroup>(R.id.chip_suggestions)
            assertTrue("Search suggestions must be available before typing", suggestions.childCount > 0)
            val suggestion = suggestions.getChildAt(0) as Chip
            suggestedQuery = suggestion.text.toString()
            suggestion.performClick()
        }
        awaitUi("suggestion to populate the search field") {
            it.findViewById<EditText>(R.id.et_search).text.toString() == suggestedQuery
        }
        onView(withId(R.id.et_search)).perform(pressImeActionButton())
        awaitKeyboard(false)
        clearSearch()
        awaitUi("submitted query to appear in search history") { activity ->
            val history = activity.findViewById<ChipGroup>(R.id.chip_history)
            (0 until history.childCount).any {
                (history.getChildAt(it) as Chip).text.toString() == suggestedQuery
            }
        }
        // A fresh repository reads the actual persisted values, independently of the ViewModel.
        assertTrue(SearchHistoryRepository(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).history.value.contains(suggestedQuery))
        closeSoftKeyboard()
        awaitKeyboard(false)
        captureScreenshot("history")
        scenario!!.onActivity { activity ->
            val history = activity.findViewById<ChipGroup>(R.id.chip_history)
            val chip = (0 until history.childCount).map { history.getChildAt(it) as Chip }
                .first { it.text.toString() == suggestedQuery }
            chip.performClick()
        }
        awaitUi("history selection to restore the query") {
            it.findViewById<EditText>(R.id.et_search).text.toString() == suggestedQuery
        }
        awaitKeyboard(false)
        clearSearch()
        closeSoftKeyboard()
        onView(withId(R.id.btn_clear_history)).perform(click())
        awaitUi("history to clear") { activity ->
            activity.findViewById<ChipGroup>(R.id.chip_history).childCount == 0 &&
                activity.findViewById<View>(R.id.tv_history_empty).visibility == View.VISIBLE
        }
        assertTrue(SearchHistoryRepository(
            InstrumentationRegistry.getInstrumentation().targetContext
        ).history.value.isEmpty())
    }

    @Test
    fun resultsHighlightMatches_andBottomControlsStayFixedWhileScrolling() {
        launchSearch()
        onView(withId(R.id.et_search)).perform(replaceText("a"))
        awaitUi("search results") {
            val results = it.findViewById<RecyclerView>(R.id.rv_results)
            results.visibility == View.VISIBLE && (results.adapter?.itemCount ?: 0) > 5
        }
        closeSoftKeyboard()
        awaitKeyboard(false)
        captureScreenshot("results")
        var controlsTop = 0
        scenario!!.onActivity { activity ->
            val results = activity.findViewById<RecyclerView>(R.id.rv_results)
            val visibleTitles = (0 until results.childCount).mapNotNull {
                results.getChildAt(it).findViewById<TextView>(R.id.tv_title)
            }
            assertTrue("A matching result should have a visible highlighted span", visibleTitles.any {
                val text = it.text as? Spanned
                text != null && text.getSpans(0, text.length, BackgroundColorSpan::class.java).isNotEmpty()
            })
            controlsTop = screenBounds(activity.findViewById(R.id.search_controls)).top
            results.scrollToPosition(results.adapter!!.itemCount - 1)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        scenario!!.onActivity { activity ->
            val controls = activity.findViewById<View>(R.id.search_controls)
            val results = activity.findViewById<RecyclerView>(R.id.rv_results)
            assertEquals("Scrolling results must not move the search controls", controlsTop, screenBounds(controls).top)
            assertTrue("Results must end above the fixed controls", screenBounds(results).bottom <= screenBounds(controls).top)
        }
        clearSearch()
        awaitUi("clearing to restore suggestions") {
            it.findViewById<View>(R.id.layout_idle).visibility == View.VISIBLE &&
                it.findViewById<View>(R.id.rv_results).visibility != View.VISIBLE
        }
    }

    @Test
    fun keyboardKeepsBackAndSearchControlsVisibleAboveIt() {
        launchSearch()
        closeSoftKeyboard()
        awaitKeyboard(false)
        var closedKeyboardTop = 0
        scenario!!.onActivity {
            closedKeyboardTop = screenBounds(it.findViewById(R.id.search_controls)).top
        }
        onView(withId(R.id.et_search)).perform(click())
        awaitKeyboard(true)
        scenario!!.onActivity { activity ->
            val visibleWindow = Rect()
            activity.window.decorView.getWindowVisibleDisplayFrame(visibleWindow)
            val controls = screenBounds(activity.findViewById(R.id.search_controls))
            assertTrue("Bottom controls should move above the opened keyboard", controls.top < closedKeyboardTop)
            assertTrue("Keyboard must not cover the search controls", controls.bottom <= visibleWindow.bottom + 1)
            assertTrue(activity.findViewById<View>(R.id.btn_back).isShown)
            assertTrue(activity.findViewById<View>(R.id.et_search).isShown)
        }
        captureScreenshot("keyboard")
    }

    @Test
    fun initialVoiceQuery_preservesEditsAndClearingAcrossRecreation() {
        launchSearch(initialQuery = "earthquake")
        awaitUi("initial voice query") {
            it.findViewById<EditText>(R.id.et_search)?.text?.toString() == "earthquake"
        }
        onView(withId(R.id.et_search)).perform(replaceText("flood"))
        scenario!!.recreate()
        awaitUi("edited query after recreation") {
            it.findViewById<EditText>(R.id.et_search)?.text?.toString() == "flood"
        }
        // The clear action must not cause the original navigation argument to reappear.
        clearSearch()
        scenario!!.recreate()
        awaitUi("cleared query and suggestions after recreation") {
            it.findViewById<EditText>(R.id.et_search)?.text?.toString() == "" &&
                it.findViewById<View>(R.id.layout_idle)?.visibility == View.VISIBLE
        }
    }

    @Test
    fun tagalogMatchesAreHighlighted_andNoResultsCanBeClearedWithoutSavingPartialQueries() {
        launchSearch()
        onView(withId(R.id.et_search)).perform(replaceText("Lindol"))
        awaitUi("visible highlighted Tagalog result") { activity ->
            val results = activity.findViewById<RecyclerView>(R.id.rv_results)
            results.isShown && (0 until results.childCount).any { index ->
                val title = results.getChildAt(index).findViewById<TextView>(R.id.tv_title)?.text as? Spanned
                title != null && title.getSpans(0, title.length, BackgroundColorSpan::class.java).any { span ->
                    title.subSequence(title.getSpanStart(span), title.getSpanEnd(span))
                        .toString().equals("Lindol", ignoreCase = true)
                }
            }
        }
        onView(withId(R.id.et_search)).perform(replaceText("zzresqnomatch837421"))
        awaitUi("no-results state with accessible bottom controls") {
            it.findViewById<View>(R.id.layout_empty).isShown &&
                it.findViewById<View>(R.id.search_controls).isShown &&
                !it.findViewById<View>(R.id.rv_results).isShown
        }
        assertEquals("Typing without submitting must not create history entries", originalHistory,
            SearchHistoryRepository(InstrumentationRegistry.getInstrumentation().targetContext).history.value)
        clearSearch()
        awaitUi("suggestions after clearing an unmatched query") {
            it.findViewById<View>(R.id.layout_idle).isShown &&
                it.findViewById<ChipGroup>(R.id.chip_suggestions).childCount > 0 &&
                !it.findViewById<View>(R.id.layout_empty).isShown
        }
    }

    private fun launchSearch(
        nightMode: Int = AppCompatDelegate.MODE_NIGHT_NO,
        initialQuery: String? = null
    ) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitUi("navigation graph to initialize") { activity ->
            val host = activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            host?.navController?.currentDestination != null
        }
        scenario!!.onActivity { activity ->
            val host = activity.supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            host.navController.navigate(R.id.nav_global_search, Bundle().apply {
                initialQuery?.let { putString(GlobalSearchFragment.ARG_INITIAL_QUERY, it) }
            })
        }
        awaitUi("search controls and suggestions to load") {
            it.findViewById<View>(R.id.search_controls)?.isShown == true &&
                (it.findViewById<ChipGroup>(R.id.chip_suggestions)?.childCount ?: 0) > 0
        }
    }

    private fun assertSearchChrome() {
        closeSoftKeyboard()
        awaitKeyboard(false)
        scenario!!.onActivity { activity ->
            assertEquals(View.GONE, activity.findViewById<View>(R.id.bottom_navigation).visibility)
            assertEquals(View.GONE, activity.findViewById<View>(R.id.fab_main).visibility)
            assertNull("Search should not have a top toolbar", activity.findViewById<View>(R.id.toolbar))
            val back = screenBounds(activity.findViewById(R.id.btn_back))
            val input = screenBounds(activity.findViewById(R.id.til_search))
            assertTrue("Back should sit to the left of the search field", back.right <= input.left)
            assertTrue("Back and search should share a row", back.top < input.bottom && input.top < back.bottom)
            val field = activity.findViewById<TextInputLayout>(R.id.til_search)
            val text = activity.findViewById<EditText>(R.id.et_search)
            assertEquals("Search field should have an opaque fill", 255, Color.alpha(field.boxBackgroundColor))
            assertTrue("Search text must remain legible in either theme",
                ColorUtils.calculateContrast(text.currentTextColor, field.boxBackgroundColor) >= 4.5)
            assertNotNull(text.hint)
        }
    }

    private fun clearSearch() {
        awaitControlsSettled()
        onView(allOf(
            withId(com.google.android.material.R.id.text_input_end_icon),
            isDescendantOfA(withId(R.id.til_search))
        )).perform(click())
        awaitUi("clear button to remove the query") {
            it.findViewById<EditText>(R.id.et_search).text.isEmpty()
        }
    }

    private fun awaitControlsSettled() {
        var previousBounds: Rect? = null
        var unchangedSince = SystemClock.uptimeMillis()
        awaitUi("search controls to settle after keyboard relocation") { activity ->
            val controls = activity.findViewById<View>(R.id.search_controls)
            val bounds = screenBounds(controls)
            if (previousBounds != bounds || controls.isLayoutRequested) {
                previousBounds = bounds
                unchangedSince = SystemClock.uptimeMillis()
                false
            } else {
                SystemClock.uptimeMillis() - unchangedSince >= 300
            }
        }
    }

    private fun awaitKeyboard(visible: Boolean) = awaitUi("keyboard visibility = $visible") {
        ViewCompat.getRootWindowInsets(it.window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.ime()) == visible
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

    private fun screenBounds(view: View): Rect {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
    }

    private fun captureScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Could not capture $name screenshot")
        try {
            val target = File(instrumentation.targetContext.getExternalFilesDir(null), "search-test-$name.png")
            FileOutputStream(target).use { output ->
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            screenshot.recycle()
        }
    }
}
