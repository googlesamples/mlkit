package com.google.mlkit.samples.nl.entityextraction.kotlin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions.ModelIdentifier
import com.google.mlkit.samples.nl.entityextraction.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Default launcher activity.  */
class MainActivityKotlin : AppCompatActivity() {

  companion object {
    private const val TAG = "MainActivityKotlin"
    private const val CURRENT_MODEL_KEY = "current_model_key"
    const val REQUEST_CODE = 1

    private fun getEntityExtractionParams(input: String): EntityExtractionParams {
      return EntityExtractionParams.Builder(input).build()
    }
  }

  @ModelIdentifier
  private var currentModel: String = EntityExtractorOptions.ENGLISH

  private lateinit var entityExtractor: EntityExtractor
  private lateinit var currentModelView: TextView
  private lateinit var input: EditText
  private lateinit var output: TextView

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    currentModel = savedInstanceState?.getString(CURRENT_MODEL_KEY, EntityExtractorOptions.ENGLISH)
      ?: EntityExtractorOptions.ENGLISH

    val options = EntityExtractorOptions.Builder(currentModel).build()
    entityExtractor = EntityExtraction.getClient(options)
    lifecycle.addObserver(entityExtractor)

    currentModelView = findViewById(R.id.current_model)
    currentModelView.text = getString(R.string.current_model, currentModel.toUpperCase(Locale.US))
    val currentLocaleView: TextView = findViewById(R.id.current_locale)
    currentLocaleView.text = getString(R.string.current_locale, Locale.getDefault())
    input = findViewById(R.id.text_input)
    output = findViewById(R.id.output)

    findViewById<Button>(R.id.button).setOnClickListener {
      val newInput = input.text.toString()
      if (newInput.isEmpty()) {
        Toast.makeText(
          this@MainActivityKotlin,
          R.string.empty_input, Toast.LENGTH_LONG
        ).show()
        return@setOnClickListener
      }
      extractEntities(newInput)
    }
  }

  fun extractEntities(input: String) {
    output.setText(R.string.wait_message)
    entityExtractor
      .downloadModelIfNeeded()
      .onSuccessTask {
        entityExtractor.annotate(
          getEntityExtractionParams(
            input
          )
        )
      }
      .addOnFailureListener { e: Exception? ->
        Log.e(TAG, "Annotation failed", e)
        output.text = getString(R.string.entity_extraction_error)
      }
      .addOnSuccessListener { result: List<EntityAnnotation> ->
        if (result.isEmpty()) {
          output.text = getString(R.string.no_entity_detected)
          return@addOnSuccessListener
        }
        output.text = getString(R.string.entities_detected).plus("\n")
        for (entityAnnotation in result) {
          val entities = entityAnnotation.entities
          val annotatedText = entityAnnotation.annotatedText
          for (entity in entities) {
            displayEntityInfo(annotatedText, entity)
            output.append("\n")
          }
        }
      }
  }

  override fun onCreateOptionsMenu(menu: Menu): Boolean {
    menuInflater.inflate(R.menu.menu_choose_model, menu)
    return true
  }

  override fun onOptionsItemSelected(item: MenuItem): Boolean {
    if (item.itemId == R.id.action_btn) {
      startActivityForResult(
        Intent(this, ModelsActivityKotlin::class.java),
        REQUEST_CODE
      )
      return true
    }
    return super.onOptionsItemSelected(item)
  }

  override fun onSaveInstanceState(bundle: Bundle) {
    super.onSaveInstanceState(bundle)
    bundle.putString(CURRENT_MODEL_KEY, currentModel)
  }

  public override fun onActivityResult(request: Int, result: Int, intent: Intent?) {
    super.onActivityResult(request, result, intent)
    if (request == REQUEST_CODE && result == Activity.RESULT_OK) {
      output.text = ""
      val newModel = intent!!.getStringExtra(ModelsActivityKotlin.MODEL_KEY)
      if (newModel != null) {
        currentModel = newModel
      }
      currentModelView.text = getString(R.string.current_model, currentModel.toUpperCase(Locale.US))
      entityExtractor =
        EntityExtraction.getClient(EntityExtractorOptions.Builder(currentModel).build())
    }
  }

  private fun displayEntityInfo(annotatedText: String, entity: Entity) {
    when (entity.type) {
      Entity.TYPE_ADDRESS -> displayAddressInfo(annotatedText)
      Entity.TYPE_DATE_TIME -> displayDateTimeInfo(entity, annotatedText)
      Entity.TYPE_EMAIL -> displayEmailInfo(annotatedText)
      Entity.TYPE_FLIGHT_NUMBER -> displayFlightNoInfo(entity, annotatedText)
      Entity.TYPE_IBAN -> displayIbanInfo(entity, annotatedText)
      Entity.TYPE_ISBN -> displayIsbnInfo(entity, annotatedText)
      Entity.TYPE_MONEY -> displayMoneyEntityInfo(entity, annotatedText)
      Entity.TYPE_PAYMENT_CARD -> displayPaymentCardInfo(entity, annotatedText)
      Entity.TYPE_PHONE -> displayPhoneInfo(annotatedText)
      Entity.TYPE_TRACKING_NUMBER -> displayTrackingNoInfo(entity, annotatedText)
      Entity.TYPE_URL -> displayUrlInfo(annotatedText)
      else -> displayDefaultInfo(annotatedText)
    }
  }

  private fun displayAddressInfo(annotatedText: String) {
    output.append(getString(R.string.address_entity_info, annotatedText))
  }

  private fun displayEmailInfo(annotatedText: String) {
    output.append(getString(R.string.email_entity_info, annotatedText))
  }

  private fun displayPhoneInfo(annotatedText: String) {
    output.append(
      getString(
        R.string.phone_entity_info_formatted,
        annotatedText,
        PhoneNumberUtils.formatNumber(annotatedText)
      )
    )
  }

  private fun displayDefaultInfo(annotatedText: String) {
    output.append(getString(R.string.unknown_entity_info, annotatedText))
  }

  private fun displayUrlInfo(annotatedText: String) {
    output.append(getString(R.string.url_entity_info, annotatedText))
  }

  private fun displayDateTimeInfo(entity: Entity, annotatedText: String) {
    val dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)
      .format(Date(entity.asDateTimeEntity()!!.timestampMillis))
    output.append(
      getString(
        R.string.date_time_entity_info,
        annotatedText,
        dateTimeFormat,
        convertGranularityToString(entity)
      )
    )
  }

  private fun convertGranularityToString(entity: Entity): String {
    val dateTimeEntity = entity.asDateTimeEntity()
    return when (dateTimeEntity!!.dateTimeGranularity) {
      DateTimeEntity.GRANULARITY_YEAR -> getString(R.string.granularity_year)
      DateTimeEntity.GRANULARITY_MONTH -> getString(R.string.granularity_month)
      DateTimeEntity.GRANULARITY_WEEK -> getString(R.string.granularity_week)
      DateTimeEntity.GRANULARITY_DAY -> getString(R.string.granularity_day)
      DateTimeEntity.GRANULARITY_HOUR -> getString(R.string.granularity_hour)
      DateTimeEntity.GRANULARITY_MINUTE -> getString(R.string.granularity_minute)
      DateTimeEntity.GRANULARITY_SECOND -> getString(R.string.granularity_second)
      else -> getString(R.string.granularity_unknown)
    }
  }

  private fun displayTrackingNoInfo(entity: Entity, annotatedText: String) {
    val trackingNumberEntity = entity.asTrackingNumberEntity()
    output.append(
      getString(
        R.string.tracking_number_entity_info,
        annotatedText,
        trackingNumberEntity!!.parcelCarrier,
        trackingNumberEntity.parcelTrackingNumber
      )
    )
  }

  private fun displayPaymentCardInfo(entity: Entity, annotatedText: String) {
    val paymentCardEntity = entity.asPaymentCardEntity()
    output.append(
      getString(
        R.string.payment_card_entity_info,
        annotatedText,
        paymentCardEntity!!.paymentCardNetwork,
        paymentCardEntity.paymentCardNumber
      )
    )
  }

  private fun displayIsbnInfo(entity: Entity, annotatedText: String) {
    output.append(
      getString(R.string.isbn_entity_info, annotatedText, entity.asIsbnEntity()!!.isbn)
    )
  }

  private fun displayIbanInfo(entity: Entity, annotatedText: String) {
    val ibanEntity = entity.asIbanEntity()
    output.append(
      getString(
        R.string.iban_entity_info,
        annotatedText,
        ibanEntity!!.iban,
        ibanEntity.ibanCountryCode
      )
    )
  }

  private fun displayFlightNoInfo(entity: Entity, annotatedText: String) {
    val flightNumberEntity = entity.asFlightNumberEntity()
    output.append(
      getString(
        R.string.flight_number_entity_info,
        annotatedText,
        flightNumberEntity!!.airlineCode,
        flightNumberEntity.flightNumber
      )
    )
  }

  private fun displayMoneyEntityInfo(entity: Entity, annotatedText: String) {
    val moneyEntity = entity.asMoneyEntity()
    output.append(
      getString(
        R.string.money_entity_info,
        annotatedText,
        moneyEntity!!.unnormalizedCurrency,
        moneyEntity.integerPart,
        moneyEntity.fractionalPart
      )
    )
  }
}
