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

package com.google.mlkit.samples.nl.smartreply.kotlin.chat

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import com.google.mlkit.samples.nl.smartreply.R
import java.util.ArrayList

class ReplyChipAdapter(
  private val listener: ClickListener
) : RecyclerView.Adapter<ReplyChipAdapter.ViewHolder>() {

  private val suggestions = ArrayList<SmartReplySuggestion>()

  interface ClickListener {

    fun onChipClick(chipText: String)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val v = LayoutInflater.from(parent.context).inflate(R.layout.smart_reply_chip, parent, false)
    return ViewHolder(v)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    val suggestion = suggestions[position]
    holder.bind(suggestion)
  }

  override fun getItemCount(): Int {
    return suggestions.size
  }

  fun setSuggestions(suggestions: List<SmartReplySuggestion>) {
    this.suggestions.clear()
    this.suggestions.addAll(suggestions)
    notifyDataSetChanged()
  }

  inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val text: TextView = itemView.findViewById(R.id.smartReplyText)

    fun bind(suggestion: SmartReplySuggestion) {
      text.text = suggestion.text
      itemView.setOnClickListener { listener.onChipClick(suggestion.text) }
    }
  }
}
