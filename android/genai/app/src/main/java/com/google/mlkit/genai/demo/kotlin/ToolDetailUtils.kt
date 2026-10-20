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

import com.google.mlkit.genai.schema.tools.FunctionDeclaration
import com.google.mlkit.genai.schema.tools.FunctionType
import org.json.JSONArray
import org.json.JSONObject

/** Utility to format [FunctionDeclaration]s for display. */
object ToolDetailUtils {

  fun getToolDetailJson(declarations: List<FunctionDeclaration>): String {
    val toolsArray = JSONArray()
    for (decl in declarations) {
      val toolJson = JSONObject()
      toolJson.put("name", decl.name)
      if (decl.description.isNotEmpty()) {
        toolJson.put("description", decl.description)
      }

      decl.parameters?.let { schema ->
        val paramsJson = JSONObject()
        schema.properties?.forEach { (name, prop) ->
          val paramJson = JSONObject()
          prop.description?.let { paramJson.put("description", it) }
          paramJson.put("required", schema.required?.contains(name) == true)
          val typeName =
            when (prop.type) {
              FunctionType.STRING -> "string"
              FunctionType.NUMBER -> "number"
              FunctionType.INTEGER -> "integer"
              FunctionType.BOOLEAN -> "boolean"
              FunctionType.ARRAY -> "array"
              FunctionType.OBJECT -> "object"
              FunctionType.NULL -> "null"
              else -> "unspecified"
            }
          paramJson.put("type", typeName)
          paramsJson.put(name, paramJson)
        }
        if (paramsJson.length() > 0) {
          toolJson.put("parameters", paramsJson)
        }
      }
      toolsArray.put(toolJson)
    }
    return toolsArray.toString(2)
  }
}
