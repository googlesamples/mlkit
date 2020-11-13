package com.google.mlkit.samples.nl.entityextraction.java;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.entityextraction.EntityExtractionRemoteModel;
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions;
import com.google.mlkit.samples.nl.entityextraction.R;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Activity for user to select models. */
public class ModelsActivityJava extends AppCompatActivity {

  private static final String TAG = "ModelsActivityJava";
  public static final String MODEL_KEY = "model";

  private ListView listView;
  private final RemoteModelManager remoteModelManager = RemoteModelManager.getInstance();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_models);

    listView = findViewById(R.id.models_list_view);
    List</* @ModelIdentifier */ String> languages = EntityExtractorOptions.getAllModelIdentifiers();
    LanguageAdapter languageAdapter = new LanguageAdapter(languages);
    listView.setAdapter(languageAdapter);

    listView.setOnItemClickListener(
        (parent, view, position, id) -> {
          Intent intent = new Intent(ModelsActivityJava.this, MainActivityJava.class);
          intent.putExtra(MODEL_KEY, listView.getItemAtPosition(position).toString());
          setResult(RESULT_OK, intent);
          finish();
        });
  }

  class LanguageAdapter extends BaseAdapter {

    private final List<String> languageList;
    private final Set<String> downloadedModels = new HashSet<>();
    private final List<String> downloadingModels = new ArrayList<>();

    @Nullable private Snackbar downloadsSnackbar;

    public LanguageAdapter(List<String> languageList) {
      this.languageList = languageList;
      Task<Set<EntityExtractionRemoteModel>> downloadedModelsTask =
          remoteModelManager.getDownloadedModels(EntityExtractionRemoteModel.class);
      downloadedModelsTask
          .addOnFailureListener(e -> Log.w(TAG, "DownloadedModels failed with exception", e))
          .addOnSuccessListener(
              models -> {
                downloadedModels.clear();
                for (EntityExtractionRemoteModel model : models) {
                  downloadedModels.add(model.getModelIdentifier());
                }
                notifyDataSetChanged();
              });
    }

    @Override
    public int getCount() {
      return languageList.size();
    }

    @Override
    public String getItem(int position) {
      return languageList.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView =
            LayoutInflater.from(ModelsActivityJava.this).inflate(R.layout.list_item, parent, false);
      }
      TextView textView = convertView.findViewById(R.id.text_view_item);
      String modelIdentifier = getItem(position);
      textView.setText(modelIdentifier);
      ImageView imageView = convertView.findViewById(R.id.image_view_item);

      EntityExtractionRemoteModel remoteModel =
          new EntityExtractionRemoteModel.Builder(modelIdentifier).build();
      if (downloadedModels.contains(modelIdentifier)) {
        imageView.setImageResource(R.drawable.ic_baseline_delete_gray_32);
        imageView.setOnClickListener(
            view ->
                remoteModelManager
                    .deleteDownloadedModel(remoteModel)
                    .addOnFailureListener(
                        e -> Log.w(TAG, "Deleting model failed with exception", e))
                    .addOnSuccessListener(
                        aVoid -> {
                          downloadedModels.remove(modelIdentifier);
                          notifyDataSetChanged();
                        }));
      } else {
        imageView.setImageResource(R.drawable.ic_baseline_get_app_gray_32);
        imageView.setOnClickListener(
            view -> {
              downloadingModels.add(modelIdentifier);
              updateSnackbar();
              remoteModelManager
                  .download(remoteModel, new DownloadConditions.Builder().build())
                  .addOnFailureListener(
                      e -> Log.w(TAG, "Downloading model failed with exception", e))
                  .addOnSuccessListener(
                      aVoid -> {
                        downloadedModels.add(modelIdentifier);
                        notifyDataSetChanged();
                      })
                  .addOnCompleteListener(
                      r -> {
                        downloadingModels.remove(modelIdentifier);
                        updateSnackbar();
                      });
            });
      }
      return convertView;
    }

    private void updateSnackbar() {
      if (downloadingModels.isEmpty()) {
        if (downloadsSnackbar != null) {
          downloadsSnackbar.dismiss();
        }
        return;
      }
      if (downloadsSnackbar == null) {
        downloadsSnackbar = Snackbar.make(listView, "", Snackbar.LENGTH_INDEFINITE);
      }
      downloadsSnackbar.setText(getSnackbarMessage());
      downloadsSnackbar.show();
    }

    private String getSnackbarMessage() {
      return getResources()
          .getQuantityString(
              R.plurals.snackbar_message,
              downloadingModels.size(),
              getFormattedListOfDownloadingModels());
    }

    private String getFormattedListOfDownloadingModels() {
      List<String> uppercasedDownloadingModels = new ArrayList<>();
      for (String downloadingModel : downloadingModels) {
        uppercasedDownloadingModels.add(downloadingModel.toUpperCase(Locale.US));
      }
      return TextUtils.join(", ", uppercasedDownloadingModels);
    }
  }
}
