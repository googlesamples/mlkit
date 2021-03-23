package com.google.mlkit

import android.os.Bundle
import android.widget.Toast
import info.hannes.github.AppUpdateHelper
import javax.annotation.Nonnull

class MLMainActivity : NavigationActivity() {
    override fun onCreate(@Nonnull savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ml_main)
        supportFragmentManager
                .beginTransaction()
                .add(R.id.contentInfo, SystemInfoFragment())
                .commit()

        AppUpdateHelper.checkForNewVersion(
                this,
                BuildConfig.GIT_REPOSITORY,
                BuildConfig.VERSION_NAME,
                { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        )
    }
}