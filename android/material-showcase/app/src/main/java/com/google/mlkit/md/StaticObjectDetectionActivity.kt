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

package com.google.mlkit.md

import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.Activity
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.common.collect.ImmutableList
import com.google.mlkit.md.productsearch.BottomSheetScrimView
import com.google.mlkit.md.objectdetection.DetectedObjectInfo
import com.google.mlkit.md.objectdetection.StaticObjectDotView
import com.google.mlkit.md.productsearch.PreviewCardAdapter
import com.google.mlkit.md.productsearch.Product
import com.google.mlkit.md.productsearch.ProductAdapter
import com.google.mlkit.md.productsearch.SearchEngine
import com.google.mlkit.md.productsearch.SearchedObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import java.io.IOException
import java.lang.NullPointerException
import java.util.TreeMap

/** Demonstrates the object detection and visual search workflow using static image.  */
class StaticObjectDetectionActivity : AppCompatActivity(), View.OnClickListener {

    private val searchedObjectMap = TreeMap<Int, SearchedObject>()

    private var loadingView: View? = null
    private var bottomPromptChip: Chip? = null
    private var inputImageView: ImageView? = null
    private var previewCardCarousel: RecyclerView? = null
    private var dotViewContainer: ViewGroup? = null

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var bottomSheetScrimView: BottomSheetScrimView? = null
    private var bottomSheetTitleView: TextView? = null
    private var productRecyclerView: RecyclerView? = null

    private var inputBitmap: Bitmap? = null
    private var searchedObjectForBottomSheet: SearchedObject? = null
    private var dotViewSize: Int = 0
    private var detectedObjectNum = 0
    private var currentSelectedObjectIndex = 0

    private var detector: ObjectDetector? = null
    private var searchEngine: SearchEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchEngine = SearchEngine(applicationContext)

        setContentView(R.layout.activity_static_object)

        loadingView = findViewById<View>(R.id.loading_view).apply {
            setOnClickListener(this@StaticObjectDetectionActivity)
        }

        bottomPromptChip = findViewById(R.id.bottom_prompt_chip)
        inputImageView = findViewById(R.id.input_image_view)

        previewCardCarousel = findViewById<RecyclerView>(R.id.card_recycler_view).apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@StaticObjectDetectionActivity, RecyclerView.HORIZONTAL, false)
            addItemDecoration(
                CardItemDecoration(
                    resources
                )
            )
        }

        dotViewContainer = findViewById(R.id.dot_view_container)
        dotViewSize = resources.getDimensionPixelOffset(R.dimen.static_image_dot_view_size)

        setUpBottomSheet()

        findViewById<View>(R.id.close_button).setOnClickListener(this)
        findViewById<View>(R.id.photo_library_button).setOnClickListener(this)

        detector = ObjectDetection.getClient(
            ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
                .enableMultipleObjects()
                .build()
        )
        intent.data?.let(::detectObjects)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            detector?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to close the detector!", e)
        }

        searchEngine?.shutdown()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == Utils.REQUEST_CODE_PHOTO_LIBRARY && resultCode == Activity.RESULT_OK) {
            data?.data?.let(::detectObjects)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior?.state != BottomSheetBehavior.STATE_HIDDEN) {
            bottomSheetBehavior?.setState(BottomSheetBehavior.STATE_HIDDEN)
        } else {
            super.onBackPressed()
        }
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.close_button -> onBackPressed()
            R.id.photo_library_button -> Utils.openImagePicker(this)
            R.id.bottom_sheet_scrim_view -> bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun showSearchResults(searchedObject: SearchedObject) {
        searchedObjectForBottomSheet = searchedObject
        val productList = searchedObject.productList
        bottomSheetTitleView?.text = resources
            .getQuantityString(
                R.plurals.bottom_sheet_title, productList.size, productList.size
            )
        productRecyclerView?.adapter = ProductAdapter(productList)
        bottomSheetBehavior?.peekHeight = (inputImageView?.parent as View).height / 2
        bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun setUpBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById<View>(R.id.bottom_sheet)).apply {
            setBottomSheetCallback(
                object : BottomSheetBehavior.BottomSheetCallback() {
                    override fun onStateChanged(bottomSheet: View, newState: Int) {
                        Log.d(TAG, "Bottom sheet new state: $newState")
                        bottomSheetScrimView?.visibility =
                            if (newState == BottomSheetBehavior.STATE_HIDDEN) View.GONE else View.VISIBLE
                    }

                    override fun onSlide(bottomSheet: View, slideOffset: Float) {
                        if (java.lang.Float.isNaN(slideOffset)) {
                            return
                        }

                        val collapsedStateHeight = bottomSheetBehavior!!.peekHeight.coerceAtMost(bottomSheet.height)
                        val searchedObjectForBottomSheet = searchedObjectForBottomSheet
                            ?: return
                        bottomSheetScrimView?.updateWithThumbnailTranslate(
                            searchedObjectForBottomSheet.getObjectThumbnail(),
                            collapsedStateHeight,
                            slideOffset,
                            bottomSheet
                        )
                    }
                }
            )
            state = BottomSheetBehavior.STATE_HIDDEN
        }

        bottomSheetScrimView = findViewById<BottomSheetScrimView>(R.id.bottom_sheet_scrim_view).apply {
            setOnClickListener(this@StaticObjectDetectionActivity)
        }

        bottomSheetTitleView = findViewById(R.id.bottom_sheet_title)
        productRecyclerView = findViewById<RecyclerView>(R.id.product_recycler_view)?.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@StaticObjectDetectionActivity)
            adapter = ProductAdapter(ImmutableList.of())
        }
    }

    private fun detectObjects(imageUri: Uri) {
        inputImageView?.setImageDrawable(null)
        bottomPromptChip?.visibility = View.GONE
        previewCardCarousel?.adapter = PreviewCardAdapter(ImmutableList.of()) { showSearchResults(it) }
        previewCardCarousel?.clearOnScrollListeners()
        dotViewContainer?.removeAllViews()
        currentSelectedObjectIndex = 0

        try {
            inputBitmap = Utils.loadImage(
                this, imageUri,
                MAX_IMAGE_DIMENSION
            )
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load file: $imageUri", e)
            showBottomPromptChip("Failed to load file!")
            return
        }

        inputImageView?.setImageBitmap(inputBitmap)
        loadingView?.visibility = View.VISIBLE
        val image = InputImage.fromBitmap(inputBitmap!!, 0)
        detector?.process(image)
            ?.addOnSuccessListener { objects -> onObjectsDetected(BitmapInputInfo(inputBitmap!!), objects) }
            ?.addOnFailureListener { onObjectsDetected(BitmapInputInfo(inputBitmap!!), ImmutableList.of()) }
    }

    @MainThread
    private fun onObjectsDetected(image: InputInfo, objects: List<DetectedObject>) {
        detectedObjectNum = objects.size
        Log.d(TAG, "Detected objects num: $detectedObjectNum")
        if (detectedObjectNum == 0) {
            loadingView?.visibility = View.GONE
            showBottomPromptChip(getString(R.string.static_image_prompt_detected_no_results))
        } else {
            searchedObjectMap.clear()
            for (i in objects.indices) {
                searchEngine?.search(DetectedObjectInfo(objects[i], i, image)) { detectedObject, products ->
                    onSearchCompleted(detectedObject, products)
                }
            }
        }
    }

    private fun onSearchCompleted(detectedObject: DetectedObjectInfo, productList: List<Product>) {
        Log.d(TAG, "Search completed for object index: ${detectedObject.objectIndex}")
        searchedObjectMap[detectedObject.objectIndex] = SearchedObject(resources, detectedObject, productList)
        if (searchedObjectMap.size < detectedObjectNum) {
            // Hold off showing the result until the search of all detected objects completes.
            return
        }

        showBottomPromptChip(getString(R.string.static_image_prompt_detected_results))
        loadingView?.visibility = View.GONE
        previewCardCarousel?.adapter =
            PreviewCardAdapter(ImmutableList.copyOf(searchedObjectMap.values)) { showSearchResults(it) }
        previewCardCarousel?.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    Log.d(TAG, "New card scroll state: $newState")
                    if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                        for (i in 0 until recyclerView.childCount) {
                            val childView = recyclerView.getChildAt(i)
                            if (childView.x >= 0) {
                                val cardIndex = recyclerView.getChildAdapterPosition(childView)
                                if (cardIndex != currentSelectedObjectIndex) {
                                    selectNewObject(cardIndex)
                                }
                                break
                            }
                        }
                    }
                }
            })

        for (searchedObject in searchedObjectMap.values) {
            val dotView = createDotView(searchedObject)
            dotView.setOnClickListener {
                if (searchedObject.objectIndex == currentSelectedObjectIndex) {
                    showSearchResults(searchedObject)
                } else {
                    selectNewObject(searchedObject.objectIndex)
                    showSearchResults(searchedObject)
                    previewCardCarousel!!.smoothScrollToPosition(searchedObject.objectIndex)
                }
            }

            dotViewContainer?.addView(dotView)
            val animatorSet = AnimatorInflater.loadAnimator(this, R.animator.static_image_dot_enter) as AnimatorSet
            animatorSet.setTarget(dotView)
            animatorSet.start()
        }
    }

    private fun createDotView(searchedObject: SearchedObject): StaticObjectDotView {
        val viewCoordinateScale: Float
        val horizontalGap: Float
        val verticalGap: Float
        val inputImageView = inputImageView ?: throw NullPointerException()
        val inputBitmap = inputBitmap ?: throw NullPointerException()
        val inputImageViewRatio = inputImageView.width.toFloat() / inputImageView.height
        val inputBitmapRatio = inputBitmap.width.toFloat() / inputBitmap.height
        if (inputBitmapRatio <= inputImageViewRatio) { // Image content fills height
            viewCoordinateScale = inputImageView.height.toFloat() / inputBitmap.height
            horizontalGap = (inputImageView.width - inputBitmap.width * viewCoordinateScale) / 2
            verticalGap = 0f
        } else { // Image content fills width
            viewCoordinateScale = inputImageView.width.toFloat() / inputBitmap.width
            horizontalGap = 0f
            verticalGap = (inputImageView.height - inputBitmap.height * viewCoordinateScale) / 2
        }

        val boundingBox = searchedObject.boundingBox
        val boxInViewCoordinate = RectF(
            boundingBox.left * viewCoordinateScale + horizontalGap,
            boundingBox.top * viewCoordinateScale + verticalGap,
            boundingBox.right * viewCoordinateScale + horizontalGap,
            boundingBox.bottom * viewCoordinateScale + verticalGap
        )
        val initialSelected = searchedObject.objectIndex == 0
        val dotView = StaticObjectDotView(this, initialSelected)
        val layoutParams = FrameLayout.LayoutParams(dotViewSize, dotViewSize)
        val dotCenter = PointF(
            (boxInViewCoordinate.right + boxInViewCoordinate.left) / 2,
            (boxInViewCoordinate.bottom + boxInViewCoordinate.top) / 2
        )
        layoutParams.setMargins(
            (dotCenter.x - dotViewSize / 2f).toInt(),
            (dotCenter.y - dotViewSize / 2f).toInt(),
            0,
            0
        )
        dotView.layoutParams = layoutParams
        return dotView
    }

    private fun selectNewObject(objectIndex: Int) {
        val dotViewToDeselect = dotViewContainer!!.getChildAt(currentSelectedObjectIndex) as StaticObjectDotView
        dotViewToDeselect.playAnimationWithSelectedState(false)

        currentSelectedObjectIndex = objectIndex

        val selectedDotView = dotViewContainer!!.getChildAt(currentSelectedObjectIndex) as StaticObjectDotView
        selectedDotView.playAnimationWithSelectedState(true)
    }

    private fun showBottomPromptChip(message: String) {
        bottomPromptChip?.visibility = View.VISIBLE
        bottomPromptChip?.text = message
    }

    private class CardItemDecoration constructor(resources: Resources) : RecyclerView.ItemDecoration() {

        private val cardSpacing: Int = resources.getDimensionPixelOffset(R.dimen.preview_card_spacing)

        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val adapterPosition = parent.getChildAdapterPosition(view)
            outRect.left = if (adapterPosition == 0) cardSpacing * 2 else cardSpacing
            val adapter = parent.adapter ?: return
            if (adapterPosition == adapter.itemCount - 1) {
                outRect.right = cardSpacing
            }
        }
    }

    companion object {
        private const val TAG = "StaticObjectActivity"
        private const val MAX_IMAGE_DIMENSION = 1024
    }
}
