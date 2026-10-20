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
import com.google.mlkit.genai.schema.tools.FunctionSchema
import com.google.mlkit.genai.schema.tools.FunctionType

/** Mock tools for testing function calling in the demo app. */
object MockTools {
  val tools: List<FunctionDeclaration> =
    listOf(
      FunctionDeclaration(
        name = "get_current_weather",
        description = "Get the current weather for a location",
        parameters =
          FunctionSchema(
            type = FunctionType.OBJECT,
            properties =
              mapOf(
                "location" to
                  FunctionSchema(
                    type = FunctionType.STRING,
                    description = "The city and state, e.g. San Francisco, CA",
                  ),
                "unit" to
                  FunctionSchema(
                    type = FunctionType.STRING,
                    description = "The temperature unit, either 'celsius' or 'fahrenheit'",
                  ),
              ),
            required = listOf("location"),
          ),
      ),
      FunctionDeclaration(
        name = "turn_on_light",
        description = "Turn on the light in a specific room",
        parameters =
          FunctionSchema(
            type = FunctionType.OBJECT,
            properties =
              mapOf(
                "room" to
                  FunctionSchema(
                    type = FunctionType.STRING,
                    description =
                      "The room where the light should be turned on, e.g. living room, bedroom",
                  ),
                "brightness" to
                  FunctionSchema(
                    type = FunctionType.INTEGER,
                    description = "The brightness level, from 1 to 100",
                  ),
              ),
            required = listOf("room"),
          ),
      ),
      FunctionDeclaration(
        name = "open_app",
        description = "Open a specific application on the device",
        parameters =
          FunctionSchema(
            type = FunctionType.OBJECT,
            properties =
              mapOf(
                "appName" to
                  FunctionSchema(
                    type = FunctionType.STRING,
                    description = "The name of the app to open, e.g. Camera, Maps, YouTube",
                  )
              ),
            required = listOf("appName"),
          ),
      ),
    )
}
