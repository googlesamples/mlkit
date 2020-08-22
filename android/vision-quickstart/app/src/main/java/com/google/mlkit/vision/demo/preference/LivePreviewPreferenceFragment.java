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

import android.hardware.Camera;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import androidx.annotation.StringRes;
import android.widget.Toast;
import com.google.mlkit.vision.demo.CameraSource;
import com.google.mlkit.vision.demo.CameraSource.SizePair;
import com.google.mlkit.vision.demo.R;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Configures live preview demo settings. */
public class LivePreviewPreferenceFragment extends PreferenceFragment {

  protected boolean isCameraXSetting;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    addPreferencesFromResource(R.xml.preference_live_preview_quickstart);
    setUpCameraPreferences();
    setUpFaceDetectionPreferences();
  }

  private void setUpCameraPreferences() {
    PreferenceCategory cameraPreference =
        (PreferenceCategory) findPreference(getString(R.string.pref_category_key_camera));

    if (isCameraXSetting) {
      cameraPreference.removePreference(
          findPreference(getString(R.string.pref_key_rear_camera_preview_size)));
      cameraPreference.removePreference(
          findPreference(getString(R.string.pref_key_front_camera_preview_size)));
      setUpCameraXTargetAnalysisSizePreference();
    } else {
      cameraPreference.removePreference(
          findPreference(getString(R.string.pref_key_camerax_target_analysis_size)));
      setUpCameraPreviewSizePreference(
          R.string.pref_key_rear_camera_preview_size,
          R.string.pref_key_rear_camera_picture_size,
          CameraSource.CAMERA_FACING_BACK);
      setUpCameraPreviewSizePreference(
          R.string.pref_key_front_camera_preview_size,
          R.string.pref_key_front_camera_picture_size,
          CameraSource.CAMERA_FACING_FRONT);
    }
  }

  private void setUpCameraPreviewSizePreference(
      @StringRes int previewSizePrefKeyId, @StringRes int pictureSizePrefKeyId, int cameraId) {
    ListPreference previewSizePreference =
        (ListPreference) findPreference(getString(previewSizePrefKeyId));

    Camera camera = null;
    try {
      camera = Camera.open(cameraId);

      List<SizePair> previewSizeList = CameraSource.generateValidPreviewSizeList(camera);
      String[] previewSizeStringValues = new String[previewSizeList.size()];
      Map<String, String> previewToPictureSizeStringMap = new HashMap<>();
      for (int i = 0; i < previewSizeList.size(); i++) {
        SizePair sizePair = previewSizeList.get(i);
        previewSizeStringValues[i] = sizePair.preview.toString();
        if (sizePair.picture != null) {
          previewToPictureSizeStringMap.put(
              sizePair.preview.toString(), sizePair.picture.toString());
        }
      }
      previewSizePreference.setEntries(previewSizeStringValues);
      previewSizePreference.setEntryValues(previewSizeStringValues);

      if (previewSizePreference.getEntry() == null) {
        // First time of opening the Settings page.
        SizePair sizePair =
            CameraSource.selectSizePair(
                camera,
                CameraSource.DEFAULT_REQUESTED_CAMERA_PREVIEW_WIDTH,
                CameraSource.DEFAULT_REQUESTED_CAMERA_PREVIEW_HEIGHT);
        String previewSizeString = sizePair.preview.toString();
        previewSizePreference.setValue(previewSizeString);
        previewSizePreference.setSummary(previewSizeString);
        PreferenceUtils.saveString(
            getActivity(),
            pictureSizePrefKeyId,
            sizePair.picture != null ? sizePair.picture.toString() : null);
      } else {
        previewSizePreference.setSummary(previewSizePreference.getEntry());
      }

      previewSizePreference.setOnPreferenceChangeListener(
          (preference, newValue) -> {
            String newPreviewSizeStringValue = (String) newValue;
            previewSizePreference.setSummary(newPreviewSizeStringValue);
            PreferenceUtils.saveString(
                getActivity(),
                pictureSizePrefKeyId,
                previewToPictureSizeStringMap.get(newPreviewSizeStringValue));
            return true;
          });

    } catch (Exception e) {
      // If there's no camera for the given camera id, hide the corresponding preference.
      ((PreferenceCategory) findPreference(getString(R.string.pref_category_key_camera)))
          .removePreference(previewSizePreference);
    } finally {
      if (camera != null) {
        camera.release();
      }
    }
  }

  private void setUpCameraXTargetAnalysisSizePreference() {
    ListPreference pref =
        (ListPreference) findPreference(getString(R.string.pref_key_camerax_target_analysis_size));
    String[] entries =
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
    pref.setEntries(entries);
    pref.setEntryValues(entries);
    pref.setSummary(pref.getEntry() == null ? "Default" : pref.getEntry());
    pref.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          String newStringValue = (String) newValue;
          pref.setSummary(newStringValue);
          PreferenceUtils.saveString(
              getActivity(), R.string.pref_key_camerax_target_analysis_size, newStringValue);
          return true;
        });
  }

  private void setUpFaceDetectionPreferences() {
    setUpListPreference(R.string.pref_key_live_preview_face_detection_landmark_mode);
    setUpListPreference(R.string.pref_key_live_preview_face_detection_contour_mode);
    setUpListPreference(R.string.pref_key_live_preview_face_detection_classification_mode);
    setUpListPreference(R.string.pref_key_live_preview_face_detection_performance_mode);

    EditTextPreference minFaceSizePreference =
        (EditTextPreference)
            findPreference(getString(R.string.pref_key_live_preview_face_detection_min_face_size));
    minFaceSizePreference.setSummary(minFaceSizePreference.getText());
    minFaceSizePreference.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          try {
            float minFaceSize = Float.parseFloat((String) newValue);
            if (minFaceSize >= 0.0f && minFaceSize <= 1.0f) {
              minFaceSizePreference.setSummary((String) newValue);
              return true;
            }
          } catch (NumberFormatException e) {
            // Fall through intentionally.
          }

          Toast.makeText(
                  getActivity(), R.string.pref_toast_invalid_min_face_size, Toast.LENGTH_LONG)
              .show();
          return false;
        });
  }

  private void setUpListPreference(@StringRes int listPreferenceKeyId) {
    ListPreference listPreference = (ListPreference) findPreference(getString(listPreferenceKeyId));
    listPreference.setSummary(listPreference.getEntry());
    listPreference.setOnPreferenceChangeListener(
        (preference, newValue) -> {
          int index = listPreference.findIndexOfValue((String) newValue);
          listPreference.setSummary(listPreference.getEntries()[index]);
          return true;
        });
  }
}
