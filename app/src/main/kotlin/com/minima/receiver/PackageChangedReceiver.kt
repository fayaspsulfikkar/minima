package com.minima.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Dynamic [BroadcastReceiver] listening for package additions, removals, and updates.
 *
 * This enables the launcher to refresh its list immediately when the user installs
 * or uninstalls an app while MINIMA is running.
 */
class PackageChangedReceiver(
    private val onPackageChanged: () -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_PACKAGE_ADDED ||
            action == Intent.ACTION_PACKAGE_REMOVED ||
            action == Intent.ACTION_PACKAGE_REPLACED
        ) {
            onPackageChanged()
        }
    }

    companion object {
        /**
         * Creates an IntentFilter configured with the required package actions
         * and the mandatory "package" data scheme.
         */
        fun createIntentFilter(): IntentFilter {
            return IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        }
    }
}

