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

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestion
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import com.google.mlkit.samples.nl.smartreply.R
import com.google.mlkit.samples.nl.smartreply.kotlin.model.Message
import java.util.ArrayList
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

  private val remoteUserId = UUID.randomUUID().toString()

  private val suggestions = MediatorLiveData<List<SmartReplySuggestion>>()
  private val messageList = MutableLiveData<MutableList<Message>>()
  private val emulatingRemoteUser = MutableLiveData<Boolean>()
  private val smartReply = SmartReply.getClient()

  val messages: LiveData<MutableList<Message>>
    get() = messageList

  companion object {
    private const val TAG = "ChatViewModel"
  }

  init {
    initSuggestionsGenerator()
    emulatingRemoteUser.postValue(false)
  }

  fun getSuggestions(): LiveData<List<SmartReplySuggestion>> {
    return suggestions
  }

  fun getEmulatingRemoteUser(): LiveData<Boolean> {
    return emulatingRemoteUser
  }

  internal fun setMessages(messages: MutableList<Message>) {
    clearSuggestions()
    messageList.postValue(messages)
  }

  internal fun switchUser() {
    clearSuggestions()
    val value = emulatingRemoteUser.value!!
    emulatingRemoteUser.postValue(!value)
  }

  private fun clearSuggestions() {
    suggestions.postValue(ArrayList())
  }

  internal fun addMessage(message: String) {
    var list: MutableList<Message>? = messageList.value
    if (list == null) {
      list = ArrayList()
    }
    val value = emulatingRemoteUser.value!!
    list.add(Message(message, !value, System.currentTimeMillis()))
    clearSuggestions()
    messageList.postValue(list)
  }

  private fun initSuggestionsGenerator() {
    suggestions.addSource(
      emulatingRemoteUser,
      Observer { isEmulatingRemoteUser ->
        val list = messageList.value
        if (list == null || list.isEmpty()) {
          return@Observer
        }

        generateReplies(list, isEmulatingRemoteUser!!).addOnSuccessListener { result ->
          suggestions.postValue(result)
        }
      }
    )

    suggestions.addSource(
      messageList,
      Observer { list ->
        val isEmulatingRemoteUser = emulatingRemoteUser.value
        if (isEmulatingRemoteUser == null || list!!.isEmpty()) {
          return@Observer
        }

        generateReplies(list, isEmulatingRemoteUser).addOnSuccessListener { result ->
          suggestions.postValue(result)
        }
      }
    )
  }

  private fun generateReplies(
    messages: List<Message>,
    isEmulatingRemoteUser: Boolean
  ): Task<List<SmartReplySuggestion>> {
    val lastMessage = messages[messages.size - 1]

    // If the last message in the chat thread is not sent by the "other" user, don't generate
    // smart replies.
    if (lastMessage.isLocalUser != isEmulatingRemoteUser) {
      return Tasks.forException(Exception("Not running smart reply!"))
    }

    val chatHistory = ArrayList<TextMessage>()
    for (message in messages) {
      if (message.isLocalUser != isEmulatingRemoteUser) {
        chatHistory.add(TextMessage.createForLocalUser(message.text, message.timestamp))
      } else {
        chatHistory.add(
          TextMessage.createForRemoteUser(message.text, message.timestamp, remoteUserId)
        )
      }
    }

    return smartReply
      .suggestReplies(chatHistory)
      .continueWith { task ->
        val result = task.result
        when (result.status) {
          SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE ->
            // This error happens when the detected language is not English, as that is the
            // only supported language in Smart Reply.
            Toast.makeText(
                getApplication(),
                R.string.error_not_supported_language,
                Toast.LENGTH_SHORT
              )
              .show()
          SmartReplySuggestionResult.STATUS_NO_REPLY ->
            // This error happens when the inference completed successfully, but no replies
            // were returned.
            Toast.makeText(getApplication(), R.string.error_no_reply, Toast.LENGTH_SHORT).show()
          else -> {
            // Do nothing.
          }
        }
        result!!.suggestions
      }
      .addOnFailureListener { e ->
        Log.e(TAG, "Smart reply error", e)
        Toast.makeText(
            getApplication(),
            "Smart reply error" + "\nError: " + e.getLocalizedMessage() + "\nCause: " + e.cause,
            Toast.LENGTH_LONG
          )
          .show()
      }
  }

  override fun onCleared() {
    super.onCleared()
    // Instances of smartReply must be closed appropriately, so here we utilize ViewModel's
    // onCleared() to close the smartReply instance when this ViewModel is no longer in use and
    // destroyed.
    smartReply.close()
  }
}
