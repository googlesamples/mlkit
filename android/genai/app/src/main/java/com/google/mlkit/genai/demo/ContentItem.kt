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
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_CACHE_REQUEST
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_REQUEST_IMAGE
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_REQUEST_TEXT
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_REQUEST_TEXT_AND_IMAGES
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_REQUEST_TEXT_WITH_PREFIX_CACHE
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_REQUEST_TEXT_WITH_PROMPT_PREFIX
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_RESPONSE
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_RESPONSE_ERROR
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_RESPONSE_STREAMING
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_RESPONSE_STREAMING_THOUGHT
import com.google.mlkit.genai.demo.ContentAdapter.Companion.VIEW_TYPE_RESPONSE_THOUGHT

/**
 * Represents a generic content item that can be rendered in a RecyclerView and holds GenAI API
 * request or response data.
 */
sealed interface ContentItem {

  val viewType: Int

  /** A content item that contains only text. */
  data class TextItem(
    val text: String,
    val metadata: String? = null,
    val systemInstruction: String = DEFAULT_EMPTY_SYSTEM_INSTRUCTION,
    override val viewType: Int,
  ) : ContentItem {
    companion object {
      fun fromRequest(request: String, systemInstruction: String): TextItem =
        TextItem(request, null, systemInstruction, VIEW_TYPE_REQUEST_TEXT)

      fun fromResponse(response: String, metadata: String?): TextItem =
        TextItem(response, metadata, DEFAULT_EMPTY_SYSTEM_INSTRUCTION, VIEW_TYPE_RESPONSE)

      fun fromErrorResponse(response: String): TextItem =
        TextItem(response, null, DEFAULT_EMPTY_SYSTEM_INSTRUCTION, VIEW_TYPE_RESPONSE_ERROR)

      fun fromStreamingResponse(response: String): TextItem =
        TextItem(response, null, DEFAULT_EMPTY_SYSTEM_INSTRUCTION, VIEW_TYPE_RESPONSE_STREAMING)

      fun fromThoughtResponse(response: String): TextItem =
        TextItem(response, null, DEFAULT_EMPTY_SYSTEM_INSTRUCTION, VIEW_TYPE_RESPONSE_THOUGHT)

      fun fromStreamingThoughtResponse(response: String): TextItem =
        TextItem(
          response,
          null,
          DEFAULT_EMPTY_SYSTEM_INSTRUCTION,
          VIEW_TYPE_RESPONSE_STREAMING_THOUGHT,
        )
    }
  }

  /** A content item that contains only one image. */
  data class ImageItem(val imageUri: Uri, override val viewType: Int) : ContentItem {
    companion object {
      fun fromRequest(imageUri: Uri): ImageItem = ImageItem(imageUri, VIEW_TYPE_REQUEST_IMAGE)
    }
  }

  /** A content item that contains both text and one or more images. */
  data class TextAndImagesItem(
    val text: String,
    val imageUris: List<Uri>,
    val systemInstruction: String = DEFAULT_EMPTY_SYSTEM_INSTRUCTION,
    override val viewType: Int,
  ) : ContentItem {
    constructor(
      text: String,
      imageUris: List<Uri>,
      viewType: Int,
    ) : this(text, imageUris, DEFAULT_EMPTY_SYSTEM_INSTRUCTION, viewType)

    companion object {
      fun fromRequest(
        text: String,
        imageUris: List<Uri>,
        systemInstruction: String = DEFAULT_EMPTY_SYSTEM_INSTRUCTION,
      ): TextAndImagesItem =
        TextAndImagesItem(text, imageUris, systemInstruction, VIEW_TYPE_REQUEST_TEXT_AND_IMAGES)
    }
  }

  /** A content item that contains interleaved text and media parts. */
  data class InterleavedContentItem(
    val parts: List<com.google.mlkit.genai.prompt.Part>,
    val systemInstruction: String = DEFAULT_EMPTY_SYSTEM_INSTRUCTION,
    override val viewType: Int,
  ) : ContentItem {
    companion object {
      fun fromRequest(
        parts: List<com.google.mlkit.genai.prompt.Part>,
        systemInstruction: String = DEFAULT_EMPTY_SYSTEM_INSTRUCTION,
      ): InterleavedContentItem =
        InterleavedContentItem(
          parts,
          systemInstruction,
          com.google.mlkit.genai.demo.ContentAdapter.VIEW_TYPE_REQUEST_INTERLEAVED_CONTENT,
        )
    }
  }

  /** A content item that contains a prompt prefix and a dynamic suffix. */
  data class TextWithPromptPrefixItem(
    val promptPrefix: String,
    val dynamicSuffix: String,
    val systemInstruction: String,
    override val viewType: Int,
  ) : ContentItem {
    companion object {
      fun fromRequest(
        promptPrefix: String,
        dynamicSuffix: String,
        systemInstruction: String,
      ): TextWithPromptPrefixItem =
        TextWithPromptPrefixItem(
          promptPrefix,
          dynamicSuffix,
          systemInstruction,
          VIEW_TYPE_REQUEST_TEXT_WITH_PROMPT_PREFIX,
        )
    }
  }

  /** A content item that contains a prefix cache name and a dynamic suffix. */
  data class TextWithPrefixCacheItem(
    val cacheName: String,
    val dynamicSuffix: String,
    override val viewType: Int,
  ) : ContentItem {
    companion object {
      fun fromRequest(cacheName: String, dynamicSuffix: String): TextWithPrefixCacheItem =
        TextWithPrefixCacheItem(cacheName, dynamicSuffix, VIEW_TYPE_REQUEST_TEXT_WITH_PREFIX_CACHE)
    }
  }

  /** A content item that contains a cache request. */
  data class CacheRequestItem(
    val cacheName: String,
    val prefixToCache: String,
    override val viewType: Int,
  ) : ContentItem {
    companion object {
      fun fromRequest(cacheName: String, prefixToCache: String): CacheRequestItem =
        CacheRequestItem(cacheName, prefixToCache, VIEW_TYPE_CACHE_REQUEST)
    }
  }

  companion object {
    val DEFAULT_EMPTY_SYSTEM_INSTRUCTION = ""
  }
}
