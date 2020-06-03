package com.google.example.mlkit.kotlin

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions

private const val TAG = "LangIDActivity"

class LanguageIdentificationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun identifyLanguageWithStringInput(text: String) {
        // [START identify_languages]
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyLanguage(text)
                .addOnSuccessListener { languageCode ->
                    if (languageCode == "und") {
                        Log.i(TAG, "Can't identify language.")
                    } else {
                        Log.i(TAG, "Language: $languageCode")
                    }
                }
                .addOnFailureListener {
                    // Model couldn’t be loaded or other internal error.
                    // ...
                }
        // [END identify_languages]
    }

    fun setConfidence() {
        // [START set_confidence]
        val languageIdentifier = LanguageIdentification
                .getClient(LanguageIdentificationOptions.Builder()
                        .setConfidenceThreshold(0.34f)
                        .build())
        // [END set_confidence]
    }

    fun getPossibleLanguuages(text: String) {
        // [START get_possible_languages]
        val languageIdentifier = LanguageIdentification.getClient()
        languageIdentifier.identifyPossibleLanguages(text)
                .addOnSuccessListener { identifiedLanguages ->
                    for (identifiedLanguage in identifiedLanguages) {
                        val language = identifiedLanguage.languageTag
                        val confidence = identifiedLanguage.confidence
                        Log.i(TAG, "$language $confidence")
                    }
                }
                .addOnFailureListener {
                    // Model couldn’t be loaded or other internal error.
                    // ...
                }
        // [END get_possible_languages]
    }

    private fun setConfidenceThreshold() {
        // [START set_confidence_threshold]
        val identifierOptions = LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        val languageIdentifier = LanguageIdentification
                .getClient(identifierOptions)
        // [END set_confidence_threshold]
    }


}
