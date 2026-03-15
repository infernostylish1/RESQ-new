package com.resq.app

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.databinding.ActivityMainBinding
import com.resq.app.ui.fragment.DisastersFragmentDirections
import com.resq.app.ui.voice.VoiceCommandBottomSheet
import com.resq.app.ui.voice.VoiceCommandViewModel
import com.resq.app.util.VoiceCommand
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    @Inject
    lateinit var userSettingsRepository: UserSettingsRepository

    private val voiceViewModel: VoiceCommandViewModel by viewModels()

    private var isFabMenuOpen = false

    private val fabHiddenDestinations = setOf(
        R.id.nav_onboarding,
        R.id.nav_settings
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupNavigation()
        setupFabMenu()
        observeVoiceCommands()
    }

    // ── Navigation ────────────────────────────────────────────────────────────

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

            navController.addOnDestinationChangedListener { _, destination, _ ->
                collapseFabMenu()

                val isHiddenDest    = destination.id in fabHiddenDestinations
                val isOnContactsScreen = destination.id == R.id.nav_emergency_contacts

                binding.bottomNavigation.visibility =
                    if (destination.id == R.id.nav_onboarding) View.GONE else View.VISIBLE

                binding.fabMain.visibility =
                    if (isHiddenDest) View.GONE else View.VISIBLE

                updateEmergencyRowAvailability(isOnContactsScreen)
                syncBottomNavSelection(destination.id)
            }
        }
    }

    private fun syncBottomNavSelection(destinationId: Int) {
        val ownerTab = when (destinationId) {
            R.id.nav_home -> R.id.nav_home
            R.id.nav_disasters,
            R.id.nav_disaster_detail  -> R.id.nav_disasters
            R.id.nav_first_aid,
            R.id.nav_first_aid_detail -> R.id.nav_first_aid
            R.id.nav_tips,
            R.id.nav_tip_detail       -> R.id.nav_tips
            else -> null
        }
        ownerTab?.let { binding.bottomNavigation.menu.findItem(it)?.isChecked = true }
    }

    // ── FAB speed-dial ────────────────────────────────────────────────────────

    private var emergencyRowSuppressed = false

    private fun updateEmergencyRowAvailability(onContactsScreen: Boolean) {
        emergencyRowSuppressed = onContactsScreen
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
    }

    private fun expandFabMenu() {
        isFabMenuOpen = true
        binding.fabMain.animate().rotation(45f).setDuration(200).start()
        binding.fabScrim.visibility = View.VISIBLE
        binding.fabScrim.animate().alpha(1f).setDuration(200).start()
        animateChildIn(binding.fabVoiceRow)
        if (!emergencyRowSuppressed) animateChildIn(binding.fabEmergencyRow)
    }

    private fun collapseFabMenu() {
        if (!isFabMenuOpen &&
            binding.fabVoiceRow.visibility == View.GONE &&
            binding.fabEmergencyRow.visibility == View.GONE) return

        isFabMenuOpen = false
        binding.fabMain.animate().rotation(0f).setDuration(200).start()
        binding.fabScrim.animate().alpha(0f).setDuration(200).withEndAction {
            binding.fabScrim.visibility = View.GONE
        }.start()
        animateChildOut(binding.fabVoiceRow)
        animateChildOut(binding.fabEmergencyRow)
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

    private fun executeVoiceCommand(command: VoiceCommand) {
        when (command) {

            // ── Navigation ────────────────────────────────────────────────────
            is VoiceCommand.NavigateHome      -> navController.navigate(R.id.nav_home)
            is VoiceCommand.NavigateDisasters -> navController.navigate(R.id.nav_disasters)
            is VoiceCommand.NavigateFirstAid  -> navController.navigate(R.id.nav_first_aid)
            is VoiceCommand.NavigateTips      -> navController.navigate(R.id.nav_tips)
            is VoiceCommand.NavigateSettings  -> navController.navigate(R.id.nav_settings)
            is VoiceCommand.NavigateContacts  -> navController.navigate(R.id.nav_emergency_contacts)

            // ── Deep-link: go directly to disaster detail ─────────────────────
            // VoiceCommandViewModel has already resolved the id from the DB.
            is VoiceCommand.OpenDisasterDetail -> {
                val action = DisastersFragmentDirections.actionDisastersToDetail(
                    disasterId   = command.disasterId,
                    disasterName = command.disasterName
                )
                // Navigate to the list first if we're not already in the
                // disasters back-stack, then to detail — or navigate directly
                // using the global-safe approach below.
                safeNavigateToDisasterDetail(command.disasterId, command.disasterName)
            }

            // ── Deep-link: go directly to first aid detail ────────────────────
            is VoiceCommand.OpenFirstAidDetail -> {
                safeNavigateToFirstAidDetail(command.categoryId, command.categoryName)
            }

            // ── Legacy list-level commands (fallback if DB lookup failed) ─────
            // VoiceCommandViewModel already converts these before they reach here,
            // but we keep the handlers in case of future code paths.
            is VoiceCommand.OpenDisaster -> {
                navController.navigate(R.id.nav_disasters)
            }
            is VoiceCommand.OpenFirstAidCategory -> {
                navController.navigate(R.id.nav_first_aid)
            }

            // ── Search ────────────────────────────────────────────────────────
            is VoiceCommand.SearchDisaster -> {
                val bundle = Bundle().apply { putString(ARG_VOICE_QUERY, command.query) }
                navController.navigate(R.id.nav_disasters, bundle)
            }
            is VoiceCommand.SearchFirstAid -> {
                val bundle = Bundle().apply { putString(ARG_VOICE_QUERY, command.query) }
                navController.navigate(R.id.nav_first_aid, bundle)
            }
            is VoiceCommand.GeneralSearch -> {
                val bundle = Bundle().apply { putString(ARG_VOICE_QUERY, command.query) }
                navController.navigate(R.id.nav_disasters, bundle)
            }

            // ── App control ───────────────────────────────────────────────────
            is VoiceCommand.GoBack -> navController.popBackStack()
            is VoiceCommand.ScrollUp -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_SCROLL, Bundle().apply { putString(KEY_SCROLL, SCROLL_UP) }
                )
            }
            is VoiceCommand.ScrollDown -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_SCROLL, Bundle().apply { putString(KEY_SCROLL, SCROLL_DOWN) }
                )
            }

            // ── TTS ───────────────────────────────────────────────────────────
            is VoiceCommand.ReadContent -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_TTS, Bundle().apply { putString(KEY_TTS, TTS_READ) }
                )
            }
            is VoiceCommand.StopReading -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_TTS, Bundle().apply { putString(KEY_TTS, TTS_STOP) }
                )
            }
            is VoiceCommand.PauseReading -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_TTS, Bundle().apply { putString(KEY_TTS, TTS_PAUSE) }
                )
            }
            is VoiceCommand.ResumeReading -> {
                supportFragmentManager.setFragmentResult(
                    REQUEST_TTS, Bundle().apply { putString(KEY_TTS, TTS_RESUME) }
                )
            }

            // ── Emergency / contacts ──────────────────────────────────────────
            is VoiceCommand.CallEmergency -> navController.navigate(R.id.nav_emergency_contacts)
            is VoiceCommand.CallContact -> {
                val bundle = Bundle().apply { putString(ARG_CONTACT_NAME, command.contactName) }
                navController.navigate(R.id.nav_emergency_contacts, bundle)
            }

            // ── Tips ──────────────────────────────────────────────────────────
            is VoiceCommand.GetRandomTip,
            is VoiceCommand.GetNextTip -> navController.navigate(R.id.nav_tips)

            // ── Unknown ───────────────────────────────────────────────────────
            is VoiceCommand.Unknown -> Unit
        }
    }

    /**
     * Navigates to [nav_disaster_detail] regardless of which screen is currently shown.
     *
     * Uses the global action if already in the disasters back-stack, otherwise
     * navigates to the list first then immediately to detail so the back-stack
     * is correct (Back → list → previous screen).
     */
    private fun safeNavigateToDisasterDetail(disasterId: Long, disasterName: String) {
        val args = Bundle().apply {
            putLong("disasterId", disasterId)
            putString("disasterName", disasterName)
        }
        val currentDest = navController.currentDestination?.id

        when (currentDest) {
            // Already on the disasters list — navigate directly to detail
            R.id.nav_disasters -> {
                navController.navigate(R.id.action_disasters_to_detail, args)
            }
            // Already on a disaster detail — pop back to list then navigate
            R.id.nav_disaster_detail -> {
                navController.popBackStack(R.id.nav_disasters, false)
                navController.navigate(R.id.action_disasters_to_detail, args)
            }
            // Anywhere else — go to disasters list first, then detail
            else -> {
                navController.navigate(R.id.nav_disasters)
                navController.navigate(R.id.action_disasters_to_detail, args)
            }
        }
    }

    /**
     * Navigates to [nav_first_aid_detail] regardless of which screen is currently shown.
     * Same pattern as [safeNavigateToDisasterDetail].
     */
    private fun safeNavigateToFirstAidDetail(categoryId: Long, categoryName: String) {
        val args = Bundle().apply {
            putLong("categoryId", categoryId)
            putString("categoryName", categoryName)
        }
        val currentDest = navController.currentDestination?.id

        when (currentDest) {
            R.id.nav_first_aid -> {
                navController.navigate(R.id.action_first_aid_to_detail, args)
            }
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

    companion object {
        const val REQUEST_SCROLL = "request_scroll"
        const val KEY_SCROLL    = "key_scroll"
        const val SCROLL_UP     = "scroll_up"
        const val SCROLL_DOWN   = "scroll_down"

        const val REQUEST_TTS   = "request_tts"
        const val KEY_TTS       = "key_tts"
        const val TTS_READ      = "tts_read"
        const val TTS_STOP      = "tts_stop"
        const val TTS_PAUSE     = "tts_pause"
        const val TTS_RESUME    = "tts_resume"

        const val ARG_VOICE_QUERY  = "arg_voice_query"
        const val ARG_CONTACT_NAME = "arg_contact_name"
    }
}