/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.kotlin

import android.content.Context
import android.content.Intent
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import com.google.mlkit.vision.demo.R

/** Demo app chooser which allows you pick from all available testing Activities. */
class ChooserActivity :
  AppCompatActivity(), ActivityCompat.OnRequestPermissionsResultCallback, OnItemClickListener {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Log.d(TAG, "onCreate")
    setContentView(R.layout.activity_chooser)

    // Set up ListView and Adapter
    val listView = findViewById<ListView>(R.id.test_activity_list_view)
    val adapter = MyArrayAdapter(this, android.R.layout.simple_list_item_2, CLASSES)
    adapter.setDescriptionIds(DESCRIPTION_IDS)
    listView.adapter = adapter
    listView.onItemClickListener = this
  }

  override fun onItemClick(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
    val clicked = CLASSES[position]
    startActivity(Intent(this, clicked))
  }

  private class MyArrayAdapter(
    private val ctx: Context,
    resource: Int,
    private val classes: Array<Class<*>>
  ) : ArrayAdapter<Class<*>>(ctx, resource, classes) {
    private var descriptionIds: IntArray? = null

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
      var view = convertView

      if (convertView == null) {
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        view = inflater.inflate(android.R.layout.simple_list_item_2, null)
      }

      (view!!.findViewById<View>(android.R.id.text1) as TextView).text =
        classes[position].simpleName
      descriptionIds?.let {
        (view.findViewById<View>(android.R.id.text2) as TextView).setText(it[position])
      }

      return view
    }

    fun setDescriptionIds(descriptionIds: IntArray) {
      this.descriptionIds = descriptionIds
    }
  }

  companion object {
    private const val TAG = "ChooserActivity"
    private val CLASSES =
      if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP)
        arrayOf<Class<*>>(
          LivePreviewActivity::class.java,
          StillImageActivity::class.java,
        )
      else
        arrayOf<Class<*>>(
          LivePreviewActivity::class.java,
          StillImageActivity::class.java,
          CameraXLivePreviewActivity::class.java,
          CameraXSourceDemoActivity::class.java
        )
    private val DESCRIPTION_IDS =
      if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP)
        intArrayOf(
          R.string.desc_camera_source_activity,
          R.string.desc_still_image_activity,
        )
      else
        intArrayOf(
          R.string.desc_camera_source_activity,
          R.string.desc_still_image_activity,
          R.string.desc_camerax_live_preview_activity,
          R.string.desc_cameraxsource_demo_activity
        )
  }
}
