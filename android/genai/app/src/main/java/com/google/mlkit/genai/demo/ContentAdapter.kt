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

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.bumptech.glide.Glide
import com.google.mlkit.genai.demo.ContentItem.CacheRequestItem
import com.google.mlkit.genai.demo.ContentItem.ImageItem
import com.google.mlkit.genai.demo.ContentItem.TextAndImagesItem
import com.google.mlkit.genai.demo.ContentItem.TextItem
import com.google.mlkit.genai.demo.ContentItem.TextWithPrefixCacheItem
import com.google.mlkit.genai.demo.ContentItem.TextWithPromptPrefixItem

/** A recycler view adapter for displaying the request and response views. */
class ContentAdapter : RecyclerView.Adapter<ViewHolder>() {
  private var recyclerView: RecyclerView? = null

  private val contentList: MutableList<ContentItem> = ArrayList()

  fun addContent(content: ContentItem) {
    contentList.add(content)
    notifyItemInserted(contentList.size - 1)
    recyclerView?.post { recyclerView?.scrollToPosition(contentList.size - 1) }
  }

  fun updateStreamingResponse(response: String) {
    contentList[contentList.size - 1] = TextItem.fromStreamingResponse(response)
    notifyItemChanged(contentList.size - 1)
    recyclerView?.post { recyclerView?.scrollToPosition(contentList.size - 1) }
  }

  fun updateStreamingThoughtResponse(response: String) {
    contentList[contentList.size - 1] = TextItem.fromStreamingThoughtResponse(response)
    notifyItemChanged(contentList.size - 1)
    recyclerView?.post { recyclerView?.scrollToPosition(contentList.size - 1) }
  }

  fun finalizeStreamingThought(finalThought: String) {
    contentList[contentList.size - 1] = TextItem.fromThoughtResponse(finalThought)
    notifyItemChanged(contentList.size - 1)
  }

  override fun getItemViewType(position: Int): Int {
    return contentList[position].viewType
  }

  override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
    super.onAttachedToRecyclerView(recyclerView)
    this.recyclerView = recyclerView
  }

  override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
    val layoutInflater = LayoutInflater.from(viewGroup.context)
    return when (viewType) {
      VIEW_TYPE_REQUEST_TEXT ->
        TextViewHolder(layoutInflater.inflate(R.layout.row_item_request_text, viewGroup, false))
      VIEW_TYPE_REQUEST_IMAGE ->
        ImageViewHolder(layoutInflater.inflate(R.layout.row_item_request_image, viewGroup, false))
      VIEW_TYPE_RESPONSE,
      VIEW_TYPE_RESPONSE_STREAMING,
      VIEW_TYPE_RESPONSE_THOUGHT,
      VIEW_TYPE_RESPONSE_STREAMING_THOUGHT,
      VIEW_TYPE_RESPONSE_ERROR ->
        TextViewHolder(layoutInflater.inflate(R.layout.row_item_response, viewGroup, false))
      VIEW_TYPE_REQUEST_TEXT_AND_IMAGES ->
        TextAndImagesViewHolder(
          layoutInflater.inflate(R.layout.row_item_request_text_and_images, viewGroup, false)
        )
      VIEW_TYPE_REQUEST_TEXT_WITH_PROMPT_PREFIX ->
        TextWithPromptPrefixViewHolder(
          layoutInflater.inflate(R.layout.row_item_request_text, viewGroup, false)
        )
      VIEW_TYPE_CACHE_REQUEST ->
        CacheRequestViewHolder(
          layoutInflater.inflate(R.layout.row_item_request_text, viewGroup, false)
        )
      VIEW_TYPE_REQUEST_TEXT_WITH_PREFIX_CACHE ->
        TextWithPrefixCacheViewHolder(
          layoutInflater.inflate(R.layout.row_item_request_text, viewGroup, false)
        )
      VIEW_TYPE_REQUEST_INTERLEAVED_CONTENT ->
        InterleavedContentViewHolder(
          layoutInflater.inflate(R.layout.row_item_request_interleaved_content, viewGroup, false)
        )
      else -> throw IllegalArgumentException("Invalid view type $viewType")
    }
  }

  override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
    (viewHolder as ContentViewHolder).bind(contentList[position])
  }

  override fun onViewRecycled(holder: ViewHolder) {
    super.onViewRecycled(holder)
    if (holder is ImageViewHolder) {
      Glide.with(holder.itemView).clear(holder.contentImageView)
    } else if (holder is TextAndImagesViewHolder) {
      for (i in 0 until holder.imageContainer.childCount) {
        val view = holder.imageContainer.getChildAt(i)
        if (view is ImageView) {
          Glide.with(view).clear(view)
        }
      }
    }
  }

  override fun getItemCount(): Int {
    return contentList.size
  }

  interface ContentViewHolder {
    fun bind(item: ContentItem)
  }

  /** Hosts text request or response item view. */
  class TextViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {

    private val contentTextView: TextView = itemView.findViewById(R.id.content_text_view)
    private val metadataTextView: TextView? = itemView.findViewById(R.id.metadata_text_view)
    private val defaultTextColors: ColorStateList = contentTextView.textColors

    override fun bind(item: ContentItem) {
      if (item is TextItem) {
        contentTextView.setTextColor(defaultTextColors)
        metadataTextView?.visibility = View.GONE

        if (item.viewType == VIEW_TYPE_RESPONSE_ERROR) {
          contentTextView.text = item.text
          contentTextView.setTextColor(Color.RED)
        } else if (item.viewType == VIEW_TYPE_RESPONSE_STREAMING) {
          contentTextView.text =
            SpannableStringBuilder().apply {
              append(STREAMING_INDICATOR)
              setSpan(StyleSpan(Typeface.BOLD), 0, length, SPAN_EXCLUSIVE_EXCLUSIVE)
              append(item.text)
            }
        } else if (item.viewType == VIEW_TYPE_RESPONSE_STREAMING_THOUGHT) {
          contentTextView.setTextColor(Color.GRAY)
          contentTextView.text =
            SpannableStringBuilder().apply {
              append(STREAMING_THOUGHT_INDICATOR)
              setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, length, SPAN_EXCLUSIVE_EXCLUSIVE)
              append(item.text)
              setSpan(
                StyleSpan(Typeface.ITALIC),
                length - item.text.length,
                length,
                SPAN_EXCLUSIVE_EXCLUSIVE,
              )
            }
        } else if (item.viewType == VIEW_TYPE_RESPONSE_THOUGHT) {
          contentTextView.setTextColor(Color.GRAY)
          contentTextView.text =
            SpannableStringBuilder().apply {
              append("[Thought]\n")
              setSpan(StyleSpan(Typeface.BOLD_ITALIC), 0, length, SPAN_EXCLUSIVE_EXCLUSIVE)
              append(item.text)
              setSpan(
                StyleSpan(Typeface.ITALIC),
                length - item.text.length,
                length,
                SPAN_EXCLUSIVE_EXCLUSIVE,
              )
            }
        } else {
          contentTextView.text = item.text
          if (item.metadata != null) {
            metadataTextView?.apply {
              text = item.metadata
              visibility = View.VISIBLE
            }
          }
        }
      }
    }
  }

  /** Hosts image request item view. */
  class ImageViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {

    val contentImageView: ImageView = itemView.findViewById(R.id.content_image_view)

    override fun bind(item: ContentItem) {
      if (item is ImageItem) {
        Glide.with(itemView).load(item.imageUri).into(contentImageView)
      }
    }
  }

  /** Hosts combined text and image request item view. */
  class TextAndImagesViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {
    private val messageText: TextView = itemView.findViewById(R.id.chat_message_text)
    val imageContainer: LinearLayout = itemView.findViewById(R.id.image_container)
    private val bubbleLayout: LinearLayout = itemView.findViewById(R.id.chat_bubble_layout)

    private val defaultTextColors: ColorStateList = messageText.textColors

    override fun bind(item: ContentItem) {
      if (item !is TextAndImagesItem) {
        return
      }
      if (!item.text.isEmpty()) {
        messageText.text = item.text
        messageText.visibility = View.VISIBLE
      } else {
        messageText.visibility = View.GONE
      }

      imageContainer.removeAllViews()
      if (item.imageUris.isNotEmpty()) {
        imageContainer.visibility = View.VISIBLE
        val imageSize =
          imageContainer.context.resources.getDimensionPixelSize(R.dimen.interleaved_image_size)
        for (uri in item.imageUris) {
          val imageView = ImageView(imageContainer.context)
          val layoutParams = LinearLayout.LayoutParams(imageSize, imageSize)
          layoutParams.setMargins(0, 0, 16, 0)
          imageView.layoutParams = layoutParams
          Glide.with(imageView).load(uri).into(imageView)
          imageView.scaleType = ImageView.ScaleType.CENTER_CROP
          imageContainer.addView(imageView)
        }
      } else {
        imageContainer.visibility = View.GONE
      }

      bubbleLayout.setBackgroundResource(R.drawable.request_item_background)
      messageText.setTextColor(defaultTextColors)
    }
  }

  /** Hosts interleaved text and image request item view. */
  class InterleavedContentViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {
    private val bubbleLayout: LinearLayout = itemView.findViewById(R.id.chat_bubble_layout)

    override fun bind(item: ContentItem) {
      if (item !is ContentItem.InterleavedContentItem) {
        return
      }
      bubbleLayout.removeAllViews()

      for (part in item.parts) {
        when (part) {
          is com.google.mlkit.genai.prompt.TextPart -> {
            val textView = TextView(bubbleLayout.context)
            textView.text = part.textString
            textView.layoutParams =
              LinearLayout.LayoutParams(
                  LinearLayout.LayoutParams.WRAP_CONTENT,
                  LinearLayout.LayoutParams.WRAP_CONTENT,
                )
                .apply { setMargins(0, 0, 0, 8) }
            bubbleLayout.addView(textView)
          }
          is com.google.mlkit.genai.prompt.ImagePart -> {
            val imageView = ImageView(bubbleLayout.context)
            val imageSize =
              bubbleLayout.context.resources.getDimensionPixelSize(R.dimen.interleaved_image_size)
            val layoutParams = LinearLayout.LayoutParams(imageSize, imageSize)
            layoutParams.setMargins(0, 0, 0, 8)
            imageView.layoutParams = layoutParams
            Glide.with(imageView).load(part.bitmap).into(imageView)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            bubbleLayout.addView(imageView)
          }
          else -> {}
        }
      }
    }
  }

  /** Hosts text request with prompt prefix item view. */
  class TextWithPromptPrefixViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {
    private val contentTextView: TextView = itemView.findViewById(R.id.content_text_view)
    private val defaultTextColors: ColorStateList = contentTextView.textColors

    override fun bind(item: ContentItem) {
      if (item !is TextWithPromptPrefixItem) {
        return
      }
      contentTextView.setTextColor(defaultTextColors)

      contentTextView.text =
        contentTextView.context.getString(
          R.string.message_format_prefix_and_suffix,
          item.promptPrefix,
          item.dynamicSuffix,
        )
    }
  }

  /** Hosts text request with prefix cache item view. */
  class TextWithPrefixCacheViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {
    private val contentTextView: TextView = itemView.findViewById(R.id.content_text_view)
    private val defaultTextColors: ColorStateList = contentTextView.textColors

    override fun bind(item: ContentItem) {
      if (item !is TextWithPrefixCacheItem) {
        return
      }
      contentTextView.setTextColor(defaultTextColors)

      contentTextView.text =
        contentTextView.context.getString(
          R.string.message_format_cache_name_and_suffix,
          item.cacheName,
          item.dynamicSuffix,
        )
    }
  }

  /** Hosts cache request item view. */
  class CacheRequestViewHolder(itemView: View) : ViewHolder(itemView), ContentViewHolder {
    private val contentTextView: TextView = itemView.findViewById(R.id.content_text_view)
    private val defaultTextColors: ColorStateList = contentTextView.textColors

    override fun bind(item: ContentItem) {
      if (item !is CacheRequestItem) {
        return
      }
      contentTextView.setTextColor(defaultTextColors)

      contentTextView.text =
        contentTextView.context.getString(
          R.string.message_format_cache_request,
          item.cacheName,
          item.prefixToCache,
        )
    }
  }

  companion object {
    const val VIEW_TYPE_REQUEST_TEXT: Int = 0
    const val VIEW_TYPE_REQUEST_IMAGE: Int = 1
    const val VIEW_TYPE_RESPONSE: Int = 2
    const val VIEW_TYPE_RESPONSE_STREAMING: Int = 3
    const val VIEW_TYPE_RESPONSE_ERROR: Int = 4
    const val VIEW_TYPE_REQUEST_TEXT_AND_IMAGES: Int = 5
    const val VIEW_TYPE_REQUEST_TEXT_WITH_PROMPT_PREFIX: Int = 6
    const val VIEW_TYPE_CACHE_REQUEST: Int = 7
    const val VIEW_TYPE_REQUEST_TEXT_WITH_PREFIX_CACHE: Int = 8
    const val VIEW_TYPE_REQUEST_INTERLEAVED_CONTENT: Int = 9
    const val VIEW_TYPE_RESPONSE_THOUGHT: Int = 10
    const val VIEW_TYPE_RESPONSE_STREAMING_THOUGHT: Int = 11

    const val STREAMING_INDICATOR: String = "STREAMING...\n"
    const val STREAMING_THOUGHT_INDICATOR: String = "THINKING...\n"
  }
}
