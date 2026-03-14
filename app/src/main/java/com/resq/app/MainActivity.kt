package com.resq.app

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.databinding.ActivityMainBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    @Inject
    lateinit var userSettingsRepository: UserSettingsRepository

    // Destinations where the emergency FAB must NOT appear
    private val fabHiddenDestinations = setOf(
        R.id.nav_onboarding,
        R.id.nav_settings,
        R.id.nav_emergency_contacts
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
        setupEmergencyFab() // NEW
    }

    private fun setupNavigation() {
        lifecycleScope.launch {
            val settings = userSettingsRepository.getSettingsOnce()

            val navHostFragment = supportFragmentManager
                .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
            navController = navHostFragment.navController
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

            navGraph.setStartDestination(
                if (settings?.hasCompletedOnboarding == true) R.id.nav_home else R.id.nav_onboarding
            )
            navController.graph = navGraph

            binding.bottomNavigation.setupWithNavController(navController)

            // Override item selected so bottom nav works even when on Emergency Contacts
            binding.bottomNavigation.setOnItemSelectedListener { item ->
                val currentDestination = navController.currentDestination?.id

                if (currentDestination == R.id.nav_emergency_contacts) {
                    // Pop Emergency Contacts off the back stack first, then navigate
                    navController.popBackStack()
                }

                if (item.itemId == navController.currentDestination?.id) {
                    // Already on that destination, do nothing
                    true
                } else {
                    navController.navigate(item.itemId)
                    true
                }
            }

            navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.nav_onboarding -> {
                        binding.bottomNavigation.visibility = View.GONE
                    }
                    else -> {
                        binding.bottomNavigation.visibility = View.VISIBLE

                        val ownerTabId = resolveOwnerTab(destination.id)
                        if (ownerTabId != null) {
                            binding.bottomNavigation.menu.findItem(ownerTabId)?.isChecked = true
                        }
                    }
                }

                updateEmergencyFabVisibility(destination.id)
            }
        }
    }

    private fun setupEmergencyFab() {
        binding.fabEmergency.setOnClickListener {
            if (navController.currentDestination?.id != R.id.nav_emergency_contacts) {
                navController.navigate(R.id.nav_emergency_contacts)
            }
        }
    }

    // NEW — show with animation on allowed screens, hide on excluded ones
    private fun updateEmergencyFabVisibility(destinationId: Int) {
        if (destinationId in fabHiddenDestinations) {
            binding.fabEmergency.hide()
        } else {
            binding.fabEmergency.show()
        }
    }

    private fun resolveOwnerTab(destinationId: Int): Int? = when (destinationId) {
        R.id.nav_disaster_detail  -> R.id.nav_disasters
        R.id.nav_tip_detail       -> R.id.nav_tips
        R.id.nav_first_aid_detail -> R.id.nav_first_aid
        else -> null
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}