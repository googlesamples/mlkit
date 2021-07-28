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

package com.google.mlkit.samples.nl.smartreply.java.chat;

import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.mlkit.samples.nl.smartreply.R;
import com.google.mlkit.samples.nl.smartreply.java.model.Message;
import java.util.ArrayList;
import java.util.List;

class MessageListAdapter extends RecyclerView.Adapter<MessageListAdapter.MessageViewHolder> {

  private final List<Message> messagesList = new ArrayList<>();
  private boolean emulatingRemoteUser = false;

  public MessageListAdapter() {}

  @Override
  @NonNull
  public MessageListAdapter.MessageViewHolder onCreateViewHolder(
      @NonNull ViewGroup parent, int viewType) {
    ViewGroup v =
        (ViewGroup) LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
    return new MessageViewHolder(v);
  }

  @Override
  public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
    Message message = messagesList.get(position);
    holder.bind(message);
  }

  @Override
  public int getItemViewType(int position) {
    if (messagesList.get(position).isLocalUser != emulatingRemoteUser) {
      return R.layout.item_message_local;
    } else {
      return R.layout.item_message_remote;
    }
  }

  @Override
  public int getItemCount() {
    return messagesList.size();
  }

  public void setMessages(List<Message> messages) {
    messagesList.clear();
    messagesList.addAll(messages);
    notifyDataSetChanged();
  }

  public boolean getEmulatingRemoteUser() {
    return this.emulatingRemoteUser;
  }

  public void setEmulatingRemoteUser(boolean emulatingRemoteUser) {
    this.emulatingRemoteUser = emulatingRemoteUser;
    notifyDataSetChanged();
  }

  static class MessageViewHolder extends RecyclerView.ViewHolder {

    private final ImageView icon;
    private final TextView text;

    MessageViewHolder(View itemView) {
      super(itemView);
      icon = itemView.findViewById(R.id.messageAuthor);
      text = itemView.findViewById(R.id.messageText);
    }

    private void bind(Message message) {
      icon.setImageDrawable(message.getIcon(icon.getContext()));
      text.setText(message.text);
    }
  }
}
