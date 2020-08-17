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

package com.google.mlkit.vision.demo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import com.google.mlkit.vision.demo.java.ChooserActivity

class EntryChoiceActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_vision_entry_choice)

    findViewById<TextView>(R.id.java_entry_point).setOnClickListener {
      val intent = Intent(this@EntryChoiceActivity, ChooserActivity::class.java)
      startActivity(intent)
    }

    findViewById<TextView>(R.id.kotlin_entry_point).setOnClickListener {
      val intent =
        Intent(
          this@EntryChoiceActivity,
          com.google.mlkit.vision.demo.kotlin.ChooserActivity::class.java
        )
      startActivity(intent)
    }
  }
}
