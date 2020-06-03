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

import android.os.CountDownTimer
import com.google.mlkit.md.camera.GraphicOverlay
import com.google.mlkit.md.settings.PreferenceUtils

/**
 * Controls the progress of object confirmation before performing additional operation on the
 * detected object.
 */
internal class ObjectConfirmationController
/**
 * @param graphicOverlay Used to refresh camera overlay when the confirmation progress updates.
 */
    (graphicOverlay: GraphicOverlay) {

    private val countDownTimer: CountDownTimer

    private var objectId: Int? = null

    /** Returns the confirmation progress described as a float value in the range of [0, 1].  */
    var progress = 0f
        private set

    val isConfirmed: Boolean
        get() = progress.compareTo(1f) == 0

    init {
        val confirmationTimeMs = PreferenceUtils.getConfirmationTimeMs(graphicOverlay.context).toLong()
        countDownTimer = object : CountDownTimer(confirmationTimeMs, /* countDownInterval= */ 20) {
            override fun onTick(millisUntilFinished: Long) {
                progress = (confirmationTimeMs - millisUntilFinished).toFloat() / confirmationTimeMs
                graphicOverlay.invalidate()
            }

            override fun onFinish() {
                progress = 1f
            }
        }
    }

    fun confirming(objectId: Int?) {
        if (objectId == this.objectId) {
            // Do nothing if it's already in confirming.
            return
        }

        reset()
        this.objectId = objectId
        countDownTimer.start()
    }

    fun reset() {
        countDownTimer.cancel()
        objectId = null
        progress = 0f
    }
}
