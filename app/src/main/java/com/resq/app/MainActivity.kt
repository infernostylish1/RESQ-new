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

            navController.addOnDestinationChangedListener { _, destination, _ ->
                when (destination.id) {
                    R.id.nav_onboarding -> {
                        binding.bottomNavigation.visibility = View.GONE
                    }
                    else -> {
                        binding.bottomNavigation.visibility = View.VISIBLE

                        // Resolve which tab owns this destination so the
                        // correct nav icon stays highlighted on detail screens
                        val ownerTabId = resolveOwnerTab(destination.id)
                        if (ownerTabId != null) {
                            // Silence the re-selection listener so switching the
                            // selected item programmatically does not trigger
                            // a navigation event and duplicate back stack entries
                            binding.bottomNavigation.setOnItemSelectedListener(null)
                            binding.bottomNavigation.selectedItemId = ownerTabId
                            // Restore setupWithNavController behaviour
                            binding.bottomNavigation.setupWithNavController(navController)
                        }
                    }
                }
            }
        }
    }

    /**
     * Maps every detail/sub-screen destination back to the tab that owns it.
     * Returns null for top-level destinations that setupWithNavController already
     * handles correctly on its own.
     */
    private fun resolveOwnerTab(destinationId: Int): Int? = when (destinationId) {
        R.id.nav_disaster_detail -> R.id.nav_disasters
        R.id.nav_tip_detail      -> R.id.nav_tips
        R.id.nav_first_aid_detail -> R.id.nav_first_aid
        // nav_emergency_contacts can be reached from both Home and Settings,
        // so we do not force a tab highlight for it — leave it as-is.
        // Add any future detail fragments here following the same pattern.
        else -> null
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}