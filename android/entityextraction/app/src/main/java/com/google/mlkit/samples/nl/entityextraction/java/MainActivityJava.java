package com.google.mlkit.samples.nl.entityextraction.java;

import static com.google.mlkit.nl.entityextraction.DateTimeEntity.GRANULARITY_DAY;
import static com.google.mlkit.nl.entityextraction.DateTimeEntity.GRANULARITY_HOUR;
import static com.google.mlkit.nl.entityextraction.DateTimeEntity.GRANULARITY_MINUTE;
import static com.google.mlkit.nl.entityextraction.DateTimeEntity.GRANULARITY_MONTH;
import static com.google.mlkit.nl.entityextraction.DateTimeEntity.GRANULARITY_SECOND;
import static com.google.mlkit.nl.entityextraction.DateTimeEntity.GRANULARITY_WEEK;
import static com.google.mlkit.nl.entityextraction.DateTimeEntity.GRANULARITY_YEAR;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import com.google.mlkit.nl.entityextraction.DateTimeEntity;
import com.google.mlkit.nl.entityextraction.DateTimeEntity.DateTimeGranularity;
import com.google.mlkit.nl.entityextraction.Entity;
import com.google.mlkit.nl.entityextraction.EntityAnnotation;
import com.google.mlkit.nl.entityextraction.EntityExtraction;
import com.google.mlkit.nl.entityextraction.EntityExtractionParams;
import com.google.mlkit.nl.entityextraction.EntityExtractor;
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions;
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions.ModelIdentifier;
import com.google.mlkit.nl.entityextraction.FlightNumberEntity;
import com.google.mlkit.nl.entityextraction.IbanEntity;
import com.google.mlkit.nl.entityextraction.MoneyEntity;
import com.google.mlkit.nl.entityextraction.PaymentCardEntity;
import com.google.mlkit.nl.entityextraction.TrackingNumberEntity;
import com.google.mlkit.samples.nl.entityextraction.R;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Default launcher activity. */
public class MainActivityJava extends AppCompatActivity {

  private static final String TAG = "MainActivityJava";
  private static final String CURRENT_MODEL_KEY = "current_model_key";
  public static final int REQUEST_CODE = 1;

  @ModelIdentifier private String currentModel = EntityExtractorOptions.ENGLISH;
  private EntityExtractor entityExtractor;
  private TextView currentModelView;
  private EditText input;
  private TextView output;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (savedInstanceState != null) {
      currentModel =
          savedInstanceState.getString(CURRENT_MODEL_KEY, EntityExtractorOptions.ENGLISH);
    }
    setContentView(R.layout.activity_main);

    EntityExtractorOptions options = new EntityExtractorOptions.Builder(currentModel).build();
    entityExtractor = EntityExtraction.getClient(options);
    getLifecycle().addObserver(entityExtractor);

    currentModelView = findViewById(R.id.current_model);
    currentModelView.setText(
        getString(R.string.current_model, currentModel.toUpperCase(Locale.US)));
    TextView currentLocaleView = findViewById(R.id.current_locale);
    currentLocaleView.setText(getString(R.string.current_locale, Locale.getDefault()));
    input = findViewById(R.id.text_input);
    output = findViewById(R.id.output);
    Button extractionButton = findViewById(R.id.button);

    extractionButton.setOnClickListener(
        v -> {
          String newInput = input.getText().toString();
          if (newInput.isEmpty()) {
            Toast.makeText(MainActivityJava.this, R.string.empty_input, Toast.LENGTH_LONG).show();
            return;
          }
          extractEntities(newInput);
        });
  }

  public void extractEntities(final String input) {
    output.setText(R.string.wait_message);

    entityExtractor
        .downloadModelIfNeeded()
        .onSuccessTask(ignored -> entityExtractor.annotate(getEntityExtractionParams(input)))
        .addOnFailureListener(
            e -> {
              Log.e(TAG, "Annotation failed", e);
              output.setText(getString(R.string.entity_extraction_error));
            })
        .addOnSuccessListener(
            result -> {
              if (result.isEmpty()) {
                output.setText(getString(R.string.no_entity_detected));
                return;
              }
              output.setText(getString(R.string.entities_detected));
              output.append("\n");
              for (EntityAnnotation entityAnnotation : result) {
                List<Entity> entities = entityAnnotation.getEntities();
                String annotatedText = entityAnnotation.getAnnotatedText();
                for (Entity entity : entities) {
                  displayEntityInfo(annotatedText, entity);
                  output.append("\n");
                }
              }
            });
  }

  private static EntityExtractionParams getEntityExtractionParams(String input) {
    return new EntityExtractionParams.Builder(input).build();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_choose_model, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.action_btn) {
      startActivityForResult(new Intent(this, ModelsActivityJava.class), REQUEST_CODE);
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onActivityResult(int request, int result, Intent intent) {
    super.onActivityResult(request, result, intent);
    if (request == REQUEST_CODE && result == RESULT_OK) {
      output.setText("");
      String newModel = intent.getStringExtra(ModelsActivityJava.MODEL_KEY);
      currentModel = newModel;
      currentModelView.setText(
          getString(R.string.current_model, currentModel.toUpperCase(Locale.US)));
      entityExtractor =
          EntityExtraction.getClient(new EntityExtractorOptions.Builder(newModel).build());
    }
  }

  @Override
  public void onSaveInstanceState(@NonNull Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString(CURRENT_MODEL_KEY, currentModel);
  }

  private void displayEntityInfo(String annotatedText, Entity entity) {
    switch (entity.getType()) {
      case Entity.TYPE_ADDRESS:
        displayAddressInfo(annotatedText);
        break;
      case Entity.TYPE_DATE_TIME:
        displayDateTimeInfo(entity, annotatedText);
        break;
      case Entity.TYPE_EMAIL:
        displayEmailInfo(annotatedText);
        break;
      case Entity.TYPE_FLIGHT_NUMBER:
        displayFlightNoInfo(entity, annotatedText);
        break;
      case Entity.TYPE_IBAN:
        displayIbanInfo(entity, annotatedText);
        break;
      case Entity.TYPE_ISBN:
        displayIsbnInfo(entity, annotatedText);
        break;
      case Entity.TYPE_MONEY:
        displayMoneyEntityInfo(entity, annotatedText);
        break;
      case Entity.TYPE_PAYMENT_CARD:
        displayPaymentCardInfo(entity, annotatedText);
        break;
      case Entity.TYPE_PHONE:
        displayPhoneInfo(annotatedText);
        break;
      case Entity.TYPE_TRACKING_NUMBER:
        displayTrackingNoInfo(entity, annotatedText);
        break;
      case Entity.TYPE_URL:
        displayUrlInfo(annotatedText);
        break;
      default:
        displayDefaultInfo(annotatedText);
        break;
    }
  }

  private void displayAddressInfo(String annotatedText) {
    output.append(getString(R.string.address_entity_info, annotatedText));
  }

  private void displayEmailInfo(String annotatedText) {
    output.append(getString(R.string.email_entity_info, annotatedText));
  }

  private void displayPhoneInfo(String annotatedText) {
    output.append(
        getString(
            R.string.phone_entity_info_formatted,
            annotatedText,
            PhoneNumberUtils.formatNumber(annotatedText)));
  }

  private void displayDefaultInfo(String annotatedText) {
    output.append(getString(R.string.unknown_entity_info, annotatedText));
  }

  private void displayUrlInfo(String annotatedText) {
    output.append(getString(R.string.url_entity_info, annotatedText));
  }

  private void displayDateTimeInfo(Entity entity, String annotatedText) {
    output.append(
        getString(
            R.string.date_time_entity_info,
            annotatedText,
            DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.LONG)
                .format(new Date(entity.asDateTimeEntity().getTimestampMillis())),
            convertGranularityToString(entity)));
  }

  private String convertGranularityToString(Entity entity) {
    DateTimeEntity dateTimeEntity = entity.asDateTimeEntity();
    @DateTimeGranularity int granularity = dateTimeEntity.getDateTimeGranularity();
    switch (granularity) {
      case GRANULARITY_YEAR:
        return getString(R.string.granularity_year);
      case GRANULARITY_MONTH:
        return getString(R.string.granularity_month);
      case GRANULARITY_WEEK:
        return getString(R.string.granularity_week);
      case GRANULARITY_DAY:
        return getString(R.string.granularity_day);
      case GRANULARITY_HOUR:
        return getString(R.string.granularity_hour);
      case GRANULARITY_MINUTE:
        return getString(R.string.granularity_minute);
      case GRANULARITY_SECOND:
        return getString(R.string.granularity_second);
      default:
        return getString(R.string.granularity_unknown);
    }
  }

  private void displayTrackingNoInfo(Entity entity, String annotatedText) {
    TrackingNumberEntity trackingNumberEntity = entity.asTrackingNumberEntity();
    output.append(
        getString(
            R.string.tracking_number_entity_info,
            annotatedText,
            trackingNumberEntity.getParcelCarrier(),
            trackingNumberEntity.getParcelTrackingNumber()));
  }

  private void displayPaymentCardInfo(Entity entity, String annotatedText) {
    PaymentCardEntity paymentCardEntity = entity.asPaymentCardEntity();
    output.append(
        getString(
            R.string.payment_card_entity_info,
            annotatedText,
            paymentCardEntity.getPaymentCardNetwork(),
            paymentCardEntity.getPaymentCardNumber()));
  }

  private void displayIsbnInfo(Entity entity, String annotatedText) {
    output.append(
        getString(R.string.isbn_entity_info, annotatedText, entity.asIsbnEntity().getIsbn()));
  }

  private void displayIbanInfo(Entity entity, String annotatedText) {
    IbanEntity ibanEntity = entity.asIbanEntity();
    output.append(
        getString(
            R.string.iban_entity_info,
            annotatedText,
            ibanEntity.getIban(),
            ibanEntity.getIbanCountryCode()));
  }

  private void displayFlightNoInfo(Entity entity, String annotatedText) {
    FlightNumberEntity flightNumberEntity = entity.asFlightNumberEntity();
    output.append(
        getString(
            R.string.flight_number_entity_info,
            annotatedText,
            flightNumberEntity.getAirlineCode(),
            flightNumberEntity.getFlightNumber()));
  }

  private void displayMoneyEntityInfo(Entity entity, String annotatedText) {
    MoneyEntity moneyEntity = entity.asMoneyEntity();
    output.append(
        getString(
            R.string.money_entity_info,
            annotatedText,
            moneyEntity.getUnnormalizedCurrency(),
            moneyEntity.getIntegerPart(),
            moneyEntity.getFractionalPart()));
  }
}
