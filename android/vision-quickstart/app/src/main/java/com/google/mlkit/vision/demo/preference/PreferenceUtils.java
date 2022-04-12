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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION_CODES;
import android.preference.PreferenceManager;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.camera.core.CameraSelector;
import com.google.android.gms.common.images.Size;
import com.google.common.base.Preconditions;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.demo.CameraSource;
import com.google.mlkit.vision.demo.CameraSource.SizePair;
import com.google.mlkit.vision.demo.R;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase.DetectorMode;
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase;
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

/** Utility class to retrieve shared preferences. */
public class PreferenceUtils {

  private static final int POSE_DETECTOR_PERFORMANCE_MODE_FAST = 1;

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
    } catch (Exception e) {
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
    } catch (Exception e) {
      return null;
    }
  }

  public static boolean shouldHideDetectionInfo(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_info_hide);
    return sharedPreferences.getBoolean(prefKey, false);
  }

  public static ObjectDetectorOptions getObjectDetectorOptionsForStillImage(Context context) {
    return getObjectDetectorOptions(
        context,
        R.string.pref_key_still_image_object_detector_enable_multiple_objects,
        R.string.pref_key_still_image_object_detector_enable_classification,
        ObjectDetectorOptions.SINGLE_IMAGE_MODE);
  }

  public static ObjectDetectorOptions getObjectDetectorOptionsForLivePreview(Context context) {
    return getObjectDetectorOptions(
        context,
        R.string.pref_key_live_preview_object_detector_enable_multiple_objects,
        R.string.pref_key_live_preview_object_detector_enable_classification,
        ObjectDetectorOptions.STREAM_MODE);
  }

  private static ObjectDetectorOptions getObjectDetectorOptions(
      Context context,
      @StringRes int prefKeyForMultipleObjects,
      @StringRes int prefKeyForClassification,
      @DetectorMode int mode) {

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

    boolean enableMultipleObjects =
        sharedPreferences.getBoolean(context.getString(prefKeyForMultipleObjects), false);
    boolean enableClassification =
        sharedPreferences.getBoolean(context.getString(prefKeyForClassification), true);

    ObjectDetectorOptions.Builder builder =
        new ObjectDetectorOptions.Builder().setDetectorMode(mode);
    if (enableMultipleObjects) {
      builder.enableMultipleObjects();
    }
    if (enableClassification) {
      builder.enableClassification();
    }
    return builder.build();
  }

  public static CustomObjectDetectorOptions getCustomObjectDetectorOptionsForStillImage(
      Context context, LocalModel localModel) {
    return getCustomObjectDetectorOptions(
        context,
        localModel,
        R.string.pref_key_still_image_object_detector_enable_multiple_objects,
        R.string.pref_key_still_image_object_detector_enable_classification,
        CustomObjectDetectorOptions.SINGLE_IMAGE_MODE);
  }

  public static CustomObjectDetectorOptions getCustomObjectDetectorOptionsForLivePreview(
      Context context, LocalModel localModel) {
    return getCustomObjectDetectorOptions(
        context,
        localModel,
        R.string.pref_key_live_preview_object_detector_enable_multiple_objects,
        R.string.pref_key_live_preview_object_detector_enable_classification,
        CustomObjectDetectorOptions.STREAM_MODE);
  }

  private static CustomObjectDetectorOptions getCustomObjectDetectorOptions(
      Context context,
      LocalModel localModel,
      @StringRes int prefKeyForMultipleObjects,
      @StringRes int prefKeyForClassification,
      @DetectorMode int mode) {

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);

    boolean enableMultipleObjects =
        sharedPreferences.getBoolean(context.getString(prefKeyForMultipleObjects), false);
    boolean enableClassification =
        sharedPreferences.getBoolean(context.getString(prefKeyForClassification), true);

    CustomObjectDetectorOptions.Builder builder =
        new CustomObjectDetectorOptions.Builder(localModel).setDetectorMode(mode);
    if (enableMultipleObjects) {
      builder.enableMultipleObjects();
    }
    if (enableClassification) {
      builder.enableClassification().setMaxPerObjectLabelCount(1);
    }
    return builder.build();
  }

  public static FaceDetectorOptions getFaceDetectorOptions(Context context) {
    int landmarkMode =
        getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_face_detection_landmark_mode,
            FaceDetectorOptions.LANDMARK_MODE_NONE);
    int contourMode =
        getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_face_detection_contour_mode,
            FaceDetectorOptions.CONTOUR_MODE_ALL);
    int classificationMode =
        getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_face_detection_classification_mode,
            FaceDetectorOptions.CLASSIFICATION_MODE_NONE);
    int performanceMode =
        getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_face_detection_performance_mode,
            FaceDetectorOptions.PERFORMANCE_MODE_FAST);

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    boolean enableFaceTracking =
        sharedPreferences.getBoolean(
            context.getString(R.string.pref_key_live_preview_face_detection_face_tracking), false);
    float minFaceSize =
        Float.parseFloat(
            sharedPreferences.getString(
                context.getString(R.string.pref_key_live_preview_face_detection_min_face_size),
                "0.1"));

    FaceDetectorOptions.Builder optionsBuilder =
        new FaceDetectorOptions.Builder()
            .setLandmarkMode(landmarkMode)
            .setContourMode(contourMode)
            .setClassificationMode(classificationMode)
            .setPerformanceMode(performanceMode)
            .setMinFaceSize(minFaceSize);
    if (enableFaceTracking) {
      optionsBuilder.enableTracking();
    }
    return optionsBuilder.build();
  }

  public static PoseDetectorOptionsBase getPoseDetectorOptionsForLivePreview(Context context) {
    int performanceMode =
        getModeTypePreferenceValue(
            context,
            R.string.pref_key_live_preview_pose_detection_performance_mode,
            POSE_DETECTOR_PERFORMANCE_MODE_FAST);
    boolean preferGPU = preferGPUForPoseDetection(context);
    if (performanceMode == POSE_DETECTOR_PERFORMANCE_MODE_FAST) {
      PoseDetectorOptions.Builder builder =
          new PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE);
      if (preferGPU) {
        builder.setPreferredHardwareConfigs(PoseDetectorOptions.CPU_GPU);
      }
      return builder.build();
    } else {
      AccuratePoseDetectorOptions.Builder builder =
          new AccuratePoseDetectorOptions.Builder()
              .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE);
      if (preferGPU) {
        builder.setPreferredHardwareConfigs(AccuratePoseDetectorOptions.CPU_GPU);
      }
      return builder.build();
    }
  }

  public static PoseDetectorOptionsBase getPoseDetectorOptionsForStillImage(Context context) {
    int performanceMode =
        getModeTypePreferenceValue(
            context,
            R.string.pref_key_still_image_pose_detection_performance_mode,
            POSE_DETECTOR_PERFORMANCE_MODE_FAST);
    boolean preferGPU = preferGPUForPoseDetection(context);
    if (performanceMode == POSE_DETECTOR_PERFORMANCE_MODE_FAST) {
      PoseDetectorOptions.Builder builder =
          new PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE);
      if (preferGPU) {
        builder.setPreferredHardwareConfigs(PoseDetectorOptions.CPU_GPU);
      }
      return builder.build();
    } else {
      AccuratePoseDetectorOptions.Builder builder =
          new AccuratePoseDetectorOptions.Builder()
              .setDetectorMode(AccuratePoseDetectorOptions.SINGLE_IMAGE_MODE);
      if (preferGPU) {
        builder.setPreferredHardwareConfigs(AccuratePoseDetectorOptions.CPU_GPU);
      }
      return builder.build();
    }
  }

  public static boolean shouldGroupRecognizedTextInBlocks(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_group_recognized_text_in_blocks);
    return sharedPreferences.getBoolean(prefKey, false);
  }

  public static boolean showLanguageTag(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_show_language_tag);
    return sharedPreferences.getBoolean(prefKey, false);
  }

  public static boolean preferGPUForPoseDetection(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_pose_detector_prefer_gpu);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldShowPoseDetectionInFrameLikelihoodLivePreview(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey =
        context.getString(R.string.pref_key_live_preview_pose_detector_show_in_frame_likelihood);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldShowPoseDetectionInFrameLikelihoodStillImage(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey =
        context.getString(R.string.pref_key_still_image_pose_detector_show_in_frame_likelihood);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldPoseDetectionVisualizeZ(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_pose_detector_visualize_z);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldPoseDetectionRescaleZForVisualization(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_pose_detector_rescale_z);
    return sharedPreferences.getBoolean(prefKey, true);
  }

  public static boolean shouldPoseDetectionRunClassification(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_pose_detector_run_classification);
    return sharedPreferences.getBoolean(prefKey, false);
  }

  public static boolean shouldSegmentationEnableRawSizeMask(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_segmentation_raw_size_mask);
    return sharedPreferences.getBoolean(prefKey, false);
  }

  /**
   * Mode type preference is backed by {@link android.preference.ListPreference} which only support
   * storing its entry value as string type, so we need to retrieve as string and then convert to
   * integer.
   */
  private static int getModeTypePreferenceValue(
      Context context, @StringRes int prefKeyResId, int defaultValue) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(prefKeyResId);
    return Integer.parseInt(sharedPreferences.getString(prefKey, String.valueOf(defaultValue)));
  }

  public static boolean isCameraLiveViewportEnabled(Context context) {
    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    String prefKey = context.getString(R.string.pref_key_camera_live_viewport);
    return sharedPreferences.getBoolean(prefKey, false);
  }

  private PreferenceUtils() {}
}
