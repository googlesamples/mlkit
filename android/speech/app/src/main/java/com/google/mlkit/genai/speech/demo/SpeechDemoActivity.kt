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
package com.google.mlkit.genai.speech.demo

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity

/** Landing activity for ML Kit Speech API demo. */
class SpeechDemoActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_speech_demo)

    findViewById<View>(R.id.card_speech_recognition).setOnClickListener {
      startActivity(Intent(this, SpeechRecognitionActivity::class.java))
    }

    findViewById<View>(R.id.card_speech_translation).setOnClickListener {
      startActivity(Intent(this, SpeechTranslationActivity::class.java))
    }

    findViewById<View>(R.id.card_text_to_speech).setOnClickListener {
      startActivity(Intent(this, TextToSpeechActivity::class.java))
    }
  }
}
