package com.google.mlkit.samples.nl.translate.java;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.mlkit.common.model.DownloadConditions;
import com.google.mlkit.common.model.RemoteModelManager;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.TranslateLanguage.Language;
import com.google.mlkit.nl.translate.TranslateRemoteModel;
import com.google.mlkit.samples.nl.translate.R;
import com.google.mlkit.samples.nl.translate.kotlin.ModelManagementActivityKotlin;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Activity for Model Management. */
public class ModelManagementActivityJava extends AppCompatActivity {

  private static final String TAG = "ModelManagementActivity";
  private RemoteModelManager modelManager;

  private ModelManagementAdapter listAdapter;
  private ImmutableList</* @Language */ String> languages;
  @Nullable private Set<TranslateRemoteModel> availableModels;

  private final List<String> currentDownloads = new ArrayList<>();
  @Nullable private Snackbar downloadsSnackbar;

  public static Intent makeLaunchIntent(Context context) {
    return new Intent(context, ModelManagementActivityKotlin.class);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_model_management);

    modelManager = RemoteModelManager.getInstance();
    ImmutableList.Builder<String> languagesBuilder = ImmutableList.builder();
    for (String language : TranslateLanguage.getAllLanguages()) {
      if ("ENGLISH".equals(language)) {
        continue;
      }
      languagesBuilder.add(language);
    }
    languages = languagesBuilder.build();

    ListView listView = findViewById(android.R.id.list);
    listAdapter = new ModelManagementAdapter();
    listView.setAdapter(listAdapter);
    listView.setOnItemClickListener(listAdapter);
  }

  @Override
  protected void onStart() {
    super.onStart();
    refreshAvailabilityData();
  }

  private void refreshAvailabilityData() {
    modelManager
        .getDownloadedModels(TranslateRemoteModel.class)
        .addOnSuccessListener(
            this,
            result -> {
              availableModels = result;
              listAdapter.notifyDataSetChanged();
            })
        .addOnFailureListener(this, e -> showError(e, R.string.error_get_models));
  }

  private void downloadModel(TranslateRemoteModel model) {
    currentDownloads.add(model.getLanguage());
    updateDownloadsSnackbar();

    modelManager
        .download(model, new DownloadConditions.Builder().requireWifi().build())
        .addOnFailureListener(this, e -> showError(e, R.string.error_download))
        .addOnSuccessListener(this, aVoid -> refreshAvailabilityData())
        .addOnCompleteListener(
            this,
            task -> {
              currentDownloads.remove(model.getLanguage());
              updateDownloadsSnackbar();
            });
  }

  private void updateDownloadsSnackbar() {
    if (downloadsSnackbar == null) {
      downloadsSnackbar =
          Snackbar.make(findViewById(android.R.id.content), "", Snackbar.LENGTH_INDEFINITE);
    }

    if (currentDownloads.isEmpty()) {
      downloadsSnackbar.dismiss();
      return;
    }

    downloadsSnackbar.setText(
        getString(
            R.string.download_progress,
            FluentIterable.from(currentDownloads)
                .transform(input -> new Locale(input).getDisplayName())
                .join(Joiner.on(", "))));

    downloadsSnackbar.show();
  }

  private void deleteModel(TranslateRemoteModel model) {
    if (availableModels != null && !availableModels.contains(model)) {
      showToast(R.string.model_not_downloaded);
      return;
    }

    String name = new Locale(model.getLanguage()).getDisplayName();
    final Dialog dialog = buildProgressDialog(getString(R.string.deletion_progress, name));
    modelManager
        .deleteDownloadedModel(model)
        .addOnSuccessListener(aVoid -> showToast(R.string.deletion_successful))
        .addOnFailureListener(e -> showError(e, R.string.error_delete))
        .addOnCompleteListener(
            task -> {
              dialog.dismiss();
              refreshAvailabilityData();
            });
  }

  private class ModelManagementAdapter extends BaseAdapter implements OnItemClickListener {

    @Override
    public int getCount() {
      return languages.size();
    }

    @Override
    public String getItem(int position) {
      return languages.get(position);
    }

    @Override
    public long getItemId(int position) {
      return position;
    }

    @Language
    private String getLanguage(int position) {
      return languages.get(position);
    }

    private TranslateRemoteModel getModel(int position) {
      return new TranslateRemoteModel.Builder(getLanguage(position)).build();
    }

    private String getLanguageName(int position) {
      return new Locale(getItem(position)).getDisplayLanguage();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
      if (convertView == null) {
        convertView = getLayoutInflater().inflate(R.layout.item_model_management, parent, false);
      }

      TextView textView = convertView.findViewById(android.R.id.text1);
      textView.setText(getLanguageName(position));

      if (availableModels != null) {
        textView.setCompoundDrawablesWithIntrinsicBounds(
            null,
            null,
            ContextCompat.getDrawable(
                ModelManagementActivityJava.this,
                availableModels.contains(getModel(position))
                    ? R.drawable.ic_baseline_delete_24
                    : R.drawable.ic_file_download_white_24dp),
            null);
      }

      return convertView;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
      if (availableModels == null) {
        showToast(R.string.error_get_models);
        return;
      }
      if (availableModels.contains(getModel(position))) {
        new AlertDialog.Builder(ModelManagementActivityJava.this)
            .setMessage(getString(R.string.deletion_confirmation_prompt, getLanguageName(position)))
            .setPositiveButton(R.string.yes, (dialog, which) -> deleteModel(getModel(position)))
            .setNegativeButton(
                R.string.no,
                (dialog1, which) -> {
                  // Do nothing.
                })
            .show();
      } else {
        downloadModel(getModel(position));
      }
    }
  }

  private void showError(Exception exception, @StringRes int messageId) {
    showToast(messageId);
    Log.e(TAG, getString(messageId), exception);
  }

  private Dialog buildProgressDialog(String message) {
    return ProgressDialog.show(
        this,
        getString(R.string.app_name),
        message,
        /*indeterminate=*/ true,
        /*cancelable=*/ false);
  }

  private void showToast(@StringRes int messageId) {
    Toast.makeText(this, messageId, Toast.LENGTH_LONG).show();
  }
}
