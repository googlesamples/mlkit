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

package com.google.mlkit.samples.nl.smartreply.java.model;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import com.google.mlkit.samples.nl.smartreply.R;

/** Represents a chat message. */
public class Message {

  public final String text;
  public final boolean isLocalUser;
  public final long timestamp;

  public Message(String text, boolean isLocalUser, long timestamp) {
    this.text = text;
    this.isLocalUser = isLocalUser;
    this.timestamp = timestamp;
  }

  @NonNull
  public Drawable getIcon(Context context) {
    Drawable drawable = ContextCompat.getDrawable(context, R.drawable.ic_tag_faces_black_24dp);
    if (drawable == null) {
      throw new IllegalStateException("Could not get drawable ic_tag_faces_black_24dp");
    }

    // See:
    // https://stackoverflow.com/questions/36731919/drawablecompat-settint-not-working-on-api-19
    drawable = DrawableCompat.wrap(drawable);
    int color = isLocalUser ? Color.BLUE : Color.RED;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      DrawableCompat.setTint(drawable.mutate(), color);
    } else {
      drawable.mutate().setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    return drawable;
  }
}
