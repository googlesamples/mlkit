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

package com.mlkit.example.internal;

import android.app.Activity;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.util.List;

public class ChoiceAdapter extends RecyclerView.Adapter<ChoiceAdapter.ViewHolder> {

  private final Activity activity;
  private final List<Choice> choices;

  public ChoiceAdapter(Activity activity, List<Choice> choices) {
    this.activity = activity;
    this.choices = choices;
  }

  @NonNull
  @Override
  public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
    View view =
        LayoutInflater.from(parent.getContext()).inflate(R.layout.item_choice, parent, false);
    return new ViewHolder(view);
  }

  @Override
  public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
    Choice choice = choices.get(position);
    holder.bind(choice);
  }

  @Override
  public int getItemCount() {
    return choices.size();
  }

  public class ViewHolder extends RecyclerView.ViewHolder {

    private final TextView titleText;
    private final TextView descText;
    private final Button launchButton;

    public ViewHolder(View itemView) {
      super(itemView);
      titleText = itemView.findViewById(R.id.item_title);
      descText = itemView.findViewById(R.id.item_description);
      launchButton = itemView.findViewById(R.id.item_launch_button);
    }

    public void bind(final Choice choice) {
      titleText.setText(choice.title);
      descText.setText(choice.description);
      launchButton.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              activity.startActivity(choice.launchIntent);
            }
          });
    }
  }
}
