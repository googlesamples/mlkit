package com.google.mlkit.samples.nl.translate.kotlin

import android.app.AlertDialog
import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.common.collect.ImmutableList
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateLanguage.Language
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.samples.nl.translate.R
import java.util.ArrayList
import java.util.Locale

class ModelManagementActivityKotlin : AppCompatActivity() {
  companion object {
    private const val TAG = "ModelManagementActivity"

    fun makeLaunchIntent(context: Context?): Intent {
      return Intent(context, ModelManagementActivityKotlin::class.java)
    }
  }

  private val modelManager = RemoteModelManager.getInstance()

  private lateinit var listAdapter: ModelManagementAdapter
  private var languages: ImmutableList<String> = ImmutableList.of()
  private var availableModels: Set<TranslateRemoteModel>? = null

  private val currentDownloads: MutableList<String> = ArrayList()
  private lateinit var downloadsSnackbar: Snackbar

  public override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_model_management)
    val languagesBuilder = ImmutableList.builder<String>()
    for (language in TranslateLanguage.getAllLanguages()) {
      if ("English".equals(language)) {
        continue
      }
      languagesBuilder.add(language)
    }
    languages = languagesBuilder.build()
    val listView = findViewById<ListView>(android.R.id.list)
    listAdapter = ModelManagementAdapter()
    listView.adapter = listAdapter
    listView.onItemClickListener = listAdapter

    downloadsSnackbar =
      Snackbar.make(findViewById(android.R.id.content), "", Snackbar.LENGTH_INDEFINITE)
  }

  override fun onStart() {
    super.onStart()
    refreshAvailabilityData()
  }

  private fun refreshAvailabilityData() {
    modelManager
      .getDownloadedModels(TranslateRemoteModel::class.java)
      .addOnSuccessListener(this) { result: Set<TranslateRemoteModel>? ->
        availableModels = result
        listAdapter.notifyDataSetChanged()
      }
      .addOnFailureListener(this) { e: Exception ->
        showError(
          e,
          R.string.error_get_models
        )
      }
  }

  private fun downloadModel(model: TranslateRemoteModel) {
    currentDownloads.add(model.language)
    updateDownloadsSnackbar()
    modelManager
      .download(model, DownloadConditions.Builder().requireWifi().build())
      .addOnFailureListener(this) { e: Exception ->
        showError(
          e,
          R.string.error_download
        )
      }
      .addOnSuccessListener(this) { refreshAvailabilityData() }
      .addOnCompleteListener(this) {
        currentDownloads.remove(model.language)
        updateDownloadsSnackbar()
      }
  }

  private fun updateDownloadsSnackbar() {
    if (currentDownloads.isEmpty()) {
      downloadsSnackbar.dismiss()
      return
    }
    downloadsSnackbar.setText(
      getString(
        R.string.download_progress,
        currentDownloads.map { Locale(it).displayName }.joinToString(", ")
      )
    )
    downloadsSnackbar.show()
  }

  private fun deleteModel(model: TranslateRemoteModel) {
    if (availableModels?.contains(model) != true) {
      showToast(R.string.model_not_downloaded)
      return
    }
    val name = Locale(model.language).displayName
    val dialog = buildProgressDialog(getString(R.string.deletion_progress, name))
    modelManager
      .deleteDownloadedModel(model)
      .addOnSuccessListener { showToast(R.string.deletion_successful) }
      .addOnFailureListener { e: Exception ->
        showError(
          e,
          R.string.error_delete
        )
      }
      .addOnCompleteListener {
        dialog.dismiss()
        refreshAvailabilityData()
      }
  }

  private inner class ModelManagementAdapter : BaseAdapter(), OnItemClickListener {
    override fun getCount(): Int {
      return languages.size
    }

    override fun getItem(position: Int): String {
      return languages[position]
    }

    override fun getItemId(position: Int): Long {
      return position.toLong()
    }

    @Language
    private fun getLanguage(position: Int): String {
      return languages[position]
    }

    private fun getModel(position: Int): TranslateRemoteModel {
      return TranslateRemoteModel.Builder(getLanguage(position)).build()
    }

    private fun getLanguageName(position: Int): String {
      return Locale(getItem(position)).displayLanguage
    }

    override fun getView(
      position: Int,
      convertView: View?,
      parent: ViewGroup
    ): View {
      val view = convertView
        ?: layoutInflater.inflate(R.layout.item_model_management, parent, false)
      val textView = view.findViewById<TextView>(android.R.id.text1)
      textView.text = getLanguageName(position)
      availableModels?.let { availableModels ->
        textView.setCompoundDrawablesWithIntrinsicBounds(
          null,
          null,
          ContextCompat.getDrawable(
            this@ModelManagementActivityKotlin,
            if (availableModels.contains(getModel(position))) R.drawable.ic_baseline_delete_24
            else R.drawable.ic_file_download_white_24dp
          ),
          null
        )
      }
      return view
    }

    override fun onItemClick(
      parent: AdapterView<*>?,
      view: View,
      position: Int,
      id: Long
    ) {
      val availableModels = availableModels ?: return showToast(R.string.error_get_models)
      if (availableModels.contains(getModel(position))) {
        AlertDialog.Builder(this@ModelManagementActivityKotlin)
          .setMessage(
            getString(R.string.deletion_confirmation_prompt, getLanguageName(position))
          )
          .setPositiveButton(R.string.yes) { _, _ ->
            deleteModel(getModel(position))
          }
          .setNegativeButton(R.string.no) { _, _ -> }
          .show()
      } else {
        downloadModel(getModel(position))
      }
    }
  }

  private fun showError(exception: Exception, @StringRes messageId: Int) {
    showToast(messageId)
    Log.e(TAG, getString(messageId), exception)
  }

  private fun buildProgressDialog(message: String): Dialog {
    val indeterminate = true
    val cancelable = false
    return ProgressDialog.show(
      this,
      getString(R.string.app_name),
      message,
      indeterminate,
      cancelable
    )
  }

  private fun showToast(@StringRes messageId: Int) {
    Toast.makeText(this, messageId, Toast.LENGTH_LONG).show()
  }
}
