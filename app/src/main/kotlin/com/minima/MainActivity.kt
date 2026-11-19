package com.minima

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.minima.receiver.PackageChangedReceiver
import com.minima.ui.home.HomeScreen
import com.minima.ui.home.HomeViewModel
import com.minima.ui.theme.MinimaTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * MainActivity serves as the primary home screen host for MINIMA.
 *
 * Annotated with [@AndroidEntryPoint] to enable Hilt dependency injection,
 * allowing [HomeViewModel] to be injected cleanly via [by viewModels()]
 * without manual factory boilerplate.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels()

    private val packageChangedReceiver = PackageChangedReceiver {
        homeViewModel.refreshInstalledApps()
    }

    private var isReceiverRegistered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Register receiver for package installs/uninstalls while MINIMA is running
        registerPackageReceiver()

        setContent {
            MinimaTheme {
                HomeScreen(viewModel = homeViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh usage statistics, installed applications, clock, and repeat-open awareness on resume
        homeViewModel.refreshUsageStats()
        homeViewModel.refreshInstalledApps()
        homeViewModel.refreshCurrentTime()
        homeViewModel.checkRepeatOpenAlert()
    }

    override fun onPause() {
        super.onPause()
        // Cancel any active countdown immediately when MINIMA loses focus or is backgrounded
        homeViewModel.cancelCountdown()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterPackageReceiver()
    }

    private fun registerPackageReceiver() {
        if (!isReceiverRegistered) {
            val filter = PackageChangedReceiver.createIntentFilter()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(packageChangedReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(packageChangedReceiver, filter)
            }
            isReceiverRegistered = true
        }
    }

    private fun unregisterPackageReceiver() {
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(packageChangedReceiver)
            } catch (_: IllegalArgumentException) {
                // Safely handle already unregistered case
            }
            isReceiverRegistered = false
        }
    }
}
