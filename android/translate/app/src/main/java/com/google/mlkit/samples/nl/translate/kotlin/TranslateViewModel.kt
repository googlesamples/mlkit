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

package com.google.mlkit.samples.nl.translate.kotlin

import android.app.Application
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.samples.nl.translate.R
import java.util.Locale

/**
 * Model class for tracking available models and performing live translations
 */
class TranslateViewModel(application: Application) : AndroidViewModel(application) {

  companion object {
    // This specifies the number of translators instance we want to keep in our LRU cache.
    // Each instance of the translator is built with different options based on the source
    // language and the target language, and since we want to be able to manage the number of
    // translator instances to keep around, an LRU cache is an easy way to achieve this.
    private const val NUM_TRANSLATORS = 3
  }

  private val modelManager: RemoteModelManager = RemoteModelManager.getInstance()
  private val pendingDownloads: HashMap<String, Task<Void>> = hashMapOf()
  private val translators =
    object : LruCache<TranslatorOptions, Translator>(NUM_TRANSLATORS) {
      override fun create(options: TranslatorOptions): Translator {
        return Translation.getClient(options)
      }
      override fun entryRemoved(
        evicted: Boolean,
        key: TranslatorOptions,
        oldValue: Translator,
        newValue: Translator?,
      ) {
        oldValue.close()
      }
    }
  val sourceLang = MutableLiveData<Language>()
  val targetLang = MutableLiveData<Language>()
  val sourceText = MutableLiveData<String>()
  val translatedText = MediatorLiveData<ResultOrError>()
  val availableModels = MutableLiveData<List<String>>()

  // Gets a list of all available translation languages.
  val availableLanguages: List<Language> = TranslateLanguage.getAllLanguages().map { Language(it) }

  init {
    // Create a translation result or error object.
    val processTranslation =
      OnCompleteListener<String> { task ->
        if (task.isSuccessful) {
          translatedText.value = ResultOrError(task.result, null)
        } else {
          translatedText.value = ResultOrError(null, task.exception)
        }
        // Update the list of downloaded models as more may have been
        // automatically downloaded due to requested translation.
        fetchDownloadedModels()
      }
    // Start translation if any of the following change: input text, source lang, target lang.
    translatedText.addSource(sourceText) { translate().addOnCompleteListener(processTranslation) }
    val languageObserver =
      Observer<Language> { translate().addOnCompleteListener(processTranslation) }
    translatedText.addSource(sourceLang, languageObserver)
    translatedText.addSource(targetLang, languageObserver)

    // Update the list of downloaded models.
    fetchDownloadedModels()
  }

  private fun getModel(languageCode: String): TranslateRemoteModel {
    return TranslateRemoteModel.Builder(languageCode).build()
  }

  // Updates the list of downloaded models available for local translation.
  private fun fetchDownloadedModels() {
    modelManager.getDownloadedModels(TranslateRemoteModel::class.java).addOnSuccessListener {
      remoteModels ->
      availableModels.value = remoteModels.sortedBy { it.language }.map { it.language }
    }
  }

  // Starts downloading a remote model for local translation.
  internal fun downloadLanguage(language: Language) {
    val model = getModel(TranslateLanguage.fromLanguageTag(language.code)!!)
    var downloadTask: Task<Void>?
    if (pendingDownloads.containsKey(language.code)) {
      downloadTask = pendingDownloads[language.code]
      // found existing task. exiting
      if (downloadTask != null && !downloadTask.isCanceled) {
        return
      }
    }
    downloadTask =
      modelManager.download(model, DownloadConditions.Builder().build()).addOnCompleteListener {
        pendingDownloads.remove(language.code)
        fetchDownloadedModels()
      }
    pendingDownloads[language.code] = downloadTask
  }

  // Returns if a new model download task should be started.
  fun requiresModelDownload(
    lang: Language,
    downloadedModels: List<String?>?,
  ): Boolean {
    return if (downloadedModels == null) {
      true
    } else !downloadedModels.contains(lang.code) && !pendingDownloads.containsKey(lang.code)
  }

  // Deletes a locally stored translation model.
  internal fun deleteLanguage(language: Language) {
    val model = getModel(TranslateLanguage.fromLanguageTag(language.code)!!)
    modelManager.deleteDownloadedModel(model).addOnCompleteListener { fetchDownloadedModels() }
    pendingDownloads.remove(language.code)
  }

  fun translate(): Task<String> {
    val text = sourceText.value
    val source = sourceLang.value
    val target = targetLang.value
    if (source == null || target == null || text == null || text.isEmpty()) {
      return Tasks.forResult("")
    }
    val sourceLangCode = TranslateLanguage.fromLanguageTag(source.code)!!
    val targetLangCode = TranslateLanguage.fromLanguageTag(target.code)!!
    val options =
      TranslatorOptions.Builder()
        .setSourceLanguage(sourceLangCode)
        .setTargetLanguage(targetLangCode)
        .build()
    return translators[options].downloadModelIfNeeded().continueWithTask { task ->
      if (task.isSuccessful) {
        translators[options].translate(text)
      } else {
        Tasks.forException<String>(
          task.exception
            ?: Exception(getApplication<Application>().getString(R.string.unknown_error))
        )
      }
    }
  }

  /** Holds the result of the translation or any error. */
  inner class ResultOrError(var result: String?, var error: Exception?)

  /**
   * Holds the language code (i.e. "en") and the corresponding localized full language name (i.e.
   * "English")
   */
  class Language(val code: String) : Comparable<Language> {

    private val displayName: String
      get() = Locale(code).displayName

    override fun equals(other: Any?): Boolean {
      if (other === this) {
        return true
      }

      if (other !is Language) {
        return false
      }

      val otherLang = other as Language?
      return otherLang!!.code == code
    }

    override fun toString(): String {
      return "$code - $displayName"
    }

    override fun compareTo(other: Language): Int {
      return this.displayName.compareTo(other.displayName)
    }

    override fun hashCode(): Int {
      return code.hashCode()
    }
  }

  override fun onCleared() {
    super.onCleared()
    // Each new instance of a translator needs to be closed appropriately. Here we utilize the
    // ViewModel's onCleared() to clear our LruCache and close each Translator instance when
    // this ViewModel is no longer used and destroyed.
    translators.evictAll()
  }
}
