package com.google.mlkit.samples.nl.translate

import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.google.mlkit.samples.nl.translate.java.MainActivity
import com.mlkit.example.internal.BaseEntryChoiceActivity
import com.mlkit.example.internal.Choice

class EntryChoiceActivity : BaseEntryChoiceActivity() {

  override fun getChoices(): List<Choice> {
    return listOf(
      Choice(
        "Java",
        "Run the ML Kit Translate quickstart written in Java.",
        Intent(this, MainActivity::class.java)
      ),
      Choice(
        "Kotlin",
        "Run the ML Kit Translate quickstart written in Kotlin.",
        Intent(this, com.google.mlkit.samples.nl.translate.kotlin.MainActivity::class.java)
      )
    )
  }

  companion object {
    init {
      AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
    }
  }
}
