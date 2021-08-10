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
import android.widget.ImageView
import android.widget.TextView
import com.google.mlkit.samples.nl.smartreply.R
import com.google.mlkit.samples.nl.smartreply.kotlin.model.Message
import java.util.ArrayList

internal class MessageListAdapter : RecyclerView.Adapter<MessageListAdapter.MessageViewHolder>() {

  private val messagesList = ArrayList<Message>()

  var emulatingRemoteUser = false
    set(emulatingRemoteUser) {
      field = emulatingRemoteUser
      notifyDataSetChanged()
    }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
    val v = LayoutInflater.from(parent.context).inflate(viewType, parent, false) as ViewGroup
    return MessageViewHolder(v)
  }

  override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
    val message = messagesList[position]
    holder.bind(message)
  }

  override fun getItemViewType(position: Int): Int {
    return if (
      messagesList[position].isLocalUser && !emulatingRemoteUser ||
      !messagesList[position].isLocalUser && emulatingRemoteUser
    ) {
      R.layout.item_message_local
    } else {
      R.layout.item_message_remote
    }
  }

  override fun getItemCount(): Int {
    return messagesList.size
  }

  fun setMessages(messages: List<Message>) {
    messagesList.clear()
    messagesList.addAll(messages)
    notifyDataSetChanged()
  }

  inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val icon: ImageView
    private val text: TextView

    init {
      icon = itemView.findViewById(R.id.messageAuthor)
      text = itemView.findViewById(R.id.messageText)
    }

    fun bind(message: Message) {
      icon.setImageDrawable(message.getIcon(icon.context))
      text.text = message.text
    }
  }
}
