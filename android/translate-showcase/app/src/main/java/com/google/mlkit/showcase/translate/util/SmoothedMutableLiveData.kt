/*
 * Copyright 2020 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.google.mlkit.showcase.translate.util

import android.os.Handler
import androidx.lifecycle.MutableLiveData

/**
 * A {@link MutableLiveData} that only emits change events when the underlying data has been stable
 * for the configured amount of time.
 *
 * @param duration time delay to wait in milliseconds
 */
class SmoothedMutableLiveData<T>(private val duration: Long) : MutableLiveData<T>() {
    private var pendingValue: T? = null
    private val runnable = Runnable {
        super.setValue(pendingValue)
    }

    override fun setValue(value: T) {
        if (value != pendingValue) {
            pendingValue = value
            Handler().removeCallbacks(runnable)
            Handler().postDelayed(runnable, duration)
        }
    }
}