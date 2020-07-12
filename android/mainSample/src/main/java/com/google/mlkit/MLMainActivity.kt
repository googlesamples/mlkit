package com.google.mlkit

import android.os.Bundle
import javax.annotation.Nonnull

class MLMainActivity : NavigationActivity() {
    override fun onCreate(@Nonnull savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ml_main)
        supportFragmentManager
                .beginTransaction()
                .add(R.id.contentInfo, SystemInfoFragment())
                .commit()
    }
}