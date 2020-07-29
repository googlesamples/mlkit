#import <UIKit/UIKit.h>

#import "RecognizedInk.h"
#import "StrokeManager.h"

NS_ASSUME_NONNULL_BEGIN

/**
 * The `ViewController` manages the display seen by the user. The drawing canvas is actually two
 * overlapping image views. The top one contains the ink that the user is drawing before it is sent
 * to the recognizer. It can be thought of as a temporary buffer for ink in progress. When the user
 * presses the "Recognize" button, the ink is transferred to the other canvas, which displays a
 * grayed out version of the ink together with the recognition result.
 *
 * The management of the interaction with the recognizer happens in `StrokeManager`.
 * `ViewController` just takes care of receiving user events, rendering the temporary ink, and
 * handles redraw requests from the `StrokeManager` when the ink is recognized. This latter request
 * comes through the `StrokeManagerDelegate` protocol.
 *
 * The `ViewController` provides a number of buttons for controlling the `StrokeManager` which allow
 * for selecting the recognition language, downloading or deleting the recognition model, triggering
 * recognition, and clearing the ink.
 */
@interface ViewController
    : UIViewController <StrokeManagerDelegate, UIPickerViewDelegate, UIPickerViewDataSource>

@end

NS_ASSUME_NONNULL_END
