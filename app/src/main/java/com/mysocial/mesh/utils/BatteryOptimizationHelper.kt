package com.mysocial.mesh.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast

object BatteryOptimizationHelper {

    /**
     * Checks if the app is currently whitelisted / exempted from Android Doze battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
            } else {
                true
            }
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Launches the system dialog or settings screen requesting battery optimization exemption.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val packageName = context.packageName
        try {
            val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(directIntent)
        } catch (_: Exception) {
            try {
                val listIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(listIntent)
            } catch (_: Exception) {
                try {
                    val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(appDetailsIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Could not open battery settings directly", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getStatusTitle(context: Context): String {
        return if (isIgnoringBatteryOptimizations(context)) {
            "Continuous Background Relay Active"
        } else {
            "Battery Optimization Enabled (Doze Mode)"
        }
    }

    fun getStatusDescription(context: Context): String {
        return if (isIgnoringBatteryOptimizations(context)) {
            "MySocial is exempted from aggressive battery killing. Physical BLE & Wi-Fi mesh relays will continue syncing hops even when your screen is locked."
        } else {
            "Aggressive OEM power management may kill background radio sync when your phone enters deep sleep. Exempt MySocial to guarantee 24/7 background packet hopping."
        }
    }
}

