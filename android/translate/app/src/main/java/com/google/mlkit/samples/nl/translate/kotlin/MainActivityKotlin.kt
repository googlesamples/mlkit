package com.google.mlkit.samples.nl.translate.kotlin

import android.app.ProgressDialog
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.util.LruCache
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateLanguage.Language
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.samples.nl.translate.R
import java.util.Locale

class MainActivityKotlin : AppCompatActivity() {

  companion object {
    private val TAG = MainActivityKotlin::class.java.canonicalName
    /**
     * This specifies the number of translators instance we want to keep in our LRU cache. Each
     * instance of the translator is built with different options based on the source language and the
     * target language, and since we want to be able to manage the number of translator instances to
     * keep around, an LRU cache is an easy way to achieve this.
     */
    private const val NUM_TRANSLATORS = 3
  }

  /** Current translatorOptions for the selected source and target languages  */
  private lateinit var translatorOptions: TranslatorOptions

  private val translatorsCache: LruCache<TranslatorOptions, Translator> =
    object :
      LruCache<TranslatorOptions, Translator>(NUM_TRANSLATORS) {
      public override fun create(options: TranslatorOptions): Translator {
        return Translation.getClient(options)
      }

      public override fun entryRemoved(
        evicted: Boolean,
        key: TranslatorOptions,
        oldValue: Translator,
        newValue: Translator
      ) {
        oldValue.close()
      }
    }

  /** Text box where the user types in their language  */
  private lateinit var sourceBox: EditText
  /** Text box where translated text will appear  */
  private lateinit var targetBox: TextView

  private val languageSet = TranslateLanguage.getAllLanguages()
  private val languageLocales = languageSet.map { Locale(it) }.sortedBy { it.displayLanguage }
  private val languages = languageLocales.map { it.language }
  private val languageNames = languageLocales.map { it.displayLanguage }

  private lateinit var sourceLanguageSpinner: Spinner
  private lateinit var targetLanguageSpinner: Spinner

  override fun onCreate(bundle: Bundle?) {
    super.onCreate(bundle)
    setContentView(R.layout.activity_main)

    sourceBox = findViewById(R.id.sourceBox)
    targetBox = findViewById(R.id.targetBox)
    sourceLanguageSpinner = findViewById(R.id.sourceLanguageSpinner)
    targetLanguageSpinner = findViewById(R.id.targetLanguageSpinner)

    sourceBox.setOnEditorActionListener { v: TextView, actionId: Int, _: KeyEvent? ->
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        closeKeyboard()
        translate(v.text.toString())
        true
      } else {
        false
      }
    }
    findViewById<Button>(R.id.translateButton).setOnClickListener {
      translate(sourceBox.text.toString())
    }

    setupLanguageSpinner(sourceLanguageSpinner, languageNames, TranslateLanguage.GERMAN)
    setupLanguageSpinner(targetLanguageSpinner, languageNames, TranslateLanguage.FRENCH)
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_translate, menu)
    return super.onCreateOptionsMenu(menu)
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
      R.id.model_management -> {
        startActivity(
          ModelManagementActivityKotlin.makeLaunchIntent(
            this
          )
        )
        true
      }
      else -> super.onOptionsItemSelected(item)
    }
  }

  override fun onDestroy() {
    translatorsCache.evictAll()
    super.onDestroy()
  }

  private fun setupLanguageSpinner(
    spinner: Spinner,
    languageNames: List<String>,
    @Language defaultValue: String
  ) {
    spinner.adapter =
      ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, languageNames)
    spinner.onItemSelectedListener = object : OnItemSelectedListener {
      override fun onItemSelected(
        parent: AdapterView<*>?,
        view: View,
        position: Int,
        id: Long
      ) {
        updateLanguages()
      }

      override fun onNothingSelected(parent: AdapterView<*>?) {}
    }
    spinner.setSelection(languages.indexOf(defaultValue))
  }

  private fun updateLanguages() {
    @Language val sourceLanguage =
      languages[sourceLanguageSpinner.selectedItemPosition.coerceAtLeast(0)]
    @Language val targetLanguage =
      languages[targetLanguageSpinner.selectedItemPosition.coerceAtLeast(0)]
    translatorOptions = TranslatorOptions.Builder()
      .setSourceLanguage(sourceLanguage)
      .setTargetLanguage(targetLanguage)
      .build()
  }

  private fun translate(sourceString: String) {
    val dialog = ProgressDialog.show(
      this,
      "Translate",
      "Downloading Translate model",
      /*indeterminate=*/true,
      /*cancelable=*/false
    )
    translatorsCache[translatorOptions]
      .downloadModelIfNeeded()
      .addOnCompleteListener { dialog.dismiss() }
      .onSuccessTask {
        translatorsCache[translatorOptions]
          .translate(sourceString)
      }.addOnSuccessListener { targetString: String? ->
        targetBox.setText(targetString)
      }
      .addOnFailureListener { e: Exception -> this.onFailure(e) }
  }

  private fun onFailure(e: Exception) {
    Log.e(TAG, e.message, e)
    Toast.makeText(this, e.message, Toast.LENGTH_SHORT).show()
  }

  private fun closeKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val view = currentFocus ?: View(this)
    imm.hideSoftInputFromWindow(view.windowToken, 0)
  }
}
