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
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.mlkit.nl.smartreply.SmartReplySuggestion;
import com.google.mlkit.samples.nl.smartreply.R;
import java.util.ArrayList;
import java.util.List;

/** RecyclerView Adapter for reply messages. */
public class ReplyChipAdapter extends RecyclerView.Adapter<ReplyChipAdapter.ViewHolder> {

  /** Listener on click. */
  public interface ClickListener {

    void onChipClick(@NonNull String chipText);
  }

  private final List<SmartReplySuggestion> suggestions = new ArrayList<>();
  private final ClickListener listener;

  public ReplyChipAdapter(@NonNull ClickListener listener) {
    this.listener = listener;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View v =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.smart_reply_chip, parent, false);
    return new ViewHolder(v);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    SmartReplySuggestion suggestion = suggestions.get(position);
    holder.bind(suggestion);
  }

  @Override
  public int getItemCount() {
    return suggestions.size();
  }

  public void setSuggestions(List<SmartReplySuggestion> suggestions) {
    this.suggestions.clear();
    this.suggestions.addAll(suggestions);
    notifyDataSetChanged();
  }

  /** View holder to bind suggestions. */
  public class ViewHolder extends RecyclerView.ViewHolder {

    private final TextView text;

    public ViewHolder(@NonNull View itemView) {
      super(itemView);
      this.text = itemView.findViewById(R.id.smartReplyText);
    }

    public void bind(final SmartReplySuggestion suggestion) {
      text.setText(suggestion.getText());
      itemView.setOnClickListener(view -> listener.onChipClick(suggestion.getText()));
    }
  }
}
