package com.google.mlkit.samples.nl.entityextraction.kotlin

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView.OnItemClickListener
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.entityextraction.EntityExtractionRemoteModel
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.samples.nl.entityextraction.R
import java.util.Locale

class ModelsActivityKotlin : AppCompatActivity() {

  companion object {
    private const val TAG = "ModelsActivityKotlin"
    const val MODEL_KEY = "model"
  }

  private lateinit var listView: ListView
  private val remoteModelManager = RemoteModelManager.getInstance()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_models)

    listView = findViewById(R.id.models_list_view)
    val languages = EntityExtractorOptions.getAllModelIdentifiers()
    val languageAdapter = LanguageAdapter(languages)
    listView.adapter = languageAdapter

    listView.onItemClickListener =
      OnItemClickListener { _, _, position, _ ->
        val intent = Intent(this@ModelsActivityKotlin, MainActivityKotlin::class.java)
        intent.putExtra(
          MODEL_KEY,
          listView.getItemAtPosition(position).toString()
        )
        setResult(Activity.RESULT_OK, intent)
        finish()
      }
  }

  internal inner class LanguageAdapter(private val languageList: List<String>) : BaseAdapter() {
    init {
      val downloadedModelsTask =
        remoteModelManager.getDownloadedModels(EntityExtractionRemoteModel::class.java)
      downloadedModelsTask
        .addOnFailureListener { e: Exception? ->
          Log.w(TAG, "DownloadedModels failed with exception", e)
        }
        .addOnSuccessListener { models: Set<EntityExtractionRemoteModel> ->
          downloadedModels.clear()
          for (model in models) {
            downloadedModels.add(model.modelIdentifier)
          }
          notifyDataSetChanged()
        }
    }

    private val downloadedModels: MutableSet<String> = HashSet()
    private val downloadingModels: MutableList<String> = ArrayList()
    private var downloadsSnackbar: Snackbar? = null

    override fun getCount(): Int {
      return languageList.size
    }

    override fun getItem(position: Int): String {
      return languageList[position]
    }

    override fun getItemId(position: Int): Long {
      return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      var view = convertView
      if (view == null) {
        view =
          LayoutInflater.from(this@ModelsActivityKotlin).inflate(R.layout.list_item, parent, false)
      }
      val textView = view!!.findViewById<TextView>(R.id.text_view_item)
      val modelIdentifier = getItem(position)
      textView.text = modelIdentifier
      val imageView = view.findViewById<ImageView>(R.id.image_view_item)

      val remoteModel = EntityExtractionRemoteModel.Builder(modelIdentifier).build()
      if (downloadedModels.contains(modelIdentifier)) {
        imageView.setImageResource(R.drawable.ic_baseline_delete_gray_32)
        imageView.setOnClickListener {
          remoteModelManager
            .deleteDownloadedModel(remoteModel)
            .addOnFailureListener { e: Exception? ->
              Log.w(TAG, "Deleting model failed with exception", e)
            }
            .addOnSuccessListener {
              downloadedModels.remove(modelIdentifier)
              notifyDataSetChanged()
            }
        }
      } else {
        imageView.setImageResource(R.drawable.ic_baseline_get_app_gray_32)
        imageView.setOnClickListener {
          downloadingModels.add(modelIdentifier)
          updateSnackbar()
          remoteModelManager
            .download(remoteModel, DownloadConditions.Builder().build())
            .addOnFailureListener { e: Exception? ->
              Log.w(TAG, "Downloading model failed with exception", e)
            }
            .addOnSuccessListener {
              downloadedModels.add(modelIdentifier)
              notifyDataSetChanged()
            }
            .addOnCompleteListener {
              downloadingModels.remove(modelIdentifier)
              updateSnackbar()
            }
        }
      }
      return view
    }

    private fun updateSnackbar() {
      if (downloadingModels.isEmpty()) {
        downloadsSnackbar?.dismiss()
        return
      }
      downloadsSnackbar =
        (downloadsSnackbar ?: Snackbar.make(listView, "", Snackbar.LENGTH_INDEFINITE)).apply {
          setText(getSnackbarMessage())
          show()
        }
    }

    private fun getSnackbarMessage(): String = resources.getQuantityString(
      R.plurals.snackbar_message,
      downloadingModels.size,
      getFormattedListOfDownloadingModels()
    )

    private fun getFormattedListOfDownloadingModels(): String {
      return downloadingModels.map { it.toUpperCase(Locale.US) }.joinToString { ", " }
    }
  }
}
