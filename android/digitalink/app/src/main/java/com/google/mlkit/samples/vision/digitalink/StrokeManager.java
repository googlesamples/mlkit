package com.google.mlkit.samples.vision.digitalink;

import android.os.Handler;
import androidx.annotation.Nullable;
import android.util.Log;
import android.view.MotionEvent;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.digitalink.Ink;
import com.google.mlkit.vision.digitalink.Ink.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Manages the recognition logic and the content that has been added to the current page. */
public class StrokeManager {

  /** Interface to register to be notified of changes in the recognized content. */
  public interface ContentChangedListener {

    /** This method is called when the recognized content changes. */
    void onContentChanged();
  }

  /** Interface to register to be notified of changes in the status. */
  public interface StatusChangedListener {

    /** This method is called when the recognized content changes. */
    void onStatusChanged();
  }

  /** Interface to register to be notified of changes in the downloaded model state. */
  public interface DownloadedModelsChangedListener {

    /** This method is called when the downloaded models changes. */
    void onDownloadedModelsChanged(Set<String> downloadedLanguageTags);
  }

  @VisibleForTesting static final long CONVERSION_TIMEOUT_MS = 1000;
  private static final String TAG = "MLKD.StrokeManager";
  // This is a constant that is used as a message identifier to trigger the timeout.
  private static final int TIMEOUT_TRIGGER = 1;
  // For handling recognition and model downloading.
  private RecognitionTask recognitionTask = null;
  @VisibleForTesting ModelManager modelManager = new ModelManager();
  // Managing the recognition queue.
  private final List<RecognitionTask.RecognizedInk> content = new ArrayList<>();
  // Managing ink currently drawn.
  private Ink.Stroke.Builder strokeBuilder = Ink.Stroke.builder();
  private Ink.Builder inkBuilder = Ink.builder();
  private boolean stateChangedSinceLastRequest = false;
  @Nullable private ContentChangedListener contentChangedListener = null;
  @Nullable private StatusChangedListener statusChangedListener = null;
  @Nullable private DownloadedModelsChangedListener downloadedModelsChangedListener = null;

  private boolean triggerRecognitionAfterInput = true;
  private boolean clearCurrentInkAfterRecognition = true;
  private String status = "";

  public void setTriggerRecognitionAfterInput(boolean shouldTrigger) {
    triggerRecognitionAfterInput = shouldTrigger;
  }

  public void setClearCurrentInkAfterRecognition(boolean shouldClear) {
    clearCurrentInkAfterRecognition = shouldClear;
  }

  // Handler to handle the UI Timeout.
  // This handler is only used to trigger the UI timeout. Each time a UI interaction happens,
  // the timer is reset by clearing the queue on this handler and sending a new delayed message (in
  // addNewTouchEvent).
  private final Handler uiHandler =
      new Handler(
          msg -> {
            if (msg.what == TIMEOUT_TRIGGER) {
              Log.i(TAG, "Handling timeout trigger.");
              commitResult();
              return true;
            }
            // In the current use this statement is never reached because we only ever send
            // TIMEOUT_TRIGGER messages to this handler.
            // This line is necessary because otherwise Java's static analysis doesn't allow for
            // compiling. Returning false indicates that a message wasn't handled.
            return false;
          });

  private void setStatus(String newStatus) {
    status = newStatus;
    if (statusChangedListener != null) {
      statusChangedListener.onStatusChanged();
    }
  }

  private void commitResult() {
    if (recognitionTask.done() && recognitionTask.result() != null) {
      content.add(recognitionTask.result());
      setStatus("Successful recognition: " + recognitionTask.result().text);
      if (clearCurrentInkAfterRecognition) {
        resetCurrentInk();
      }
      if (contentChangedListener != null) {
        contentChangedListener.onContentChanged();
      }
    }
  }

  public void reset() {
    Log.i(TAG, "reset");
    resetCurrentInk();
    content.clear();
    if (recognitionTask != null && !recognitionTask.done()) {
      recognitionTask.cancel();
    }
    setStatus("");
  }

  private void resetCurrentInk() {
    inkBuilder = Ink.builder();
    strokeBuilder = Ink.Stroke.builder();
    stateChangedSinceLastRequest = false;
  }

  public Ink getCurrentInk() {
    return inkBuilder.build();
  }

  /**
   * This method is called when a new touch event happens on the drawing client and notifies the
   * StrokeManager of new content being added.
   *
   * <p>This method takes care of triggering the UI timeout and scheduling recognitions on the
   * background thread.
   *
   * @return whether the touch event was handled.
   */
  public boolean addNewTouchEvent(MotionEvent event) {
    int action = event.getActionMasked();
    float x = event.getX();
    float y = event.getY();
    long t = System.currentTimeMillis();

    // A new event happened -> clear all pending timeout messages.
    uiHandler.removeMessages(TIMEOUT_TRIGGER);

    switch (action) {
      case MotionEvent.ACTION_DOWN:
      case MotionEvent.ACTION_MOVE:
        strokeBuilder.addPoint(Point.create(x, y, t));
        break;
      case MotionEvent.ACTION_UP:
        strokeBuilder.addPoint(Point.create(x, y, t));
        inkBuilder.addStroke(strokeBuilder.build());
        strokeBuilder = Ink.Stroke.builder();
        stateChangedSinceLastRequest = true;
        if (triggerRecognitionAfterInput) {
          recognize();
        }
        break;
      default:
        // Indicate touch event wasn't handled.
        return false;
    }

    return true;
  }

  // Listeners to update the drawing and status.
  public void setContentChangedListener(ContentChangedListener contentChangedListener) {
    this.contentChangedListener = contentChangedListener;
  }

  public void setStatusChangedListener(StatusChangedListener statusChangedListener) {
    this.statusChangedListener = statusChangedListener;
  }

  public void setDownloadedModelsChangedListener(
      DownloadedModelsChangedListener downloadedModelsChangedListener) {
    this.downloadedModelsChangedListener = downloadedModelsChangedListener;
  }

  public List<RecognitionTask.RecognizedInk> getContent() {
    return content;
  }

  public String getStatus() {
    return status;
  }

  // Model downloading / deleting / setting.

  public void setActiveModel(String languageTag) {
    setStatus(modelManager.setModel(languageTag));
  }

  public Task<Void> deleteActiveModel() {
    return modelManager
        .deleteActiveModel()
        .addOnSuccessListener(unused -> refreshDownloadedModelsStatus())
        .onSuccessTask(
            status -> {
              setStatus(status);
              return Tasks.forResult(null);
            });
  }

  public Task<Void> download() {
    setStatus("Download started.");
    return modelManager
        .download()
        .addOnSuccessListener(unused -> refreshDownloadedModelsStatus())
        .onSuccessTask(
            status -> {
              setStatus(status);
              return Tasks.forResult(null);
            });
  }

  // Recognition-related.

  public Task<String> recognize() {

    if (!stateChangedSinceLastRequest || inkBuilder.isEmpty()) {
      setStatus("No recognition, ink unchanged or empty");
      return Tasks.forResult(null);
    }
    if (modelManager.getRecognizer() == null) {
      setStatus("Recognizer not set");
      return Tasks.forResult(null);
    }

    return modelManager
        .checkIsModelDownloaded()
        .onSuccessTask(
            result -> {
              if (!result) {
                setStatus("Model not downloaded yet");
                return Tasks.forResult(null);
              }

              stateChangedSinceLastRequest = false;
              recognitionTask =
                  new RecognitionTask(modelManager.getRecognizer(), inkBuilder.build());
              uiHandler.sendMessageDelayed(
                  uiHandler.obtainMessage(TIMEOUT_TRIGGER), CONVERSION_TIMEOUT_MS);
              return recognitionTask.run();
            });
  }

  public void refreshDownloadedModelsStatus() {
    modelManager
        .getDownloadedModelLanguages()
        .addOnSuccessListener(
            downloadedLanguageTags -> {
              if (downloadedModelsChangedListener != null) {
                downloadedModelsChangedListener.onDownloadedModelsChanged(downloadedLanguageTags);
              }
            });
  }
}
