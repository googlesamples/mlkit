/**
 * Copyright 2020 Google ML Kit team
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

#import "MLKLiveObjectDetectionViewController.h"

#import <AVFoundation/AVFoundation.h>
#import <UIKit/UIKit.h>

#import "MLKCameraReticle.h"
#import "MLKConfirmationSpinner.h"
#import "MLKDetectionOverlayView.h"
#import "MLKImageUtilities.h"
#import "MLKODTStatus.h"
#import "MLKResult.h"
#import "MLKResultListHeaderView.h"
#import "MLKResultListViewController.h"
#import "MLKProductSearchRequest.h"
#import "MLKUIUtilities.h"

@import MLKitObjectDetection;
@import MLKitObjectDetectionCommon;
@import MLKitCommon;
@import MLKitVision;
@import MLKitObjectDetectionCustom;
@import MaterialComponents;
@import GTMSessionFetcher;

NS_ASSUME_NONNULL_BEGIN

static char *const MLKVideoDataOutputQueueLabel =
    "com.google.mlkit.visiondetector.VideoDataOutputQueue";
static char *const MLKVideoSessionQueueLabel = "com.google.mlkit.visiondetector.VideoSessionQueue";

/** Duration for presenting the bottom sheet. */
static const CGFloat kBottomSheetAnimationDurationInSec = 0.25f;

/** Duration for confirming stage. */
static const CGFloat kconfirmingDurationInSec = 1.5f;

// Constants for alpha values.
static const CGFloat kOpaqueAlpha = 1.0f;
static const CGFloat kTransparentAlpha = 0.0f;

/**  Radius of the searching indicator. */
static const CGFloat kSearchingIndicatorRadius = 24.0f;

/** Target height of the thumbnail when it sits on top of the bottom sheet. */
static const CGFloat kThumbnailbottomSheetTargetHeight = 200.0f;

/** Padding around the thumbnail when it sits on top of the bottom sheet. */
static const CGFloat kThumbnailPaddingAround = 24.0f;

/** The thumbnail will fade out when it reaches this threshold from screen edge. */
static const CGFloat kThumbnailFadeOutEdgeThreshold = 200.0f;

// Chip message related values.
static const CGFloat kChipBackgroundAlpha = 0.6f;
static const CGFloat kChipCornerRadius = 8.0f;
static const CGFloat kChipFadeInDuration = 0.075f;
static const CGFloat kChipScaleDuration = 0.15f;
static const CGFloat kChipScaleFromRatio = 0.8f;
static const CGFloat kChipScaleToRatio = 1.25f;
static const CGFloat kChipBottomPadding = 36.0f;

/** Number of faked product search results. */
static const NSUInteger kFakeSearchResultCount = 10;

/**  Back button related constants. */
static const CGFloat kBackButtonSize = 48.0f;
static const CGFloat kBackButtonBackgroundAlpha = 0.6f;
static const CGFloat kBackButtonPadding = 16.0f;

/** The messages shown in detecting stage.  */
static NSString *const kDetectingStageMessage = @"Point your camera at an object";
static NSString *const kDetectingStageMessageBird = @"Point your camera at a bird";

/** The Message shown in confirming stage. */
static NSString *const kConfirmingStageMessage = @"Keep camera still for a moment";

/** The message shown in searching stage. */
static NSString *const kSearchingMessage = @"Searching";

/** Strings for bird search. */
static NSString *const kDescriptionPlaceHoler = @"Some description about the bird from wiki";
static NSString *const kWikiSearchURLPattern = @"https://en.wikipedia.org/w/api.php?format=json&action=query&prop=extracts&exintro&explaintext&redirects=1&titles=%@";

/** Model related strings. */
static NSString *const kModelNameBird = @"bird";
static NSString *const kModelTypeTFLite = @"tflite";

// Strings for fake search results.
static NSString *const kFakeResultTitleFormat = @"Fake product name: %li";
static NSString *const kFakeProductTypeName = @"Fashion";
static NSString *const kFakeProductPriceText = @"$10";
static NSString *const kFakeProductItemNumberText = @"12345678";

/**
 * A wrapper class that holds a reference to `CMSampleBufferRef` to let ARC take care of its
 * lifecyle for this `CMSampleBufferRef`.
 */
@interface MLKSampleBuffer : NSObject

// The encapsulated `CMSampleBufferRef` data.
@property(nonatomic) CMSampleBufferRef data;

@end

@implementation MLKSampleBuffer

#pragma mark - Public

- (instancetype)initWithSampleBuffer:(CMSampleBufferRef)sampleBuffer {
  self = [super init];
  if (self != nil) {
    _data = sampleBuffer;
    CFRetain(sampleBuffer);
  }
  return self;
}

- (void)dealloc {
  CFRelease(self.data);
}

@end

@interface MLKLiveObjectDetectionViewController () <AVCaptureVideoDataOutputSampleBufferDelegate,
                                                    MDCBottomSheetControllerDelegate>

// Views to be added as subviews of current view.
@property(nonatomic) UIView *previewView;
@property(nonatomic) MLKDetectionOverlayView *overlayView;
@property(nonatomic) MDCButton *backButton;
@property(nonatomic) MLKCameraReticle *detectingReticle;
@property(nonatomic) MLKConfirmationSpinner *confirmingSpinner;
@property(nonatomic) MDCActivityIndicator *searchingIndicator;

// Video capture related properties.
@property(nonatomic) AVCaptureSession *session;
@property(nonatomic, nullable) AVCaptureVideoDataOutput *videoDataOutput;
@property(nonatomic) dispatch_queue_t videoDataOutputQueue;
@property(nonatomic) dispatch_queue_t sessionQueue;
@property(nonatomic) AVCaptureVideoPreviewLayer *previewLayer;

// The `MLKObjectDetector` used to detect objects.
@property(nonatomic) MLKObjectDetector *detector;

// Current status in object detection.
@property(nonatomic) MLKODTStatus status;

// View to show message during different stages.
@property(nonatomic) MDCChipView *messageView;

// Properties to record latest detected results.
@property(nonatomic, nullable) MLKObject *lastDetectedObject;
@property(nonatomic, nullable) MLKSampleBuffer *lastDetectedSampleBuffer;

// Width to height ratio of the thumbnail.
@property(nonatomic) CGFloat thumbnailWidthHeightRatio;

// Target Y offset of the bottom sheet.
@property(nonatomic) CGFloat bottomSheetTargetOffsetY;

// Array of timers scheduled before Confirmation.
@property(nonatomic, nullable) NSMutableArray<NSTimer *> *timers;

// Used to fetch product search results.
@property(nonatomic) GTMSessionFetcherService *fetcherService;

/** Type of current detector. */
@property(nonatomic) MLKDetectorType detectorType;

@end

@implementation MLKLiveObjectDetectionViewController

#pragma mark - Public

- (id)initWithDetectorType:(MLKDetectorType)detectorType {
  self = [super init];
  if (self != nil) {
    _detectorType = detectorType;
    _videoDataOutputQueue =
        dispatch_queue_create(MLKVideoDataOutputQueueLabel, DISPATCH_QUEUE_SERIAL);
    _sessionQueue = dispatch_queue_create(MLKVideoSessionQueueLabel, DISPATCH_QUEUE_SERIAL);
    _session = [[AVCaptureSession alloc] init];
    _fetcherService = [[GTMSessionFetcherService alloc] init];
    _status = MLKODTStatus_NotStarted;
    _timers = [NSMutableArray array];
    [self setUpDetectorOfType:detectorType];
  }
  return self;
}

- (void)dealloc {
  [self clearLastDetectedObject];
  [self.fetcherService stopAllFetchers];
}

#pragma mark - UIViewController

- (void)loadView {
  [super loadView];

  self.view.clipsToBounds = YES;

  [self setUpPreviewView];
  [self setUpOverlayView];
}

- (void)viewDidLoad {
  [super viewDidLoad];
  self.view.backgroundColor = UIColor.whiteColor;

  [self setCameraSelection];

  // Set up video processing pipeline.
  [self setUpVideoProcessing];

  // Set up camera preview.
#if !TARGET_IPHONE_SIMULATOR
  [self setUpCameraPreviewLayer];
#endif

  [self setUpDetectingReticle];
  [self setUpconfirmingSpinner];
  [self setUpSearchingIndicator];
  [self setUpMessageView];

  [self setupBackButton];
  [self startToDetect];
}

- (void)viewDidLayoutSubviews {
  [super viewDidLayoutSubviews];

  self.previewLayer.frame = self.view.frame;
  self.previewLayer.position =
      CGPointMake(CGRectGetMidX(self.previewLayer.frame), CGRectGetMidY(self.previewLayer.frame));
}

- (void)viewDidDisappear:(BOOL)animated {
  [super viewDidDisappear:animated];
  __weak typeof(self) weakSelf = self;
#if !TARGET_IPHONE_SIMULATOR
  dispatch_async(self.sessionQueue, ^{
    [weakSelf.session stopRunning];
  });
#endif
}

#pragma mark - AVCaptureVideoDataOutputSampleBufferDelegate

- (void)captureOutput:(AVCaptureOutput *)captureOutput
    didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
           fromConnection:(AVCaptureConnection *)connection {
  [self detectObjectInSampleBuffer:[[MLKSampleBuffer alloc] initWithSampleBuffer:sampleBuffer]];
}

#pragma mark - MDCBottomSheetControllerDelegate

- (void)bottomSheetControllerDidDismissBottomSheet:(nonnull MDCBottomSheetController *)controller {
  self.bottomSheetTargetOffsetY = 0;
  [self startToDetect];
}

- (void)bottomSheetControllerDidChangeYOffset:(MDCBottomSheetController *)controller
                                      yOffset:(CGFloat)yOffset {
  CGFloat imageStartY = yOffset - kThumbnailbottomSheetTargetHeight - kThumbnailPaddingAround;
  CGRect rect =
      CGRectMake(kThumbnailPaddingAround,                                             // X
                 imageStartY,                                                         // Y
                 kThumbnailbottomSheetTargetHeight * self.thumbnailWidthHeightRatio,  // Width
                 kThumbnailbottomSheetTargetHeight);                                  // Height

  UIWindow *currentWindow = UIApplication.sharedApplication.keyWindow;

  UIEdgeInsets safeInsets = [MLKUIUtilities safeAreaInsets];
  CGFloat screenHeight = currentWindow.bounds.size.height;
  CGFloat topFadeOutOffsetY = safeInsets.top + kThumbnailFadeOutEdgeThreshold;
  CGFloat bottomFadeOutOffsetY = screenHeight - safeInsets.bottom - kThumbnailFadeOutEdgeThreshold;

  CGFloat imageAlpha =
      [self ratioOfCurrentValue:yOffset
                           from:(yOffset > self.bottomSheetTargetOffsetY) ? bottomFadeOutOffsetY
                                                                          : topFadeOutOffsetY
                             to:self.bottomSheetTargetOffsetY];
  [self.overlayView showImageInRect:rect alpha:imageAlpha];
}

#pragma mark - Private

- (NSString *)detectingStageMessage {
  switch (self.detectorType) {
    case MLKDetectorTypeODTBirdModel:
      return kDetectingStageMessageBird;
    case MLKDetectorTypeODTDefaultModel: // Falls through
    default:
      return kDetectingStageMessage;
  }
}

- (void) didTapBackButton {
  [self.delegate didTapBackButton];
}

- (void)setUpDetectorOfType:(MLKDetectorType)detectorType {
  switch (detectorType) {
    case MLKDetectorTypeODTDefaultModel: {
      MLKObjectDetectorOptions *options = [[MLKObjectDetectorOptions alloc] init];
      _detector = [MLKObjectDetector objectDetectorWithOptions:options];
      break;
    }
    case MLKDetectorTypeODTBirdModel: {
      NSString *localModelPath = [[NSBundle mainBundle] pathForResource:kModelNameBird
                                                                 ofType:kModelTypeTFLite];
      MLKLocalModel *localModel = [[MLKLocalModel alloc] initWithPath:localModelPath];
      MLKCommonObjectDetectorOptions *options =
          [[MLKCustomObjectDetectorOptions alloc] initWithLocalModel:localModel];
      options.shouldEnableClassification = YES;
      _detector = [MLKObjectDetector objectDetectorWithOptions:options];
      break;
    }
    default:
      break;
  }
}

/** Prepares camera session for video processing. */
- (void)setUpVideoProcessing {
  __weak typeof(self) weakSelf = self;
  dispatch_async(self.sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    if (strongSelf == nil) {
      return;
    }
    strongSelf.videoDataOutput = [[AVCaptureVideoDataOutput alloc] init];
    NSDictionary<NSString *, NSNumber *> *rgbOutputSettings =
        @{(__bridge NSString *)kCVPixelBufferPixelFormatTypeKey : @(kCVPixelFormatType_32BGRA)};
    [strongSelf.videoDataOutput setVideoSettings:rgbOutputSettings];

    if (![strongSelf.session canAddOutput:strongSelf.videoDataOutput]) {
      if (strongSelf.videoDataOutput) {
        [strongSelf.session removeOutput:strongSelf.videoDataOutput];
        strongSelf.videoDataOutput = nil;
      }
      NSLog(@"Failed to set up video output");
      return;
    }
    [strongSelf.videoDataOutput setAlwaysDiscardsLateVideoFrames:YES];
    [strongSelf.videoDataOutput setSampleBufferDelegate:strongSelf
                                                  queue:strongSelf.videoDataOutputQueue];
    [strongSelf.session addOutput:strongSelf.videoDataOutput];
  });
}

/** Prepares preview view for camera session. */
- (void)setUpPreviewView {
  self.previewView = [[UIView alloc] initWithFrame:self.view.frame];
  self.previewView.translatesAutoresizingMaskIntoConstraints = NO;
  [self.view addSubview:self.previewView];
}

/** Initiates and prepares camera preview layer for later video capture. */
- (void)setUpCameraPreviewLayer {
  self.previewLayer = [[AVCaptureVideoPreviewLayer alloc] initWithSession:self.session];
  [self.previewLayer setBackgroundColor:UIColor.blackColor.CGColor];
  [self.previewLayer setVideoGravity:AVLayerVideoGravityResizeAspectFill];
  CALayer *rootLayer = [self.previewView layer];
  [rootLayer setMasksToBounds:YES];
  [self.previewView setFrame:[rootLayer bounds]];
  [rootLayer addSublayer:self.previewLayer];
}

/** Prepares camera for later video capture. */
- (void)setCameraSelection {
  __weak typeof(self) weakSelf = self;
  dispatch_async(self.sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    if (strongSelf == nil) {
      return;
    }

    [strongSelf.session beginConfiguration];
    strongSelf.session.sessionPreset = AVCaptureSessionPreset1280x720;

    NSArray<AVCaptureInput *> *oldInputs = [strongSelf.session inputs];
    for (AVCaptureInput *oldInput in oldInputs) {
      [strongSelf.session removeInput:oldInput];
    }

    AVCaptureDeviceInput *input = [strongSelf pickCamera:AVCaptureDevicePositionBack];
    if (!input) {
      // Failed, restore old inputs
      for (AVCaptureInput *oldInput in oldInputs) {
        [strongSelf.session addInput:oldInput];
      }
    } else {
      // Succeeded, set input and update connection states
      [strongSelf.session addInput:input];
    }
    [strongSelf.session commitConfiguration];
  });
}

/** Determines camera for later video capture. Here only rear camera is picked. */
- (AVCaptureDeviceInput *)pickCamera:(AVCaptureDevicePosition)desiredPosition {
  BOOL hadError = NO;
  for (AVCaptureDevice *device in [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo]) {
    if ([device position] == desiredPosition) {
      NSError *error = nil;
      AVCaptureDeviceInput *input = [AVCaptureDeviceInput deviceInputWithDevice:device
                                                                          error:&error];
      if (error != nil) {
        hadError = YES;
        NSLog(@"Could not initialize for AVMediaTypeVideo for device %@", device);
      } else if ([self.session canAddInput:input]) {
        return input;
      }
    }
  }
  if (!hadError) {
    NSLog(@"No camera found for requested orientation");
  }
  return nil;
}

/** Initiates and prepares overlay view for later video capture. */
- (void)setUpOverlayView {
  self.overlayView = [[MLKDetectionOverlayView alloc] initWithFrame:self.view.frame];
  self.overlayView.translatesAutoresizingMaskIntoConstraints = NO;
  [self.view addSubview:self.overlayView];
}

- (void)setupBackButton {
  self.backButton = [[MDCButton alloc] init];
  UIImage *backArrow = [UIImage imageNamed:@"ic_arrow_back_ios"];
  [self.backButton setTintColor:UIColor.whiteColor];
  [self.backButton setImage:backArrow forState:UIControlStateNormal];
  [self.backButton clipsToBounds];
  [self.backButton.layer setCornerRadius:kBackButtonSize / 2];
  CGFloat statusBarHeight = [UIApplication sharedApplication].statusBarFrame.size.height;
  self.backButton.frame = CGRectMake(kBackButtonPadding, //X
                                     kBackButtonPadding + statusBarHeight, // Y
                                     kBackButtonSize, // Width
                                     kBackButtonSize); // Height
  self.backButton.backgroundColor =
    [UIColor.blackColor colorWithAlphaComponent:kBackButtonBackgroundAlpha];
  [self.backButton addTarget:self
                      action:@selector(didTapBackButton)
            forControlEvents:UIControlEventTouchUpInside];
  [self.view addSubview:self.backButton];
  [NSLayoutConstraint activateConstraints:@[
    [self.backButton.leftAnchor constraintEqualToAnchor:self.view.leftAnchor],
    [self.backButton.topAnchor constraintEqualToAnchor:self.view.topAnchor],
  ]];
}

/** Clears up the overlay view. Caller must make sure this runs on the main thread. */
- (void)cleanUpOverlayView {
  NSAssert([NSThread.currentThread isEqual:NSThread.mainThread],
           @"cleanUpOverlayView is not running on the main thread");

  [self.overlayView hideSubviews];
  self.overlayView.frame = self.view.frame;
}

/** Initiates and prepares detecting reticle for later video capture. */
- (void)setUpDetectingReticle {
  self.detectingReticle = [[MLKCameraReticle alloc] init];
  self.detectingReticle.translatesAutoresizingMaskIntoConstraints = NO;
  CGSize size = [self.detectingReticle sizeThatFits:CGSizeMake(CGFLOAT_MAX, CGFLOAT_MAX)];
  self.detectingReticle.frame = CGRectMake(0, 0, size.width, size.height);
  [self.view addSubview:self.detectingReticle];
  [NSLayoutConstraint activateConstraints:@[
    [self.detectingReticle.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
    [self.detectingReticle.centerYAnchor constraintEqualToAnchor:self.view.centerYAnchor],
  ]];
}

/** Initiates and prepares confirming spinner for later video capture. */
- (void)setUpconfirmingSpinner {
  self.confirmingSpinner =
      [[MLKConfirmationSpinner alloc] initWithDuration:kconfirmingDurationInSec];
  self.confirmingSpinner.translatesAutoresizingMaskIntoConstraints = NO;
  CGSize size = [self.confirmingSpinner sizeThatFits:CGSizeMake(CGFLOAT_MAX, CGFLOAT_MAX)];
  self.confirmingSpinner.frame = CGRectMake(0, 0, size.width, size.height);
  [self.view addSubview:self.confirmingSpinner];
  [NSLayoutConstraint activateConstraints:@[
    [self.confirmingSpinner.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
    [self.confirmingSpinner.centerYAnchor constraintEqualToAnchor:self.view.centerYAnchor],
  ]];
}

/** Initiates and prepares searching indicator for later video capture. */
- (void)setUpSearchingIndicator {
  self.searchingIndicator = [[MDCActivityIndicator alloc] init];
  self.searchingIndicator.radius = kSearchingIndicatorRadius;
  self.searchingIndicator.cycleColors = @[ UIColor.whiteColor ];
  CGSize size = [self.confirmingSpinner sizeThatFits:CGSizeMake(CGFLOAT_MAX, CGFLOAT_MAX)];
  CGFloat centerX = CGRectGetMidX(self.view.frame);
  CGFloat centerY = CGRectGetMidY(self.view.frame);
  self.searchingIndicator.frame = CGRectMake(centerX, centerY, size.width, size.height);
  [self.view addSubview:self.searchingIndicator];
  [NSLayoutConstraint activateConstraints:@[
    [self.searchingIndicator.centerXAnchor constraintEqualToAnchor:self.view.centerXAnchor],
    [self.searchingIndicator.centerYAnchor constraintEqualToAnchor:self.view.centerYAnchor],
  ]];
}

/** Initiates and prepares message view for later video capture. */
- (void)setUpMessageView {
  self.messageView = [[MDCChipView alloc] init];
  [self.messageView setBackgroundColor:[UIColor.blackColor colorWithAlphaComponent:kChipBackgroundAlpha] forState:UIControlStateNormal];

  self.messageView.userInteractionEnabled = NO;
  self.messageView.clipsToBounds = YES;
  self.messageView.titleLabel.textColor = UIColor.whiteColor;
  self.messageView.layer.cornerRadius = kChipCornerRadius;
  [self.view addSubview:self.messageView];
  self.messageView.alpha = kTransparentAlpha;
}

/**
 * Clears last detected object. Caller must make sure that this method runs on the main thread.
 */
- (void)clearLastDetectedObject {
  NSAssert([NSThread.currentThread isEqual:NSThread.mainThread],
           @"clearLastDetectedObject is not running on the main thread");

  self.lastDetectedObject = nil;
  self.lastDetectedSampleBuffer = nil;
  for (NSTimer *timer in self.timers) {
    [timer invalidate];
  }
}

#pragma mark - Object detection and tracking.

/**
 * Called to detect objects in the given sample buffer.
 *
 * @param sampleBuffer The `SampleBuffer` for object detection.
 */
- (void)detectObjectInSampleBuffer:(MLKSampleBuffer *)sampleBuffer {
  MLKVisionImage *image = [[MLKVisionImage alloc] initWithBuffer:sampleBuffer.data];
  image.orientation =
      [MLKUIUtilities imageOrientationFromOrientation:UIDevice.currentDevice.orientation
                            withCaptureDevicePosition:AVCaptureDevicePositionBack];
  NSError *error;
  NSArray<MLKObject *> *objects = [self.detector resultsInImage:image error:&error];
  if (error == nil) {
    __weak typeof(self) weakSelf = self;
    dispatch_async(dispatch_get_main_queue(), ^{
      [weakSelf onDetectedObjects:objects inSampleBuffer:sampleBuffer];
    });
  }
}

/**
 * Call when objects are detected in the given sample buffer. Caller must make sure that this method
 * runs on the main thread.
 *
 * @param objects The list of objects that is detected in the given sample buffer.
 * @param sampleBuffer The given sampleBuffer.
 */
- (void)onDetectedObjects:(nullable NSArray<MLKObject *> *)objects
           inSampleBuffer:(MLKSampleBuffer *)sampleBuffer {
  NSAssert([NSThread.currentThread isEqual:NSThread.mainThread],
           @"onDetectedObjects:inSampleBuffer is not running on the main thread");

  if (objects.count < 1) {
    [self startToDetect];
    return;
  }

  MLKObject *object = [objects firstObject];
  self.lastDetectedObject = object;
  if (object.trackingID.stringValue.length == 0) {
    [self startToDetect];
    return;
  }

  CGSize sampleBufferSize = [self sampleBufferSize:sampleBuffer.data];
  BOOL isFocusInsideObjectFrame = CGRectContainsPoint(
      object.frame, CGPointMake(sampleBufferSize.width / 2, sampleBufferSize.height / 2));
  if (!isFocusInsideObjectFrame) {
    [self startToDetect];
    return;
  }

  switch (self.status) {
    case MLKODTStatus_Detecting: {
      [self cleanUpOverlayView];
      CGRect convertedRect = [self convertedRectOfObjectFrame:object.frame
                                      inSampleBufferFrameSize:sampleBufferSize];
      [self.overlayView showBoxInRect:convertedRect];
      [self startToConfirmObject:object sampleBuffer:sampleBuffer];
      break;
    }
    case MLKODTStatus_Confirming: {
      CGRect convertedRect = [self convertedRectOfObjectFrame:object.frame
                                      inSampleBufferFrameSize:sampleBufferSize];
      [self.overlayView showBoxInRect:convertedRect];
      self.lastDetectedObject = object;
      self.lastDetectedSampleBuffer = sampleBuffer;
      break;
    }
    case MLKODTStatus_Searching:
    case MLKODTStatus_Searched:
    case MLKODTStatus_NotStarted:
      break;
  }
}

#pragma mark - Status Handling

/**
 * Called when it needs to start the detection. Caller must make sure that this method runs on the
 * main thread.
 */
- (void)startToDetect {
  NSAssert([NSThread.currentThread isEqual:NSThread.mainThread],
           @"startToDetect is not running on the main thread");

  self.status = MLKODTStatus_Detecting;
  [self cleanUpOverlayView];
  [self clearLastDetectedObject];
  __weak typeof(self) weakSelf = self;
  dispatch_async(self.sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    if (strongSelf == nil) {
      return;
    }
#if !TARGET_IPHONE_SIMULATOR
    if (![strongSelf.session isRunning]) {
      [strongSelf.session startRunning];
    }
#endif
  });
}

/**
 * Starts a search with last detected object. Caller must make sure that this method runs on the
 * main thread.
 */
- (void)startToSearch {
  NSAssert(
      [NSThread.currentThread isEqual:NSThread.mainThread],
      @"startToSearchWithImage:originalWidth:originalHeight is not running on the main thread");

  self.status = MLKODTStatus_Searching;

  CGSize originalSampleBufferSize = [self sampleBufferSize:self.lastDetectedSampleBuffer.data];

  UIImage *croppedImage = [self croppedImageFromSampleBuffer:self.lastDetectedSampleBuffer.data
                                                      inRect:self.lastDetectedObject.frame];
  CGRect convertedRect = [self convertedRectOfObjectFrame:self.lastDetectedObject.frame
                                  inSampleBufferFrameSize:originalSampleBufferSize];
  self.thumbnailWidthHeightRatio =
      self.lastDetectedObject.frame.size.height / self.lastDetectedObject.frame.size.width;
  self.overlayView.image.image = croppedImage;
  [self cleanUpOverlayView];
  [self.overlayView showImageInRect:convertedRect alpha:1];

  switch (self.detectorType) {
    case MLKDetectorTypeODTDefaultModel: {
      MLKProductSearchRequest *request = [[MLKProductSearchRequest alloc] initWithUIImage:croppedImage];
      GTMSessionFetcher *fetcher = [self.fetcherService fetcherWithRequest:request];
      if (request.URL.absoluteString.length == 0) {
        [self processSearchResponse:nil
                           forImage:croppedImage
                      originalWidth:originalSampleBufferSize.width
                     originalHeight:originalSampleBufferSize.height
                    useFakeResponse:YES];
        [self clearLastDetectedObject];
        return;
      }
      __weak typeof(self) weakSelf = self;
      dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH, 0), ^{
        [fetcher beginFetchWithCompletionHandler:^(NSData *_Nullable data, NSError *_Nullable error) {
          __strong typeof(weakSelf) strongSelf = weakSelf;
          if (strongSelf == nil) {
            return;
          }
          if (error) {
            NSLog(@"error in fetching: %@", error);
            [strongSelf clearLastDetectedObject];
            return;
          }
          dispatch_async(dispatch_get_main_queue(), ^{
            __strong typeof(weakSelf) strongSelf = weakSelf;
            if (strongSelf == nil) {
              return;
            }
            [strongSelf processSearchResponse:data
                                     forImage:croppedImage
                                originalWidth:originalSampleBufferSize.width
                               originalHeight:originalSampleBufferSize.height
                              useFakeResponse:NO];
            [strongSelf clearLastDetectedObject];
          });
        }];
      });
      break;
    }
    case MLKDetectorTypeODTBirdModel: {
      NSString *birdName = self.lastDetectedObject.labels[0].text;
      NSString *encodedKeyword = [self encodedKeyword:birdName];
      NSString *urlWithKeyword = [NSString stringWithFormat:kWikiSearchURLPattern,
                                  encodedKeyword];
      NSMutableURLRequest *request =
        [[NSMutableURLRequest alloc] initWithURL: [NSURL URLWithString:urlWithKeyword]];
      static GTMSessionFetcherService * gFetcherService;
      static dispatch_once_t onceToken;
      dispatch_once(&onceToken, ^{
        gFetcherService = [[GTMSessionFetcherService alloc] init];
      });
      GTMSessionFetcher *fetcher = [gFetcherService fetcherWithRequest:request];
      __weak typeof(self) weakSelf = self;
      __block NSString *abstract = nil;
      dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH, 0), ^{
        [fetcher beginFetchWithCompletionHandler:^(NSData *_Nullable data, NSError *_Nullable error) {
          __strong typeof(weakSelf) strongSelf = weakSelf;
          if (strongSelf == nil) {
            return;
          }
          if (error) {
            NSLog(@"error in fetching: %@", error);
            abstract = @"No result";
            return;
          }
          [strongSelf processSearchResponse:data
                                   forImage:croppedImage
                              originalWidth:originalSampleBufferSize.width
                             originalHeight:originalSampleBufferSize.height
                            useFakeResponse:NO];
          [strongSelf clearLastDetectedObject];
        }];
      });
      break;
    }
    default: {
      break;
  }
  }
}

/**
 * Processes search response from server. Caller must make sure that this method runs on the main
 * thread.
 *
 * @param response The raw response from server on product search request.
 * @param image The image of the detected object that is to be searched.
 * @param width The width of the original sample buffer.
 * @param height The height of the original sample buffer.
 * @param useFakeResponse Whether to use fake response or send a product search request to the
 * server.
 */
- (void)processSearchResponse:(nullable NSData *)response
                     forImage:(UIImage *)image
                originalWidth:(size_t)width
               originalHeight:(size_t)height
              useFakeResponse:(BOOL)useFakeResponse {
  NSAssert([NSThread.currentThread isEqual:NSThread.mainThread],
           @"processSearchRespose:forImage:originalWidth:originalHeight is not running on the main "
           @"thread");
  self.status = MLKODTStatus_Searched;
  NSArray<MLKResult *> *results;
  if (useFakeResponse) {
    results = [self fakeSearchResults];
  } else {
    results = [MLKResult resultsFromResponse:response forDetectortype: self.detectorType];
  }
  MLKResultListViewController *resultsViewController =
      [[MLKResultListViewController alloc] initWithResults:results];

  MDCBottomSheetController *bottomSheet =
      [[MDCBottomSheetController alloc] initWithContentViewController:resultsViewController];
  bottomSheet.trackingScrollView = resultsViewController.collectionView;

  bottomSheet.scrimColor = UIColor.clearColor;
  bottomSheet.dismissOnBackgroundTap = YES;
  bottomSheet.delegate = self;

  CGFloat contentHeight =
      resultsViewController.collectionViewLayout.collectionViewContentSize.height;
  CGFloat screenHeight = self.view.frame.size.height;

  UIEdgeInsets safeInsets = [MLKUIUtilities safeAreaInsets];

  CGFloat toOffsetY = contentHeight > screenHeight / 2.0f
                          ? screenHeight / 2.0f - safeInsets.bottom
                          : screenHeight - contentHeight - safeInsets.top - safeInsets.bottom;
  self.bottomSheetTargetOffsetY = toOffsetY;

  CGRect toFrame =
      CGRectMake(kThumbnailPaddingAround,                                                  // X
                 toOffsetY - kThumbnailbottomSheetTargetHeight - kThumbnailPaddingAround,  // Y
                 self.thumbnailWidthHeightRatio * kThumbnailbottomSheetTargetHeight,       // Width
                 kThumbnailbottomSheetTargetHeight);                                       // Height

  [UIView animateWithDuration:kBottomSheetAnimationDurationInSec
                   animations:^{
                     [self.overlayView showImageInRect:toFrame alpha:1];
                   }];
  [self presentViewController:bottomSheet animated:YES completion:nil];
}

/**
 * Calculates the ratio of current value based on `from` and `to` value.
 *
 * @param currentValue The current value.
 * @param fromValue The start point of the range.
 * @param toValue The end point of the range.
 * @return Position of current value in the whole range. It falls into [0,1].
 */
- (CGFloat)ratioOfCurrentValue:(CGFloat)currentValue from:(CGFloat)fromValue to:(CGFloat)toValue {
  CGFloat ratio = (currentValue - fromValue) / (toValue - fromValue);
  ratio = MIN(ratio, 1);
  return MAX(ratio, 0);
}

/**
 * Called to confirm on the given object.Caller must make sure that this method runs on the main
 * thread.
 *
 * @param object The object to confirm. It will be regarded as the same object if its objectID stays
 *     the same during this stage.
 * @param sampleBuffer The original sample buffer that this object was detected in.
 */
- (void)startToConfirmObject:(MLKObject *)object sampleBuffer:(MLKSampleBuffer *)sampleBuffer {
  NSAssert([NSThread.currentThread isEqual:NSThread.mainThread],
           @"startToConfirmObject:sampleBuffer is not running on the main thread");
  [self clearLastDetectedObject];
  NSTimer *timer = [NSTimer scheduledTimerWithTimeInterval:kconfirmingDurationInSec
                                                    target:self
                                                  selector:@selector(onTimerMLKed)
                                                  userInfo:nil
                                                   repeats:NO];
  [self.timers addObject:timer];

  self.status = MLKODTStatus_Confirming;
  self.lastDetectedObject = object;
  self.lastDetectedSampleBuffer = sampleBuffer;
}

/** Called when timer is up and the detected object is confirmed. */
- (void)onTimerMLKed {
  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    if (strongSelf == nil) {
      return;
    }
    switch (strongSelf.status) {
      case MLKODTStatus_Confirming: {
#if !TARGET_IPHONE_SIMULATOR
        dispatch_async(strongSelf.sessionQueue, ^{
          [weakSelf.session stopRunning];
        });
#endif
        [strongSelf startToSearch];
        break;
      }
      case MLKODTStatus_Detecting:
      case MLKODTStatus_NotStarted:
      case MLKODTStatus_Searched:
      case MLKODTStatus_Searching:
        break;
    }
  });
}

/**
 * Overrides setter for `status` property. It also shows corresponding indicator/message with the
 * status change. Caller must make sure that this method runs on the main thread.
 *
 * @param status The new status.
 */
- (void)setStatus:(MLKODTStatus)status {
  NSAssert([NSThread.currentThread isEqual:NSThread.mainThread],
           @"setStatus is not running on the main thread");

  if (_status == status) {
    return;
  }
  _status = status;

  switch (status) {
    case MLKODTStatus_NotStarted: {
      [self hideMessage];
      [self.confirmingSpinner setHidden:YES];
      [self.detectingReticle setHidden:YES];
      [self showSearchingIndicator:NO];
      break;
    }
    case MLKODTStatus_Detecting: {
      [self showMessage:[self detectingStageMessage]];
      [self.detectingReticle setHidden:NO];
      [self.confirmingSpinner setHidden:YES];
      [self showSearchingIndicator:NO];
      break;
    }
    case MLKODTStatus_Confirming: {
      [self showMessage:kConfirmingStageMessage];
      [self.detectingReticle setHidden:YES];
      [self.confirmingSpinner setHidden:NO];
      [self showSearchingIndicator:NO];
      break;
    }
    case MLKODTStatus_Searching: {
      [self showMessage:kSearchingMessage];
      [self.confirmingSpinner setHidden:YES];
      [self.detectingReticle setHidden:YES];
      [self showSearchingIndicator:YES];
      break;
    }
    case MLKODTStatus_Searched: {
      [self hideMessage];
      [self.confirmingSpinner setHidden:YES];
      [self.detectingReticle setHidden:YES];
      [self showSearchingIndicator:NO];
      break;
    }
  }
}

#pragma mark - Util methods

/**
 * Returns size of given `CMSampleBufferRef`.
 *
 * @param sampleBuffer The `CMSampleBufferRef` to get size from.
 * @return The size of the given `CMSampleBufferRef`. It describes its width and height.
 */
- (CGSize)sampleBufferSize:(CMSampleBufferRef)sampleBuffer {
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
  size_t imageWidth = CVPixelBufferGetWidth(imageBuffer);
  size_t imageHeight = CVPixelBufferGetHeight(imageBuffer);
  return CGSizeMake(imageWidth, imageHeight);
}

/**
 * Converts given frame of a detected object to a `CGRect` in coordinate system of current view.
 *
 * @param frame The frame of detected object.
 * @param size The frame size of the sample buffer.
 * @return Converted rect.
 */
- (CGRect)convertedRectOfObjectFrame:(CGRect)frame inSampleBufferFrameSize:(CGSize)size {
  CGRect normalizedRect = CGRectMake(frame.origin.x / size.width,       // X
                                     frame.origin.y / size.height,      // Y
                                     frame.size.width / size.width,     // Width
                                     frame.size.height / size.height);  // Height
  CGRect convertedRect = [self.previewLayer rectForMetadataOutputRectOfInterest:normalizedRect];
  return CGRectStandardize(convertedRect);
}

/**
 * Crops given `CMSampleBufferRef` with given rect.
 *
 * @param sampleBuffer The sample buffer to be cropped.
 * @param rect The rect of the area to be cropped.
 * @return Returns cropped image to the given rect.
 */
- (UIImage *)croppedImageFromSampleBuffer:(CMSampleBufferRef)sampleBuffer inRect:(CGRect)rect {
  CMSampleBufferRef croppedSampleBuffer = [MLKImageUtilities croppedSampleBuffer:sampleBuffer
                                                                        withRect:rect];
  UIImage *croppedImage = [MLKImageUtilities imageFromSampleBuffer:croppedSampleBuffer];
  return [MLKUIUtilities orientedUpImageFromImage:croppedImage];
}

/**
 * Shows/Hides searching indicator.
 *
 * @param isVisible Whether to show/hide searching indicator. YES to show, NO to hide.
 */
- (void)showSearchingIndicator:(BOOL)isVisible {
  if (isVisible) {
    [self.searchingIndicator setHidden:NO];
    [self.searchingIndicator startAnimating];
  } else {
    [self.searchingIndicator setHidden:YES];
    [self.searchingIndicator stopAnimating];
  }
}

- (void)showMessage:(NSString *)message {
  if ([self.messageView.titleLabel.text isEqual:message]) {
    return;
  }
  self.messageView.titleLabel.text = message;
  [self.messageView sizeToFit];
  CGSize size = [self.messageView sizeThatFits:self.view.frame.size];
  CGFloat startX = (self.view.frame.size.width - size.width) / 2.0f;
  CGFloat startY = self.view.frame.size.height - kChipBottomPadding - size.height;
  self.messageView.frame = CGRectMake(startX, startY, size.width, size.height);

  if (self.messageView.alpha != kTransparentAlpha) {
    return;
  }
  self.messageView.alpha = kTransparentAlpha;
  [UIView animateWithDuration:kChipFadeInDuration
                   animations:^{
                     self.messageView.alpha = kOpaqueAlpha;
                   }];

  CGPoint messageCenter =
      CGPointMake(CGRectGetMidX(self.messageView.frame), CGRectGetMidY(self.messageView.frame));

  self.messageView.transform =
      CGAffineTransformScale(self.messageView.transform, kChipScaleFromRatio, kChipScaleFromRatio);
  [self.messageView sizeToFit];

  [UIView animateWithDuration:kChipScaleDuration
                   animations:^{
                     self.messageView.center = messageCenter;
                     self.messageView.transform = CGAffineTransformScale(
                         self.messageView.transform, kChipScaleToRatio, kChipScaleToRatio);
                   }];
}

- (void)hideMessage {
  [UIView animateWithDuration:kChipFadeInDuration
                   animations:^{
                     self.messageView.alpha = kTransparentAlpha;
                   }];
}

/**
 * Generates fake search results for demo when there are no backend server hooked up.
 */
- (NSArray<MLKResult *> *)fakeSearchResults {
  switch (self.detectorType) {
    case MLKDetectorTypeODTDefaultModel: {
      NSMutableArray<MLKResult *> *fakeSearchResults = [NSMutableArray array];
       for (NSInteger index = 0; index < kFakeSearchResultCount; index++) {
         MLKResult *result = [[MLKResult alloc] init];
         result.title = [NSString stringWithFormat:kFakeResultTitleFormat, index + 1];
         result.details = kFakeProductTypeName;
         result.priceFullText = kFakeProductPriceText;
         result.itemNumber = kFakeProductItemNumberText;
         [fakeSearchResults addObject:result];
       }
       return fakeSearchResults;
    }
    case MLKDetectorTypeODTBirdModel: {
      NSMutableArray<MLKResult *> *fakeSearchResults = [NSMutableArray array];
      for (NSInteger index = 0; index < self.lastDetectedObject.labels.count; index++) {
        MLKObjectLabel *label = self.lastDetectedObject.labels[index];
        MLKResult *result = [[MLKResult alloc] init];
        result.title = label.text;
        NSCharacterSet *set = [NSCharacterSet characterSetWithCharactersInString:@" "];
        NSArray<NSString *>*keywords = [label.text componentsSeparatedByCharactersInSet:set];
        result.details = kDescriptionPlaceHoler;
        [fakeSearchResults addObject:result];
      }
      return fakeSearchResults;
    }
    default: {
      return nil;
    }
  }
}

/** Converts the given keyword to a encoded one so as to be used in requests. */
- (nullable NSString *)encodedKeyword:(NSString *)birdName{
  if (birdName.length == 0 || [birdName isEqual:@"N/A"] || [birdName isEqual: @"n/a"]) {
    return nil;
  }
  NSCharacterSet *set = [NSCharacterSet characterSetWithCharactersInString:@"("];
  NSArray<NSString *>*keywords = [birdName componentsSeparatedByCharactersInSet:set];
    NSUInteger location = 0;
    unichar charBuffer[[keywords[0] length]];
    [keywords[0] getCharacters:charBuffer];
    NSUInteger i = 0;
    for(i = [keywords[0] length]; i >0; i--) {
        NSCharacterSet* charSet = [NSCharacterSet whitespaceCharacterSet];
        if(![charSet characterIsMember:charBuffer[i - 1]]) {
            break;
        }
    }
    NSString *strippedKeyword = [keywords[0] substringWithRange:
                                 NSMakeRange(location, i  - location)];
    return [strippedKeyword stringByAddingPercentEncodingWithAllowedCharacters:
      [NSCharacterSet  alphanumericCharacterSet]];
}

@end

NS_ASSUME_NONNULL_END
