package com.google.mlkit.samples.nl.entityextraction

import android.content.Intent
import com.google.mlkit.samples.nl.entityextraction.java.MainActivityJava
import com.google.mlkit.samples.nl.entityextraction.kotlin.MainActivityKotlin
import com.mlkit.example.internal.BaseEntryChoiceActivity
import com.mlkit.example.internal.Choice

class EntryChoiceActivity : BaseEntryChoiceActivity() {

  override fun getChoices(): List<Choice> {
    return listOf(
      Choice(
        "Java",
        "Run the ML Kit Entity Extraction quickstart written in Java.",
        Intent(this, MainActivityJava::class.java)
      ),
      Choice(
        "Kotlin",
        "Run the ML Kit Entity Extraction quickstart written in Kotlin.",
        Intent(this, MainActivityKotlin::class.java)
      )
    )
  }
}
