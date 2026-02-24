package com.resq.app

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
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
            val navController = navHostFragment.navController
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)

            navGraph.setStartDestination(
                if (settings?.hasCompletedOnboarding == true) R.id.nav_home else R.id.nav_onboarding
            )
            navController.graph = navGraph

            binding.bottomNavigation.setupWithNavController(navController)

            navController.addOnDestinationChangedListener { _, destination, _ ->
                binding.bottomNavigation.visibility = when (destination.id) {
                    R.id.nav_onboarding -> View.GONE
                    else -> View.VISIBLE
                }
            }
        }
    }
}