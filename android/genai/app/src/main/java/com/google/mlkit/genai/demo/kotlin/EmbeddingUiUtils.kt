/*
 * Copyright 2026 Google LLC. All rights reserved.
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
package com.google.mlkit.genai.demo.kotlin

import java.util.Locale

/** Shared utility helpers for common Text Embedding UI presentations. */
object EmbeddingUiUtils {

  /**
   * Formats a high-dimensional vector float array cleanly by taking the first 5 and last 5
   * dimensions.
   */
  @JvmStatic
  fun formatVector(vector: FloatArray): String {
    if (vector.isEmpty()) return "[]"
    if (vector.size <= 10) {
      return vector.joinToString(prefix = "[", postfix = "]", separator = ", ") {
        String.format(Locale.ENGLISH, "%.4f", it)
      }
    }
    val first5 =
      vector.take(5).joinToString(separator = ", ") { String.format(Locale.ENGLISH, "%.4f", it) }
    val last5 =
      vector.takeLast(5).joinToString(separator = ", ") {
        String.format(Locale.ENGLISH, "%.4f", it)
      }
    return "[$first5, ..., $last5]"
  }
}
