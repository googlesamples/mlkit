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

package com.google.mlkit.genai.demo.java;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.mlkit.genai.demo.ContentItem.TextItem;
import com.google.mlkit.genai.demo.R;

/** Base Activity for APIs that accept text input as request. */
abstract class TextInputBaseActivity extends BaseActivity<TextItem> {

  private EditText requestEditText;
  private Button sendButton;

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestEditText = findViewById(R.id.request_edit_text);
    sendButton = findViewById(R.id.send_button);
    sendButton.setOnClickListener(
        view -> {
          String request = requestEditText.getText().toString();
          if (TextUtils.isEmpty(request)) {
            Toast.makeText(this, R.string.input_message_is_empty, Toast.LENGTH_SHORT).show();
            return;
          }
          onSend(TextItem.Companion.fromRequest(request));
        });
  }

  @Override
  protected void startGeneratingUi() {
    sendButton.setEnabled(false);
    sendButton.setText(R.string.generating);
    requestEditText.setText(R.string.empty);
  }

  @Override
  protected void endGeneratingUi(String debugInfo) {
    sendButton.setEnabled(true);
    sendButton.setText(R.string.button_send);
  }
}
