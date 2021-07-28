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

package com.google.mlkit.samples.nl.smartreply

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.google.mlkit.samples.nl.smartreply.java.MainActivityJava
import com.google.mlkit.samples.nl.smartreply.kotlin.MainActivityKotlin
import com.mlkit.example.internal.BaseEntryChoiceActivity
import com.mlkit.example.internal.Choice

class EntryChoiceActivity : BaseEntryChoiceActivity() {

  override fun getChoices(): List<Choice> {
    return listOf(
      Choice(
        "Java",
        "Run the ML Kit Smart Reply quickstart written in Java.",
        Intent(this, MainActivityJava::class.java)
      ),
      Choice(
        "Kotlin",
        "Run the ML Kit Smart Reply quickstart written in Kotlin.",
        Intent(this, MainActivityKotlin::class.java)
      )
    )
  }

  companion object {
    init {
      AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }
  }
}
