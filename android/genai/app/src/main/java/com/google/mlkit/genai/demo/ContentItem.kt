/*
 * Copyright 2025 Google LLC. All rights reserved.
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
package com.google.mlkit.genai.demo

import android.net.Uri
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_REQUEST_IMAGE
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_REQUEST_TEXT
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_RESPONSE

/**
 * Represents a generic content item that can be rendered in a RecyclerView and holds GenAI API
 * request or response data.
 */
sealed interface ContentItem {

  val viewType: Int

  /** A content item that contains only text. */
  data class TextItem(val text: String, override val viewType: Int) : ContentItem {
    companion object {
      fun fromRequest(request: String): TextItem = TextItem(request, VIEW_TYPE_REQUEST_TEXT)

      fun fromResponse(response: String): TextItem = TextItem(response, VIEW_TYPE_RESPONSE)
    }
  }

  /** A content item that contains only one image. */
  data class ImageItem(val imageUri: Uri, override val viewType: Int) : ContentItem {
    companion object {
      fun fromRequest(imageUri: Uri): ImageItem = ImageItem(imageUri, VIEW_TYPE_REQUEST_IMAGE)
    }
  }
}
