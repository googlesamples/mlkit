/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.preference;

import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import androidx.annotation.RequiresApi;
import com.google.mlkit.vision.demo.R;

/** Configures CameraXSource live preview demo settings. */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CameraXSourceDemoPreferenceFragment extends CameraXLivePreviewPreferenceFragment {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    PreferenceScreen preferenceScreen =
        (PreferenceScreen) findPreference(getResources().getString(R.string.pref_screen));

    PreferenceCategory preferenceCategory =
        (PreferenceCategory) findPreference(getString(R.string.pref_category_key_camera));

    preferenceCategory.removePreference(
        findPreference(getString(R.string.pref_key_camera_live_viewport)));
    // Remove the PreferenceCategories for hiding camera detection info.
    preferenceScreen.removePreference(preferenceScreen.getPreference(1));

    // Remove the last 3 PreferenceCategories
    preferenceScreen.removePreference(preferenceScreen.getPreference(2));
    preferenceScreen.removePreference(preferenceScreen.getPreference(2));
    preferenceScreen.removePreference(preferenceScreen.getPreference(2));
  }
}
