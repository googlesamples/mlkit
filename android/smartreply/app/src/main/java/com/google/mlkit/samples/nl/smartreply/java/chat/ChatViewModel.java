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

import android.app.Application;
import android.util.Log;
import android.widget.Toast;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.Iterables;
import com.google.mlkit.nl.smartreply.SmartReply;
import com.google.mlkit.nl.smartreply.SmartReplyGenerator;
import com.google.mlkit.nl.smartreply.SmartReplySuggestion;
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult;
import com.google.mlkit.nl.smartreply.TextMessage;
import com.google.mlkit.samples.nl.smartreply.R;
import com.google.mlkit.samples.nl.smartreply.java.model.Message;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** View model for chat message. */
public class ChatViewModel extends AndroidViewModel {

  private static final String TAG = "ChatViewModel";
  private static final String REMOTE_USER_ID = UUID.randomUUID().toString();

  private final MediatorLiveData<List<SmartReplySuggestion>> suggestions = new MediatorLiveData<>();
  private final MutableLiveData<List<Message>> messageList = new MutableLiveData<>();
  private final MutableLiveData<Boolean> emulatingRemoteUser = new MutableLiveData<>();
  private final SmartReplyGenerator smartReply = SmartReply.getClient();

  public ChatViewModel(Application application) {
    super(application);
    initSuggestionsGenerator();
    emulatingRemoteUser.postValue(false);
  }

  public LiveData<List<SmartReplySuggestion>> getSuggestions() {
    return suggestions;
  }

  public LiveData<List<Message>> getMessages() {
    return messageList;
  }

  public LiveData<Boolean> getEmulatingRemoteUser() {
    return emulatingRemoteUser;
  }

  void setMessages(List<Message> messages) {
    clearSuggestions();
    messageList.postValue(messages);
  }

  void switchUser() {
    clearSuggestions();
    emulatingRemoteUser.postValue(!emulatingRemoteUser.getValue());
  }

  private void clearSuggestions() {
    suggestions.postValue(new ArrayList<>());
  }

  void addMessage(String message) {
    List<Message> list = messageList.getValue();
    if (list == null) {
      list = new ArrayList<>();
    }
    list.add(new Message(message, !emulatingRemoteUser.getValue(), System.currentTimeMillis()));
    clearSuggestions();
    messageList.postValue(list);
  }

  private void initSuggestionsGenerator() {
    suggestions.addSource(
        emulatingRemoteUser,
        isEmulatingRemoteUser -> {
          List<Message> list = messageList.getValue();
          if (list == null || list.isEmpty()) {
            return;
          }

          generateReplies(list, isEmulatingRemoteUser).addOnSuccessListener(suggestions::postValue);
        });

    suggestions.addSource(
        messageList,
        list -> {
          Boolean isEmulatingRemoteUser = emulatingRemoteUser.getValue();
          if (isEmulatingRemoteUser == null || list.isEmpty()) {
            return;
          }

          generateReplies(list, isEmulatingRemoteUser).addOnSuccessListener(suggestions::postValue);
        });
  }

  private Task<List<SmartReplySuggestion>> generateReplies(
      List<Message> messages, boolean isEmulatingRemoteUser) {
    Message lastMessage = Iterables.getLast(messages);

    // If the last message in the chat thread is not sent by the "other" user, don't generate
    // smart replies.
    if (lastMessage.isLocalUser != isEmulatingRemoteUser) {
      return Tasks.forException(new Exception("Not running smart reply!"));
    }

    List<TextMessage> chatHistory = new ArrayList<>();
    for (Message message : messages) {
      if (message.isLocalUser != isEmulatingRemoteUser) {
        chatHistory.add(TextMessage.createForLocalUser(message.text, message.timestamp));
      } else {
        chatHistory.add(
            TextMessage.createForRemoteUser(message.text, message.timestamp, REMOTE_USER_ID));
      }
    }

    return smartReply
        .suggestReplies(chatHistory)
        .continueWith(
            task -> {
              SmartReplySuggestionResult result = task.getResult();
              switch (result.getStatus()) {
                case SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE:
                  // This error happens when the detected language is not English, as that is the
                  // only supported language in Smart Reply.
                  Toast.makeText(
                          getApplication(),
                          R.string.error_not_supported_language,
                          Toast.LENGTH_SHORT)
                      .show();
                  break;
                case SmartReplySuggestionResult.STATUS_NO_REPLY:
                  // This error happens when the inference completed successfully, but no replies
                  // were returned.
                  Toast.makeText(getApplication(), R.string.error_no_reply, Toast.LENGTH_SHORT)
                      .show();
                  break;
                default: // fall out
              }
              return result.getSuggestions();
            })
        .addOnFailureListener(
            e -> {
              Log.e(TAG, "Smart reply error", e);
              Toast.makeText(
                      getApplication(),
                      "Smart reply error"
                          + "\nError: "
                          + e.getLocalizedMessage()
                          + "\nCause: "
                          + e.getCause(),
                      Toast.LENGTH_LONG)
                  .show();
            });
  }

  @Override
  public void onCleared() {
    super.onCleared();
    // Instances of smartReply must be closed appropriately, so here we utilize ViewModel's
    // onCleared() to close the smartReply instance when this ViewModel is no longer in use and
    // destroyed.
    smartReply.close();
  }
}
