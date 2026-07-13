/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.md.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.md.R
import com.google.mlkit.md.Utils
import com.google.mlkit.md.camera.CameraSizePair
import com.google.mlkit.md.camera.CameraSource

/** Hosts the preference fragment to configure settings.  */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        val previewSizeList = intent.getParcelableArrayListExtra<CameraSizePair>(EXTRA_PREVIEW_SIZE_LIST) ?: arrayListOf()
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportFragmentManager
            .beginTransaction()
            .replace(R.id.settings_container, SettingsFragment.newInstance(previewSizeList))
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        private const val EXTRA_PREVIEW_SIZE_LIST = "extra_preview_size_list"

        fun newIntent(context: Context, cameraSource: CameraSource?) = Intent(context, SettingsActivity::class.java).apply {
            cameraSource?.let {
                putParcelableArrayListExtra(EXTRA_PREVIEW_SIZE_LIST, ArrayList(Utils.generateValidPreviewSizeList(it)))
            }
        }
    }

}
