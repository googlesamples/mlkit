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

package com.google.mlkit.samples.nl.smartreply.kotlin.model

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.mlkit.samples.nl.smartreply.R

class Message(val text: String, val isLocalUser: Boolean, val timestamp: Long) {

  fun getIcon(context: Context): Drawable {
    val drawable =
      ContextCompat.getDrawable(context, R.drawable.ic_tag_faces_black_24dp)
        ?: throw IllegalStateException("Could not get drawable ic_tag_faces_black_24dp")

    if (isLocalUser) {
      DrawableCompat.setTint(drawable.mutate(), Color.BLUE)
    } else {
      DrawableCompat.setTint(drawable.mutate(), Color.RED)
    }

    return drawable
  }
}
