/*
 * Copyright 2019 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.google.mlkit.samples.nl.translate.java;

import android.app.Application;
import android.util.LruCache;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.samples.nl.translate.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Model class for tracking available models and performing live translations
 */
public class TranslateViewModel extends AndroidViewModel {
  // This specifies the number of translators instance we want to keep in our LRU cache.
  // Each instance of the translator is built with different options based on the source
  // language and the target language, and since we want to be able to manage the number of
  // translator instances to keep around, an LRU cache is an easy way to achieve this.
  private static final int NUM_TRANSLATORS = 3;

  private final RemoteModelManager modelManager;
  private final LruCache<TranslatorOptions, Translator> translators =
      new LruCache<TranslatorOptions, Translator>(NUM_TRANSLATORS) {
        @Override
        public Translator create(TranslatorOptions options) {
          return Translation.getClient(options);
        }

        @Override
        public void entryRemoved(
            boolean evicted, TranslatorOptions key, Translator oldValue, Translator newValue) {
          oldValue.close();
        }
      };
  MutableLiveData<Language> sourceLang = new MutableLiveData<>();
  MutableLiveData<Language> targetLang = new MutableLiveData<>();
  MutableLiveData<String> sourceText = new MutableLiveData<>();
  MediatorLiveData<ResultOrError> translatedText = new MediatorLiveData<>();
  MutableLiveData<List<String>> availableModels = new MutableLiveData<>();

  HashMap<String, Task<Void>> pendingDownloads = new HashMap<>();

  public TranslateViewModel(@NonNull Application application) {
    super(application);
    modelManager = RemoteModelManager.getInstance();

    // Create a translation result or error object.
    final OnCompleteListener<String> processTranslation =
        new OnCompleteListener<String>() {
          @Override
          public void onComplete(@NonNull Task<String> task) {
            if (task.isSuccessful()) {
              translatedText.setValue(new ResultOrError(task.getResult(), null));
            } else {
              translatedText.setValue(new ResultOrError(null, task.getException()));
              task.getException().printStackTrace();
            }
            // Update the list of downloaded models as more may have been
            // automatically downloaded due to requested translation.
            fetchDownloadedModels();
          }
        };

    // Start translation if any of the following change: input text, source lang, target lang.
    translatedText.addSource(
        sourceText,
        new Observer<String>() {
          @Override
          public void onChanged(@Nullable String s) {
            translate().addOnCompleteListener(processTranslation);
          }
        });
    Observer<Language> languageObserver =
        new Observer<Language>() {
          @Override
          public void onChanged(@Nullable Language language) {
            translate().addOnCompleteListener(processTranslation);
          }
        };
    translatedText.addSource(sourceLang, languageObserver);
    translatedText.addSource(targetLang, languageObserver);

    // Update the list of downloaded models.
    fetchDownloadedModels();
  }

  // Gets a list of all available translation languages.
  List<Language> getAvailableLanguages() {
    List<Language> languages = new ArrayList<>();
    List<String> languageIds = TranslateLanguage.getAllLanguages();
    for (String languageId : languageIds) {
      languages.add(new Language(TranslateLanguage.fromLanguageTag(languageId)));
    }
    return languages;
  }

  private TranslateRemoteModel getModel(String languageCode) {
    return new TranslateRemoteModel.Builder(languageCode).build();
  }

  // Updates the list of downloaded models available for local translation.
  private void fetchDownloadedModels() {
    modelManager
        .getDownloadedModels(TranslateRemoteModel.class)
        .addOnSuccessListener(
            new OnSuccessListener<Set<TranslateRemoteModel>>() {
              @Override
              public void onSuccess(Set<TranslateRemoteModel> remoteModels) {
                List<String> modelCodes = new ArrayList<>(remoteModels.size());
                for (TranslateRemoteModel model : remoteModels) {
                  modelCodes.add(model.getLanguage());
                }
                Collections.sort(modelCodes);
                availableModels.setValue(modelCodes);
              }
            });
  }

  // Starts downloading a remote model for local translation.
  void downloadLanguage(Language language) {
    TranslateRemoteModel model = getModel(TranslateLanguage.fromLanguageTag(language.getCode()));
    Task<Void> downloadTask;
    if (pendingDownloads.containsKey(language.code)) {
      downloadTask = pendingDownloads.get(language.code);
      // found existing task. exiting
      if (downloadTask != null && !downloadTask.isCanceled()) {
        return;
      }
    }
    downloadTask =
        modelManager
            .download(model, new DownloadConditions.Builder().build())
            .addOnCompleteListener(
                new OnCompleteListener<Void>() {
                  @Override
                  public void onComplete(@NonNull Task<Void> task) {
                    pendingDownloads.remove(language.getCode());
                    fetchDownloadedModels();
                  }
                });
    pendingDownloads.put(language.code, downloadTask);
  }

  // Returns if a new model download task should be started.
  boolean requiresModelDownload(Language lang, @Nullable List<String> downloadedModels) {
    if (downloadedModels == null) {
      return true;
    }
    return !downloadedModels.contains(lang.code) && !pendingDownloads.containsKey(lang.code);
  }

  // Deletes a locally stored translation model.
  void deleteLanguage(Language language) {
    TranslateRemoteModel model = getModel(TranslateLanguage.fromLanguageTag(language.getCode()));
    modelManager
        .deleteDownloadedModel(model)
        .addOnCompleteListener(
            new OnCompleteListener<Void>() {
              @Override
              public void onComplete(@NonNull Task<Void> task) {
                fetchDownloadedModels();
              }
            });
    pendingDownloads.remove(language.code);
  }

  public Task<String> translate() {
    final String text = sourceText.getValue();
    final Language source = sourceLang.getValue();
    final Language target = targetLang.getValue();
    if (source == null || target == null || text == null || text.isEmpty()) {
      return Tasks.forResult("");
    }
    String sourceLangCode = TranslateLanguage.fromLanguageTag(source.getCode());
    String targetLangCode = TranslateLanguage.fromLanguageTag(target.getCode());
    TranslatorOptions options =
        new TranslatorOptions.Builder()
            .setSourceLanguage(sourceLangCode)
            .setTargetLanguage(targetLangCode)
            .build();
    return translators
        .get(options)
        .downloadModelIfNeeded()
        .continueWithTask(
            new Continuation<Void, Task<String>>() {
              @Override
              public Task<String> then(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                  return translators.get(options).translate(text);
                } else {
                  Exception e = task.getException();
                  if (e == null) {
                    e = new Exception(getApplication().getString(R.string.unknown_error));
                  }
                  return Tasks.forException(e);
                }
              }
            });
  }

  /** Holds the result of the translation or any error. */
  static class ResultOrError {
    final String result;
    final Exception error;

    ResultOrError(@Nullable String result, @Nullable Exception error) {
      this.result = result;
      this.error = error;
    }
  }

  /**
   * Holds the language code (i.e. "en") and the corresponding localized full language name (i.e.
   * "English")
   */
  static class Language implements Comparable<Language> {
    private final String code;

    Language(String code) {
      this.code = code;
    }

    String getDisplayName() {
      return new Locale(code).getDisplayName();
    }

    String getCode() {
      return code;
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) {
        return true;
      }

      if (!(o instanceof Language)) {
        return false;
      }

      Language otherLang = (Language) o;
      return otherLang.code.equals(code);
    }

    @NonNull
    @Override
    public String toString() {
      return code + " - " + getDisplayName();
    }

    @Override
    public int hashCode() {
      return code.hashCode();
    }

    @Override
    public int compareTo(@NonNull Language o) {
      return this.getDisplayName().compareTo(o.getDisplayName());
    }
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    // Each new instance of a translator needs to be closed appropriately. Here we utilize the
    // ViewModel's onCleared() to clear our LruCache and close each Translator instance when
    // this ViewModel is no longer used and destroyed.
    translators.evictAll();
  }
}
