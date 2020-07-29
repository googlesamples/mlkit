package com.google.mlkit

import android.annotation.SuppressLint
import android.provider.Settings
import androidx.multidex.MultiDexApplication
import com.google.firebase.crashlytics.FirebaseCrashlytics
import info.hannes.crashlytic.CrashlyticsTree
import info.hannes.timber.FileLoggingTree
import timber.log.Timber


class MLApplication : MultiDexApplication() {

    @SuppressLint("HardwareIds")
    override fun onCreate() {
        super.onCreate()

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            @Suppress("ControlFlowWithEmptyBody")
            Timber.e(e.cause?.also { } ?: run { e })
            oldHandler?.uncaughtException(t, e)
        }

        externalCacheDir?.let {
            Timber.plant(FileLoggingTree(it, this))
        }

        FirebaseCrashlytics.getInstance().setCustomKey("VERSION_NAME", BuildConfig.VERSION_NAME)

        if (!BuildConfig.DEBUG)
            Timber.plant(CrashlyticsTree(Settings.Secure.getString(applicationContext.contentResolver, Settings.Secure.ANDROID_ID)))
    }
}