package com.resq.app

import android.animation.AnimatorSet
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

    // Tracks whether the FAB speed-dial menu is open
    private var isFabMenuOpen = false

    // Destinations where ALL fabs (including main trigger) are hidden
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
                // Always collapse the FAB menu when navigating anywhere
                collapseFabMenu()

                val isHiddenDest = destination.id in fabHiddenDestinations
                val isOnContactsScreen = destination.id == R.id.nav_emergency_contacts

                // Bottom nav
                binding.bottomNavigation.visibility =
                    if (destination.id == R.id.nav_onboarding) View.GONE else View.VISIBLE

                // Main trigger FAB — hide only on hidden destinations
                binding.fabMain.visibility =
                    if (isHiddenDest) View.GONE else View.VISIBLE

                // Emergency child row — hide when on contacts screen or hidden destinations
                binding.fabEmergencyRow.visibility =
                    if (isHiddenDest || isOnContactsScreen) View.GONE else View.GONE
                // Note: child rows start GONE and are shown only when menu is open;
                // we just update the flag so openFabMenu() respects it.
                updateEmergencyRowAvailability(isOnContactsScreen)

                syncBottomNavSelection(destination.id)
            }
        }
    }

    private fun syncBottomNavSelection(destinationId: Int) {
        val ownerTab = when (destinationId) {
            R.id.nav_home -> R.id.nav_home
            R.id.nav_disasters,
            R.id.nav_disaster_detail -> R.id.nav_disasters
            R.id.nav_first_aid,
            R.id.nav_first_aid_detail -> R.id.nav_first_aid
            R.id.nav_tips,
            R.id.nav_tip_detail -> R.id.nav_tips
            else -> null
        }
        ownerTab?.let {
            binding.bottomNavigation.menu.findItem(it)?.isChecked = true
        }
    }

    // ── FAB speed-dial ────────────────────────────────────────────────────────

    // True when the emergency row should NOT be shown even if menu opens
    private var emergencyRowSuppressed = false

    private fun updateEmergencyRowAvailability(onContactsScreen: Boolean) {
        emergencyRowSuppressed = onContactsScreen
    }

    private fun setupFabMenu() {
        // Main trigger FAB toggles the speed-dial
        binding.fabMain.setOnClickListener {
            if (isFabMenuOpen) collapseFabMenu() else expandFabMenu()
        }

        // Scrim tap collapses the menu
        binding.fabScrim.setOnClickListener {
            collapseFabMenu()
        }

        // Voice child FAB
        binding.fabVoiceCommand.setOnClickListener {
            collapseFabMenu()
            if (supportFragmentManager.findFragmentByTag(VoiceCommandBottomSheet.TAG) == null) {
                VoiceCommandBottomSheet()
                    .show(supportFragmentManager, VoiceCommandBottomSheet.TAG)
            }
        }

        // Emergency child FAB
        binding.fabEmergency.setOnClickListener {
            collapseFabMenu()
            if (navController.currentDestination?.id != R.id.nav_emergency_contacts) {
                navController.navigate(R.id.nav_emergency_contacts)
            }
        }
    }

    private fun expandFabMenu() {
        isFabMenuOpen = true

        // Rotate main FAB icon 45° to hint "close"
        binding.fabMain.animate().rotation(45f).setDuration(200).start()

        // Show scrim
        binding.fabScrim.visibility = View.VISIBLE
        binding.fabScrim.animate().alpha(1f).setDuration(200).start()

        // Always show voice row
        animateChildIn(binding.fabVoiceRow)

        // Show emergency row only if not suppressed
        if (!emergencyRowSuppressed) {
            animateChildIn(binding.fabEmergencyRow)
        }
    }

    private fun collapseFabMenu() {
        if (!isFabMenuOpen &&
            binding.fabVoiceRow.visibility == View.GONE &&
            binding.fabEmergencyRow.visibility == View.GONE) return

        isFabMenuOpen = false

        // Rotate main FAB icon back
        binding.fabMain.animate().rotation(0f).setDuration(200).start()

        // Hide scrim
        binding.fabScrim.animate().alpha(0f).setDuration(200).withEndAction {
            binding.fabScrim.visibility = View.GONE
        }.start()

        // Hide child rows
        animateChildOut(binding.fabVoiceRow)
        animateChildOut(binding.fabEmergencyRow)
    }

    private fun animateChildIn(row: View) {
        row.visibility = View.VISIBLE
        row.alpha = 0f
        row.translationY = 24f
        row.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .start()
    }

    private fun animateChildOut(row: View) {
        row.animate()
            .alpha(0f)
            .translationY(24f)
            .setDuration(150)
            .withEndAction {
                row.visibility = View.GONE
                row.translationY = 0f
            }
            .start()
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
            is VoiceCommand.NavigateHome -> {
                navController.navigate(R.id.nav_home)
            }
            is VoiceCommand.NavigateDisasters -> {
                navController.navigate(R.id.nav_disasters)
            }
            is VoiceCommand.NavigateFirstAid -> {
                navController.navigate(R.id.nav_first_aid)
            }
            is VoiceCommand.NavigateTips -> {
                navController.navigate(R.id.nav_tips)
            }
            is VoiceCommand.NavigateSettings -> {
                navController.navigate(R.id.nav_settings)
            }
            is VoiceCommand.NavigateContacts -> {
                navController.navigate(R.id.nav_emergency_contacts)
            }

            // ── Disaster deep-link ────────────────────────────────────────────
            is VoiceCommand.OpenDisaster -> {
                val bundle = Bundle().apply {
                    putString(ARG_VOICE_QUERY, command.disasterName)
                }
                navController.navigate(R.id.nav_disasters, bundle)
            }

            // ── First-aid deep-link ───────────────────────────────────────────
            is VoiceCommand.OpenFirstAidCategory -> {
                val bundle = Bundle().apply {
                    putString(ARG_VOICE_QUERY, command.categoryName)
                }
                navController.navigate(R.id.nav_first_aid, bundle)
            }

            // ── Search ────────────────────────────────────────────────────────
            is VoiceCommand.SearchDisaster -> {
                val bundle = Bundle().apply {
                    putString(ARG_VOICE_QUERY, command.query)
                }
                navController.navigate(R.id.nav_disasters, bundle)
            }
            is VoiceCommand.SearchFirstAid -> {
                val bundle = Bundle().apply {
                    putString(ARG_VOICE_QUERY, command.query)
                }
                navController.navigate(R.id.nav_first_aid, bundle)
            }
            is VoiceCommand.GeneralSearch -> {
                val bundle = Bundle().apply {
                    putString(ARG_VOICE_QUERY, command.query)
                }
                navController.navigate(R.id.nav_disasters, bundle)
            }

            // ── App control ───────────────────────────────────────────────────
            is VoiceCommand.GoBack -> {
                navController.popBackStack()
            }
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
            is VoiceCommand.CallEmergency -> {
                navController.navigate(R.id.nav_emergency_contacts)
            }
            is VoiceCommand.CallContact -> {
                val bundle = Bundle().apply {
                    putString(ARG_CONTACT_NAME, command.contactName)
                }
                navController.navigate(R.id.nav_emergency_contacts, bundle)
            }

            // ── Tips ──────────────────────────────────────────────────────────
            is VoiceCommand.GetRandomTip,
            is VoiceCommand.GetNextTip -> {
                navController.navigate(R.id.nav_tips)
            }

            // ── Unknown — do nothing ──────────────────────────────────────────
            is VoiceCommand.Unknown -> Unit
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