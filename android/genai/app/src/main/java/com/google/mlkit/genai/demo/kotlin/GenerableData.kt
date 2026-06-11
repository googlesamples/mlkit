package com.google.mlkit.genai.demo.kotlin

import com.google.mlkit.genai.structuredoutput.annotations.Generable
import com.google.mlkit.genai.structuredoutput.annotations.Guide

@Generable(description = "Basic information about a plant")
data class Plant(
  @Guide(description = "The full latin name of the plant in ALL CAPS") val name: String,
  @Guide(
    description = "The maximum height of the plant in centimeters",
    minimum = 4.0,
    maximum = 9999.0,
  )
  val height: Int,
  @Guide(description = "Whether the plant is poisonous or not") val isPoisonous: Boolean?,
  @Guide(
    description = "The continent most commonly found in",
    enumValues = ["Europe", "Asia", "Africa", "N.America", "S.America", "Australia", "Antarctica"],
  )
  val continentFound: String,
  @Guide(description = "Cute nicknames for this plant.", minItems = 1, maxItems = 2)
  val possibleNicknames: List<String>,
)

@Generable
data class PlantList(
  @Guide(description = "The list of plants", minItems = 2, maxItems = 2) val plantList: List<Plant>
)

@Generable(description = "A schedule event")
data class ScheduleEvent(
  @Guide(description = "The title of the event") val title: String,
  @Guide(description = "The date of the event in YYYY-MM-DD format") val date: String,
  @Guide(description = "The time of the event in HH:MM format") val time: String,
  @Guide(description = "The position of the event") val position: String,
)
