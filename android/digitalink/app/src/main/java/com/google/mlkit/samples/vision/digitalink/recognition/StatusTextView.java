package com.google.mlkit.samples.vision.digitalink.recognition;

import android.content.Context;
import androidx.appcompat.widget.AppCompatTextView;
import android.util.AttributeSet;
import androidx.annotation.NonNull;
import com.google.mlkit.samples.vision.digitalink.recognition.StrokeManager.StatusChangedListener;

/**
 * Status bar for the test app.
 *
 * <p>It is updated upon status changes announced by the StrokeManager.
 */
public class StatusTextView extends AppCompatTextView implements StatusChangedListener {

  private StrokeManager strokeManager;

  public StatusTextView(@NonNull Context context) {
    super(context);
  }

  public StatusTextView(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
  }

  @Override
  public void onStatusChanged() {
    this.setText(this.strokeManager.getStatus());
  }

  void setStrokeManager(StrokeManager strokeManager) {
    this.strokeManager = strokeManager;
  }
}
