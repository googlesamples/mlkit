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

package com.google.mlkit.md.camera

import androidx.annotation.GuardedBy
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskExecutors
import com.google.android.odml.image.MediaImageExtractor
import com.google.android.odml.image.MlImage
import com.google.mlkit.md.*

/** Abstract base class of [FrameProcessor].  */
abstract class Frame2ProcessorBase<T> : Frame2Processor {

    // To keep the reference of current detection task
    @GuardedBy("this")
    private var currentDetectionTask: Task<T>? = null

    private val executor = ScopedExecutor(TaskExecutors.MAIN_THREAD)

    @Synchronized
    override fun process(image: MlImage, graphicOverlay: GraphicOverlay): Boolean {
        return processLatestFrame(image, graphicOverlay)
    }

    @Synchronized
    private fun processLatestFrame(frame: MlImage, graphicOverlay: GraphicOverlay): Boolean {
        return if(currentDetectionTask?.isComplete == false){
            false
        }else {
            //val startMs = SystemClock.elapsedRealtime()
            currentDetectionTask = detectInImage(frame).addOnCompleteListener(executor) { task ->
                if (task.isSuccessful){
                    //Log.d(TAG, "Latency is: ${SystemClock.elapsedRealtime() - startMs}")
                    MediaImageExtractor.extract(frame).let {
                        this@Frame2ProcessorBase.onSuccess(CameraInputInfo(it.planes[0].buffer, FrameMetadata(frame.width,
                            frame.height,frame.rotation)), task.result, graphicOverlay)
                    }
                }
                else{
                    //Log.d(TAG, "Detect In Image Failure: ${e.message}")
                    this@Frame2ProcessorBase.onFailure(task.exception)
                }

                //Close the processing frame
                frame.close()
            }
            true
        }
    }

    override fun stop() {
        executor.shutdown()
    }

    protected abstract fun detectInImage(image: MlImage): Task<T>

    /** Be called when the detection succeeds.  */
    protected abstract fun onSuccess(
        inputInfo: InputInfo,
        results: T,
        graphicOverlay: GraphicOverlay
    )

    protected abstract fun onFailure(e: Exception?)

    companion object {
        private const val TAG = "FrameProcessorBase"
    }
}
