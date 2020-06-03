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

import android.hardware.Camera
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.mlkit.md.camera.CameraSource
import com.google.mlkit.md.R
import com.google.mlkit.md.Utils
import java.util.HashMap

/** Configures App settings.  */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(bundle: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        setUpRearCameraPreviewSizePreference()
    }

    private fun setUpRearCameraPreviewSizePreference() {
        val previewSizePreference =
            findPreference<ListPreference>(getString(R.string.pref_key_rear_camera_preview_size))!!

        var camera: Camera? = null

        try {
            camera = Camera.open(CameraSource.CAMERA_FACING_BACK)
            val previewSizeList = Utils.generateValidPreviewSizeList(camera!!)
            val previewSizeStringValues = arrayOfNulls<String>(previewSizeList.size)
            val previewToPictureSizeStringMap = HashMap<String, String>()
            for (i in previewSizeList.indices) {
                val sizePair = previewSizeList[i]
                previewSizeStringValues[i] = sizePair.preview.toString()
                if (sizePair.picture != null) {
                    previewToPictureSizeStringMap[sizePair.preview.toString()] = sizePair.picture.toString()
                }
            }
            previewSizePreference.entries = previewSizeStringValues
            previewSizePreference.entryValues = previewSizeStringValues
            previewSizePreference.summary = previewSizePreference.entry
            previewSizePreference.setOnPreferenceChangeListener { _, newValue ->
                val newPreviewSizeStringValue = newValue as String
                val context = activity ?: return@setOnPreferenceChangeListener false
                previewSizePreference.summary = newPreviewSizeStringValue
                PreferenceUtils.saveStringPreference(
                    context,
                    R.string.pref_key_rear_camera_picture_size,
                    previewToPictureSizeStringMap[newPreviewSizeStringValue]
                )
                true
            }
        } catch (e: Exception) {
            // If there's no camera for the given camera id, hide the corresponding preference.
            previewSizePreference.parent?.removePreference(previewSizePreference)
        } finally {
            camera?.release()
        }
    }
}
