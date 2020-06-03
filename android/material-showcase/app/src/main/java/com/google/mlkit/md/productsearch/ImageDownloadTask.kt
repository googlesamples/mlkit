/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.md.productsearch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.text.TextUtils
import android.util.Log
import android.widget.ImageView
import java.net.URL

/** todo: migrate to Coroutines. */
/** Async task to download the image and then feed into the provided image view.  */
internal class ImageDownloadTask(private val imageView: ImageView, private val maxImageWidth: Int) :
    AsyncTask<String, Void, Bitmap>() {

    override fun doInBackground(vararg urls: String): Bitmap? {
        if (TextUtils.isEmpty(urls[0])) {
            return null
        }

        var bitmap: Bitmap? = null
        try {
            val inputStream = URL(urls[0]).openStream()
            bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
        } catch (e: Exception) {
            Log.e(TAG, "Image download failed: ${urls[0]}")
        }

        if (bitmap != null && bitmap.width > maxImageWidth) {
            val dstHeight = (maxImageWidth.toFloat() / bitmap.width * bitmap.height).toInt()
            bitmap = Bitmap.createScaledBitmap(bitmap, maxImageWidth, dstHeight, /* filter= */ false)
        }
        return bitmap
    }

    override fun onPostExecute(result: Bitmap?) {
        result?.let {
            imageView.setImageBitmap(result)
        }
    }

    companion object {
        private const val TAG = "ImageDownloadTask"
    }
}
