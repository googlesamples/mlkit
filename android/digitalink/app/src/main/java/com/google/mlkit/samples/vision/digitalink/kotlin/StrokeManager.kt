package com.google.mlkit.samples.vision.digitalink.kotlin

import android.os.Handler
import android.os.Message
import androidx.annotation.VisibleForTesting
import android.util.Log
import android.view.MotionEvent
import com.google.android.gms.tasks.SuccessContinuation
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.samples.vision.digitalink.kotlin.RecognitionTask.RecognizedInk
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.Ink.Stroke
import java.util.ArrayList

/** Manages the recognition logic and the content that has been added to the current page.  */
class StrokeManager {
  /** Interface to register to be notified of changes in the recognized content.  */
  interface ContentChangedListener {
    /** This method is called when the recognized content changes.  */
    fun onContentChanged()
  }

  /** Interface to register to be notified of changes in the status.  */
  interface StatusChangedListener {
    /** This method is called when the recognized content changes.  */
    fun onStatusChanged()
  }

  /** Interface to register to be notified of changes in the downloaded model state.  */
  interface DownloadedModelsChangedListener {
    /** This method is called when the downloaded models changes.  */
    fun onDownloadedModelsChanged(downloadedLanguageTags: Set<String>)
  }

  // For handling recognition and model downloading.
  private var recognitionTask: RecognitionTask? = null

  @JvmField
  @VisibleForTesting
  var modelManager =
    ModelManager()

  // Managing the recognition queue.
  private val content: MutableList<RecognizedInk> = ArrayList()

  // Managing ink currently drawn.
  private var strokeBuilder = Stroke.builder()
  private var inkBuilder = Ink.builder()
  private var stateChangedSinceLastRequest = false
  private var contentChangedListener: ContentChangedListener? = null
  private var statusChangedListener: StatusChangedListener? = null
  private var downloadedModelsChangedListener: DownloadedModelsChangedListener? = null
  private var triggerRecognitionAfterInput = true
  private var clearCurrentInkAfterRecognition = true
  var status: String? = ""
    private set(newStatus) {
      field = newStatus
      statusChangedListener?.onStatusChanged()
    }

  fun setTriggerRecognitionAfterInput(shouldTrigger: Boolean) {
    triggerRecognitionAfterInput = shouldTrigger
  }

  fun setClearCurrentInkAfterRecognition(shouldClear: Boolean) {
    clearCurrentInkAfterRecognition = shouldClear
  }

  // Handler to handle the UI Timeout.
  // This handler is only used to trigger the UI timeout. Each time a UI interaction happens,
  // the timer is reset by clearing the queue on this handler and sending a new delayed message (in
  // addNewTouchEvent).
  private val uiHandler = Handler(
    Handler.Callback { msg: Message ->
      if (msg.what == TIMEOUT_TRIGGER) {
        Log.i(
          TAG,
          "Handling timeout trigger."
        )
        commitResult()
        return@Callback true
      }
      false
    }
  )

  private fun commitResult() {
    recognitionTask!!.result()?.let {
      content.add(it)
      status = "Successful recognition: " + it.text
      if (clearCurrentInkAfterRecognition) {
        resetCurrentInk()
      }

      contentChangedListener?.onContentChanged()
    }
  }

  fun reset() {
    Log.i(TAG, "reset")
    resetCurrentInk()
    content.clear()
    recognitionTask?.cancel()
    status = ""
  }

  private fun resetCurrentInk() {
    inkBuilder = Ink.builder()
    strokeBuilder = Stroke.builder()
    stateChangedSinceLastRequest = false
  }

  val currentInk: Ink
    get() = inkBuilder.build()

  /**
   * This method is called when a new touch event happens on the drawing client and notifies the
   * StrokeManager of new content being added.
   *
   *
   * This method takes care of triggering the UI timeout and scheduling recognitions on the
   * background thread.
   *
   * @return whether the touch event was handled.
   */
  fun addNewTouchEvent(event: MotionEvent): Boolean {
    val action = event.actionMasked
    val x = event.x
    val y = event.y
    val t = System.currentTimeMillis()

    // A new event happened -> clear all pending timeout messages.
    uiHandler.removeMessages(TIMEOUT_TRIGGER)
    when (action) {
      MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> strokeBuilder.addPoint(
        Ink.Point.create(
          x,
          y,
          t
        )
      )
      MotionEvent.ACTION_UP -> {
        strokeBuilder.addPoint(Ink.Point.create(x, y, t))
        inkBuilder.addStroke(strokeBuilder.build())
        strokeBuilder = Stroke.builder()
        stateChangedSinceLastRequest = true
        if (triggerRecognitionAfterInput) {
          recognize()
        }
      }
      else -> // Indicate touch event wasn't handled.
        return false
    }
    return true
  }

  // Listeners to update the drawing and status.
  fun setContentChangedListener(contentChangedListener: ContentChangedListener?) {
    this.contentChangedListener = contentChangedListener
  }

  fun setStatusChangedListener(statusChangedListener: StatusChangedListener?) {
    this.statusChangedListener = statusChangedListener
  }

  fun setDownloadedModelsChangedListener(
    downloadedModelsChangedListener: DownloadedModelsChangedListener?
  ) {
    this.downloadedModelsChangedListener = downloadedModelsChangedListener
  }

  fun getContent(): List<RecognizedInk> {
    return content
  }

  // Model downloading / deleting / setting.
  fun setActiveModel(languageTag: String) {
    status = modelManager.setModel(languageTag)
  }

  fun deleteActiveModel(): Task<Nothing?> {
    return modelManager
      .deleteActiveModel()
      .addOnSuccessListener { refreshDownloadedModelsStatus() }
      .onSuccessTask(
        SuccessContinuation { status: String? ->
          this.status = status
          return@SuccessContinuation Tasks.forResult(null)
        }
      )
  }

  fun download(): Task<Nothing?> {
    status = "Download started."
    return modelManager
      .download()
      .addOnSuccessListener { refreshDownloadedModelsStatus() }
      .onSuccessTask(
        SuccessContinuation { status: String? ->
          this.status = status
          return@SuccessContinuation Tasks.forResult(null)
        }
      )
  }

  // Recognition-related.
  fun recognize(): Task<String?> {
    if (!stateChangedSinceLastRequest || inkBuilder.isEmpty) {
      status = "No recognition, ink unchanged or empty"
      return Tasks.forResult(null)
    }
    if (modelManager.recognizer == null) {
      status = "Recognizer not set"
      return Tasks.forResult(null)
    }
    return modelManager
      .checkIsModelDownloaded()
      .onSuccessTask { result: Boolean? ->
        if (!result!!) {
          status = "Model not downloaded yet"
          return@onSuccessTask Tasks.forResult<String?>(
            null
          )
        }
        stateChangedSinceLastRequest = false
        recognitionTask =
          RecognitionTask(
            modelManager.recognizer,
            inkBuilder.build()
          )
        uiHandler.sendMessageDelayed(
          uiHandler.obtainMessage(TIMEOUT_TRIGGER),
          CONVERSION_TIMEOUT_MS
        )
        recognitionTask!!.run()
      }
  }

  fun refreshDownloadedModelsStatus() {
    modelManager
      .downloadedModelLanguages
      .addOnSuccessListener { downloadedLanguageTags: Set<String> ->
        downloadedModelsChangedListener?.onDownloadedModelsChanged(downloadedLanguageTags)
      }
  }

  companion object {
    @JvmField
    @VisibleForTesting
    val CONVERSION_TIMEOUT_MS: Long = 1000
    private const val TAG = "MLKD.StrokeManager"

    // This is a constant that is used as a message identifier to trigger the timeout.
    private const val TIMEOUT_TRIGGER = 1
  }
}
