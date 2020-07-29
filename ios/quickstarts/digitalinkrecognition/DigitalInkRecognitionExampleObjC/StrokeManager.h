#ifndef StrokeManager_h
#define StrokeManager_h

#import <UIKit/UIKit.h>

#import "RecognizedInk.h"

NS_ASSUME_NONNULL_BEGIN

/**
 * Protocol used by the `StrokeManager` to send requests back to the `ViewController` to update the
 * display.
 */
@protocol StrokeManagerDelegate <NSObject>
/** Clears any temporary ink managed by the caller. */
- (void)clearInk;
/** Redraws the ink and recognition results. */
- (void)redraw;
/** Display the given message to the user. */
- (void)displayMessage:(NSString *)message;
@end

/**
 * The `StrokeManager` object is responsible for storing the ink and recognition results, and
 * managing the interaction with the recognizer. It receives the touch points as the user is drawing
 * from the `ViewController` (which takes care of rendering the ink), and stores them into an array
 * of `Stroke`s. When the user taps "recognize", the strokes are collected together into an `Ink`
 * object, and passed to the recognizer. The `StrokeManagerDelegate` protocol is used to inform the
 * `ViewController` when the display needs to be updated.
 *
 * The `StrokeManager` provides additional methods to handle other buttons in the UI, including
 * selecting a recognition language, downloading or deleting the recognition model, or clearing the
 * ink.
 */
@interface StrokeManager : NSObject

/**
 * Array of `RecognizedInk`s that have been sent to the recognizer along with any recognition
 * results.
 */
@property(readonly, nonatomic) NSArray<RecognizedInk *> *recognizedInks;

/**
 * Initializes internal state and stores a pointer to the view to allow for redrawing when ink is
 * sent to the recognizer or recognition results come back.
 */
- (nullable instancetype)initWithDelegate:(nullable id<StrokeManagerDelegate>)delegate
    NS_DESIGNATED_INITIALIZER;
- (nullable instancetype)init NS_UNAVAILABLE;

/** Function called by the `ViewController` to create the first point of a stroke. */
- (void)startStrokeAtPoint:(CGPoint)point time:(NSTimeInterval)t;

/** Function called by the `ViewController` to add a point to a stroke. */
- (void)continueStrokeAtPoint:(CGPoint)point time:(NSTimeInterval)t;

/** Function called by the `ViewController` to end a stroke. */
- (void)endStrokeAtPoint:(CGPoint)point time:(NSTimeInterval)t;

/** Clears all ink. */
- (void)clear;

/** Informs the `StrokeManager` of which recognizer to use for subsequent recognitions. */
- (void)selectLanguage:(NSString *)languageTag;

/**
 * Asks the `StrokeManager` to start downloading the recognition model indicated by
 * `selectLanguage:`.
 */
- (void)downloadModel;

/**
 * Check whether the model for the given language tag is already downloaded.
 */
- (BOOL)isLanguageDownloaded:(NSString *)languageTag;

/** Asks the `StrokeManager` to delete the reocngition model indicated by `selectLanguage:`. */
- (void)deleteModel;

/**
 * Asks the `StrokeManager` to recognize the unrecognized ink using the recognition model indicated
 * by `selectLanguage:`.
 */
- (void)recognizeInk;

@end

#endif /* StrokeManager_h */

NS_ASSUME_NONNULL_END
