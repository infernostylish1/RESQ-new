package com.resq.app

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.ui.adapter.EmergencyContactAdapter
import com.resq.app.ui.fragment.EmergencyContactsFragment
import com.resq.app.ui.viewmodel.EmergencyContactsViewModel
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses existing contacts read-only; synthetic card callbacks never place calls or change the database. */
@RunWith(AndroidJUnit4::class)
class EmergencyContactsScreenTest {
    private var scenario: ActivityScenario<MainActivity>? = null
    private var originalNightMode = AppCompatDelegate.MODE_NIGHT_UNSPECIFIED

    @Before
    fun rememberTheme() {
        originalNightMode = AppCompatDelegate.getDefaultNightMode()
    }

    @After
    fun restoreTheme() {
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(originalNightMode)
        }
    }

    @Test
    fun lightTheme_tabsBlendWithContent_andSettingsAndBackWork() {
        verifyChromeAndNavigation(AppCompatDelegate.MODE_NIGHT_NO)
    }

    @Test
    fun darkTheme_tabsBlendWithContent_andSettingsAndBackWork() {
        verifyChromeAndNavigation(AppCompatDelegate.MODE_NIGHT_YES)
    }

    @Test
    fun tabsFilterExistingContacts_andExposeAddOnlyOnCustom() {
        launchContacts()
        awaitTab(0)
        assertTabContents(0)
        tapView { it.findViewById(R.id.fab_main) }
        awaitUi("primary tab floating actions") { it.findViewById<View>(R.id.fab_voice_row).isShown }
        scenario!!.onActivity {
            assertEquals(View.GONE, it.findViewById<View>(R.id.fab_add_contact_row).visibility)
            assertEquals(View.GONE, it.findViewById<View>(R.id.fab_emergency_row).visibility)
        }
        collapseFloatingActions()

        selectTab(R.string.contacts_tab_custom)
        awaitTab(1)
        assertTabContents(1)
        tapView { it.findViewById(R.id.fab_main) }
        awaitUi("custom tab Add Contact action") { it.findViewById<View>(R.id.fab_add_contact_row).isShown }
        scenario!!.onActivity {
            assertTrue(it.findViewById<View>(R.id.fab_voice_row).isShown)
            assertEquals(View.GONE, it.findViewById<View>(R.id.fab_emergency_row).visibility)
        }
        collapseFloatingActions()

        selectTab(R.string.contacts_tab_primary)
        awaitTab(0)
        assertTabContents(0)
        var lastPosition = 0
        scenario!!.onActivity { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_contacts)
            lastPosition = list.adapter!!.itemCount - 1
            assertTrue("Bundled primary contacts should be available", lastPosition >= 0)
            list.scrollToPosition(lastPosition)
        }
        awaitUi("last primary contact to settle above the floating menu") { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_contacts)
            val lastCard = list.findViewHolderForAdapterPosition(lastPosition)?.itemView
            lastCard?.isShown == true && lastCard.alpha >= 1f &&
                !list.isLayoutRequested && list.itemAnimator?.isRunning != true &&
                lastCard.findViewById<View>(R.id.btn_call).width > 0
        }
        scenario!!.onActivity { activity ->
            val lastCard = activity.findViewById<RecyclerView>(R.id.rv_contacts)
                .findViewHolderForAdapterPosition(lastPosition)!!.itemView
            val call = lastCard.findViewById<View>(R.id.btn_call)
            val floatingMenu = activity.findViewById<View>(R.id.fab_main)
            assertTrue("The final contact call action should scroll above the floating menu",
                screenBounds(call).bottom <= screenBounds(floatingMenu).top)
        }
    }

    @Test
    fun recycledCardsResetManagementActions_andCallbacksUseTheCurrentContact() {
        launchContacts()
        val calls = mutableListOf<EmergencyContactEntity>()
        val edits = mutableListOf<EmergencyContactEntity>()
        val deletes = mutableListOf<EmergencyContactEntity>()
        val custom = EmergencyContactEntity(id = -1, name = "Test family contact", phoneNumber = "5550100")
        val primary = custom.copy(name = "Test primary hotline", phoneNumber = "5550200", isCustom = false)
        lateinit var adapter: EmergencyContactAdapter
        lateinit var originalHolder: RecyclerView.ViewHolder
        scenario!!.onActivity { activity ->
            adapter = EmergencyContactAdapter({ calls.add(it) }, { edits.add(it) }, { deletes.add(it) })
            activity.findViewById<RecyclerView>(R.id.rv_contacts).apply {
                itemAnimator = null
                this.adapter = adapter
            }
            adapter.submitList(listOf(custom))
        }
        awaitSyntheticCard(adapter, custom)
        scenario!!.onActivity { activity ->
            originalHolder = activity.findViewById<RecyclerView>(R.id.rv_contacts)
                .findViewHolderForAdapterPosition(0)!!
            assertManagementActions(originalHolder.itemView, true)
            originalHolder.itemView.findViewById<View>(R.id.btn_call).performClick()
            originalHolder.itemView.findViewById<View>(R.id.btn_edit).performClick()
            originalHolder.itemView.findViewById<View>(R.id.btn_delete).performClick()
            assertEquals(listOf(custom), calls)
            assertEquals(listOf(custom), edits)
            assertEquals(listOf(custom), deletes)
            adapter.submitList(listOf(primary))
        }
        awaitSyntheticCard(adapter, primary)
        scenario!!.onActivity { activity ->
            val rebound = activity.findViewById<RecyclerView>(R.id.rv_contacts)
                .findViewHolderForAdapterPosition(0)!!
            assertSame("The same holder should be rebound from custom to primary", originalHolder, rebound)
            assertManagementActions(rebound.itemView, false)
            rebound.itemView.findViewById<View>(R.id.btn_call).performClick()
            assertEquals(listOf(custom, primary), calls)
            assertEquals("Rebinding should not invoke edit", listOf(custom), edits)
            assertEquals("Rebinding should not invoke delete", listOf(custom), deletes)
            adapter.submitList(listOf(custom))
        }
        awaitSyntheticCard(adapter, custom)
        scenario!!.onActivity { activity ->
            val rebound = activity.findViewById<RecyclerView>(R.id.rv_contacts)
                .findViewHolderForAdapterPosition(0)!!
            assertSame(originalHolder, rebound)
            assertManagementActions(rebound.itemView, true)
            rebound.itemView.findViewById<View>(R.id.btn_edit).performClick()
            assertEquals(listOf(custom, custom), edits)
        }
    }

    @Test
    fun longContactTextAtLargerSize_wrapsWithoutCoveringAccessibleActions() {
        launchContacts()
        val custom = EmergencyContactEntity(
            id = -2,
            name = "Family emergency contact and neighborhood evacuation coordinator",
            phoneNumber = "+63 (999) 123-4567 / +63 (998) 765-4321"
        )
        lateinit var adapter: EmergencyContactAdapter
        scenario!!.onActivity { activity ->
            adapter = EmergencyContactAdapter({})
            activity.findViewById<RecyclerView>(R.id.rv_contacts).apply {
                itemAnimator = null
                this.adapter = adapter
            }
            adapter.submitList(listOf(custom))
        }
        awaitSyntheticCard(adapter, custom)
        scenario!!.onActivity { activity ->
            val card = activity.findViewById<RecyclerView>(R.id.rv_contacts)
                .findViewHolderForAdapterPosition(0)!!.itemView
            for (id in listOf(R.id.tv_contact_name, R.id.tv_contact_number, R.id.btn_edit, R.id.btn_delete)) {
                val text = card.findViewById<TextView>(id)
                text.setTextSize(TypedValue.COMPLEX_UNIT_PX, text.textSize * 1.5f)
            }
        }
        awaitUi("enlarged contact card layout") { activity ->
            val card = activity.findViewById<RecyclerView>(R.id.rv_contacts)
                .findViewHolderForAdapterPosition(0)?.itemView
            card != null && !card.isLayoutRequested &&
                card.findViewById<TextView>(R.id.tv_contact_name).layout != null
        }
        scenario!!.onActivity { activity ->
            val card = activity.findViewById<RecyclerView>(R.id.rv_contacts)
                .findViewHolderForAdapterPosition(0)!!.itemView as MaterialCardView
            val name = card.findViewById<TextView>(R.id.tv_contact_name)
            val phone = card.findViewById<TextView>(R.id.tv_contact_number)
            val call = card.findViewById<View>(R.id.btn_call)
            assertTrue("Long names should wrap", name.layout.lineCount > 1)
            assertTrue("Long phone numbers should wrap", phone.layout.lineCount > 1)
            for (text in listOf(name, phone)) {
                assertTrue("Contact text should be fully visible", (0 until text.layout.lineCount).all {
                    text.layout.getEllipsisCount(it) == 0
                })
                assertEquals("Contact text must not be truncated", text.text.length,
                    text.layout.getLineEnd(text.layout.lineCount - 1))
                assertTrue("Text height should accommodate every line",
                    text.height >= text.layout.height + text.compoundPaddingTop + text.compoundPaddingBottom)
                assertTrue("Text should fit inside the card", screenBounds(card).contains(screenBounds(text)))
                assertTrue("Text should end before the call action", screenBounds(text).right <= screenBounds(call).left)
            }
            assertTrue("The phone number should sit below the name",
                screenBounds(phone).top >= screenBounds(name).bottom)
            assertManagementActions(card, true)
            for (id in listOf(R.id.btn_call, R.id.btn_edit, R.id.btn_delete)) {
                val action = card.findViewById<View>(id)
                val minimumSize = (48 * activity.resources.displayMetrics.density).roundToInt()
                assertTrue("Contact actions need a 48dp touch target",
                    action.width >= minimumSize && action.height >= minimumSize)
                assertTrue("Contact actions should fit inside the card", screenBounds(card).contains(screenBounds(action)))
                assertTrue("Contact actions should accept taps and keyboard focus", action.isClickable && action.isFocusable)
                assertTrue("Each contact action should have a readable label",
                    !action.contentDescription.isNullOrBlank() || (action as? TextView)?.text?.isNotBlank() == true)
            }
            val oldIcon = activity.resources.getIdentifier("iv_contact_icon", "id", activity.packageName)
            assertTrue("Cards should not retain the decorative call icon on the left",
                oldIcon == 0 || card.findViewById<View>(oldIcon) == null)
            val marginLayoutParams = card.layoutParams as ViewGroup.MarginLayoutParams
            assertTrue("Cards should retain the disaster card's rounded elevated shell",
                card.radius > 0 && card.cardElevation > 0)
            assertEquals("Cards should use the disaster card's 16dp spacing",
                activity.resources.getDimensionPixelSize(R.dimen.spacing_md),
                marginLayoutParams.bottomMargin)
            assertEquals("The elevated disaster card shell should not add an outline",
                0, card.strokeWidth)
        }
        captureScreenshot("large-text-custom-card")
    }

    private fun verifyChromeAndNavigation(mode: Int) {
        launchContacts(mode)
        captureScreenshot("${themeName(mode)}-primary")
        assertChrome(mode)
        selectTab(R.string.contacts_tab_custom)
        awaitTab(1)
        captureScreenshot("${themeName(mode)}-custom")
        assertChrome(mode)

        tapView { it.findViewById(R.id.btn_toolbar_settings) }
        awaitDestination(R.id.nav_settings)
        clickToolbarBack()
        awaitDestination(R.id.nav_emergency_contacts)
        awaitTab(1)
        captureScreenshot("${themeName(mode)}-returned-custom")
        assertChrome(mode)
        clickToolbarBack()
        awaitDestination(R.id.nav_home)
    }

    private fun assertChrome(mode: Int) {
        scenario!!.onActivity { activity ->
            assertEquals("Requested theme should be active", mode == AppCompatDelegate.MODE_NIGHT_YES,
                activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES)
            val root = contactsFragment(activity).requireView()
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            val tabs = activity.findViewById<TabLayout>(R.id.tab_layout)
            val title = (0 until toolbar.childCount).map { toolbar.getChildAt(it) }
                .filterIsInstance<TextView>().firstOrNull { it.text.toString() == toolbar.title.toString() }
                ?: throw AssertionError("Contacts should render its full toolbar title")
            assertTrue("Contacts toolbar title should have no ellipsis", (0 until title.layout.lineCount).all {
                title.layout.getEllipsisCount(it) == 0
            })
            val background = activity.getColor(R.color.background)
            for (view in listOf(root, toolbar, tabs)) {
                assertEquals("Contacts header, tabs, and content should share the theme background",
                    background, drawableCenterColor(view.background))
                assertEquals("Contacts chrome should not create a raised seam", 0f, view.elevation, 0.01f)
            }
            assertEquals("Tabs should directly follow the toolbar", screenBounds(toolbar).bottom, screenBounds(tabs).top)
            assertNotNull("Contacts should offer toolbar Back", toolbar.navigationIcon)
            val settings = activity.findViewById<View>(R.id.btn_toolbar_settings)
            val actions = activity.findViewById<ViewGroup>(R.id.toolbar_actions)
            assertTrue("Contacts should use the shared visible Settings action", settings.isShown && actions.isShown)
            assertTrue("Settings should accept taps and keyboard focus", settings.isClickable && settings.isFocusable)
            assertFalse("Settings should have an accessibility label", settings.contentDescription.isNullOrBlank())
            val minimumSize = (48 * activity.resources.displayMetrics.density).roundToInt()
            assertTrue("Settings needs a 48dp touch target", settings.width >= minimumSize && settings.height >= minimumSize)
            assertTrue("Settings should fit inside the toolbar", screenBounds(toolbar).contains(screenBounds(actions)))
        }
    }

    private fun assertTabContents(position: Int) {
        scenario!!.onActivity { activity ->
            val state = contactsModel(activity).uiState.value
            val expected = state.allContacts.filter { it.isCustom == (position == 1) }
            val list = activity.findViewById<RecyclerView>(R.id.rv_contacts)
            assertEquals("The selected tab should filter the existing contacts", expected,
                (list.adapter as EmergencyContactAdapter).currentList)
            assertEquals("Empty state should follow the selected tab", expected.isEmpty(),
                activity.findViewById<View>(R.id.tv_empty).visibility == View.VISIBLE)
            assertEquals("List visibility should follow the selected tab", expected.isNotEmpty(), list.isShown)
        }
    }

    private fun assertManagementActions(card: View, custom: Boolean) {
        assertEquals("Management row is available only for custom contacts", if (custom) View.VISIBLE else View.GONE,
            card.findViewById<View>(R.id.contact_actions).visibility)
        for (id in listOf(R.id.btn_edit, R.id.btn_delete)) {
            assertEquals("Management actions are available only for custom contacts", custom,
                card.findViewById<View>(id).isShown)
        }
        assertTrue("Every contact should retain a call action", card.findViewById<View>(R.id.btn_call).isShown)
    }

    private fun launchContacts(mode: Int = AppCompatDelegate.MODE_NIGHT_NO) {
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
        scenario!!.onActivity { navHost(it).navController.navigate(R.id.nav_emergency_contacts) }
        awaitDestination(R.id.nav_emergency_contacts)
        awaitTab(0)
        var cardsSettledSince = SystemClock.uptimeMillis()
        awaitUi("bundled primary contact cards to render") { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_contacts)
            val firstCard = list.findViewHolderForAdapterPosition(0)?.itemView
            val ready = list.isShown && (list.adapter?.itemCount ?: 0) > 0 &&
                firstCard?.isShown == true && firstCard.width > 0 && firstCard.height > 0 &&
                firstCard.alpha >= 1f && !firstCard.isLayoutRequested && list.itemAnimator?.isRunning != true &&
                activity.findViewById<View>(R.id.progress_indicator).visibility == View.GONE
            if (!ready) cardsSettledSince = SystemClock.uptimeMillis()
            ready && SystemClock.uptimeMillis() - cardsSettledSince >= 150
        }
    }

    private fun selectTab(label: Int) {
        tapView { activity ->
            val text = activity.getString(label)
            descendants(activity.findViewById(R.id.tab_layout)).filterIsInstance<TextView>()
                .first { it.text.toString() == text }
        }
    }

    private fun clickToolbarBack() {
        tapView { activity ->
            val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
            val description = toolbar.navigationContentDescription?.toString().orEmpty()
            assertTrue("Toolbar Back should have an accessibility label", description.isNotBlank())
            descendants(toolbar).first { it.contentDescription?.toString() == description }
        }
    }

    private fun collapseFloatingActions() {
        tapView { it.findViewById(R.id.fab_main) }
        awaitUi("floating actions to collapse") {
            it.findViewById<View>(R.id.fab_voice_row).visibility == View.GONE &&
                it.findViewById<View>(R.id.fab_add_contact_row).visibility == View.GONE &&
                it.findViewById<View>(R.id.fab_scrim).visibility == View.GONE
        }
    }

    private fun awaitTab(position: Int) = awaitUi(
        "contacts tab $position to finish loading",
        diagnostic = { activity ->
            val state = contactsModel(activity).uiState.value
            val list = activity.findViewById<RecyclerView>(R.id.rv_contacts)
            "selectedTab=${activity.findViewById<TabLayout>(R.id.tab_layout).selectedTabPosition}, " +
                "stateTab=${state.selectedTab}, loading=${state.isLoading}, " +
                "currentListSize=${list.adapter?.itemCount}, allContactsSize=${state.allContacts.size}, " +
                "filteredSize=${state.filteredContacts.size}, listVisibility=${list.visibility}, " +
                "layoutRequested=${list.isLayoutRequested}, " +
                "emptyVisibility=${activity.findViewById<View>(R.id.tv_empty).visibility}, " +
                "progressVisibility=${activity.findViewById<View>(R.id.progress_indicator).visibility}"
        }
    ) { activity ->
        val state = contactsModel(activity).uiState.value
        val list = activity.findViewById<RecyclerView>(R.id.rv_contacts)
        !state.isLoading && state.selectedTab == position &&
            activity.findViewById<TabLayout>(R.id.tab_layout).selectedTabPosition == position &&
            (list.adapter as EmergencyContactAdapter).currentList == state.filteredContacts &&
            (list.visibility == View.GONE || !list.isLayoutRequested) &&
            activity.findViewById<View>(R.id.progress_indicator).visibility == View.GONE &&
            (activity.findViewById<View>(R.id.tv_empty).visibility == View.VISIBLE) == state.filteredContacts.isEmpty()
    }

    private fun awaitSyntheticCard(adapter: EmergencyContactAdapter, contact: EmergencyContactEntity) =
        awaitUi("synthetic contact ${contact.name} to bind") { activity ->
            val list = activity.findViewById<RecyclerView>(R.id.rv_contacts)
            val card = list.findViewHolderForAdapterPosition(0)?.itemView
            adapter.currentList == listOf(contact) && card != null && !card.isLayoutRequested &&
                card.findViewById<TextView>(R.id.tv_contact_name).text.toString() == contact.name
        }

    private fun awaitDestination(destination: Int) = awaitUi("destination $destination to settle") { activity ->
        val toolbar = activity.findViewById<Toolbar>(R.id.toolbar)
        navHost(activity).navController.currentDestination?.id == destination &&
            toolbar?.isShown == true && toolbar.width > 0 && !toolbar.isLayoutRequested
    }

    private fun awaitUi(
        description: String,
        diagnostic: ((MainActivity) -> String)? = null,
        condition: (MainActivity) -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            var fulfilled = false
            scenario!!.onActivity { fulfilled = condition(it) }
            if (fulfilled) return
            SystemClock.sleep(50)
        }
        var details = ""
        if (diagnostic != null) scenario!!.onActivity { details = "; ${diagnostic(it)}" }
        throw AssertionError("Timed out waiting for $description$details")
    }

    /** Inject real touch events without Espresso's legacy InputManager reflection on newer Android. */
    private fun tapView(findView: (MainActivity) -> View) {
        var target = Rect()
        scenario!!.onActivity { activity ->
            val view = findView(activity)
            val visibleBounds = Rect()
            assertTrue("Tap target should be visible", view.isShown && view.getGlobalVisibleRect(visibleBounds))
            target = screenBounds(view)
            assertTrue("Tap target should have a measured size", target.width() > 0 && target.height() > 0)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val downTime = SystemClock.uptimeMillis()
        val x = target.exactCenterX()
        val y = target.exactCenterY()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try {
                assertTrue("Native touch event should be injected", instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        instrumentation.waitForIdleSync()
    }

    private fun descendants(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) addAll(descendants(view.getChildAt(index)))
        }
    }

    private fun navHost(activity: MainActivity) = activity.supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment

    private fun contactsFragment(activity: MainActivity) = navHost(activity).childFragmentManager
        .primaryNavigationFragment as EmergencyContactsFragment

    private fun contactsModel(activity: MainActivity) =
        ViewModelProvider(contactsFragment(activity))[EmergencyContactsViewModel::class.java]

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
        instrumentation.waitForIdleSync()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Could not capture $name screenshot")
        try {
            val target = File(instrumentation.targetContext.getExternalFilesDir(null), "contacts-test-$name.png")
            FileOutputStream(target).use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            screenshot.recycle()
        }
    }

    private fun themeName(mode: Int) = if (mode == AppCompatDelegate.MODE_NIGHT_YES) "dark" else "light"
}
