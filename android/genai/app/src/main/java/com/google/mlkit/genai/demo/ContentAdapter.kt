/*
 * Copyright 2025 Google LLC. All rights reserved.
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
package com.google.mlkit.genai.demo

import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import android.text.SpannedString
import android.util.Pair
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/** A recycler view adapter for displaying the request and response views. */
class ContentAdapter : RecyclerView.Adapter<ViewHolder>() {
  private var recyclerView: RecyclerView? = null

  private val contentList: MutableList<Pair<Int, SpannedStringOrUri>> = ArrayList()

  fun addContent(viewType: Int, content: String) {
    addContent(viewType, SpannedString(content))
  }

  fun addContent(viewType: Int, content: SpannedString) {
    addContent(viewType, SpannedStringOrUri.AsSpannedString(content))
  }

  fun addContent(viewType: Int, content: Uri) {
    addContent(viewType, SpannedStringOrUri.AsUri(content))
  }

  private fun addContent(viewType: Int, content: SpannedStringOrUri) {
    contentList.add(Pair(viewType, content))
    notifyItemInserted(contentList.size - 1)
    recyclerView?.smoothScrollToPosition(contentList.size - 1)
  }

  fun updateStreamingResponse(response: String) {
    updateStreamingResponse(SpannedString(response))
  }

  fun updateStreamingResponse(response: SpannedString) {
    contentList[contentList.size - 1] =
      Pair(VIEW_TYPE_RESPONSE, SpannedStringOrUri.AsSpannedString(response))
    notifyDataSetChanged()
    recyclerView?.smoothScrollToPosition(contentList.size - 1)
  }

  override fun getItemViewType(position: Int): Int {
    return contentList[position].first
  }

  override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
    super.onAttachedToRecyclerView(recyclerView)
    this.recyclerView = recyclerView
  }

  override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
    val layoutInflater = LayoutInflater.from(viewGroup.context)
    return when (viewType) {
      VIEW_TYPE_REQUEST_TEXT ->
        TextViewHolder(layoutInflater.inflate(R.layout.row_item_request_text, viewGroup, false))
      VIEW_TYPE_REQUEST_IMAGE ->
        ImageViewHolder(layoutInflater.inflate(R.layout.row_item_request_image, viewGroup, false))
      VIEW_TYPE_RESPONSE,
      VIEW_TYPE_RESPONSE_ERROR ->
        TextViewHolder(layoutInflater.inflate(R.layout.row_item_response, viewGroup, false))
      else -> throw IllegalArgumentException("Invalid view type $viewType")
    }
  }

  override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
    val content = contentList[position]
    if (viewHolder is TextViewHolder) {
      viewHolder.bind(content.first, (content.second as SpannedStringOrUri.AsSpannedString).value)
    } else if (viewHolder is ImageViewHolder) {
      viewHolder.bind((content.second as SpannedStringOrUri.AsUri).value)
    }
  }

  override fun getItemCount(): Int {
    return contentList.size
  }

  /** Hosts text request or response item view. */
  class TextViewHolder internal constructor(itemView: View) : ViewHolder(itemView) {

    private val contentTextView: TextView = itemView.findViewById(R.id.content_text_view)
    private val defaultTextColors: ColorStateList = contentTextView.textColors

    fun bind(viewType: Int, content: SpannedString) {
      contentTextView.text = content
      if (viewType == VIEW_TYPE_RESPONSE_ERROR) {
        contentTextView.setTextColor(Color.RED)
      } else {
        contentTextView.setTextColor(defaultTextColors)
      }
    }
  }

  /** Hosts image request item view. */
  class ImageViewHolder internal constructor(itemView: View) : ViewHolder(itemView) {

    private val contentTextView: ImageView = itemView.findViewById(R.id.content_image_view)

    fun bind(imageUri: Uri) {
      contentTextView.setImageURI(imageUri)
    }
  }

  sealed class SpannedStringOrUri {
    data class AsSpannedString(val value: SpannedString) : SpannedStringOrUri()

    data class AsUri(val value: Uri) : SpannedStringOrUri()
  }

  companion object {
    const val VIEW_TYPE_REQUEST_TEXT: Int = 0
    const val VIEW_TYPE_REQUEST_IMAGE: Int = 1
    const val VIEW_TYPE_RESPONSE: Int = 2
    const val VIEW_TYPE_RESPONSE_ERROR: Int = 3
  }
}
