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

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.mlkit.genai.demo.kotlin.ImageDescriptionActivity
import com.google.mlkit.genai.demo.kotlin.ProofreadingActivity
import com.google.mlkit.genai.demo.kotlin.RewritingActivity
import com.google.mlkit.genai.demo.kotlin.SummarizationActivity

/** Entry activity to choose which ML Kit GenAI API to demo. */
class EntryChoiceActivity : AppCompatActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_entry_choice)

    val activityItems =
      listOf(
        ActivityItem(R.string.summarization_entry_title_kotlin, SummarizationActivity::class.java),
        ActivityItem(
          R.string.summarization_entry_title_java,
          com.google.mlkit.genai.demo.java.SummarizationActivity::class.java,
        ),
        ActivityItem(R.string.rewriting_entry_title_kotlin, RewritingActivity::class.java),
        ActivityItem(
          R.string.rewriting_entry_title_java,
          com.google.mlkit.genai.demo.java.RewritingActivity::class.java,
        ),
        ActivityItem(R.string.proofreading_entry_title_kotlin, ProofreadingActivity::class.java),
        ActivityItem(
          R.string.proofreading_entry_title_java,
          com.google.mlkit.genai.demo.java.ProofreadingActivity::class.java,
        ),
        ActivityItem(
          R.string.image_description_entry_title_kotlin,
          ImageDescriptionActivity::class.java,
        ),
        ActivityItem(
          R.string.image_description_entry_title_java,
          com.google.mlkit.genai.demo.java.ImageDescriptionActivity::class.java,
        ),
      )

    val recyclerView: RecyclerView = findViewById(R.id.entry_recycler_view)
    recyclerView.layoutManager = LinearLayoutManager(this)
    recyclerView.adapter = ActivityAdapter(this, activityItems)
  }

  class ActivityAdapter(private val context: Context, private val items: List<ActivityItem>) :
    RecyclerView.Adapter<ActivityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
      val titleTextView: TextView = view.findViewById(R.id.title_text_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
      val layoutInflater = LayoutInflater.from(parent.context)
      return ViewHolder(layoutInflater.inflate(R.layout.entry_choice_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
      val item = items[position]
      holder.titleTextView.setText(item.titleResId)
      holder.itemView.setOnClickListener {
        context.startActivity(Intent(context, item.activityClass))
      }
    }

    override fun getItemCount(): Int = items.size
  }

  data class ActivityItem(val titleResId: Int, val activityClass: Class<*>)
}
