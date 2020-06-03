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

package com.google.mlkit.samples.smartreply.java.chat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.annotation.NonNull;

import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.nl.smartreply.SmartReply;
import com.google.mlkit.nl.smartreply.SmartReplyGenerator;
import com.google.mlkit.nl.smartreply.SmartReplySuggestion;
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult;
import com.google.mlkit.nl.smartreply.TextMessage;
import com.google.mlkit.samples.smartreply.java.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatViewModel extends ViewModel {

    private final String REMOTE_USER_ID = UUID.randomUUID().toString();

    private MediatorLiveData<List<SmartReplySuggestion>> suggestions = new MediatorLiveData<>();
    private MutableLiveData<List<Message>> messageList = new MutableLiveData<>();
    private MutableLiveData<Boolean> emulatingRemoteUser = new MutableLiveData<>();
    private SmartReplyGenerator smartReply = SmartReply.getClient();

    public ChatViewModel() {
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
        suggestions.postValue(new ArrayList<SmartReplySuggestion>());
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
        suggestions.addSource(emulatingRemoteUser, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isEmulatingRemoteUser) {
                List<Message> list = messageList.getValue();
                if (list == null || list.isEmpty()) {
                    return;
                }

                generateReplies(list, isEmulatingRemoteUser)
                        .addOnSuccessListener(new OnSuccessListener<List<SmartReplySuggestion>>() {
                            @Override
                            public void onSuccess(List<SmartReplySuggestion> result) {
                                suggestions.postValue(result);
                            }
                        });
            }
        });

        suggestions.addSource(messageList, new Observer<List<Message>>() {
            @Override
            public void onChanged(List<Message> list) {
                Boolean isEmulatingRemoteUser = emulatingRemoteUser.getValue();
                if (isEmulatingRemoteUser == null || list.isEmpty()) {
                    return;
                }

                generateReplies(list, isEmulatingRemoteUser).addOnSuccessListener(new OnSuccessListener<List<SmartReplySuggestion>>() {
                    @Override
                    public void onSuccess(List<SmartReplySuggestion> result) {
                        suggestions.postValue(result);
                    }
                });
            }
        });
    }

    private Task<List<SmartReplySuggestion>> generateReplies(List<Message> messages,
                                                             boolean isEmulatingRemoteUser) {
        Message lastMessage = messages.get(messages.size() - 1);

        // If the last message in the chat thread is not sent by the "other" user, don't generate
        // smart replies.
        if (lastMessage.isLocalUser && !isEmulatingRemoteUser || !lastMessage.isLocalUser && isEmulatingRemoteUser) {
            return Tasks.forException(new Exception("Not running smart reply!"));
        }

        List<TextMessage> chatHistory = new ArrayList<>();
        for (Message message : messages) {
            if (message.isLocalUser && !isEmulatingRemoteUser || !message.isLocalUser && isEmulatingRemoteUser) {
                chatHistory.add(TextMessage.createForLocalUser(message.text,
                        message.timestamp));
            } else {
                chatHistory.add(TextMessage.createForRemoteUser(message.text,
                        message.timestamp, REMOTE_USER_ID));
            }
        }

        return smartReply.suggestReplies(chatHistory)
                .continueWith(new Continuation<SmartReplySuggestionResult, List<SmartReplySuggestion>>() {
                    @Override
                    public List<SmartReplySuggestion> then(@NonNull Task<SmartReplySuggestionResult> task) {
                        return task.getResult().getSuggestions();
                    }
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
