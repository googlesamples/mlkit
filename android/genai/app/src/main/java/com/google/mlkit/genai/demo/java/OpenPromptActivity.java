/*
 * Copyright 2025 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mlkit.genai.demo.java;

import static com.google.common.util.concurrent.Futures.immediateFailedFuture;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia;
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.genai.common.DownloadCallback;
import com.google.mlkit.genai.common.StreamingCallback;
import com.google.mlkit.genai.demo.ContentItem;
import com.google.mlkit.genai.demo.GenerationConfigDialog;
import com.google.mlkit.genai.demo.GenerationConfigUtils;
import com.google.mlkit.genai.demo.R;
import com.google.mlkit.genai.prompt.CachedContext;
import com.google.mlkit.genai.prompt.Candidate;
import com.google.mlkit.genai.prompt.Content;
import com.google.mlkit.genai.prompt.CountTokensResponse;
import com.google.mlkit.genai.prompt.CreateCachedContextRequest;
import com.google.mlkit.genai.prompt.GenerateContentRequest;
import com.google.mlkit.genai.prompt.GenerateContentResponse;
import com.google.mlkit.genai.prompt.Generation;
import com.google.mlkit.genai.prompt.GenerationConfig;
import com.google.mlkit.genai.prompt.GenerativeModel;
import com.google.mlkit.genai.prompt.ImagePart;
import com.google.mlkit.genai.prompt.Part;
import com.google.mlkit.genai.prompt.PromptPrefix;
import com.google.mlkit.genai.prompt.SystemInstruction;
import com.google.mlkit.genai.prompt.TextPart;
import com.google.mlkit.genai.prompt.java.CachesFutures;
import com.google.mlkit.genai.prompt.java.GenerativeModelFutures;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An activity that demonstrates a chat-like interface for the Open Prompt API in Java, allowing
 * requests with both text and multiple images, and including generation configuration.
 */
public class OpenPromptActivity extends BaseActivity<ContentItem>
    implements GenerationConfigDialog.OnConfigUpdateListener {

  private static final String TAG = OpenPromptActivity.class.getSimpleName();
  private static final int ACTION_CLEAR_CACHES = 1000;

  private GenerativeModelFutures generativeModelFutures;
  private CachesFutures cachesFutures;
  private EditText requestEditText;
  private Button sendButton;
  private ImageButton selectImageButton;
  private ImageView imagePreview;
  private Button configButton;
  private Button modelReleaseStageButton;
  private Button modelPreferenceButton;
  private EditText prefixEditText;
  private CheckBox createCacheCheckBox;

  @Nullable private Uri selectedImageUri = null;

  @Nullable private Float curTemperature = null;
  @Nullable private Integer curTopK = null;
  @Nullable private Integer curSeed = null;
  @Nullable private Integer curMaxOutputTokens = null;
  @Nullable private Integer curCandidateCount = null;
  private boolean useDefaultConfig = false;
  private boolean useExplicitCache = false;
  private EditText systemPromptEditText;

  private ActivityResultLauncher<PickVisualMediaRequest> pickImageLauncher;

  private void insertMediaToEditText(Uri uri) {
    Editable editable = requestEditText.getEditableText();
    int cursorPosition = requestEditText.getSelectionStart();

    editable.insert(cursorPosition, " ");

    Drawable drawable = getResources().getDrawable(android.R.drawable.ic_menu_gallery, null);
    int thumbnailSize = getResources().getDimensionPixelSize(R.dimen.interleaved_thumbnail_size);
    drawable.setBounds(0, 0, thumbnailSize, thumbnailSize); // Set fixed bounds for thumbnail
    ImageSpan span = new ImageSpan(drawable, uri.toString());

    editable.setSpan(span, cursorPosition, cursorPosition + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    requestEditText.setSelection(cursorPosition + 1);
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    requestEditText = findViewById(R.id.request_edit_text);
    sendButton = findViewById(R.id.send_button);
    selectImageButton = findViewById(R.id.select_image_prompt_button);
    imagePreview = findViewById(R.id.image_thumbnail_preview_input);
    configButton = findViewById(R.id.config_button);
    prefixEditText = findViewById(R.id.prefix_edit_text);
    createCacheCheckBox = findViewById(R.id.create_cache_checkbox);
    systemPromptEditText = findViewById(R.id.system_prompt_edit_text);
    createCacheCheckBox.setOnCheckedChangeListener(
        (buttonView, isChecked) -> {
          prefixEditText.setText("");
          updateRequestEditTextHint();
          updatePrefixEditTextState();
        });

    pickImageLauncher =
        registerForActivityResult(
            new PickMultipleVisualMedia(10),
            uris -> {
              if (uris != null && !uris.isEmpty()) {
                for (Uri uri : uris) {
                  insertMediaToEditText(uri);
                }
                Toast.makeText(this, uris.size() + " images selected", Toast.LENGTH_SHORT).show();
              } else {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show();
              }
            });

    selectImageButton.setOnClickListener(
        v ->
            pickImageLauncher.launch(
                new PickVisualMediaRequest.Builder().setMediaType(ImageOnly.INSTANCE).build()));

    configButton.setOnClickListener(
        v -> new GenerationConfigDialog().show(getSupportFragmentManager(), null));

    sendButton.setOnClickListener(
        v -> {
          if (useExplicitCache) {
            String cacheName = prefixEditText.getText().toString().trim();
            if (TextUtils.isEmpty(cacheName)) {
              Toast.makeText(this, R.string.cache_name_empty, Toast.LENGTH_SHORT).show();
              return;
            }
            Editable requestEditable = requestEditText.getText();
            String text = requestEditable != null ? requestEditable.toString().trim() : "";
            if (createCacheCheckBox.isChecked()) {
              if (TextUtils.isEmpty(text)) {
                Toast.makeText(this, R.string.prefix_to_cache_empty, Toast.LENGTH_SHORT).show();
                return;
              }
              onSend(ContentItem.CacheRequestItem.Companion.fromRequest(cacheName, text));
            } else {
              if (TextUtils.isEmpty(text)) {
                Toast.makeText(this, R.string.input_message_is_empty, Toast.LENGTH_SHORT).show();
                return;
              }
              onSend(ContentItem.TextWithPrefixCacheItem.Companion.fromRequest(cacheName, text));
            }
            requestEditText.setText("");
            return;
          }

          String prefixText = prefixEditText.getText().toString().trim();
          CompletableFuture<Void> unused =
              getContentFromEditText()
                  .thenAccept(
                      parts -> {
                        if (parts.isEmpty()
                            || (parts.size() == 1
                                && parts.get(0) instanceof TextPart
                                && ((TextPart) parts.get(0)).getTextString().trim().isEmpty())) {
                          runOnUiThread(
                              () ->
                                  Toast.makeText(
                                          OpenPromptActivity.this,
                                          R.string.input_message_is_empty,
                                          Toast.LENGTH_SHORT)
                                      .show());
                          return;
                        }

                        List<Part> resolvedParts = new ArrayList<>(parts);
                        if (!TextUtils.isEmpty(prefixText)) {
                          resolvedParts.add(0, new TextPart(prefixText));
                        }

                        String systemInstruction = systemPromptEditText.getText().toString().trim();
                        ContentItem requestItem =
                            ContentItem.InterleavedContentItem.Companion.fromRequest(
                                resolvedParts, systemInstruction);

                        runOnUiThread(
                            () -> {
                              onSend(requestItem);
                              requestEditText.setText("");
                              systemPromptEditText.setText("");
                              imagePreview.setVisibility(View.GONE);
                              selectedImageUri = null;
                            });
                      });
        });

    onConfigUpdated();

    initGenerator();
  }

  @Override
  public void onConfigUpdated() {
    useDefaultConfig = GenerationConfigUtils.getUseDefaultConfig(getApplicationContext());
    if (useDefaultConfig) {
      // Cache cannot be used in the simple utility API.
      GenerationConfigUtils.setUseExplicitCache(getApplicationContext(), false);
    }
    useExplicitCache = GenerationConfigUtils.getUseExplicitCache(getApplicationContext());

    if (useExplicitCache) {
      prefixEditText.setVisibility(View.VISIBLE);
      prefixEditText.setHint(R.string.hint_add_cache_name);
      createCacheCheckBox.setVisibility(View.VISIBLE);
      configButton.setVisibility(View.VISIBLE);
      selectImageButton.setVisibility(View.GONE);
      imagePreview.setVisibility(View.GONE);
      selectedImageUri = null;
    } else {
      prefixEditText.setVisibility(useDefaultConfig ? View.GONE : View.VISIBLE);
      prefixEditText.setHint(R.string.hint_add_prompt_prefix);
      createCacheCheckBox.setVisibility(View.GONE);
      configButton.setVisibility(useDefaultConfig ? View.GONE : View.VISIBLE);
      selectImageButton.setVisibility(useDefaultConfig ? View.GONE : View.VISIBLE);
      imagePreview.setVisibility(
          useDefaultConfig || selectedImageUri == null ? View.GONE : View.VISIBLE);
    }
    prefixEditText.setText("");
    requestEditText.setText("");
    updateRequestEditTextHint();
    updatePrefixEditTextState();

    curTemperature = GenerationConfigUtils.getTemperature(getApplicationContext());
    curTopK = GenerationConfigUtils.getTopK(getApplicationContext());
    curSeed = GenerationConfigUtils.getSeed(getApplicationContext());
    curCandidateCount = GenerationConfigUtils.getCandidateCount(getApplicationContext());
    curMaxOutputTokens = GenerationConfigUtils.getMaxOutputTokens(getApplicationContext());
  }

  private void updateRequestEditTextHint() {
    int hintResourceId = R.string.hint_type_a_message;
    if (useExplicitCache) {
      if (createCacheCheckBox.isChecked()) {
        hintResourceId = R.string.hint_add_prefix_to_cache;
      } else {
        hintResourceId = R.string.hint_add_suffix_for_inference;
      }
    }
    requestEditText.setHint(hintResourceId);
  }

  private void showCacheSelectionDialog() {
    Futures.addCallback(
        cachesFutures.list(),
        new FutureCallback<List<CachedContext>>() {
          @Override
          public void onSuccess(List<CachedContext> result) {
            if (result == null || result.isEmpty()) {
              Toast.makeText(
                      OpenPromptActivity.this, "No caches available to select", Toast.LENGTH_SHORT)
                  .show();
              return;
            }
            List<String> cacheNamesList = new ArrayList<>();
            for (CachedContext cache : result) {
              cacheNamesList.add(cache.getName());
            }
            String[] cacheNames = cacheNamesList.toArray(new String[0]);
            new AlertDialog.Builder(OpenPromptActivity.this)
                .setTitle("Select Cache")
                .setItems(cacheNames, (dialog, which) -> prefixEditText.setText(cacheNames[which]))
                .show();
          }

          @Override
          public void onFailure(Throwable t) {
            Toast.makeText(OpenPromptActivity.this, "Failed to list caches", Toast.LENGTH_SHORT)
                .show();
          }
        },
        ContextCompat.getMainExecutor(this));
  }

  private void updatePrefixEditTextState() {
    if (useExplicitCache && !createCacheCheckBox.isChecked()) {
      prefixEditText.setFocusable(false);
      prefixEditText.setClickable(true);
      prefixEditText.setOnClickListener(v -> showCacheSelectionDialog());
      prefixEditText.setHint(R.string.hint_select_cache_name);
    } else {
      prefixEditText.setFocusable(true);
      prefixEditText.setFocusableInTouchMode(true);
      prefixEditText.setClickable(false);
      prefixEditText.setOnClickListener(null);
      if (useExplicitCache) {
        prefixEditText.setHint(R.string.hint_add_cache_name);
      } else {
        prefixEditText.setHint(R.string.hint_add_prompt_prefix);
      }
    }
  }

  @Override
  protected int getLayoutResId() {
    return R.layout.activity_openprompt;
  }

  @Override
  protected ListenableFuture<List<ContentItem>> runInferenceImpl(
      ContentItem request, @Nullable StreamingCallback streamingCallback) {
    if (request instanceof ContentItem.CacheRequestItem cacheRequestItem) {
      return createCache(cacheRequestItem);
    }
    try {
      ListenableFuture<GenerateContentResponse> inferenceFuture;
      if (request instanceof ContentItem.TextItem textItem && useDefaultConfig) {
        // useDefaultConfig is used for the case where user wants to use utility function with
        // default config values
        inferenceFuture =
            (streamingCallback != null)
                ? generativeModelFutures.generateContent(textItem.getText(), streamingCallback)
                : generativeModelFutures.generateContent(textItem.getText());
      } else {
        GenerateContentRequest genRequest = buildGenerateContentRequest(request);
        inferenceFuture =
            (streamingCallback != null)
                ? generativeModelFutures.generateContent(genRequest, streamingCallback)
                : generativeModelFutures.generateContent(genRequest);
      }

      return Futures.transform(
          inferenceFuture,
          (GenerateContentResponse result) -> {
            if (result == null) {
              return ImmutableList.of();
            }

            ImmutableList.Builder<ContentItem> listBuilder = new ImmutableList.Builder<>();
            if (GenerationConfigUtils.getShowThinking(getApplicationContext())) {
              for (Candidate thought : result.getThoughtProcess()) {
                listBuilder.add(
                    ContentItem.TextItem.Companion.fromThoughtResponse(thought.getText()));
              }
            }

            for (Candidate candidate : result.getCandidates()) {
              String text = candidate.getText();
              Integer finishReason = candidate.getFinishReason();
              String formattedText = text;
              if (finishReason != null && finishReason == Candidate.FinishReason.MAX_TOKENS) {
                formattedText = text + "\n(FinishReason: MAX_TOKENS)";
              }
              listBuilder.add(ContentItem.TextItem.Companion.fromResponse(formattedText, null));
            }
            return listBuilder.build();
          },
          ContextCompat.getMainExecutor(this));
    } catch (RuntimeException e) {
      return immediateFailedFuture(e);
    }
  }

  @Override
  protected ListenableFuture<CountTokensResponse> countTokens(ContentItem request) {
    if (request instanceof ContentItem.CacheRequestItem) {
      // Count tokens does not support for cache request by now.
      return immediateFuture(new CountTokensResponse(0));
    }
    try {
      GenerateContentRequest genRequest = buildGenerateContentRequest(request);
      return generativeModelFutures.countTokens(genRequest);
    } catch (RuntimeException e) {
      return immediateFailedFuture(e);
    }
  }

  @Override
  protected ListenableFuture<Integer> getTokenLimit() {
    try {
      return generativeModelFutures.getTokenLimit();
    } catch (RuntimeException e) {
      return immediateFailedFuture(e);
    }
  }

  private GenerateContentRequest buildGenerateContentRequest(ContentItem request) {
    String requestText = "";
    String promptPrefixText = "";
    Bitmap imageBitmap = null;
    String cachedContextNameText = null;
    String systemPromptText = "";

    if (request instanceof ContentItem.InterleavedContentItem interleavedContentItem) {
      Content.Builder contentBuilder = new Content.Builder();
      for (Part part : interleavedContentItem.getParts()) {
        contentBuilder.addPart(part);
      }
      GenerateContentRequest.Builder requestBuilder =
          new GenerateContentRequest.Builder(contentBuilder.build());

      String systemInstruction = interleavedContentItem.getSystemInstruction();
      if (systemInstruction != null && !systemInstruction.isEmpty()) {
        requestBuilder.setSystemInstruction(new SystemInstruction(systemInstruction));
      }
      requestBuilder.setTemperature(curTemperature);
      requestBuilder.setTopK(curTopK);
      requestBuilder.setSeed(curSeed);
      requestBuilder.setMaxOutputTokens(curMaxOutputTokens);
      requestBuilder.setCandidateCount(curCandidateCount);

      return requestBuilder.build();
    } else if (request instanceof ContentItem.TextAndImagesItem tiRequest) {
      requestText = tiRequest.getText();
      systemPromptText = tiRequest.getSystemInstruction();
      if (!tiRequest.getImageUris().isEmpty()) {
        Uri uri = tiRequest.getImageUris().get(0);
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
          if (inputStream != null) {
            imageBitmap = BitmapFactory.decodeStream(inputStream);
          }
        } catch (IOException e) {
          Log.e(TAG, "Error decoding image URI: " + uri, e);
        }
      }
    } else if (request instanceof ContentItem.TextItem textItem) {
      requestText = textItem.getText();
      systemPromptText = textItem.getSystemInstruction();
    } else if (request instanceof ContentItem.TextWithPromptPrefixItem textWithPromptPrefixItem) {
      requestText = textWithPromptPrefixItem.getDynamicSuffix();
      promptPrefixText = textWithPromptPrefixItem.getPromptPrefix();
      systemPromptText = textWithPromptPrefixItem.getSystemInstruction();
    } else if (request instanceof ContentItem.TextWithPrefixCacheItem textWithPrefixCacheItem) {
      requestText = textWithPrefixCacheItem.getDynamicSuffix();
      cachedContextNameText = textWithPrefixCacheItem.getCacheName();
    } else if (request instanceof ContentItem.CacheRequestItem) {
      throw new IllegalArgumentException("CacheRequestItem is for creating cache only.");
    }

    GenerateContentRequest.Builder requestBuilder;
    if (imageBitmap != null) {
      requestBuilder =
          new GenerateContentRequest.Builder(
              new SystemInstruction(systemPromptText),
              new ImagePart(imageBitmap),
              new TextPart(requestText));
    } else {
      requestBuilder =
          new GenerateContentRequest.Builder(
              new SystemInstruction(systemPromptText), new TextPart(requestText));
      if (useExplicitCache) {
        requestBuilder.setCachedContextName(cachedContextNameText);
      } else {
        requestBuilder.setPromptPrefix(new PromptPrefix(promptPrefixText));
      }
    }

    requestBuilder.setTemperature(curTemperature);
    requestBuilder.setTopK(curTopK);
    requestBuilder.setSeed(curSeed);
    requestBuilder.setMaxOutputTokens(curMaxOutputTokens);
    requestBuilder.setCandidateCount(curCandidateCount);

    return requestBuilder.build();
  }

  private CompletableFuture<List<Part>> getContentFromEditText() {
    Editable editable = requestEditText.getEditableText();
    String text = editable.toString();
    ImageSpan[] spans = editable.getSpans(0, editable.length(), ImageSpan.class);

    // Sort spans by their start index to guarantee visual layout order, matching the exact
    // sequence of text and images entered by the user.
    Arrays.sort(
        spans, (a, b) -> Integer.compare(editable.getSpanStart(a), editable.getSpanStart(b)));

    class SpanInfo {
      final int start;
      final int end;
      @Nullable final String source;

      SpanInfo(int start, int end, @Nullable String source) {
        this.start = start;
        this.end = end;
        this.source = source;
      }
    }

    List<SpanInfo> spanInfos = new ArrayList<>();
    for (ImageSpan span : spans) {
      spanInfos.add(
          new SpanInfo(editable.getSpanStart(span), editable.getSpanEnd(span), span.getSource()));
    }

    return CompletableFuture.supplyAsync(
        () -> {
          List<Part> result = new ArrayList<>();
          int lastIndex = 0;
          for (SpanInfo span : spanInfos) {
            // Extract and slice the plain text segment situated between the last processed image
            // span
            // and the current image span.
            if (span.start > lastIndex) {
              result.add(new TextPart(text.substring(lastIndex, span.start)));
            }

            if (span.source != null) {
              try {
                Uri uri = Uri.parse(span.source);
                try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                  if (inputStream != null) {
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    result.add(new ImagePart(bitmap));
                  }
                }
              } catch (Exception e) {
                Log.e(TAG, "Error decoding image from span", e);
                runOnUiThread(
                    () -> Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show());
              }
            }
            lastIndex = span.end;
          }
          // Extract any remaining trailing plain text located after the very last image span has
          // been
          // processed.
          if (lastIndex < text.length()) {
            result.add(new TextPart(text.substring(lastIndex)));
          }
          return result;
        });
  }

  @Override
  protected List<String> runInferenceForBatchTask(String request) {
    try {
      GenerateContentResponse result;
      if (useDefaultConfig) {
        // useDefaultConfig is used for the case where user wants to use utility function with
        // default config values
        result = generativeModelFutures.generateContent(request).get();
      } else {
        result =
            generativeModelFutures
                .generateContent(
                    buildGenerateContentRequest(
                        ContentItem.TextItem.Companion.fromRequest(request, "")))
                .get();
      }
      if (result.getCandidates().get(0).getText() != null) {
        return ImmutableList.of(result.getCandidates().get(0).getText());
      }
      return ImmutableList.of();
    } catch (Exception e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return ImmutableList.of("Failed to run inference: " + e.getMessage());
    }
  }

  @Override
  protected void startGeneratingUi() {
    super.startGeneratingUi();
    sendButton.setText(R.string.generating);
    sendButton.setEnabled(false);
    requestEditText.setEnabled(false);
    selectImageButton.setEnabled(false);
    configButton.setEnabled(false);
  }

  @Override
  protected void endGeneratingUi(String debugInfo) {
    super.endGeneratingUi(debugInfo);
    sendButton.setText(R.string.button_send);
    sendButton.setEnabled(true);
    requestEditText.setEnabled(true);
    selectImageButton.setEnabled(true);
    configButton.setEnabled(true);
  }

  private void initGenerator() {
    if (generativeModelFutures != null) {
      generativeModelFutures.getGenerativeModel().close();
    }
    GenerationConfig.Builder optionsBuilder = new GenerationConfig.Builder();

    GenerativeModel generativeModel = Generation.INSTANCE.getClient(optionsBuilder.build());
    this.generativeModelFutures = GenerativeModelFutures.from(generativeModel);
    this.cachesFutures = CachesFutures.from(generativeModel);
    resetProcessor();
  }

  private ListenableFuture<List<ContentItem>> createCache(ContentItem.CacheRequestItem request) {
    CreateCachedContextRequest cacheRequest =
        new CreateCachedContextRequest.Builder(
                request.getCacheName(), new PromptPrefix(request.getPrefixToCache()))
            .build();

    String cachedSuccessMessage = getString(R.string.prefix_cached) + ": " + request.getCacheName();
    return Futures.transform(
        cachesFutures.create(cacheRequest),
        response ->
            ImmutableList.of(
                ContentItem.TextItem.Companion.fromResponse(cachedSuccessMessage, null)),
        ContextCompat.getMainExecutor(this));
  }

  @Override
  protected ListenableFuture<String> getBaseModelName() {
    return generativeModelFutures.getBaseModelName();
  }

  @Override
  protected ListenableFuture<Integer> checkFeatureStatus() {
    return generativeModelFutures.checkStatus();
  }

  @Override
  protected ListenableFuture<Void> downloadFeature(DownloadCallback callback) {
    return generativeModelFutures.download(callback);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    if (!super.onCreateOptionsMenu(menu)) {
      return false;
    }
    menu.add(Menu.NONE, ACTION_CLEAR_CACHES, Menu.NONE, "Clear all prefix caches");
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuItem item = menu.findItem(R.id.action_simple_api);
    if (item != null) {
      item.setVisible(true);
      item.setChecked(useDefaultConfig);
    }
    MenuItem explicitCacheItem = menu.findItem(R.id.action_explicit_cache);
    if (explicitCacheItem != null) {
      explicitCacheItem.setVisible(true);
      explicitCacheItem.setEnabled(!useDefaultConfig);
      explicitCacheItem.setChecked(useExplicitCache);
    }
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == ACTION_CLEAR_CACHES) {
      if (useExplicitCache) {
        Futures.addCallback(
            cachesFutures.list(),
            generateExplicitCachesClearCallback(),
            ContextCompat.getMainExecutor(this));
      } else {
        Futures.addCallback(
            generativeModelFutures.clearImplicitCaches(),
            generateImplicitCachesClearCallback(),
            ContextCompat.getMainExecutor(this));
      }
      return true;
    } else if (item.getItemId() == R.id.action_simple_api) {
      boolean newState = !item.isChecked();
      item.setChecked(newState);
      GenerationConfigUtils.setUseDefaultConfig(getApplicationContext(), newState);
      onConfigUpdated();
      return true;
    } else if (item.getItemId() == R.id.action_explicit_cache) {
      boolean newState = !item.isChecked();
      item.setChecked(newState);
      GenerationConfigUtils.setUseExplicitCache(getApplicationContext(), newState);
      onConfigUpdated();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  private FutureCallback<List<CachedContext>> generateExplicitCachesClearCallback() {
    return new FutureCallback<>() {
      @Override
      public void onSuccess(List<CachedContext> result) {
        if (result != null && !result.isEmpty()) {
          Log.d(TAG, "Going to delete explicit caches, size: " + result.size());
          List<ListenableFuture<Boolean>> deleteFutures = new ArrayList<>();
          for (CachedContext cache : result) {
            String cacheName = cache.getName();
            ListenableFuture<Boolean> deleteFuture = cachesFutures.delete(cacheName);
            deleteFutures.add(deleteFuture);
            Futures.addCallback(
                deleteFuture,
                generateExplicitCacheDeleteCallback(cacheName),
                ContextCompat.getMainExecutor(OpenPromptActivity.this));
          }
          var unused =
              Futures.whenAllComplete(deleteFutures)
                  .run(
                      () -> {
                        prefixEditText.setText("");
                        Toast.makeText(
                                OpenPromptActivity.this, "Caches cleared", Toast.LENGTH_SHORT)
                            .show();
                      },
                      ContextCompat.getMainExecutor(OpenPromptActivity.this));
        } else {
          Toast.makeText(OpenPromptActivity.this, "No caches to clear", Toast.LENGTH_SHORT).show();
        }
      }

      @Override
      public void onFailure(Throwable t) {
        Toast.makeText(OpenPromptActivity.this, "Failed to list caches", Toast.LENGTH_SHORT).show();
      }
    };
  }

  private FutureCallback<Boolean> generateExplicitCacheDeleteCallback(String cacheName) {
    return new FutureCallback<>() {
      @Override
      public void onSuccess(Boolean deleted) {
        if (deleted) {
          Log.d(TAG, "Deleted explicit cache: " + cacheName);
        } else {
          Log.d(TAG, "Failed to delete explicit cache: " + cacheName);
        }
      }

      @Override
      public void onFailure(Throwable t) {
        Log.d(TAG, "Failed to delete explicit cache: " + cacheName, t);
      }
    };
  }

  private FutureCallback<Void> generateImplicitCachesClearCallback() {
    return new FutureCallback<Void>() {
      @Override
      public void onSuccess(Void result) {
        Toast.makeText(OpenPromptActivity.this, "Caches cleared", Toast.LENGTH_SHORT).show();
      }

      @Override
      public void onFailure(Throwable t) {
        Toast.makeText(OpenPromptActivity.this, "Failed to clear caches", Toast.LENGTH_SHORT)
            .show();
      }
    };
  }
}
