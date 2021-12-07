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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import com.google.mlkit.samples.nl.smartreply.R;
import com.google.mlkit.samples.nl.smartreply.java.model.Message;
import java.util.ArrayList;
import java.util.Calendar;

/** Represents an individual chat message. */
public class ChatFragment extends Fragment implements ReplyChipAdapter.ClickListener {

  private ChatViewModel viewModel;
  private TextView inputText;

  private RecyclerView chatRecycler;
  private MessageListAdapter chatAdapter;

  private ReplyChipAdapter chipAdapter;

  private TextView emulatedUserText;

  public static ChatFragment newInstance() {
    return new ChatFragment();
  }

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(true);
  }

  @Nullable
  @Override
  public View onCreateView(
      @NonNull LayoutInflater inflater,
      @Nullable ViewGroup container,
      @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.chat_fragment, container, false);
  }

  @SuppressLint("ClickableViewAccessibility")
  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    viewModel = new ViewModelProvider(this).get(ChatViewModel.class);

    chatRecycler = view.findViewById(R.id.chatHistory);
    emulatedUserText = view.findViewById(R.id.switchText);
    RecyclerView smartRepliesRecycler = view.findViewById(R.id.smartRepliesRecycler);
    inputText = view.findViewById(R.id.inputText);
    Button sendButton = view.findViewById(R.id.button);
    Button switchUserButton = view.findViewById(R.id.switchEmulatedUser);

    // Set up recycler view for chat messages
    LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
    chatRecycler.setLayoutManager(layoutManager);
    chatAdapter = new MessageListAdapter();

    // Set up recycler view for smart replies
    LinearLayoutManager chipManager = new LinearLayoutManager(getContext());
    chipManager.setOrientation(RecyclerView.HORIZONTAL);
    chipAdapter = new ReplyChipAdapter(this);
    smartRepliesRecycler.setLayoutManager(chipManager);
    smartRepliesRecycler.setAdapter(chipAdapter);

    chatRecycler.setAdapter(chatAdapter);
    chatRecycler.setOnTouchListener(
        (v, motionEvent) -> {
          InputMethodManager imm =
              (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
          imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
          return false;
        });

    switchUserButton.setOnClickListener(
        ignored -> {
          chatAdapter.setEmulatingRemoteUser(!chatAdapter.getEmulatingRemoteUser());
          viewModel.switchUser();
        });

    sendButton.setOnClickListener(
        ignored -> {
          String input = inputText.getText().toString();
          if (TextUtils.isEmpty(input)) {
            return;
          }

          viewModel.addMessage(input);
          inputText.setText("");
        });

    viewModel
        .getSuggestions()
        .observe(getViewLifecycleOwner(), suggestions -> chipAdapter.setSuggestions(suggestions));

    viewModel
        .getMessages()
        .observe(
            getViewLifecycleOwner(),
            messages -> {
              chatAdapter.setMessages(messages);
              if (chatAdapter.getItemCount() > 0) {
                chatRecycler.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
              }
            });

    viewModel
        .getEmulatingRemoteUser()
        .observe(
            getViewLifecycleOwner(),
            isEmulatingRemoteUser -> {
              if (isEmulatingRemoteUser) {
                emulatedUserText.setText(R.string.chatting_as_red);
                emulatedUserText.setTextColor(getResources().getColor(R.color.red));
              } else {
                emulatedUserText.setText(R.string.chatting_as_blue);
                emulatedUserText.setTextColor(getResources().getColor(R.color.blue));
              }
            });

    // Only set initial message for the new ViewModel instance.
    if (viewModel.getMessages().getValue() == null) {
      ArrayList<Message> messageList = new ArrayList<>();
      messageList.add(new Message("Hello. How are you?", false, System.currentTimeMillis()));
      viewModel.setMessages(messageList);
    }
  }

  @Override
  public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
    super.onCreateOptionsMenu(menu, inflater);
    inflater.inflate(R.menu.chat_fragment_actions, menu);
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    if (item.getItemId() == R.id.generateHistoryBasic) {
      generateChatHistoryBasic();
      return true;
    } else if (item.getItemId() == R.id.generateHistorySensitive) {
      generateChatHistoryWithSensitiveContent();
      return true;
    } else if (item.getItemId() == R.id.clearHistory) {
      viewModel.setMessages(new ArrayList<>());
      return true;
    } else {
      return false;
    }
  }

  @Override
  public void onChipClick(@NonNull String chipText) {
    inputText.setText(chipText);
  }

  private void generateChatHistoryBasic() {
    ArrayList<Message> messageList = new ArrayList<>();
    Calendar calendar = Calendar.getInstance();

    calendar.set(Calendar.DATE, -1);
    messageList.add(new Message("Hello", true, calendar.getTimeInMillis()));

    calendar.add(Calendar.MINUTE, 10);
    messageList.add(new Message("Hey", false, calendar.getTimeInMillis()));

    viewModel.setMessages(messageList);
  }

  private void generateChatHistoryWithSensitiveContent() {
    ArrayList<Message> messageList = new ArrayList<>();
    Calendar calendar = Calendar.getInstance();

    calendar.set(Calendar.DATE, -1);
    messageList.add(new Message("Hi", false, calendar.getTimeInMillis()));

    calendar.add(Calendar.MINUTE, 10);
    messageList.add(new Message("How are you?", true, calendar.getTimeInMillis()));

    calendar.add(Calendar.MINUTE, 10);
    messageList.add(new Message("My cat died", false, calendar.getTimeInMillis()));

    viewModel.setMessages(messageList);
  }
}
