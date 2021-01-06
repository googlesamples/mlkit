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

package com.google.mlkit.vision.automl.demo.preference;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION_CODES;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.camera.core.CameraSelector;
import com.google.android.gms.common.images.Size;
import com.google.common.base.Preconditions;
import com.google.mlkit.vision.automl.demo.CameraSource;
import com.google.mlkit.vision.automl.demo.CameraSource.SizePair;
import com.google.mlkit.vision.automl.demo.R;

/** Utility class to retrieve shared preferences. */
public final class PreferenceUtils {

  static void saveString(Context context, @StringRes int prefKeyId, @Nullable String value) {
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putString(context.getString(prefKeyId), value)
        .apply();
  }

  @Nullable
  public static SizePair getCameraPreviewSizePair(Context context, int cameraId) {
    Preconditions.checkArgument(
        cameraId == CameraSource.CAMERA_FACING_BACK
            || cameraId == CameraSource.CAMERA_FACING_FRONT);
    String previewSizePrefKey;
    String pictureSizePrefKey;
    if (cameraId == CameraSource.CAMERA_FACING_BACK) {
      previewSizePrefKey = context.getString(R.string.pref_key_rear_camera_preview_size);
      pictureSizePrefKey = context.getString(R.string.pref_key_rear_camera_picture_size);
    } else {
      previewSizePrefKey = context.getString(R.string.pref_key_front_camera_preview_size);
      pictureSizePrefKey = context.getString(R.string.pref_key_front_camera_picture_size);
    }

    try {
      SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
      return new SizePair(
          Size.parseSize(sharedPreferences.getString(previewSizePrefKey, null)),
          Size.parseSize(sharedPreferences.getString(pictureSizePrefKey, null)));
    } catch (RuntimeException e) {
      return null;
    }
  }

  @RequiresApi(VERSION_CODES.LOLLIPOP)
  @Nullable
  public static android.util.Size getCameraXTargetResolution(Context context, int lensfacing) {
    Preconditions.checkArgument(
        lensfacing == CameraSelector.LENS_FACING_BACK
            || lensfacing == CameraSelector.LENS_FACING_FRONT);
    String prefKey =
        lensfacing == CameraSelector.LENS_FACING_BACK
            ? context.getString(R.string.pref_key_camerax_rear_camera_target_resolution)
            : context.getString(R.string.pref_key_camerax_front_camera_target_resolution);
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    try {
      return android.util.Size.parseSize(sharedPreferences.getString(prefKey, null));
    } catch (RuntimeException e) {
      return null;
    }
  }

  public static boolean isCameraLiveViewportEnabled(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_camera_live_viewport);
    return sharedPreferences.getBoolean(prefKey, false);
  }

  public static String getAutoMLRemoteModelName(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String modelNamePrefKey = context.getString(R.string.pref_key_automl_remote_model_name);
    String defaultModelName = "mlkit_flowers";
    String remoteModelName = sharedPreferences.getString(modelNamePrefKey, defaultModelName);
    if (remoteModelName.isEmpty()) {
      remoteModelName = defaultModelName;
    }
    return remoteModelName;
  }

  public static void setUpRemoteModelNamePreferences(PreferenceFragment preferenceFragment) {
    EditTextPreference autoMLRemoteModelNamePref =
        (EditTextPreference)
            preferenceFragment.findPreference(
                preferenceFragment.getString(R.string.pref_key_automl_remote_model_name));
    autoMLRemoteModelNamePref.setSummary(autoMLRemoteModelNamePref.getText());
    autoMLRemoteModelNamePref.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          String modelName = (String) newValue;
          if (!modelName.isEmpty()) {
            autoMLRemoteModelNamePref.setSummary((String) newValue);
            return true;
          }

          Toast.makeText(
                  preferenceFragment.getActivity(),
                  R.string.pref_key_automl_remote_model_name,
                  Toast.LENGTH_LONG)
              .show();
          return false;
        });
  }

  private PreferenceUtils() {}
}
