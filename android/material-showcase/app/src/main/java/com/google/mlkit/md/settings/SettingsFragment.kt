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

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.mlkit.md.R
import com.google.mlkit.md.camera.CameraSizePair

/** Configures App settings.  */
class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(bundle: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
        setUpRearCameraPreviewSizePreference()
    }

    private fun setUpRearCameraPreviewSizePreference() {
        val previewSizePreference = findPreference<ListPreference>(getString(R.string.pref_key_rear_camera_preview_size))!!
        val previewSizeList = arguments?.getParcelableArrayList<CameraSizePair>(ARG_PREVIEW_SIZE_LIST) ?: arrayListOf()
        if (previewSizeList.isEmpty()){
            previewSizePreference.parent?.removePreference(previewSizePreference)
        }
        else{
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
        }
    }

    companion object {
        private const val ARG_PREVIEW_SIZE_LIST = "arg_preview_size_list"

        fun newInstance(previewSizeList: ArrayList<CameraSizePair>) = SettingsFragment().apply {
            arguments = Bundle().apply {
                putParcelableArrayList(ARG_PREVIEW_SIZE_LIST, previewSizeList)
            }
        }
    }
}
