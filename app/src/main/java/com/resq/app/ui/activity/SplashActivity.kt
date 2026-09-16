package com.resq.app.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.resq.app.MainActivity
import com.resq.app.data.model.FontSize
import com.resq.app.data.repository.UserSettingsRepository
import com.resq.app.databinding.ActivitySplashBinding
import com.resq.app.util.FontSizeManager
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    @Inject lateinit var userSettingsRepository: UserSettingsRepository
    @Inject lateinit var fontSizeManager: FontSizeManager

    override fun attachBaseContext(newBase: Context) {
        val entryPoint = EntryPointAccessors.fromApplication(
            newBase.applicationContext,
            SplashActivityEntryPoint::class.java
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
    interface SplashActivityEntryPoint {
        fun userSettingsRepository(): UserSettingsRepository
        fun fontSizeManager(): FontSizeManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate custom layout
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            delay(SPLASH_DELAY)
            navigateToMain()
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    companion object {
        private const val SPLASH_DELAY = 2000L // 2 seconds
    }
}