package com.ph03nix_x.capacityinfo.interfaces

import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Context.NOTIFICATION_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.graphics.Color
import android.media.AudioAttributes
import android.os.BatteryManager
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.ph03nix_x.capacityinfo.MainApp.Companion.batteryIntent
import com.ph03nix_x.capacityinfo.MainApp.Companion.isGooglePlay
import com.ph03nix_x.capacityinfo.R
import com.ph03nix_x.capacityinfo.activities.MainActivity
import com.ph03nix_x.capacityinfo.helpers.ThemeHelper.isSystemDarkMode
import com.ph03nix_x.capacityinfo.services.CapacityInfoService
import com.ph03nix_x.capacityinfo.services.CloseNotificationBatteryStatusInformationService
import com.ph03nix_x.capacityinfo.services.DisableNotificationBatteryStatusInformationService
import com.ph03nix_x.capacityinfo.utilities.Constants.CHARGED_CHANNEL_ID
import com.ph03nix_x.capacityinfo.utilities.Constants.CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE
import com.ph03nix_x.capacityinfo.utilities.Constants.DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE
import com.ph03nix_x.capacityinfo.utilities.Constants.DISCHARGED_CHANNEL_ID
import com.ph03nix_x.capacityinfo.utilities.Constants.FULLY_CHARGED_CHANNEL_ID
import com.ph03nix_x.capacityinfo.utilities.Constants.OPEN_APP_REQUEST_CODE
import com.ph03nix_x.capacityinfo.utilities.Constants.OVERHEAT_OVERCOOL_CHANNEL_ID
import com.ph03nix_x.capacityinfo.utilities.Constants.SERVICE_CHANNEL_ID
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys.IS_BYPASS_DND
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys.IS_CAPACITY_IN_WH
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys.IS_SERVICE_TIME
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys.IS_SHOW_BATTERY_INFORMATION
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys.IS_SHOW_BATTERY_LEVEL_IN_STATUS_BAR
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys.IS_SHOW_EXPANDED_NOTIFICATION
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys.NUMBER_OF_CYCLES
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys.OVERCOOL_DEGREES
import com.ph03nix_x.capacityinfo.utilities.PreferencesKeys.OVERHEAT_DEGREES
import java.text.DecimalFormat
import androidx.core.net.toUri

@SuppressLint("StaticFieldLeak")
interface NotificationInterface : BatteryInfoInterface, PremiumInterface {

    companion object {

        const val NOTIFICATION_SERVICE_ID = 101
        const val NOTIFICATION_BATTERY_STATUS_ID = 102
        const val NOTIFICATION_FULLY_CHARGED_ID = 103
        const val NOTIFICATION_BATTERY_OVERHEAT_OVERCOOL_ID = 104

        private lateinit var channelId: String

        var notificationBuilder: NotificationCompat.Builder? = null
        var notificationManager: NotificationManager? = null
        var isOverheatOvercool = false
        var isBatteryFullyCharged = false
        var isBatteryCharged = false
        var isBatteryDischarged = false
    }

    @SuppressLint("RestrictedApi")
    fun onCreateServiceNotification(context: Context) {
        if(!isGooglePlay(context)) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        channelId = onCreateNotificationChannel(context, SERVICE_CHANNEL_ID)
        val openApp = PendingIntent.getActivity(context, OPEN_APP_REQUEST_CODE, Intent(context,
            MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        batteryIntent = context.registerReceiver(null, IntentFilter(
            Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        notificationBuilder = NotificationCompat.Builder(context, channelId).apply {
            setOngoing(true)
            setCategory(Notification.CATEGORY_SERVICE)
            setSmallIcon(getSmallIcon(context))
            color = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ContextCompat.getColor(context, android.R.color.system_accent1_300) else
                    ContextCompat.getColor(context,
                        if(isSystemDarkMode(context.resources.configuration)) R.color.red
                        else R.color.blue)
            setContentIntent(openApp)

            val remoteViewsServiceContent = RemoteViews(context.packageName,
                R.layout.notification_content)
            val isShowBatteryInformation = pref.getBoolean(IS_SHOW_BATTERY_INFORMATION,
                context.resources.getBoolean(R.bool.is_show_battery_information))
            val isShowExpandedNotification =  pref.getBoolean(
                IS_SHOW_EXPANDED_NOTIFICATION, context.resources.getBoolean(
                    R.bool.is_show_expanded_notification))
            if(isShowBatteryInformation)
                remoteViewsServiceContent.setTextViewText(R.id.notification_content_text,
                    if(getCurrentCapacity(context) > 0.0) {
                        val isCapacityInWh = pref.getBoolean(IS_CAPACITY_IN_WH,
                            context.resources.getBoolean(R.bool.is_capacity_in_wh))
                        if(isCapacityInWh) context.getString(R.string.current_capacity_wh,
                            DecimalFormat("#.#").format(
                                getCapacityInWh(getCurrentCapacity(context))))
                        else context.getString(R.string.current_capacity,
                            DecimalFormat("#.#").format(getCurrentCapacity(context)))
                    } else "${context.getString(
                        R.string.battery_level, (getBatteryLevel(context) ?: 0).toString())}%")
            else remoteViewsServiceContent.setTextViewText(R.id.notification_content_text,
                context.getString(R.string.service_is_running))
            setCustomContentView(remoteViewsServiceContent)
            val isShowBigContent = isShowBatteryInformation && isShowExpandedNotification
            if(isShowBigContent) {
                val remoteViewsServiceBigContent = RemoteViews(context.packageName,
                    R.layout.service_notification_big_content)
                remoteViewsServiceBigContent.setViewVisibility(R.id
                    .voltage_service_notification, if(getCurrentCapacity(context) == 0.0
                    || mActions.isNullOrEmpty()) View.VISIBLE else View.GONE)
                getNotificationMessage(context, status, remoteViewsServiceBigContent)
                setCustomBigContentView(remoteViewsServiceBigContent)
            }
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setShowWhen(pref.getBoolean(IS_SERVICE_TIME, context.resources.getBoolean(
                R.bool.is_service_time)))
            setUsesChronometer(pref.getBoolean(IS_SERVICE_TIME, context.resources.getBoolean(
                R.bool.is_service_time)))
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                foregroundServiceBehavior = FOREGROUND_SERVICE_IMMEDIATE
        }
        try {
            notificationBuilder?.build()?.apply {
                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                    let {
                        CapacityInfoService.instance?.startForeground(NOTIFICATION_SERVICE_ID,
                            it, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                    }
                else let {
                    CapacityInfoService.instance?.startForeground(NOTIFICATION_SERVICE_ID, it)
                }
            }
        }
        catch (e: Exception) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && e is ForegroundServiceStartNotAllowedException) return
        }
    }

    @SuppressLint("RestrictedApi")
    fun onUpdateServiceNotification(context: Context) {
        if(!isGooglePlay(context)) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager
        batteryIntent = context.registerReceiver(null, IntentFilter(
            Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        notificationBuilder?.apply {
            setSmallIcon(getSmallIcon(context))
            color =  if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                ContextCompat.getColor(context, android.R.color.system_accent1_300) else
                ContextCompat.getColor(context,
                    if(isSystemDarkMode(context.resources.configuration)) R.color.red
                    else R.color.blue)

            val remoteViewsServiceContent = RemoteViews(context.packageName,
                R.layout.notification_content)

            val isShowBatteryInformation = pref.getBoolean(IS_SHOW_BATTERY_INFORMATION,
                context.resources.getBoolean(R.bool.is_show_battery_information))
            val isShowExpandedNotification =  pref.getBoolean(
                IS_SHOW_EXPANDED_NOTIFICATION, context.resources.getBoolean(
                    R.bool.is_show_expanded_notification))
            if(isShowBatteryInformation)
                remoteViewsServiceContent.setTextViewText(R.id.notification_content_text,
                    if(getCurrentCapacity(context) > 0.0) {
                        val isCapacityInWh = pref.getBoolean(IS_CAPACITY_IN_WH,
                            context.resources.getBoolean(R.bool.is_capacity_in_wh))
                        if(isCapacityInWh) context.getString(R.string.current_capacity_wh,
                            DecimalFormat("#.#").format(
                                getCapacityInWh(getCurrentCapacity(context))))
                        else context.getString(R.string.current_capacity,
                            DecimalFormat("#.#").format(getCurrentCapacity(context)))
                    } else "${context.getString(
                        R.string.battery_level, (getBatteryLevel(context) ?: 0).toString())}%")
            else remoteViewsServiceContent.setTextViewText(R.id.notification_content_text,
                context.getString(R.string.service_is_running))
            setCustomContentView(remoteViewsServiceContent)
            val isShowBigContent = isShowBatteryInformation && isShowExpandedNotification
            if(isShowBigContent) {
                val remoteViewsServiceBigContent = RemoteViews(context.packageName,
                    R.layout.service_notification_big_content)
                remoteViewsServiceBigContent.setViewVisibility(R.id
                    .voltage_service_notification, if(getCurrentCapacity(context) == 0.0
                    || mActions.isNullOrEmpty()) View.VISIBLE else View.GONE)
                getNotificationMessage(context, status, remoteViewsServiceBigContent)
                setCustomBigContentView(remoteViewsServiceBigContent)
            }
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setShowWhen(pref.getBoolean(IS_SERVICE_TIME, context.resources.getBoolean(
                R.bool.is_service_time)))
            setUsesChronometer(pref.getBoolean(IS_SERVICE_TIME, context.resources.getBoolean(
                R.bool.is_service_time)))
        }
        notificationManager?.notify(NOTIFICATION_SERVICE_ID, notificationBuilder?.build())
    }

    fun onNotifyOverheatOvercool(context: Context, temperature: Double) {
        if(!isGooglePlay(context) || isNotificationExists(context,
                NOTIFICATION_BATTERY_OVERHEAT_OVERCOOL_ID)) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val temperatureInFahrenheit = getTemperatureInFahrenheit(context)
        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as? NotificationManager
        val channelId = onCreateNotificationChannel(context, OVERHEAT_OVERCOOL_CHANNEL_ID)
        val remoteViewsContent = RemoteViews(context.packageName, R.layout.notification_content)
        when {
            temperature >= pref.getInt(OVERHEAT_DEGREES, context.resources.getInteger(
                R.integer.overheat_degrees_default)) ->
                remoteViewsContent.setTextViewText(R.id.notification_content_text,
                    context.getString(R.string.battery_overheating, DecimalFormat().format(
                        temperature), DecimalFormat().format(temperatureInFahrenheit)))
            temperature <= pref.getInt(OVERCOOL_DEGREES, context.resources.getInteger(
                R.integer.overcool_degrees_default)) ->
                remoteViewsContent.setTextViewText(R.id.notification_content_text,
                    context.getString(R.string.battery_overcooling, DecimalFormat().format(
                        temperature), DecimalFormat().format(temperatureInFahrenheit)))
            else -> return
        }
        val close = PendingIntent.getService(context,
            CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(context,
            CloseNotificationBatteryStatusInformationService::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val disable = PendingIntent.getService(context,
            DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(context,
            DisableNotificationBatteryStatusInformationService::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        isOverheatOvercool = true
        isBatteryFullyCharged = false
        isBatteryCharged = false
        isBatteryDischarged = false
        val notificationBuilder = NotificationCompat.Builder(
            context, channelId).apply {
            if(pref.getBoolean(IS_BYPASS_DND, context.resources.getBoolean(
                    R.bool.is_bypass_dnd_mode)))
                setCategory(NotificationCompat.CATEGORY_ALARM)
            setAutoCancel(true)
            setOngoing(false)
            addAction(0, context.getString(R.string.close), close)
            addAction(0, context.getString(R.string.disable), disable)
            priority = NotificationCompat.PRIORITY_MAX
            setSmallIcon(R.drawable.ic_overheat_overcool_24)
            color = ContextCompat.getColor(context, R.color.overheat_overcool)
            setContentTitle(context.getString(R.string.battery_status_information))
            setCustomContentView(remoteViewsContent)
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setShowWhen(true)
            setSound(("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                    "${context.packageName}/${R.raw.overheat_overcool}").toUri())
        }
        notificationManager?.notify(NOTIFICATION_BATTERY_OVERHEAT_OVERCOOL_ID,
            notificationBuilder.build())
    }
    
    fun onNotifyBatteryFullyCharged(context: Context, isReminder: Boolean = false) {
        if(!isGooglePlay(context) || (!isReminder &&
                    isNotificationExists(context, NOTIFICATION_FULLY_CHARGED_ID))) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as? NotificationManager
        val channelId = onCreateNotificationChannel(context, FULLY_CHARGED_CHANNEL_ID)
        val remoteViewsContent = RemoteViews(context.packageName, R.layout.notification_content)
        remoteViewsContent.setTextViewText(R.id.notification_content_text, context.getString(
            R.string.battery_is_fully_charged))
        val close = PendingIntent.getService(context,
            CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(context,
            CloseNotificationBatteryStatusInformationService::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val disable = PendingIntent.getService(context,
            DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(context,
            DisableNotificationBatteryStatusInformationService::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        isOverheatOvercool = false
        isBatteryFullyCharged = true
        isBatteryCharged = false
        isBatteryDischarged = false
        val notificationBuilder = NotificationCompat.Builder(
            context, channelId).apply {
            if(pref.getBoolean(IS_BYPASS_DND, context.resources.getBoolean(
                    R.bool.is_bypass_dnd_mode)))
                setCategory(NotificationCompat.CATEGORY_ALARM)
            setAutoCancel(true)
            setOngoing(false)
            addAction(0, context.getString(R.string.close), close)
            addAction(0, context.getString(R.string.disable), disable)
            priority = NotificationCompat.PRIORITY_MAX
            setSmallIcon(R.drawable.ic_battery_is_fully_charged_24dp)
            color = ContextCompat.getColor(context, R.color.battery_charged)
            setContentTitle(context.getString(R.string.battery_status_information))
            setCustomContentView(remoteViewsContent)
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setShowWhen(true)
            setSound(("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                    "${context.packageName}/${R.raw.battery_is_fully_charged}").toUri())
        }
        notificationManager?.notify(NOTIFICATION_FULLY_CHARGED_ID, notificationBuilder.build())
    }

    fun onNotifyBatteryCharged(context: Context) {
        if(!isGooglePlay(context) ||
            isNotificationExists(context, NOTIFICATION_BATTERY_STATUS_ID)) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val batteryLevel = getBatteryLevel(context)
        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as? NotificationManager
        val channelId = onCreateNotificationChannel(context, CHARGED_CHANNEL_ID)
        val remoteViewsContent = RemoteViews(context.packageName, R.layout.notification_content)
        remoteViewsContent.setTextViewText(R.id.notification_content_text,
            "${context.getString(R.string.battery_is_charged_notification, batteryLevel)}%")
        val close = PendingIntent.getService(context,
            CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(context,
                CloseNotificationBatteryStatusInformationService::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val disable = PendingIntent.getService(context,
            DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(context,
                DisableNotificationBatteryStatusInformationService::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        isOverheatOvercool = false
        isBatteryFullyCharged = false
        isBatteryCharged = true
        isBatteryDischarged = false
        val notificationBuilder = NotificationCompat.Builder(
            context, channelId).apply {
            if(pref.getBoolean(IS_BYPASS_DND, context.resources.getBoolean(
                    R.bool.is_bypass_dnd_mode)))
                setCategory(NotificationCompat.CATEGORY_ALARM)
            setAutoCancel(true)
            setOngoing(false)
            addAction(0, context.getString(R.string.close), close)
            addAction(0, context.getString(R.string.disable), disable)
            priority = NotificationCompat.PRIORITY_MAX
            setSmallIcon(when(batteryLevel) {
                in 0..29 -> R.drawable.ic_battery_is_charged_20_24dp
                in 30..49 -> R.drawable.ic_battery_is_charged_30_24dp
                in 50..59 -> R.drawable.ic_battery_is_charged_50_24dp
                in 60..79 -> R.drawable.ic_battery_is_charged_60_24dp
                in 80..89 -> R.drawable.ic_battery_is_charged_80_24dp
                in 90..95 -> R.drawable.ic_battery_is_charged_90_24dp
                else -> R.drawable.ic_battery_is_fully_charged_24dp
            })
            color = ContextCompat.getColor(context, R.color.battery_charged)
            setContentTitle(context.getString(R.string.battery_status_information))
            setCustomContentView(remoteViewsContent)
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setShowWhen(true)
            setLights(Color.GREEN, 1500, 500)
            setSound(("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                    "${context.packageName}/${R.raw.battery_is_charged}").toUri())
        }
        notificationManager?.notify(NOTIFICATION_BATTERY_STATUS_ID, notificationBuilder.build())
    }

    fun onNotifyBatteryDischarged(context: Context) {
        if(!isGooglePlay(context) ||
            isNotificationExists(context, NOTIFICATION_BATTERY_STATUS_ID)) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val batteryLevel = getBatteryLevel(context) ?: 0
        notificationManager = context.getSystemService(NOTIFICATION_SERVICE)
                as? NotificationManager
        val channelId = onCreateNotificationChannel(context, DISCHARGED_CHANNEL_ID)
        val remoteViewsContent = RemoteViews(context.packageName, R.layout.notification_content)
        remoteViewsContent.setTextViewText(R.id.notification_content_text,
            "${context.getString(R.string.battery_is_discharged_notification,
                batteryLevel)}%")
        val close = PendingIntent.getService(context,
            CLOSE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(context,
                CloseNotificationBatteryStatusInformationService::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        val disable = PendingIntent.getService(context,
            DISABLE_NOTIFICATION_BATTERY_STATUS_INFORMATION_REQUEST_CODE, Intent(context,
                DisableNotificationBatteryStatusInformationService::class.java),
            PendingIntent.FLAG_IMMUTABLE)
        isOverheatOvercool = false
        isBatteryFullyCharged = false
        isBatteryCharged = false
        isBatteryDischarged = true
        val notificationBuilder = NotificationCompat.Builder(
            context, channelId).apply {
            if(pref.getBoolean(IS_BYPASS_DND, context.resources.getBoolean(
                    R.bool.is_bypass_dnd_mode)))
                setCategory(NotificationCompat.CATEGORY_ALARM)
            setAutoCancel(true)
            setOngoing(false)
            addAction(0, context.getString(R.string.close), close)
            addAction(0, context.getString(R.string.disable), disable)
            priority = NotificationCompat.PRIORITY_MAX
            setSmallIcon(when(batteryLevel) {
                in 0..9 -> R.drawable.ic_battery_discharged_9_24dp
                in 10..29 -> R.drawable.ic_battery_is_discharged_20_24dp
                in 30..49 -> R.drawable.ic_battery_is_discharged_30_24dp
                in 50..59 -> R.drawable.ic_battery_is_discharged_50_24dp
                in 60..79 -> R.drawable.ic_battery_is_discharged_60_24dp
                in 80..89 -> R.drawable.ic_battery_is_discharged_80_24dp
                in 90..99 -> R.drawable.ic_battery_is_discharged_90_24dp
                else -> R.drawable.ic_battery_discharged_9_24dp
            })
            color = ContextCompat.getColor(context, R.color.battery_discharged)
            setContentTitle(context.getString(R.string.battery_status_information))
            setCustomContentView(remoteViewsContent)
            setStyle(NotificationCompat.DecoratedCustomViewStyle())
            setShowWhen(true)
            setLights(Color.RED, 1000, 500)
            setSound(("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                    "${context.packageName}/${R.raw.battery_is_discharged}").toUri())
        }
        notificationManager?.notify(NOTIFICATION_BATTERY_STATUS_ID, notificationBuilder.build())
    }

    private fun onCreateNotificationChannel(context: Context, notificationChannelId: String):
            String {
        val notificationService =
            context.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        val soundAttributes = AudioAttributes.Builder().apply {
            setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            setUsage(AudioAttributes.USAGE_NOTIFICATION)
        }
        notificationService?.apply {
            when (notificationChannelId) {
                SERVICE_CHANNEL_ID -> {
                    val channelName = context.getString(R.string.service)
                    createNotificationChannel(NotificationChannel(
                        notificationChannelId, channelName,
                        NotificationManager.IMPORTANCE_LOW).apply {
                        setShowBadge(false)
                    })
                }
                OVERHEAT_OVERCOOL_CHANNEL_ID -> {
                    val channelName = context.getString(R.string.overheat_overcool)
                    createNotificationChannel(NotificationChannel(
                        notificationChannelId, channelName,
                        NotificationManager.IMPORTANCE_HIGH).apply {
                        setShowBadge(true)
                        setSound(("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                "${context.packageName}/${R.raw.overheat_overcool}").toUri(),
                            soundAttributes.build())
                    })
                }
                FULLY_CHARGED_CHANNEL_ID -> {
                    val channelName = context.getString(R.string.fully_charged)
                    createNotificationChannel(NotificationChannel(
                        notificationChannelId, channelName,
                        NotificationManager.IMPORTANCE_HIGH).apply {
                        setShowBadge(true)
                        setSound(("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                "${context.packageName}/${R.raw.battery_is_fully_charged}").toUri(),
                            soundAttributes.build())
                    })
                }
                CHARGED_CHANNEL_ID -> {
                    val channelName = context.getString(R.string.charged)
                    createNotificationChannel(NotificationChannel(
                        notificationChannelId, channelName,
                        NotificationManager.IMPORTANCE_HIGH).apply {
                        setShowBadge(true)
                        enableLights(true)
                        lightColor = Color.GREEN
                        setSound(("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                "${context.packageName}/${R.raw.battery_is_charged}").toUri(),
                            soundAttributes.build())
                    })
                }
                DISCHARGED_CHANNEL_ID -> {
                    val channelName = context.getString(R.string.discharged)
                    createNotificationChannel(NotificationChannel(
                        notificationChannelId, channelName,
                        NotificationManager.IMPORTANCE_HIGH).apply {
                        setShowBadge(true)
                        enableLights(true)
                        lightColor = Color.RED
                        setSound(("${ContentResolver.SCHEME_ANDROID_RESOURCE}://" +
                                "${context.packageName}/${R.raw.battery_is_discharged}").toUri(),
                            soundAttributes.build())
                    })
                }   
        }
        }
        return notificationChannelId
    }

    private fun getNotificationMessage(context: Context, status: Int?, remoteViews: RemoteViews) {
        if(!isGooglePlay(context)) return
        when(status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> getBatteryStatusCharging(context,
                batteryIntent, remoteViews)

            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> getBatteryStatusNotCharging(context,
            remoteViews)

            BatteryManager.BATTERY_STATUS_FULL -> getBatteryStatusFull(context, remoteViews)

            BatteryManager.BATTERY_STATUS_DISCHARGING -> getBatteryStatusDischarging(context,
                remoteViews)
            else -> getBatteryStatusUnknown(context, remoteViews)
        }
    }

    private fun getBatteryStatusCharging(context: Context, batteryIntent: Intent?,
                                           remoteViews: RemoteViews) {
        if(!isGooglePlay(context)) return
        val capacityInfoServiceContext = CapacityInfoService.instance
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isCapacityInWh = pref.getBoolean(IS_CAPACITY_IN_WH, context.resources.getBoolean(
            R.bool.is_capacity_in_wh))
        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.IS_CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.is_charging_discharge_current_in_watt))
        remoteViews.apply {
            setViewVisibility(R.id.number_of_cycles_service_notification, View.GONE)
            setViewVisibility(R.id.charging_time_service_notification, View.VISIBLE)
            setViewVisibility(R.id.source_of_power_service_notification, View.VISIBLE)
            setViewVisibility(R.id.current_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.residual_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.battery_wear_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setTextViewText(R.id.status_service_notification, context.getString(
                R.string.status, context.getString(R.string.charging)))
            setTextViewText(R.id.battery_level_service_notification, context.getString(
                R.string.battery_level, try {
                    "${getBatteryLevel(context)}%"
                }
                catch(_: RuntimeException)  { R.string.unknown }))
            setTextViewText(R.id.charging_time_service_notification, getChargingTime(context,
                    capacityInfoServiceContext?.seconds ?: 0))
            setTextViewText(R.id.source_of_power_service_notification, getSourceOfPower(context,
                batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED,
                    -1) ?: -1))
            setTextViewText(R.id.current_capacity_service_notification, context.getString(
                if(isCapacityInWh) R.string.current_capacity_wh else R.string.current_capacity,
                DecimalFormat("#.#").format(if(isCapacityInWh) getCapacityInWh(
                    getCurrentCapacity(context)) else getCurrentCapacity(context))))
            setTextViewText(R.id.capacity_added_service_notification, getCapacityAdded(
                context))
            setTextViewText(R.id.residual_capacity_service_notification,
                getResidualCapacity(context))
            setTextViewText(R.id.battery_wear_service_notification, getBatteryWear(
                context))
            if(isChargingDischargeCurrentInWatt)
                setTextViewText(R.id.charge_discharge_current_service_notification,
                    context.getString(R.string.charge_current_watt,
                        DecimalFormat("#.##").format(getChargeDischargeCurrentInWatt(
                            getChargeDischargeCurrent(context), true))))
            else setTextViewText(R.id.charge_discharge_current_service_notification,
                context.getString(R.string.charge_current,
                    "${getChargeDischargeCurrent(context)}"))
            setTextViewText(R.id.temperature_service_notification, context.getString(R.string
                .temperature, DecimalFormat().format(getTemperatureInCelsius(context)),
                DecimalFormat().format(getTemperatureInFahrenheit(context))))
            setTextViewText(R.id.voltage_service_notification, context.getString(R.string.voltage,
                DecimalFormat("#.#").format(getVoltage(context))))
        }
    }

    private fun getBatteryStatusNotCharging(context: Context, remoteViews: RemoteViews) {
        if(!isGooglePlay(context)) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val capacityInfoServiceContext = CapacityInfoService.instance
        val isCapacityInWh = pref.getBoolean(IS_CAPACITY_IN_WH, context.resources.getBoolean(
            R.bool.is_capacity_in_wh))
        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.IS_CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.is_charging_discharge_current_in_watt))
        remoteViews.apply {
            setViewVisibility(R.id.charging_time_service_notification, View.VISIBLE)
            setViewVisibility(R.id.current_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.residual_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.battery_wear_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setTextViewText(R.id.status_service_notification, context.getString(
                R.string.status, context.getString(R.string.not_charging)))
            setTextViewText(R.id.battery_level_service_notification, context.getString(
                R.string.battery_level, try {
                    "${getBatteryLevel(context)}%"
                }
                catch(_: RuntimeException)  { R.string.unknown }))
            setTextViewText(R.id.number_of_cycles_service_notification, context.getString(
                R.string.number_of_cycles, DecimalFormat("#.##").format(pref.getFloat(
                    NUMBER_OF_CYCLES, 0f))))
            setTextViewText(R.id.charging_time_service_notification, getChargingTime(context,
                    capacityInfoServiceContext?.seconds ?: 0))
            setTextViewText(R.id.current_capacity_service_notification, context.getString(
                if(isCapacityInWh) R.string.current_capacity_wh else R.string.current_capacity,
                DecimalFormat("#.#").format(if(isCapacityInWh) getCapacityInWh(
                    getCurrentCapacity(context)) else getCurrentCapacity(context))))
            setTextViewText(R.id.capacity_added_service_notification, getCapacityAdded(
                context))
            setTextViewText(R.id.residual_capacity_service_notification,
                getResidualCapacity(context))
            setTextViewText(R.id.battery_wear_service_notification, getBatteryWear(
                context))
            if(isChargingDischargeCurrentInWatt)
                setTextViewText(R.id.charge_discharge_current_service_notification,
                    context.getString(R.string.discharge_current_watt,
                        DecimalFormat("#.##").format(getChargeDischargeCurrentInWatt(
                            getChargeDischargeCurrent(context)))))
            else setTextViewText(R.id.charge_discharge_current_service_notification,
                context.getString(R.string.discharge_current,
                    "${getChargeDischargeCurrent(context)}"))
            setTextViewText(R.id.temperature_service_notification, context.getString(R.string
                .temperature, DecimalFormat().format(getTemperatureInCelsius(context)),
                DecimalFormat().format(getTemperatureInFahrenheit(context))))
            setTextViewText(R.id.voltage_service_notification, context.getString(R.string.voltage,
                DecimalFormat("#.#").format(getVoltage(context))))
        }
    }

    private fun getBatteryStatusFull(context: Context, remoteViews: RemoteViews) {
        if(!isGooglePlay(context)) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val capacityInfoServiceContext = CapacityInfoService.instance
        val isCapacityInWh = pref.getBoolean(IS_CAPACITY_IN_WH, context.resources.getBoolean(
            R.bool.is_capacity_in_wh))
        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.IS_CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.is_charging_discharge_current_in_watt))
        remoteViews.apply {
            setViewVisibility(R.id.charging_time_service_notification, View.VISIBLE)
            setViewVisibility(R.id.current_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.residual_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.battery_wear_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setTextViewText(R.id.status_service_notification, context.getString(
                R.string.status, context.getString(R.string.full)))
            setTextViewText(R.id.battery_level_service_notification, context.getString(
                R.string.battery_level, try {
                    "${getBatteryLevel(context)}%"
                }
                catch(_: RuntimeException)  { R.string.unknown }))
            setTextViewText(R.id.number_of_cycles_service_notification, context.getString(
                R.string.number_of_cycles, DecimalFormat("#.##").format(pref.getFloat(
                    NUMBER_OF_CYCLES, 0f))))
            setTextViewText(R.id.charging_time_service_notification, getChargingTime(context,
                    capacityInfoServiceContext?.seconds ?: 0))
            setTextViewText(R.id.current_capacity_service_notification, context.getString(
                if(isCapacityInWh) R.string.current_capacity_wh else R.string.current_capacity,
                DecimalFormat("#.#").format(if(isCapacityInWh) getCapacityInWh(
                    getCurrentCapacity(context)) else getCurrentCapacity(context))))
            setTextViewText(R.id.capacity_added_service_notification, getCapacityAdded(
                context))
            setTextViewText(R.id.residual_capacity_service_notification,
                getResidualCapacity(context))
            setTextViewText(R.id.battery_wear_service_notification, getBatteryWear(
                context))
            if(isChargingDischargeCurrentInWatt)
                setTextViewText(R.id.charge_discharge_current_service_notification,
                    context.getString(R.string.discharge_current_watt,
                        DecimalFormat("#.##").format(getChargeDischargeCurrentInWatt(
                            getChargeDischargeCurrent(context)))))
            else setTextViewText(R.id.charge_discharge_current_service_notification,
                context.getString(R.string.discharge_current,
                    "${getChargeDischargeCurrent(context)}"))
            setTextViewText(R.id.temperature_service_notification, context.getString(R.string
                .temperature, DecimalFormat().format(getTemperatureInCelsius(context)),
                DecimalFormat().format(getTemperatureInFahrenheit(context))))
            setTextViewText(R.id.voltage_service_notification, context.getString(R.string.voltage,
                DecimalFormat("#.#").format(getVoltage(context))))
        }
    }

    private fun getBatteryStatusDischarging(context: Context, remoteViews: RemoteViews) {
        if(!isGooglePlay(context)) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val isCapacityInWh = pref.getBoolean(IS_CAPACITY_IN_WH, context.resources.getBoolean(
            R.bool.is_capacity_in_wh))
        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.IS_CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.is_charging_discharge_current_in_watt))
        remoteViews.apply {
            setViewVisibility(R.id.current_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.residual_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.battery_wear_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setTextViewText(R.id.status_service_notification, context.getString(
                R.string.status, context.getString(R.string.discharging)))
            setTextViewText(R.id.battery_level_service_notification, context.getString(
                R.string.battery_level, try {
                    "${getBatteryLevel(context)}%"
                }
                catch(_: RuntimeException)  { R.string.unknown }))
            setTextViewText(R.id.number_of_cycles_service_notification, context.getString(
                R.string.number_of_cycles, DecimalFormat("#.##").format(pref.getFloat(
                    NUMBER_OF_CYCLES, 0f))))
            setTextViewText(R.id.current_capacity_service_notification, context.getString(
                if(isCapacityInWh) R.string.current_capacity_wh else R.string.current_capacity,
                DecimalFormat("#.#").format(if(isCapacityInWh) getCapacityInWh(
                    getCurrentCapacity(context)) else getCurrentCapacity(context))))
            setTextViewText(R.id.capacity_added_service_notification, getCapacityAdded(
                context))
            setTextViewText(R.id.residual_capacity_service_notification,
                getResidualCapacity(context))
            setTextViewText(R.id.battery_wear_service_notification, getBatteryWear(
                context))
            if(isChargingDischargeCurrentInWatt)
                setTextViewText(R.id.charge_discharge_current_service_notification,
                    context.getString(R.string.discharge_current_watt,
                        DecimalFormat("#.##").format(getChargeDischargeCurrentInWatt(
                            getChargeDischargeCurrent(context)))))
            else setTextViewText(R.id.charge_discharge_current_service_notification,
                context.getString(R.string.discharge_current,
                    "${getChargeDischargeCurrent(context)}"))
            setTextViewText(R.id.temperature_service_notification, context.getString(R.string
                .temperature, DecimalFormat().format(getTemperatureInCelsius(context)),
                DecimalFormat().format(getTemperatureInFahrenheit(context))))
            setTextViewText(R.id.voltage_service_notification, context.getString(R.string.voltage,
                DecimalFormat("#.#").format(getVoltage(context))))
        }
    }

    private fun getBatteryStatusUnknown(context: Context, remoteViews: RemoteViews) {
        if(!isGooglePlay(context)) return
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val capacityInfoServiceContext = CapacityInfoService.instance
        val isChargingDischargeCurrentInWatt = pref.getBoolean(
            PreferencesKeys.IS_CHARGING_DISCHARGE_CURRENT_IN_WATT,
            context.resources.getBoolean(R.bool.is_charging_discharge_current_in_watt))
        remoteViews.apply {
            setViewVisibility(R.id.charging_time_service_notification, View.VISIBLE)
            setViewVisibility(R.id.current_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.residual_capacity_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.battery_wear_service_notification,
                if(getCurrentCapacity(context) > 0.0) View.VISIBLE else View.GONE)
            setTextViewText(R.id.status_service_notification, context.getString(
                R.string.status, context.getString(R.string.unknown)))
            setTextViewText(R.id.battery_level_service_notification, context.getString(
                R.string.battery_level, try {
                    "${getBatteryLevel(context)}%"
                }
                catch(_: RuntimeException)  { R.string.unknown }))
            setTextViewText(R.id.number_of_cycles_service_notification, context.getString(
                R.string.number_of_cycles, DecimalFormat("#.##").format(pref.getFloat(
                    NUMBER_OF_CYCLES, 0f))))
            setTextViewText(R.id.charging_time_service_notification, getChargingTime(context,
                    capacityInfoServiceContext?.seconds ?: 0))
            setTextViewText(R.id.current_capacity_service_notification, context.getString(
                R.string.current_capacity, DecimalFormat("#.#").format(getCurrentCapacity(
                    context))))
            setTextViewText(R.id.capacity_added_service_notification, getCapacityAdded(
                context))
            setTextViewText(R.id.residual_capacity_service_notification,
                getResidualCapacity(context))
            setTextViewText(R.id.battery_wear_service_notification, getBatteryWear(
                context))
            if(isChargingDischargeCurrentInWatt)
                setTextViewText(R.id.charge_discharge_current_service_notification,
                    context.getString(R.string.discharge_current_watt,
                        DecimalFormat("#.##").format(getChargeDischargeCurrentInWatt(
                            getChargeDischargeCurrent(context)))))
            else setTextViewText(R.id.charge_discharge_current_service_notification,
                context.getString(R.string.discharge_current,
                    "${getChargeDischargeCurrent(context)}"))
            setTextViewText(R.id.temperature_service_notification, context.getString(R.string
                .temperature, DecimalFormat().format(getTemperatureInCelsius(context)),
                DecimalFormat().format(getTemperatureInFahrenheit(context))))
            setTextViewText(R.id.voltage_service_notification, context.getString(R.string.voltage,
                DecimalFormat("#.#").format(getVoltage(context))))
        }
    }

    private fun isNotificationExists(context: Context, notificationID: Int): Boolean {
        val notificationManager =
            context.getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        val notifications = notificationManager?.activeNotifications
        var isNotificationExists = false
        if(notifications != null)
            for(notification in notifications) {
                if(notification.id == notificationID) isNotificationExists = true
            }
        return isNotificationExists
    }
    
    private fun getSmallIcon(context: Context): Int {
        if(!isGooglePlay(context)) return R.mipmap.ic_battery_level_0
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
     return if(pref.getBoolean(IS_SHOW_BATTERY_LEVEL_IN_STATUS_BAR,
             context.resources.getBoolean(R.bool.is_show_battery_level_in_status_bar)))
         when(val batteryLevel = (getBatteryLevel(context) ?: 0)) {
             0 -> R.mipmap.ic_battery_level_0
             1 -> R.mipmap.ic_battery_level_1
             2 -> R.mipmap.ic_battery_level_2
             3 -> R.mipmap.ic_battery_level_3
             4 -> R.mipmap.ic_battery_level_4
             5 -> R.mipmap.ic_battery_level_5
             6 -> R.mipmap.ic_battery_level_6
             7 -> R.mipmap.ic_battery_level_7
             8 -> R.mipmap.ic_battery_level_8
             9 -> R.mipmap.ic_battery_level_9
             10 -> R.mipmap.ic_battery_level_10
             11 -> R.mipmap.ic_battery_level_11
             12 -> R.mipmap.ic_battery_level_12
             13 -> R.mipmap.ic_battery_level_13
             14 -> R.mipmap.ic_battery_level_14
             15 -> R.mipmap.ic_battery_level_15
             16 -> R.mipmap.ic_battery_level_16
             17 -> R.mipmap.ic_battery_level_17
             18 -> R.mipmap.ic_battery_level_18
             19 -> R.mipmap.ic_battery_level_19
             20 -> R.mipmap.ic_battery_level_20
             21 -> R.mipmap.ic_battery_level_21
             22 -> R.mipmap.ic_battery_level_22
             23 -> R.mipmap.ic_battery_level_23
             24 -> R.mipmap.ic_battery_level_24
             25 -> R.mipmap.ic_battery_level_25
             26 -> R.mipmap.ic_battery_level_26
             27 -> R.mipmap.ic_battery_level_27
             28 -> R.mipmap.ic_battery_level_28
             29 -> R.mipmap.ic_battery_level_29
             30 -> R.mipmap.ic_battery_level_30
             31 -> R.mipmap.ic_battery_level_31
             32 -> R.mipmap.ic_battery_level_32
             33 -> R.mipmap.ic_battery_level_33
             34 -> R.mipmap.ic_battery_level_34
             35 -> R.mipmap.ic_battery_level_35
             36 -> R.mipmap.ic_battery_level_36
             37 -> R.mipmap.ic_battery_level_37
             38 -> R.mipmap.ic_battery_level_38
             39 -> R.mipmap.ic_battery_level_39
             40 -> R.mipmap.ic_battery_level_40
             41 -> R.mipmap.ic_battery_level_41
             42 -> R.mipmap.ic_battery_level_42
             43 -> R.mipmap.ic_battery_level_43
             44 -> R.mipmap.ic_battery_level_44
             45 -> R.mipmap.ic_battery_level_45
             46 -> R.mipmap.ic_battery_level_46
             47 -> R.mipmap.ic_battery_level_47
             48 -> R.mipmap.ic_battery_level_48
             49 -> R.mipmap.ic_battery_level_49
             50 -> R.mipmap.ic_battery_level_50
             51 -> R.mipmap.ic_battery_level_51
             52 -> R.mipmap.ic_battery_level_52
             53 -> R.mipmap.ic_battery_level_53
             54 -> R.mipmap.ic_battery_level_54
             55 -> R.mipmap.ic_battery_level_55
             56 -> R.mipmap.ic_battery_level_56
             57 -> R.mipmap.ic_battery_level_57
             58 -> R.mipmap.ic_battery_level_58
             59 -> R.mipmap.ic_battery_level_59
             60 -> R.mipmap.ic_battery_level_60
             61 -> R.mipmap.ic_battery_level_61
             62 -> R.mipmap.ic_battery_level_62
             63 -> R.mipmap.ic_battery_level_63
             64 -> R.mipmap.ic_battery_level_64
             65 -> R.mipmap.ic_battery_level_65
             66 -> R.mipmap.ic_battery_level_66
             67 -> R.mipmap.ic_battery_level_67
             68 -> R.mipmap.ic_battery_level_68
             69 -> R.mipmap.ic_battery_level_69
             70 -> R.mipmap.ic_battery_level_70
             71 -> R.mipmap.ic_battery_level_71
             72 -> R.mipmap.ic_battery_level_72
             73 -> R.mipmap.ic_battery_level_73
             74 -> R.mipmap.ic_battery_level_74
             75 -> R.mipmap.ic_battery_level_75
             76 -> R.mipmap.ic_battery_level_76
             77 -> R.mipmap.ic_battery_level_77
             78 -> R.mipmap.ic_battery_level_78
             79 -> R.mipmap.ic_battery_level_79
             80 -> R.mipmap.ic_battery_level_80
             81 -> R.mipmap.ic_battery_level_81
             82 -> R.mipmap.ic_battery_level_82
             83 -> R.mipmap.ic_battery_level_83
             84 -> R.mipmap.ic_battery_level_84
             85 -> R.mipmap.ic_battery_level_85
             86 -> R.mipmap.ic_battery_level_86
             87 -> R.mipmap.ic_battery_level_87
             88 -> R.mipmap.ic_battery_level_88
             89 -> R.mipmap.ic_battery_level_89
             90 -> R.mipmap.ic_battery_level_90
             91 -> R.mipmap.ic_battery_level_91
             92 -> R.mipmap.ic_battery_level_92
             93 -> R.mipmap.ic_battery_level_93
             94 -> R.mipmap.ic_battery_level_94
             95 -> R.mipmap.ic_battery_level_95
             96 -> R.mipmap.ic_battery_level_96
             97 -> R.mipmap.ic_battery_level_97
             98 -> R.mipmap.ic_battery_level_98
             99 -> R.mipmap.ic_battery_level_99
             100 -> R.mipmap.ic_battery_level_100
             else -> {
                 if(batteryLevel > 100) R.mipmap.ic_battery_level_100 else R.mipmap.ic_battery_level_0
             }
         }
     else R.drawable.ic_service_small_icon
    }
}