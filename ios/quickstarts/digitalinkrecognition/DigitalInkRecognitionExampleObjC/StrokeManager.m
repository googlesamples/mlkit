#import "StrokeManager.h"

#import <Foundation/Foundation.h>

@import MLKit;

#import "ViewController.h"

NS_ASSUME_NONNULL_BEGIN

/**
 * Conversion factor between `NSTimeInterval` and milliseconds, which is the unit used by the
 * recognizer.
 */
static const double kMillisecondsPerTimeInterval = 1000.0;

@interface StrokeManager ()
/** Arrays used to keep the piece of ink that is currently being drawn. */
@property(nullable, nonatomic) NSMutableArray<MLKStroke *> *strokes;
@property(nullable, nonatomic) NSMutableArray<MLKStrokePoint *> *points;

/** The recognizer that will translate the ink into text. */
@property(nullable, nonatomic) MLKDigitalInkRecognizer *recognizer;

/** The view that handles UI stuff. */
@property(weak, nullable, nonatomic) id<StrokeManagerDelegate> delegate;

/** Redeclared as `readwrite`. */
@property(nonatomic) NSMutableArray<RecognizedInk *> *recognizedInks;

/** Properties to track and manage the selected language and recognition model. */
@property(nonatomic) MLKDigitalInkRecognitionModel *model;
@property(nonatomic) MLKModelManager *modelManager;
@end

@implementation StrokeManager

#pragma mark - Public

/**
 * Initialization of internal variables as well as creating the model manager and setting up
 * observers of the recognition model downloading status.
 */
- (nullable instancetype)initWithDelegate:(nullable id<StrokeManagerDelegate>)delegate {
  self = [super init];
  if (self != nil) {
    _modelManager = [MLKModelManager modelManager];
    _delegate = delegate;
    _recognizedInks = [NSMutableArray array];

    // Add observers for download notifications, and reflect the status back to the user.
    __weak typeof(self) weakSelf = self;
    [NSNotificationCenter.defaultCenter
        addObserverForName:MLKModelDownloadDidSucceedNotification
                    object:nil
                     queue:NSOperationQueue.mainQueue
                usingBlock:^(NSNotification *notification) {
                  typeof(self) strongSelf = weakSelf;
                  if (strongSelf == nil) {
                    NSLog(@"self == nil handling download success notification");
                    return;
                  }
                  if ([notification.userInfo[MLKModelDownloadUserInfoKeyRemoteModel]
                          isEqual:strongSelf.model]) {
                    [strongSelf.delegate displayMessage:@"Model download succeeded"];
                  }
                }];
    [NSNotificationCenter.defaultCenter
        addObserverForName:MLKModelDownloadDidFailNotification
                    object:nil
                     queue:NSOperationQueue.mainQueue
                usingBlock:^(NSNotification *notification) {
                  typeof(self) strongSelf = weakSelf;
                  if (strongSelf == nil) {
                    NSLog(@"self == nil handling download fail notification");
                    return;
                  }
                  if ([notification.userInfo[MLKModelDownloadUserInfoKeyRemoteModel]
                          isEqual:strongSelf.model]) {
                    [strongSelf.delegate displayMessage:@"Model download failed"];
                  }
                }];
  }
  return self;
}

/**
 * Given a language tag, looks up the cooresponding model identifier and initializes the model. Note
 * that this doesn't actually download the model, which is triggered manually by the user for the
 * purposes of this demo app.
 */
- (void)selectLanguage:(NSString *)languageTag {
  MLKDigitalInkRecognitionModelIdentifier *identifier =
      [MLKDigitalInkRecognitionModelIdentifier modelIdentifierForLanguageTag:languageTag];
  self.model = [[MLKDigitalInkRecognitionModel alloc] initWithModelIdentifier:identifier];
  self.recognizer = nil;
  [self.delegate
      displayMessage:[NSString stringWithFormat:@"Selected language with tag %@", languageTag]];
}

/**
 * Check whether the model for the given language tag is already downloaded.
 */
- (BOOL)isLanguageDownloaded:(NSString *)languageTag {
  MLKDigitalInkRecognitionModelIdentifier *identifier =
      [MLKDigitalInkRecognitionModelIdentifier modelIdentifierForLanguageTag:languageTag];
  MLKDigitalInkRecognitionModel *model =
      [[MLKDigitalInkRecognitionModel alloc] initWithModelIdentifier:identifier];
  return [self.modelManager isModelDownloaded:model];
}

/**
 * Actually downloads the model. This happens asynchronously with the user being shown status
 * messages when the download completes or fails.
 */
- (void)downloadModel {
  if ([self.modelManager isModelDownloaded:self.model]) {
    [self.delegate displayMessage:@"Model is already downloaded"];
    return;
  }

  [self.delegate displayMessage:@"Starting download"];
  // The NSProgress object returned by downloadModel: currently only takes on the values 0% or 100%
  // so is not very useful. Instead we'll rely on the outcome listeners in the initializer to
  // inform the user if a download succeeds or fails.
  [self.modelManager
      downloadModel:self.model
         conditions:[[MLKModelDownloadConditions alloc] initWithAllowsCellularAccess:YES
                                                         allowsBackgroundDownloading:YES]];
}

/** Deletes the currently selected model. */
- (void)deleteModel {
  if (![self.modelManager isModelDownloaded:self.model]) {
    [self.delegate displayMessage:@"Model not downloaded, nothing to delete"];
    return;
  }
  __weak typeof(self) weakSelf = self;
  [self.modelManager deleteDownloadedModel:self.model
                                completion:^(NSError *_Nullable error) {
                                  typeof(self) strongSelf = weakSelf;
                                  if (strongSelf == nil) {
                                    NSLog(@"self == nil handling model download completion");
                                    return;
                                  }
                                  [strongSelf.delegate displayMessage:@"Model deleted"];
                                }];
}

/**
 * Actually carries out the recognition. The recognition may happen asynchronously so there's a
 * callback that handles the results when they are ready.
 */
- (void)recognizeInk {
  if (self.strokes.count == 0) {
    [self.delegate displayMessage:@"No ink to recognize"];
    return;
  }
  if (![self.modelManager isModelDownloaded:self.model]) {
    [self.delegate displayMessage:@"Recognizer model not downloaded"];
    return;
  }
  if (self.recognizer == nil) {
    [self.delegate displayMessage:@"Initializing recognizer"];
    MLKDigitalInkRecognizerOptions *options =
        [[MLKDigitalInkRecognizerOptions alloc] initWithModel:self.model];
    self.recognizer = [MLKDigitalInkRecognizer digitalInkRecognizerWithOptions:options];
    if (self.recognizer == nil) {
      [self.delegate displayMessage:@"Could not initialize recognizer"];
      return;
    } else {
      [self.delegate displayMessage:@"Initialized recognizer"];
    }
  }

  // Turn the list of strokes into an `Ink`, and add this ink to the `recognizedInks` array.
  MLKInk *ink = [[MLKInk alloc] initWithStrokes:self.strokes];
  RecognizedInk *recognizedInk = [[RecognizedInk alloc] initWithInk:ink];
  [_recognizedInks addObject:recognizedInk];
  // Clear the currently being drawn ink, and display the ink from `recognizedInks` (which results
  // in it changing color).
  [self.delegate redraw];
  [self.delegate clearInk];
  self.strokes = nil;
  // Start the recognizer. Callback function will store the recognized text and tell the
  // `ViewController` to redraw the screen to show it.
  __weak typeof(self) weakSelf = self;
  [self.recognizer
      recognizeInk:ink
        completion:^(MLKDigitalInkRecognitionResult *_Nullable result, NSError *_Nullable error) {
          typeof(self) strongSelf = weakSelf;
          if (strongSelf == nil) {
            NSLog(@"self == nil handling recognition completion");
            return;
          }
          if (result.candidates.count > 0) {
            recognizedInk.text = result.candidates[0].text;
            NSString *message = [@"Recognized: " stringByAppendingString:result.candidates[0].text];
            if (result.candidates[0].score != nil) {
              message = [message
                  stringByAppendingFormat:@" score %f", result.candidates[0].score.floatValue];
            }
            [strongSelf.delegate displayMessage:message];
          } else {
            recognizedInk.text = @"error";
            [strongSelf.delegate displayMessage:@"Recognition error"];
          }
          [strongSelf.delegate redraw];
        }];
}

/** Clear out all the ink and other state. */
- (void)clear {
  self.recognizedInks = [NSMutableArray array];
  self.strokes = nil;
}

/** Begins a new stroke when the user touches the screen. */
- (void)startStrokeAtPoint:(CGPoint)point time:(NSTimeInterval)t {
  self.points = [NSMutableArray array];
  [self.points addObject:[[MLKStrokePoint alloc] initWithX:point.x
                                                         y:point.y
                                                         t:t * kMillisecondsPerTimeInterval]];
}

/** Adds an additional point to the stroke when the user moves their finger. */
- (void)continueStrokeAtPoint:(CGPoint)point time:(NSTimeInterval)t {
  [self.points addObject:[[MLKStrokePoint alloc] initWithX:point.x
                                                         y:point.y
                                                         t:t * kMillisecondsPerTimeInterval]];
}

/** Completes a stroke when the user lifts their finger. */
- (void)endStrokeAtPoint:(CGPoint)point time:(NSTimeInterval)t {
  [self.points addObject:[[MLKStrokePoint alloc] initWithX:point.x
                                                         y:point.y
                                                         t:t * kMillisecondsPerTimeInterval]];
  // Create an array of strokes if it doesn't exist already, and add this stroke to it.
  if (self.strokes == nil) {
    self.strokes = [NSMutableArray array];
  }
  [self.strokes addObject:[[MLKStroke alloc] initWithPoints:self.points]];
  self.points = nil;
}

@end

NS_ASSUME_NONNULL_END
