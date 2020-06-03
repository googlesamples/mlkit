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

package com.google.mlkit.md.objectdetection

import android.graphics.PointF
import android.util.Log
import android.util.SparseArray
import androidx.annotation.MainThread
import androidx.core.util.forEach
import androidx.core.util.set
import com.google.android.gms.tasks.Task
import com.google.mlkit.md.camera.CameraReticleAnimator
import com.google.mlkit.md.camera.GraphicOverlay
import com.google.mlkit.md.R
import com.google.mlkit.md.camera.WorkflowModel
import com.google.mlkit.md.camera.FrameProcessorBase
import com.google.mlkit.md.settings.PreferenceUtils
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.md.InputInfo
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.ObjectDetectorOptionsBase
import java.io.IOException
import java.util.ArrayList
import kotlin.math.hypot

/** A processor to run object detector in multi-objects mode.  */
class MultiObjectProcessor(
    graphicOverlay: GraphicOverlay,
    private val workflowModel: WorkflowModel,
    private val customModelPath: String? = null
) :
    FrameProcessorBase<List<DetectedObject>>() {
    private val confirmationController: ObjectConfirmationController = ObjectConfirmationController(graphicOverlay)
    private val cameraReticleAnimator: CameraReticleAnimator = CameraReticleAnimator(graphicOverlay)
    private val objectSelectionDistanceThreshold: Int = graphicOverlay
        .resources
        .getDimensionPixelOffset(R.dimen.object_selection_distance_threshold)
    private val detector: ObjectDetector

    // Each new tracked object plays appearing animation exactly once.
    private val objectDotAnimatorArray = SparseArray<ObjectDotAnimator>()

    init {
        val options: ObjectDetectorOptionsBase
        val isClassificationEnabled = PreferenceUtils.isClassificationEnabled(graphicOverlay.context)

        if (customModelPath != null) {
            val localModel = LocalModel.Builder()
                .setAssetFilePath(customModelPath)
                .build()
            options = CustomObjectDetectorOptions.Builder(localModel)
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification() // Always enable classification for custom models
                .build()
        } else {
            val optionsBuilder = ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            if (isClassificationEnabled) {
                optionsBuilder.enableClassification()
            }
            options = optionsBuilder.build()
        }

        this.detector = ObjectDetection.getClient(options)
    }

    override fun stop() {
        super.stop()
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close object detector!", e)
        }
    }

    override fun detectInImage(image: InputImage): Task<List<DetectedObject>> {
        return detector.process(image)
    }

    @MainThread
    override fun onSuccess(
        inputInfo: InputInfo,
        results: List<DetectedObject>,
        graphicOverlay: GraphicOverlay
    ) {
        var objects = results
        if (!workflowModel.isCameraLive) {
            return
        }

        if (customModelPath != null) {
            objects = results.filter { result -> DetectedObjectInfo.hasValidLabels(result) }
        } else if (PreferenceUtils.isClassificationEnabled(graphicOverlay.context)) {
            val qualifiedObjects = ArrayList<DetectedObject>()
            for (result in objects) {
                qualifiedObjects.add(result)
            }
            objects = qualifiedObjects
        }

        removeAnimatorsFromUntrackedObjects(objects)

        graphicOverlay.clear()

        var selectedObject: DetectedObjectInfo? = null
        for (i in objects.indices) {
            val result = objects[i]
            if (selectedObject == null && shouldSelectObject(graphicOverlay, result)) {
                selectedObject = DetectedObjectInfo(result, i, inputInfo)
                // Starts the object confirmation once an object is regarded as selected.
                confirmationController.confirming(result.trackingId)
                graphicOverlay.add(ObjectConfirmationGraphic(graphicOverlay, confirmationController))

                graphicOverlay.add(
                    ObjectGraphicInMultiMode(
                        graphicOverlay, selectedObject, confirmationController
                    )
                )
            } else {
                if (confirmationController.isConfirmed) {
                    // Don't render other objects when an object is in confirmed state.
                    continue
                }

                val trackingId = result.trackingId ?: return
                val objectDotAnimator = objectDotAnimatorArray.get(trackingId) ?: let {
                    ObjectDotAnimator(graphicOverlay).apply {
                        start()
                        objectDotAnimatorArray[trackingId] = this
                    }
                }
                graphicOverlay.add(
                    ObjectDotGraphic(
                        graphicOverlay, DetectedObjectInfo(result, i, inputInfo), objectDotAnimator
                    )
                )
            }
        }

        if (selectedObject == null) {
            confirmationController.reset()
            graphicOverlay.add(ObjectReticleGraphic(graphicOverlay, cameraReticleAnimator))
            cameraReticleAnimator.start()
        } else {
            cameraReticleAnimator.cancel()
        }

        graphicOverlay.invalidate()

        if (selectedObject != null) {
            workflowModel.confirmingObject(selectedObject, confirmationController.progress)
        } else {
            workflowModel.setWorkflowState(
                if (objects.isEmpty()) {
                    WorkflowModel.WorkflowState.DETECTING
                } else {
                    WorkflowModel.WorkflowState.DETECTED
                }
            )
        }
    }

    private fun removeAnimatorsFromUntrackedObjects(detectedObjects: List<DetectedObject>) {
        val trackingIds = detectedObjects.mapNotNull { it.trackingId }
        // Stop and remove animators from the objects that have lost tracking.
        val removedTrackingIds = ArrayList<Int>()
        objectDotAnimatorArray.forEach { key, value ->
            if (!trackingIds.contains(key)) {
                value.cancel()
                removedTrackingIds.add(key)
            }
        }
        removedTrackingIds.forEach {
            objectDotAnimatorArray.remove(it)
        }
    }

    private fun shouldSelectObject(graphicOverlay: GraphicOverlay, visionObject: DetectedObject): Boolean {
        // Considers an object as selected when the camera reticle touches the object dot.
        val box = graphicOverlay.translateRect(visionObject.boundingBox)
        val objectCenter = PointF((box.left + box.right) / 2f, (box.top + box.bottom) / 2f)
        val reticleCenter = PointF(graphicOverlay.width / 2f, graphicOverlay.height / 2f)
        val distance =
            hypot((objectCenter.x - reticleCenter.x).toDouble(), (objectCenter.y - reticleCenter.y).toDouble())
        return distance < objectSelectionDistanceThreshold
    }

    override fun onFailure(e: Exception) {
        Log.e(TAG, "Object detection failed!", e)
    }

    companion object {

        private const val TAG = "MultiObjectProcessor"
    }
}
