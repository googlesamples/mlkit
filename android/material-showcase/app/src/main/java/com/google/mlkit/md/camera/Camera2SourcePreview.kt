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

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.FrameLayout
import com.google.mlkit.md.R
import com.google.mlkit.md.Utils
import java.io.IOException

/** Preview the camera image in the screen.  */
class Camera2SourcePreview(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {
    
    private val surfaceView: SurfaceView = SurfaceView(context).apply {
        holder.addCallback(SurfaceCallback())
        addView(this)
    }
    private var graphicOverlay: GraphicOverlay? = null
    private var startRequested = false
    private var startProcessing = false
    private var surfaceAvailable = false
    private var cameraSource: Camera2Source? = null
    private var cameraPreviewSize: Size? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        graphicOverlay = findViewById(R.id.camera_preview_graphic_overlay)
    }

    @Throws(Exception::class)
    fun start(cameraSource: Camera2Source) {
        this.cameraSource = cameraSource
        startRequested = true
        startIfReady()
    }

    @Throws(Exception::class)
    fun stop() {
        cameraSource?.let {
            it.stop()
            cameraSource = null
            startRequested = false
        }
    }

    @Throws(Exception::class)
    private fun startIfReady() {
        if (startRequested && surfaceAvailable && !startProcessing) {
            startProcessing = true
            Log.d(TAG, "Starting camera")
            cameraSource?.start(surfaceView.holder, object : CameraStartCallback{
                override fun onSuccess() {
                    post {
                        /*requestLayout()
                        graphicOverlay?.let { overlay ->
                            cameraSource?.let {
                                overlay.setCameraInfo(it)
                            }
                            overlay.clear()
                        }*/
                        startRequested = false
                        startProcessing = false
                    }

                }

                override fun onFailure(error: Exception?) {
                    startRequested = false
                    startProcessing = false
                }

            })

        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val layoutWidth = right - left
        val layoutHeight = bottom - top

        cameraSource?.previewSize?.let { cameraPreviewSize = it }

        val previewSizeRatio = cameraPreviewSize?.let { size ->
            if (Utils.isPortraitMode(context)) {
                // Camera's natural orientation is landscape, so need to swap width and height.
                size.height.toFloat() / size.width
            } else {
                size.width.toFloat() / size.height
            }
        } ?: layoutWidth.toFloat() / layoutHeight.toFloat()

        // Match the width of the child view to its parent.
        val childHeight = (layoutWidth / previewSizeRatio).toInt()
        if (childHeight <= layoutHeight) {
            for (i in 0 until childCount) {
                getChildAt(i).layout(0, 0, layoutWidth, childHeight)
            }
        } else {
            // When the child view is too tall to be fitted in its parent: If the child view is
            // static overlay view container (contains views such as bottom prompt chip), we apply
            // the size of the parent view to it. Otherwise, we offset the top/bottom position
            // equally to position it in the center of the parent.
            val excessLenInHalf = (childHeight - layoutHeight) / 2
            for (i in 0 until childCount) {
                val childView = getChildAt(i)
                when (childView.id) {
                    R.id.static_overlay_container -> {
                        childView.layout(0, 0, layoutWidth, layoutHeight)
                    }
                    else -> {
                        childView.layout(
                            0, -excessLenInHalf, layoutWidth, layoutHeight + excessLenInHalf
                        )
                    }
                }
            }
        }

        try {
            startIfReady()
        } catch (e: Exception) {
            Log.e(TAG, "Could not start camera source.", e)
        }
    }

    private inner class SurfaceCallback : SurfaceHolder.Callback {
        override fun surfaceCreated(surface: SurfaceHolder) {
            surfaceAvailable = true
            try {
                startIfReady()
            } catch (e: Exception) {
                Log.e(TAG, "Could not start camera source.", e)
            }
        }

        override fun surfaceDestroyed(surface: SurfaceHolder) {
            surfaceAvailable = false
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        }
    }

    companion object {
        private const val TAG = "CameraSourcePreview"
    }
}
