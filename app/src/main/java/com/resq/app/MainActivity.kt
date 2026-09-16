package com.resq.app

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.databinding.ActivityMainBinding
import com.resq.app.ui.voice.VoiceCommandBottomSheet
import com.resq.app.ui.voice.VoiceCommandViewModel
import com.resq.app.util.VoiceCommand
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.resq.app.ui.fragment.EmergencyContactsFragment
import androidx.activity.addCallback
import com.resq.app.data.model.FontSize
import com.resq.app.util.DialogHelper
import com.resq.app.util.FontSizeManager
import com.resq.app.util.clearSelection
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    @Inject lateinit var userSettingsRepository: UserSettingsRepository
    @Inject lateinit var fontSizeManager: FontSizeManager
    private val voiceViewModel: VoiceCommandViewModel by viewModels()

    private var isFabMenuOpen = false
    private val fabHiddenDestinations = setOf(R.id.nav_onboarding, R.id.nav_settings)

    override fun attachBaseContext(newBase: Context) {
        // We need to access the repository before Hilt is initialized in onCreate
        val entryPoint = EntryPointAccessors.fromApplication(
            newBase.applicationContext,
            MainActivityEntryPoint::class.java
        )
        val repository = entryPoint.userSettingsRepository()
        val manager = entryPoint.fontSizeManager()

        val fontSize = runBlocking {
            repository.getSettingsOnce()?.fontSize ?: FontSize.MEDIUM
        }
        
        super.attachBaseContext(manager.wrapContext(newBase, fontSize))
    }

    @dagger.hilt.EntryPoint
    @dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
    interface MainActivityEntryPoint {
        fun userSettingsRepository(): UserSettingsRepository
        fun fontSizeManager(): FontSizeManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind system bars without fitsSystemWindows inflating the nav bar
        WindowCompat.setDecorFitsSystemWindows(window, true)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply insets ONLY to the fragment container so the status bar is respected,
        // but the BottomNavigationView height stays fixed and is never pushed up.
        ViewCompat.setOnApplyWindowInsetsListener(binding.navHostFragment) { view, insets ->
            view.updatePadding(0)
            insets
        }

        setupNavigation()
        setupFabMenu()
        observeVoiceCommands()
    }

    // ── Navigation setup ──────────────────────────────────────────────────────

    private fun setupNavigation() {
        lifecycleScope.launch {
            val settings = userSettingsRepository.getSettingsOnce()
            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navController = navHostFragment.navController

            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
            navGraph.setStartDestination(
                if (settings?.hasCompletedOnboarding == true) R.id.nav_home
                else R.id.nav_onboarding
            )
            navController.graph = navGraph
            binding.bottomNavigation.setupWithNavController(navController)
            binding.bottomNavigation.setOnItemSelectedListener { item ->
                if (item.itemId == navController.currentDestination?.id) return@setOnItemSelectedListener true
                navController.navigate(item.itemId)
                true
            }

            // Proper back navigation with exit confirmation
            onBackPressedDispatcher.addCallback(this@MainActivity) {
                val currentId = navController.currentDestination?.id

                val topLevelIds = setOf(
                    R.id.nav_home,
                    R.id.nav_disasters,
                    R.id.nav_first_aid,
                    R.id.nav_tips,
                    R.id.nav_settings
                )

                if (currentId in topLevelIds) {
                    // Any top-level screen — show exit confirmation
                    DialogHelper.showExitConfirmation(this@MainActivity) {
                        finish()
                    }
                } else {
                    // On a detail/sub screen — try to pop back
                    if (!navController.popBackStack()) {
                        // Nothing to pop (shouldn't normally happen), show exit dialog as fallback
                        DialogHelper.showExitConfirmation(this@MainActivity) {
                            finish()
                        }
                    }
                }
            }

            binding.swipeNavigationLayout.attach(
                navController = navController,
                onTabSwiped = { destinationId ->
                    syncBottomNavSelection(destinationId)
                }
            )

            navController.addOnDestinationChangedListener { _, destination, _ ->
                collapseFabMenu()
                val isHiddenDest = destination.id in fabHiddenDestinations
                val isOnContactsScreen = destination.id == R.id.nav_emergency_contacts
                binding.bottomNavigation.visibility =
                    if (destination.id == R.id.nav_onboarding) View.GONE else View.VISIBLE
                binding.fabMain.visibility = if (isHiddenDest) View.GONE else View.VISIBLE
                updateFabRowAvailability(isOnContactsScreen)
                syncBottomNavSelection(destination.id)
            }
        }
    }

    private fun syncBottomNavSelection(destinationId: Int) {
        val ownerTab = when (destinationId) {
            R.id.nav_home             -> R.id.nav_home
            R.id.nav_disasters,
            R.id.nav_disaster_detail  -> R.id.nav_disasters
            R.id.nav_first_aid,
            R.id.nav_first_aid_detail -> R.id.nav_first_aid
            R.id.nav_tips,
            R.id.nav_tip_detail       -> R.id.nav_tips
            R.id.nav_settings         -> R.id.nav_settings
            else                      -> null
        }
        if (ownerTab != null) {
            binding.bottomNavigation.menu.findItem(ownerTab)?.isChecked = true
        } else {
            binding.bottomNavigation.clearSelection()
        }
    }

    // ── FAB speed-dial ────────────────────────────────────────────────────────

    private var emergencyRowSuppressed = false
    private var addContactRowAvailable = false
    private var isOnContactsScreen = false

    private fun updateFabRowAvailability(onContactsScreen: Boolean) {
        emergencyRowSuppressed = onContactsScreen
        isOnContactsScreen = onContactsScreen
        addContactRowAvailable = false
    }

    private fun setupFabMenu() {
        binding.fabMain.setOnClickListener {
            if (isFabMenuOpen) collapseFabMenu() else expandFabMenu()
        }
        binding.fabScrim.setOnClickListener { collapseFabMenu() }

        binding.fabVoiceCommand.setOnClickListener {
            collapseFabMenu()
            if (supportFragmentManager.findFragmentByTag(VoiceCommandBottomSheet.TAG) == null) {
                VoiceCommandBottomSheet().show(supportFragmentManager, VoiceCommandBottomSheet.TAG)
            }
        }
        binding.fabEmergency.setOnClickListener {
            collapseFabMenu()
            if (navController.currentDestination?.id != R.id.nav_emergency_contacts) {
                navController.navigate(R.id.nav_emergency_contacts)
            }
        }
        binding.fabAddContact.setOnClickListener {
            collapseFabMenu()
            supportFragmentManager.setFragmentResult(REQUEST_ADD_CONTACT, Bundle.EMPTY)
        }

        supportFragmentManager.setFragmentResultListener(
            EmergencyContactsFragment.RESULT_CONTACTS_TAB, this
        ) { _, bundle ->
            val isCustomTab =
                bundle.getInt(EmergencyContactsFragment.KEY_SELECTED_TAB, 0) == 1
            addContactRowAvailable = isOnContactsScreen && isCustomTab
        }
    }

    private fun expandFabMenu() {
        isFabMenuOpen = true
        binding.fabMain.animate().rotation(45f).setDuration(200).start()
        binding.fabScrim.visibility = View.VISIBLE
        binding.fabScrim.animate().alpha(1f).setDuration(200).start()

        animateChildIn(binding.fabVoiceRow)
        if (!emergencyRowSuppressed) animateChildIn(binding.fabEmergencyRow)
        if (addContactRowAvailable) animateChildIn(binding.fabAddContactRow)
    }

    private fun collapseFabMenu() {
        if (!isFabMenuOpen &&
            binding.fabVoiceRow.visibility == View.GONE &&
            binding.fabEmergencyRow.visibility == View.GONE &&
            binding.fabAddContactRow.visibility == View.GONE) return

        isFabMenuOpen = false
        binding.fabMain.animate().rotation(0f).setDuration(200).start()
        binding.fabScrim.animate().alpha(0f).setDuration(200).withEndAction {
            binding.fabScrim.visibility = View.GONE
        }.start()
        animateChildOut(binding.fabVoiceRow)
        animateChildOut(binding.fabEmergencyRow)
        animateChildOut(binding.fabAddContactRow)
    }

    private fun animateChildIn(row: View) {
        row.visibility = View.VISIBLE
        row.alpha = 0f
        row.translationY = 24f
        row.animate().alpha(1f).translationY(0f).setDuration(200).start()
    }

    private fun animateChildOut(row: View) {
        row.animate().alpha(0f).translationY(24f).setDuration(150).withEndAction {
            row.visibility = View.GONE
            row.translationY = 0f
        }.start()
    }

    // ── Voice command execution ───────────────────────────────────────────────

    private fun observeVoiceCommands() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                voiceViewModel.commandEvent.collect { command ->
                    executeVoiceCommand(command)
                }
            }
        }
    }

    private fun navigateToScreenWithTab(destinationId: Int, tab: String) {
        if (navController.currentDestination?.id == destinationId) {
            supportFragmentManager.setFragmentResult(
                REQUEST_SWITCH_TAB,
                Bundle().apply { putString(KEY_TAB_LABEL, tab) }
            )
        } else {
            navController.navigate(
                destinationId,
                Bundle().apply { putString(ARG_VOICE_TAB, tab) }
            )
        }
    }

    private fun executeVoiceCommand(command: VoiceCommand) {
        when (command) {
            is VoiceCommand.NavigateHome      -> navController.navigate(R.id.nav_home)
            is VoiceCommand.NavigateDisasters -> navController.navigate(R.id.nav_disasters)
            is VoiceCommand.NavigateFirstAid  -> navController.navigate(R.id.nav_first_aid)
            is VoiceCommand.NavigateTips      -> navController.navigate(R.id.nav_tips)
            is VoiceCommand.NavigateSettings  -> navController.navigate(R.id.nav_settings)
            is VoiceCommand.NavigateContacts  -> navController.navigate(R.id.nav_emergency_contacts)

            is VoiceCommand.NavigateDisastersTab -> {
                navigateToScreenWithTab(R.id.nav_disasters, command.tab)
            }
            is VoiceCommand.NavigateTipsTab -> {
                navigateToScreenWithTab(R.id.nav_tips, command.tab)
            }

            is VoiceCommand.OpenDisasterDetail -> {
                safeNavigateToDisasterDetail(command.disasterId, command.disasterName)
            }
            is VoiceCommand.OpenDisasterPhaseResolved -> {
                safeNavigateToDisasterDetail(
                    command.disasterId, command.disasterName, command.phase
                )
            }
            is VoiceCommand.OpenFirstAidDetail -> {
                safeNavigateToFirstAidDetail(command.categoryId, command.categoryName)
            }
            is VoiceCommand.OpenTipDetailResolved -> {
                safeNavigateToTipDetail(command.disasterId, command.disasterName)
            }

            is VoiceCommand.OpenDisaster         -> navController.navigate(R.id.nav_disasters)
            is VoiceCommand.OpenDisasterPhase    -> navController.navigate(R.id.nav_disasters)
            is VoiceCommand.OpenFirstAidCategory -> navController.navigate(R.id.nav_first_aid)
            is VoiceCommand.OpenTipDetail        -> navController.navigate(R.id.nav_tips)

            is VoiceCommand.ListAllDisasters -> {
                navigateToScreenWithTab(R.id.nav_disasters, "all")
            }
            is VoiceCommand.ListAllFirstAid  -> navController.navigate(R.id.nav_first_aid)
            is VoiceCommand.ListAllTips -> {
                navigateToScreenWithTab(R.id.nav_tips, "all")
            }

            is VoiceCommand.SearchDisaster -> {
                navController.navigate(R.id.nav_disasters,
                    Bundle().apply { putString(ARG_VOICE_QUERY, command.query) })
            }
            is VoiceCommand.SearchFirstAid -> {
                navController.navigate(R.id.nav_first_aid,
                    Bundle().apply { putString(ARG_VOICE_QUERY, command.query) })
            }
            is VoiceCommand.GeneralSearch -> {
                navController.navigate(R.id.nav_disasters,
                    Bundle().apply { putString(ARG_VOICE_QUERY, command.query) })
            }

            is VoiceCommand.GoBack -> navController.popBackStack()

            is VoiceCommand.ScrollUp -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_SCROLL,
                    Bundle().apply { putString(KEY_SCROLL, SCROLL_UP) }
                )
            }
            is VoiceCommand.ScrollDown -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_SCROLL,
                    Bundle().apply { putString(KEY_SCROLL, SCROLL_DOWN) }
                )
            }

            is VoiceCommand.ReadContent -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_TTS, Bundle().apply { putString(KEY_TTS, TTS_READ) })
            }
            is VoiceCommand.StopReading -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_TTS, Bundle().apply { putString(KEY_TTS, TTS_STOP) })
            }
            is VoiceCommand.PauseReading -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_TTS, Bundle().apply { putString(KEY_TTS, TTS_PAUSE) })
            }
            is VoiceCommand.ResumeReading -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_TTS, Bundle().apply { putString(KEY_TTS, TTS_RESUME) })
            }

            is VoiceCommand.SwitchTab -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_SWITCH_TAB,
                    Bundle().apply { putString(KEY_TAB_LABEL, command.tabLabel) }
                )
            }

            is VoiceCommand.CallEmergency -> navController.navigate(R.id.nav_emergency_contacts)
            is VoiceCommand.CallContact -> {
                navController.navigate(R.id.nav_emergency_contacts,
                    Bundle().apply { putString(ARG_CONTACT_NAME, command.contactName) })
            }

            is VoiceCommand.GetRandomTip,
            is VoiceCommand.GetNextTip -> navController.navigate(R.id.nav_tips)

            is VoiceCommand.Unknown -> Unit
        }
    }

    // ── Safe navigation helpers ───────────────────────────────────────────────

    private fun safeNavigateToDisasterDetail(
        disasterId: Long, disasterName: String, phase: String? = null
    ) {
        val args = Bundle().apply {
            putLong("disasterId", disasterId)
            putString("disasterName", disasterName)
            if (phase != null) putString(ARG_VOICE_PHASE, phase)
        }
        when (navController.currentDestination?.id) {
            R.id.nav_disasters -> navController.navigate(R.id.action_disasters_to_detail, args)
            R.id.nav_disaster_detail -> {
                navController.popBackStack(R.id.nav_disasters, false)
                navController.navigate(R.id.action_disasters_to_detail, args)
            }
            else -> {
                navController.navigate(R.id.nav_disasters)
                navController.navigate(R.id.action_disasters_to_detail, args)
            }
        }
    }

    private fun safeNavigateToFirstAidDetail(categoryId: Long, categoryName: String) {
        val args = Bundle().apply {
            putLong("categoryId", categoryId)
            putString("categoryName", categoryName)
        }
        when (navController.currentDestination?.id) {
            R.id.nav_first_aid -> navController.navigate(R.id.action_first_aid_to_detail, args)
            R.id.nav_first_aid_detail -> {
                navController.popBackStack(R.id.nav_first_aid, false)
                navController.navigate(R.id.action_first_aid_to_detail, args)
            }
            else -> {
                navController.navigate(R.id.nav_first_aid)
                navController.navigate(R.id.action_first_aid_to_detail, args)
            }
        }
    }

    private fun safeNavigateToTipDetail(disasterId: Long, disasterName: String) {
        val args = Bundle().apply {
            putLong("disasterId", disasterId)
            putString("disasterName", disasterName)
        }
        when (navController.currentDestination?.id) {
            R.id.nav_tips -> navController.navigate(R.id.action_tips_to_tip_detail, args)
            R.id.nav_tip_detail -> {
                navController.popBackStack(R.id.nav_tips, false)
                navController.navigate(R.id.action_tips_to_tip_detail, args)
            }
            else -> {
                navController.navigate(R.id.nav_tips)
                navController.navigate(R.id.action_tips_to_tip_detail, args)
            }
        }
    }

    companion object {
        const val REQUEST_SCROLL      = "request_scroll"
        const val KEY_SCROLL          = "key_scroll"
        const val SCROLL_UP           = "scroll_up"
        const val SCROLL_DOWN         = "scroll_down"

        const val REQUEST_TTS         = "request_tts"
        const val KEY_TTS             = "key_tts"
        const val TTS_READ            = "tts_read"
        const val TTS_STOP            = "tts_stop"
        const val TTS_PAUSE           = "tts_pause"
        const val TTS_RESUME          = "tts_resume"

        const val REQUEST_SWITCH_TAB  = "request_switch_tab"
        const val KEY_TAB_LABEL       = "key_tab_label"

        const val REQUEST_ADD_CONTACT = "request_add_contact"

        const val ARG_VOICE_PHASE     = "voicePhase"
        const val ARG_VOICE_QUERY     = "arg_voice_query"
        const val ARG_VOICE_TAB       = "voiceTab"
        const val ARG_CONTACT_NAME    = "arg_contact_name"
    }
}