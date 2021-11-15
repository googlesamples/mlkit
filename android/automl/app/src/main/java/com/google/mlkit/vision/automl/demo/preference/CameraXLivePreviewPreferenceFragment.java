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
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build.VERSION_CODES;
import android.preference.ListPreference;
import android.preference.PreferenceCategory;
import androidx.annotation.Nullable;
import android.util.Size;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.camera.core.CameraSelector;
import com.google.mlkit.vision.automl.demo.R;
import java.util.Arrays;
import java.util.List;

/** Configures CameraX live preview demo settings. */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CameraXLivePreviewPreferenceFragment extends LivePreviewPreferenceFragment {

  @Override
  void setUpCameraPreferences() {
    PreferenceCategory cameraPreference =
        (PreferenceCategory) findPreference(getString(R.string.pref_category_key_camera));

    cameraPreference.removePreference(
        findPreference(getString(R.string.pref_key_rear_camera_preview_size)));
    cameraPreference.removePreference(
        findPreference(getString(R.string.pref_key_front_camera_preview_size)));
    setUpCameraXTargetAnalysisSizePreference(
        R.string.pref_key_camerax_rear_camera_target_resolution, CameraSelector.LENS_FACING_BACK);
    setUpCameraXTargetAnalysisSizePreference(
        R.string.pref_key_camerax_front_camera_target_resolution, CameraSelector.LENS_FACING_FRONT);
  }

  private void setUpCameraXTargetAnalysisSizePreference(
      @StringRes int previewSizePrefKeyId, int lensFacing) {
    ListPreference pref = (ListPreference) findPreference(getString(previewSizePrefKeyId));
    CameraCharacteristics cameraCharacteristics =
        getCameraCharacteristics(getActivity(), lensFacing);
    String[] entries;
    if (cameraCharacteristics != null) {
      StreamConfigurationMap map =
          cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
      Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
      entries = new String[outputSizes.length];
      for (int i = 0; i < outputSizes.length; i++) {
        entries[i] = outputSizes[i].toString();
      }
    } else {
      entries =
          new String[] {
            "2000x2000",
            "1600x1600",
            "1200x1200",
            "1000x1000",
            "800x800",
            "600x600",
            "400x400",
            "200x200",
            "100x100",
          };
    }
    pref.setEntries(entries);
    pref.setEntryValues(entries);
    pref.setSummary(pref.getEntry() == null ? "Default" : pref.getEntry());
    pref.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          String newStringValue = (String) newValue;
          pref.setSummary(newStringValue);
          PreferenceUtils.saveString(getActivity(), previewSizePrefKeyId, newStringValue);
          return true;
        });
  }

  @Nullable
  public static CameraCharacteristics getCameraCharacteristics(
      Context context, Integer lensFacing) {
    CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    try {
      List<String> cameraList = Arrays.asList(cameraManager.getCameraIdList());
      for (String availableCameraId : cameraList) {
        CameraCharacteristics availableCameraCharacteristics =
            cameraManager.getCameraCharacteristics(availableCameraId);
        Integer availableLensFacing =
            availableCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
        if (availableLensFacing == null) {
          continue;
        }
        if (availableLensFacing.equals(lensFacing)) {
          return availableCameraCharacteristics;
        }
      }
    } catch (CameraAccessException e) {
      // Accessing camera ID info got error
    }
    return null;
  }
}
