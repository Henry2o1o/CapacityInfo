package com.ph03nix_x.capacityinfo.fragments

import android.content.*
import android.os.Bundle
import androidx.preference.*
import com.ph03nix_x.capacityinfo.interfaces.DebugOptionsInterface
import com.ph03nix_x.capacityinfo.R
import com.ph03nix_x.capacityinfo.interfaces.ServiceInterface
import com.ph03nix_x.capacityinfo.utils.Utils.launchActivity
import com.ph03nix_x.capacityinfo.activities.SettingsActivity
import com.ph03nix_x.capacityinfo.interfaces.BillingInterface
import com.ph03nix_x.capacityinfo.utils.PreferencesKeys.IS_FORCIBLY_SHOW_RATE_THE_APP
import com.ph03nix_x.capacityinfo.utils.PreferencesKeys.IS_HIDE_DONATE
import com.ph03nix_x.capacityinfo.utils.Utils.isGooglePlay
import com.ph03nix_x.capacityinfo.utils.Utils.isInstalledGooglePlay

class DebugFragment : PreferenceFragmentCompat(), DebugOptionsInterface, ServiceInterface,
    BillingInterface {

    private lateinit var pref: SharedPreferences
    
    private var forciblyShowRateTheApp: SwitchPreferenceCompat? = null
    private var hideDonate: SwitchPreferenceCompat? = null
    private var addSetting: Preference? = null
    private var changeSetting: Preference? = null
    private var resetSetting: Preference? = null
    private var resetSettings: Preference? = null
    private var openSettings: Preference? = null

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        pref = PreferenceManager.getDefaultSharedPreferences(requireContext())

        addPreferencesFromResource(R.xml.debug_settings)

        forciblyShowRateTheApp = findPreference(IS_FORCIBLY_SHOW_RATE_THE_APP)

        hideDonate = findPreference(IS_HIDE_DONATE)

        addSetting = findPreference("add_setting")

        changeSetting = findPreference("change_setting")

        resetSetting = findPreference("reset_setting")

        resetSettings = findPreference("reset_settings")

        openSettings = findPreference("open_settings")

        forciblyShowRateTheApp?.isVisible = !isGooglePlay(requireContext())

        hideDonate?.isVisible = isInstalledGooglePlay

        addSetting?.setOnPreferenceClickListener {

            addSettingDialog(requireContext(), pref)

            true
        }

        changeSetting?.setOnPreferenceClickListener {

            changeSettingDialog(requireContext(), pref)

            true
        }

        resetSetting?.setOnPreferenceClickListener {

            resetSettingDialog(requireContext(), pref)

            true
        }

        resetSettings?.setOnPreferenceClickListener {

            resetSettingsDialog(requireContext(), pref)

            true
        }

        openSettings?.setOnPreferenceClickListener {

            launchActivity(requireContext(), SettingsActivity::class.java,
                arrayListOf(Intent.FLAG_ACTIVITY_NEW_TASK))

            true
        }
    }
}