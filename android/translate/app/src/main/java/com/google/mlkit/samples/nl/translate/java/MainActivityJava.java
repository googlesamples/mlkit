package com.google.mlkit.samples.nl.translate.java;

import android.app.ProgressDialog;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.util.LruCache;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.common.collect.ImmutableList;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateLanguage.Language;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.samples.nl.translate.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivityJava extends AppCompatActivity {

  private static final String TAG = MainActivityJava.class.getCanonicalName();
  /**
   * This specifies the number of translators instance we want to keep in our LRU cache. Each
   * instance of the translator is built with different options based on the source language and the
   * target language, and since we want to be able to manage the number of translator instances to
   * keep around, an LRU cache is an easy way to achieve this.
   */
  private static final int NUM_TRANSLATORS = 3;

  /** Current translatorOptions for the selected source and target languages */
  private TranslatorOptions translatorOptions;

  private final LruCache<TranslatorOptions, Translator> translatorsCache =
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

  /** Text box where the user types in their language */
  private EditText sourceBox;
  /** Text box where translated text will appear */
  private TextView targetBox;

  private ImmutableList</* @Language */ String> languages;
  private Spinner sourceLanguageSpinner;
  private Spinner targetLanguageSpinner;

  @Override
  protected void onCreate(@Nullable Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.activity_main);
    sourceBox = findViewById(R.id.sourceBox);
    targetBox = findViewById(R.id.targetBox);
    sourceLanguageSpinner = findViewById(R.id.sourceLanguageSpinner);
    targetLanguageSpinner = findViewById(R.id.targetLanguageSpinner);

    sourceBox.setOnEditorActionListener(
        (v, actionId, event) -> {
          if (actionId == EditorInfo.IME_ACTION_SEND) {
            closeKeyboard();
            translate(v.getText().toString());
            return true;
          }
          return false;
        });
    findViewById(R.id.translateButton)
        .setOnClickListener(v -> translate(sourceBox.getText().toString()));

    List<String> languageNames = buildLanguagesList();
    setupLanguageSpinner(sourceLanguageSpinner, languageNames, TranslateLanguage.GERMAN);
    setupLanguageSpinner(targetLanguageSpinner, languageNames, TranslateLanguage.FRENCH);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_translate, menu);
    return super.onCreateOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.model_management) {
      startActivity(ModelManagementActivityJava.makeLaunchIntent(this));
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onDestroy() {
    translatorsCache.evictAll();
    super.onDestroy();
  }

  // This returns a list of language names (user readable) but also builds the `languages` field,
  // with the list of TranslateLanguages.
  private ImmutableList<String> buildLanguagesList() {
    List</* @Language */ String> languageSet = TranslateLanguage.getAllLanguages();
    List<Locale> locales = new ArrayList<>(languageSet.size());
    for (/* @Language */ String language : languageSet) {
      locales.add(new Locale(language));
    }
    Collections.sort(
        locales, (l1, l2) -> l1.getDisplayLanguage().compareTo(l2.getDisplayLanguage()));

    ImmutableList.Builder<String> languagesBuilder = new ImmutableList.Builder<>();
    ImmutableList.Builder<String> languageNames = new ImmutableList.Builder<>();
    for (Locale locale : locales) {
      languagesBuilder.add(locale.getLanguage());
      languageNames.add(locale.getDisplayLanguage());
    }
    languages = languagesBuilder.build();

    return languageNames.build();
  }

  private void setupLanguageSpinner(
      Spinner spinner, List<String> languageNames, @Language String defaultValue) {
    spinner.setAdapter(
        new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, languageNames));
    spinner.setOnItemSelectedListener(
        new OnItemSelectedListener() {
          @Override
          public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            MainActivityJava.this.updateLanguages();
          }

          @Override
          public void onNothingSelected(AdapterView<?> parent) {}
        });
    spinner.setSelection(languages.indexOf(defaultValue));
  }

  private void updateLanguages() {
    @Language
    String sourceLanguage =
        languages.get(Math.max(sourceLanguageSpinner.getSelectedItemPosition(), 0));
    @Language
    String targetLanguage =
        languages.get(Math.max(targetLanguageSpinner.getSelectedItemPosition(), 0));
    translatorOptions =
        new TranslatorOptions.Builder()
            .setSourceLanguage(sourceLanguage)
            .setTargetLanguage(targetLanguage)
            .build();
  }

  private void translate(String sourceString) {
    final ProgressDialog dialog =
        ProgressDialog.show(
            this,
            "Translate",
            "Downloading Translate model",
            /*indeterminate=*/ true,
            /*cancelable=*/ false);

    translatorsCache
        .get(translatorOptions)
        .downloadModelIfNeeded()
        .addOnCompleteListener(ignored -> dialog.dismiss())
        .addOnSuccessListener(
            task ->
                translatorsCache
                    .get(translatorOptions)
                    .translate(sourceString)
                    .addOnSuccessListener(targetString -> targetBox.setText(targetString))
                    .addOnFailureListener(this::onFailure))
        .addOnFailureListener(this::onFailure);
  }

  private void onFailure(@NonNull Exception e) {
    Log.e(TAG, e.getMessage(), e);
    Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
  }

  private void closeKeyboard() {
    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    View view = getCurrentFocus();
    if (view == null) {
      view = new View(this);
    }
    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
  }
}
