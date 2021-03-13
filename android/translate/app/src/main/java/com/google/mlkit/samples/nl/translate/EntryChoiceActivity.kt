package com.google.mlkit.samples.nl.translate

import android.content.Intent
import com.google.mlkit.samples.nl.translate.java.MainActivityJava
import com.google.mlkit.samples.nl.translate.kotlin.MainActivityKotlin
import com.mlkit.example.internal.BaseEntryChoiceActivity
import com.mlkit.example.internal.Choice

class EntryChoiceActivity : BaseEntryChoiceActivity() {

  override fun getChoices(): List<Choice> {
    return listOf(
      Choice(
        "Java",
        "Run the ML Kit Translate quickstart written in Java.",
        Intent(this, MainActivityJava::class.java)
      ),
      Choice(
        "Kotlin",
        "Run the ML Kit Translate quickstart written in Kotlin.",
        Intent(this, MainActivityKotlin::class.java)
      )
    )
  }
}
