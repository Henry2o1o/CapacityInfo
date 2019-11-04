package com.ph03nix_x.capacityinfo.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager
import com.ph03nix_x.capacityinfo.Preferences
import com.ph03nix_x.capacityinfo.Utils
import com.ph03nix_x.capacityinfo.services.CapacityInfoService
import com.ph03nix_x.capacityinfo.services.isPowerConnected

class UnpluggedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {

        val pref = PreferenceManager.getDefaultSharedPreferences(context)

        when(intent?.action) {

            Intent.ACTION_POWER_DISCONNECTED -> {

                isPowerConnected = false

                if(CapacityInfoService.instance != null) context?.stopService(Intent(context, CapacityInfoService::class.java))

                if(pref.getBoolean(Preferences.IsEnableService.prefKey, true)) Utils.startService(context)
            }
        }
    }
}