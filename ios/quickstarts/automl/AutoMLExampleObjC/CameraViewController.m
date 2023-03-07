//
//  Copyright (c) 2018 Google Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#import "CameraViewController.h"
#import <AVFoundation/AVFoundation.h>
#import <CoreImage/CoreImage.h>
#import <CoreVideo/CoreVideo.h>
#import "UIUtilities.h"

@import MLKit;

NS_ASSUME_NONNULL_BEGIN

static NSString *const alertControllerTitle = @"Vision Detectors";
static NSString *const alertControllerMessage = @"Select a detector";
static NSString *const cancelActionTitleText = @"Cancel";
static NSString *const videoDataOutputQueueLabel = @"com.google.mlkit.automl.VideoDataOutputQueue";
static NSString *const sessionQueueLabel = @"com.google.mlkit.automl.SessionQueue";
static NSString *const noResultsMessage = @"No Results";

/** Name of the remote AutoML model. */
static NSString *const MLKRemoteAutoMLModelName = @"remote_automl_model";

/** Filename of AutoML local model manifest in the main resource bundle. */
static NSString *const MLKAutoMLLocalModelManifestFilename = @"automl_labeler_manifest";

/** File type of AutoML local model manifest in the main resource bundle. */
static NSString *const MLKAutoMLManifestFileType = @"json";

static const float kLabelConfidenceThreshold = 0.75f;
static const CGFloat kImageScale = 1.0;
static const CGFloat kLayoutPadding = 10.0;
static const CGFloat kResultsLabelHeight = 200.0;
static const int kResultsLabelLines = 5;

@interface CameraViewController () <AVCaptureVideoDataOutputSampleBufferDelegate>

typedef NS_ENUM(NSInteger, Detector) {
  /** AutoML image label detector. */
  DetectorImageLabelsAutoML,
  /** AutoML object detector, single, only tracking. */
  DetectorObjectsAutoMLSingleNoClassifier,
  /** AutoML object detector, single, with classification. */
  DetectorObjectsAutoMLSingleWithClassifier,
  /** AutoML object detector, multiple, only tracking. */
  DetectorObjectsAutoMLMultipleNoClassifier,
  /** AutoML object detector, multiple, with classification. */
  DetectorObjectsAutoMLMultipleWithClassifier,
};

@property(nonatomic) NSArray *detectors;
@property(nonatomic) Detector currentDetector;
@property(nonatomic) bool isUsingFrontCamera;
@property(nonatomic, nonnull) AVCaptureVideoPreviewLayer *previewLayer;
@property(nonatomic) AVCaptureSession *captureSession;
@property(nonatomic) dispatch_queue_t sessionQueue;
@property(nonatomic) UIView *annotationOverlayView;
@property(nonatomic) UIImageView *previewOverlayView;
@property(weak, nonatomic) IBOutlet UIView *cameraView;
@property(nonatomic) CMSampleBufferRef lastFrame;
@property(nonatomic) MLKModelManager *modelManager;

@property(strong, nonatomic) IBOutlet UIProgressView *downloadProgressView;

@end

@implementation CameraViewController

- (NSString *)stringForDetector:(Detector)detector {
  switch (detector) {
    case DetectorImageLabelsAutoML:
      return @"AutoML Image Labeling";
    case DetectorObjectsAutoMLSingleNoClassifier:
      return @"AutoML ODT, single, no labeling";
    case DetectorObjectsAutoMLSingleWithClassifier:
      return @"AutoML ODT, single, labeling";
    case DetectorObjectsAutoMLMultipleNoClassifier:
      return @"AutoML ODT, multiple, no labeling";
    case DetectorObjectsAutoMLMultipleWithClassifier:
      return @"AutoML ODT, multiple, labeling";
  }
}

- (void)viewDidLoad {
  [super viewDidLoad];
  self.detectors = @[
    @(DetectorImageLabelsAutoML),
    @(DetectorObjectsAutoMLSingleNoClassifier),
    @(DetectorObjectsAutoMLSingleWithClassifier),
    @(DetectorObjectsAutoMLMultipleNoClassifier),
    @(DetectorObjectsAutoMLMultipleWithClassifier),
  ];
  self.currentDetector = DetectorImageLabelsAutoML;
  self.isUsingFrontCamera = YES;
  self.captureSession = [[AVCaptureSession alloc] init];
  self.sessionQueue = dispatch_queue_create(sessionQueueLabel.UTF8String, nil);
  self.modelManager = [MLKModelManager modelManager];
  self.previewOverlayView = [[UIImageView alloc] initWithFrame:CGRectZero];
  self.previewOverlayView.contentMode = UIViewContentModeScaleAspectFill;
  self.previewOverlayView.translatesAutoresizingMaskIntoConstraints = NO;
  self.annotationOverlayView = [[UIView alloc] initWithFrame:CGRectZero];
  self.annotationOverlayView.translatesAutoresizingMaskIntoConstraints = NO;

  self.previewLayer = [AVCaptureVideoPreviewLayer layerWithSession:self.captureSession];
  [self setUpPreviewOverlayView];
  [self setUpAnnotationOverlayView];
  [self setUpCaptureSessionOutput];
  [self setUpCaptureSessionInput];
}

- (void)viewDidAppear:(BOOL)animated {
  [super viewDidAppear:animated];
  [self startSession];
  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(remoteModelDownloadDidSucceed:)
                                             name:MLKModelDownloadDidSucceedNotification
                                           object:nil];
  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(remoteModelDownloadDidFail:)
                                             name:MLKModelDownloadDidFailNotification
                                           object:nil];
}

- (void)viewDidDisappear:(BOOL)animated {
  [super viewDidDisappear:animated];
  [self stopSession];
  // We wouldn't have needed to remove the observers if iOS 9.0+ had cleaned up the observer "the
  // next time it would have posted to it" as documented here:
  // https://developer.apple.com/documentation/foundation/nsnotificationcenter/1413994-removeobserver
  [NSNotificationCenter.defaultCenter removeObserver:self
                                                name:MLKModelDownloadDidSucceedNotification
                                              object:nil];
  [NSNotificationCenter.defaultCenter removeObserver:self
                                                name:MLKModelDownloadDidFailNotification
                                              object:nil];
}

- (void)viewDidLayoutSubviews {
  [super viewDidLayoutSubviews];
  self.previewLayer.frame = self.cameraView.frame;
}

- (IBAction)selectDetector:(id)sender {
  [self presentDetectorsAlertController];
}

- (IBAction)switchCamera:(id)sender {
  self.isUsingFrontCamera = !self.isUsingFrontCamera;
  [self removeDetectionAnnotations];
  [self setUpCaptureSessionInput];
}

#pragma mark - AutoML Image Labeling

/**
 * Detects labels on the specified image using AutoML-trained models via Custom Image Labeling API.
 *
 * @param image The input image.
 */
- (void)detectImageLabelsInImage:(MLKVisionImage *)image {
  [self requestAutoMLRemoteModelIfNeeded];

  // [START config_automl_label]
  MLKCommonImageLabelerOptions *options;
  MLKCustomRemoteModel *remoteModel = (MLKCustomRemoteModel *)[self remoteModel];
  if ([self.modelManager isModelDownloaded:remoteModel]) {
    NSLog(@"Use AutoML remote model.");
    options = [[MLKCustomImageLabelerOptions alloc] initWithRemoteModel:remoteModel];
  } else {
    NSLog(@"Use AutoML local model.");
    NSString *localModelFilePath =
        [[NSBundle mainBundle] pathForResource:MLKAutoMLLocalModelManifestFilename
                                        ofType:MLKAutoMLManifestFileType];
    if (localModelFilePath == nil) {
      NSLog(@"Failed to find AutoML local model manifest file: %@",
            MLKAutoMLLocalModelManifestFilename);
      return;
    }
    MLKLocalModel *localModel = [[MLKLocalModel alloc] initWithManifestPath:localModelFilePath];
    options = [[MLKCustomImageLabelerOptions alloc] initWithLocalModel:localModel];
  }
  options.confidenceThreshold = @(kLabelConfidenceThreshold);
  // [END config_automl_label]

  // [START init_automl_label]
  MLKImageLabeler *autoMLImageLabeler = [MLKImageLabeler imageLabelerWithOptions:options];
  // [END init_automl_label]

  // [START detect_automl_label]
  NSError *error;
  NSArray<MLKImageLabel *> *labels = [autoMLImageLabeler resultsInImage:image error:&error];

  // [START_EXCLUDE]
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf updatePreviewOverlayView];
    [strongSelf removeDetectionAnnotations];
    // [END_EXCLUDE]
    if (error != nil) {
      // [START_EXCLUDE]
      NSLog(@"Failed to detect labels with error: %@.", error.localizedDescription);
      // [END_EXCLUDE]
      return;
    }

    if (labels.count == 0) {
      return;
    }

    // [START_EXCLUDE]
    CGRect annotationFrame = strongSelf.annotationOverlayView.frame;
    CGRect resultsRect =
        CGRectMake(annotationFrame.origin.x + kLayoutPadding,
                   annotationFrame.size.height - kLayoutPadding - kResultsLabelHeight,
                   annotationFrame.size.width - 2 * kLayoutPadding, kResultsLabelHeight);
    UILabel *resultsLabel = [[UILabel alloc] initWithFrame:resultsRect];
    resultsLabel.textColor = UIColor.yellowColor;
    NSMutableArray *labelStrings = [NSMutableArray arrayWithCapacity:labels.count];
    for (MLKImageLabel *label in labels) {
      [labelStrings addObject:[NSString stringWithFormat:@"Label: %@, Confidence: %f", label.text,
                                                         label.confidence]];
    }
    resultsLabel.text = [labelStrings componentsJoinedByString:@"\n"];
    resultsLabel.adjustsFontSizeToFitWidth = YES;
    resultsLabel.numberOfLines = kResultsLabelLines;
    [strongSelf.annotationOverlayView addSubview:resultsLabel];
  });
  // [END_EXCLUDE]
  // [END detect_automl_label]
}

- (void)detectObjectsInImage:(MLKVisionImage *)image
                          width:(CGFloat)width
                         height:(CGFloat)height
     shouldEnableClassification:(BOOL)shouldEnableClassification
    shouldEnableMultipleObjects:(BOOL)shouldEnableMultipleObjects {
  [self requestAutoMLRemoteModelIfNeeded];

  MLKCustomRemoteModel *remoteModel = (MLKCustomRemoteModel *)[self remoteModel];
  MLKCustomObjectDetectorOptions *options;

  if ([self.modelManager isModelDownloaded:remoteModel]) {
    NSLog(@"Use AutoML remote model.");
    options = [[MLKCustomObjectDetectorOptions alloc] initWithRemoteModel:remoteModel];
  } else {
    NSLog(@"Use AutoML local model.");
    NSString *localModelFilePath =
        [[NSBundle mainBundle] pathForResource:MLKAutoMLLocalModelManifestFilename
                                        ofType:MLKAutoMLManifestFileType];
    if (localModelFilePath == nil) {
      NSLog(@"Failed to find AutoML local model manifest file: %@",
            MLKAutoMLLocalModelManifestFilename);
      return;
    }
    MLKLocalModel *localModel = [[MLKLocalModel alloc] initWithManifestPath:localModelFilePath];
    options = [[MLKCustomObjectDetectorOptions alloc] initWithLocalModel:localModel];
  }

  options.shouldEnableClassification = shouldEnableClassification;
  options.shouldEnableMultipleObjects = shouldEnableMultipleObjects;
  options.detectorMode = MLKObjectDetectorModeStream;
  // Due to the UI space, We will only display one label per detected object.
  options.maxPerObjectLabelCount = 1;

  MLKObjectDetector *autoMLObjectDetector = [MLKObjectDetector objectDetectorWithOptions:options];
  NSError *error;
  NSArray<MLKObject *> *objects = [autoMLObjectDetector resultsInImage:image error:&error];
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf updatePreviewOverlayView];
    [strongSelf removeDetectionAnnotations];
    if (error != nil) {
      NSLog(@"Failed to detect object with error: %@", error.localizedDescription);
      return;
    }
    if (objects.count == 0) {
      NSLog(@"Object detector returned no results.");
      return;
    }
    for (MLKObject *object in objects) {
      NSMutableString *description = [[NSMutableString alloc] init];
      CGRect normalizedRect =
          CGRectMake(object.frame.origin.x / width, object.frame.origin.y / height,
                     object.frame.size.width / width, object.frame.size.height / height);
      CGRect standardizedRect = CGRectStandardize(
          [strongSelf.previewLayer rectForMetadataOutputRectOfInterest:normalizedRect]);
      [UIUtilities addRectangle:standardizedRect
                         toView:strongSelf.annotationOverlayView
                          color:UIColor.greenColor];
      UILabel *label = [[UILabel alloc] initWithFrame:standardizedRect];
      if (object.trackingID != nil) {
        [description appendFormat:@"Object ID: %@\n", object.trackingID];
      }

      [description appendString:@"Labels:\n"];
      int i = 0;
      for (MLKObjectLabel *l in object.labels) {
        NSString *labelString = [NSString stringWithFormat:@"Label %d: %@, %f, %lu\n", i++, l.text,
                                                           l.confidence, (unsigned long)l.index];
        [description appendString:labelString];
      }
      label.text = description;
      label.numberOfLines = 0;
      label.adjustsFontSizeToFitWidth = YES;
      [strongSelf.annotationOverlayView addSubview:label];
    }
  });
}

- (void)requestAutoMLRemoteModelIfNeeded {
  MLKRemoteModel *remoteModel = [self remoteModel];
  if ([self.modelManager isModelDownloaded:remoteModel]) {
    return;
  }
  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    strongSelf.downloadProgressView.hidden = NO;
    MLKModelDownloadConditions *conditions =
        [[MLKModelDownloadConditions alloc] initWithAllowsCellularAccess:YES
                                             allowsBackgroundDownloading:YES];
    strongSelf.downloadProgressView.observedProgress =
        [strongSelf.modelManager downloadModel:remoteModel conditions:conditions];
    NSLog(@"Start downloading AutoML remote model.");
  });
}

#pragma mark - Notifications

- (void)remoteModelDownloadDidSucceed:(NSNotification *)notification {
  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    strongSelf.downloadProgressView.hidden = YES;
    MLKRemoteModel *remotemodel = notification.userInfo[MLKModelDownloadUserInfoKeyRemoteModel];
    if (remotemodel == nil) {
      NSLog(@"MLKitModelDownloadDidSucceed notification posted without a RemoteModel instance.");
      return;
    }
    NSLog(@"Successfully downloaded the remote model with name: %@. The model is ready for "
          @"detection.",
          remotemodel.name);
  });
}

- (void)remoteModelDownloadDidFail:(NSNotification *)notification {
  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    strongSelf.downloadProgressView.hidden = YES;
    MLKRemoteModel *remoteModel = notification.userInfo[MLKModelDownloadUserInfoKeyRemoteModel];
    NSError *error = notification.userInfo[MLKModelDownloadUserInfoKeyError];
    if (error == nil) {
      NSLog(@"MLKitModelDownloadDidFail notification posted without a RemoteModel instance or "
            @"error.");
      return;
    }
    NSLog(@"Failed to download the remote model with name: %@, error: %@.", remoteModel,
          error.localizedDescription);
  });
}

#pragma mark - Private

- (MLKRemoteModel *)remoteModel {
  MLKFirebaseModelSource *firebaseModelSource =
      [[MLKFirebaseModelSource alloc] initWithName:MLKRemoteAutoMLModelName];
  return [[MLKCustomRemoteModel alloc] initWithRemoteModelSource:firebaseModelSource];
}

- (void)setUpCaptureSessionOutput {
  __weak typeof(self) weakSelf = self;
  dispatch_async(self.sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf.captureSession beginConfiguration];
    // When performing latency tests to determine ideal capture settings,
    // run the app in 'release' mode to get accurate performance metrics
    strongSelf.captureSession.sessionPreset = AVCaptureSessionPresetMedium;

    AVCaptureVideoDataOutput *output = [[AVCaptureVideoDataOutput alloc] init];
    output.videoSettings = @{
      (id)
      kCVPixelBufferPixelFormatTypeKey : [NSNumber numberWithUnsignedInt:kCVPixelFormatType_32BGRA]
    };
    dispatch_queue_t outputQueue = dispatch_queue_create(videoDataOutputQueueLabel.UTF8String, nil);
    [output setSampleBufferDelegate:strongSelf queue:outputQueue];
    if ([strongSelf.captureSession canAddOutput:output]) {
      [strongSelf.captureSession addOutput:output];
      [strongSelf.captureSession commitConfiguration];
    } else {
      NSLog(@"%@", @"Failed to add capture session output.");
    }
  });
}

- (void)setUpCaptureSessionInput {
  __weak typeof(self) weakSelf = self;
  dispatch_async(self.sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    AVCaptureDevicePosition cameraPosition =
        strongSelf.isUsingFrontCamera ? AVCaptureDevicePositionFront : AVCaptureDevicePositionBack;
    AVCaptureDevice *device = [strongSelf captureDeviceForPosition:cameraPosition];
    if (device) {
      [strongSelf.captureSession beginConfiguration];
      NSArray<AVCaptureInput *> *currentInputs = strongSelf.captureSession.inputs;
      for (AVCaptureInput *input in currentInputs) {
        [strongSelf.captureSession removeInput:input];
      }
      NSError *error;
      AVCaptureDeviceInput *input = [AVCaptureDeviceInput deviceInputWithDevice:device
                                                                          error:&error];
      if (error) {
        NSLog(@"Failed to create capture device input: %@", error.localizedDescription);
        return;
      } else {
        if ([strongSelf.captureSession canAddInput:input]) {
          [strongSelf.captureSession addInput:input];
        } else {
          NSLog(@"%@", @"Failed to add capture session input.");
        }
      }
      [strongSelf.captureSession commitConfiguration];
    } else {
      NSLog(@"Failed to get capture device for camera position: %ld", cameraPosition);
    }
  });
}

- (void)startSession {
  __weak typeof(self) weakSelf = self;
  dispatch_async(self.sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf.captureSession startRunning];
  });
}

- (void)stopSession {
  __weak typeof(self) weakSelf = self;
  dispatch_async(self.sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf.captureSession stopRunning];
  });
}

- (void)setUpPreviewOverlayView {
  [self.cameraView addSubview:self.previewOverlayView];
  [NSLayoutConstraint activateConstraints:@[
    [self.previewOverlayView.centerYAnchor constraintEqualToAnchor:self.cameraView.centerYAnchor],
    [self.previewOverlayView.centerXAnchor constraintEqualToAnchor:self.cameraView.centerXAnchor],
    [self.previewOverlayView.leadingAnchor constraintEqualToAnchor:self.cameraView.leadingAnchor],
    [self.previewOverlayView.trailingAnchor constraintEqualToAnchor:self.cameraView.trailingAnchor]
  ]];
}
- (void)setUpAnnotationOverlayView {
  [self.cameraView addSubview:self.annotationOverlayView];
  [NSLayoutConstraint activateConstraints:@[
    [self.annotationOverlayView.topAnchor constraintEqualToAnchor:self.cameraView.topAnchor],
    [self.annotationOverlayView.leadingAnchor
        constraintEqualToAnchor:self.cameraView.leadingAnchor],
    [self.annotationOverlayView.trailingAnchor
        constraintEqualToAnchor:self.cameraView.trailingAnchor],
    [self.annotationOverlayView.bottomAnchor constraintEqualToAnchor:self.cameraView.bottomAnchor]
  ]];
}

- (AVCaptureDevice *)captureDeviceForPosition:(AVCaptureDevicePosition)position {
  if (@available(iOS 10, *)) {
    AVCaptureDeviceDiscoverySession *discoverySession = [AVCaptureDeviceDiscoverySession
        discoverySessionWithDeviceTypes:@[ AVCaptureDeviceTypeBuiltInWideAngleCamera ]
                              mediaType:AVMediaTypeVideo
                               position:AVCaptureDevicePositionUnspecified];
    for (AVCaptureDevice *device in discoverySession.devices) {
      if (device.position == position) {
        return device;
      }
    }
  }
  return nil;
}

- (void)presentDetectorsAlertController {
  UIAlertController *alertController =
      [UIAlertController alertControllerWithTitle:alertControllerTitle
                                          message:alertControllerMessage
                                   preferredStyle:UIAlertControllerStyleAlert];
  for (NSNumber *detectorType in self.detectors) {
    NSInteger detector = detectorType.integerValue;
    __weak typeof(self) weakSelf = self;
    UIAlertAction *action = [UIAlertAction actionWithTitle:[self stringForDetector:detector]
                                                     style:UIAlertActionStyleDefault
                                                   handler:^(UIAlertAction *_Nonnull action) {
                                                     __strong typeof(weakSelf) strongSelf =
                                                         weakSelf;
                                                     strongSelf.currentDetector = detector;
                                                     [strongSelf removeDetectionAnnotations];
                                                   }];
    if (detector == self.currentDetector) {
      [action setEnabled:NO];
    }
    [alertController addAction:action];
  }
  [alertController addAction:[UIAlertAction actionWithTitle:cancelActionTitleText
                                                      style:UIAlertActionStyleCancel
                                                    handler:nil]];
  [self presentViewController:alertController animated:YES completion:nil];
}

- (void)removeDetectionAnnotations {
  for (UIView *annotationView in self.annotationOverlayView.subviews) {
    [annotationView removeFromSuperview];
  }
}

- (void)updatePreviewOverlayView {
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(self.lastFrame);
  if (imageBuffer == nil) {
    return;
  }
  CIImage *ciImage = [CIImage imageWithCVPixelBuffer:imageBuffer];
  CIContext *context = [[CIContext alloc] initWithOptions:nil];
  CGImageRef cgImage = [context createCGImage:ciImage fromRect:ciImage.extent];
  if (cgImage == nil) {
    return;
  }
  UIImage *rotatedImage = [UIImage imageWithCGImage:cgImage
                                              scale:kImageScale
                                        orientation:UIImageOrientationRight];
  if (self.isUsingFrontCamera) {
    CGImageRef rotatedCGImage = rotatedImage.CGImage;
    if (rotatedCGImage == nil) {
      return;
    }
    UIImage *mirroredImage = [UIImage imageWithCGImage:rotatedCGImage
                                                 scale:kImageScale
                                           orientation:UIImageOrientationLeftMirrored];
    self.previewOverlayView.image = mirroredImage;
  } else {
    self.previewOverlayView.image = rotatedImage;
  }
  CGImageRelease(cgImage);
}

- (NSArray<NSValue *> *)convertedPointsFromPoints:(NSArray<NSValue *> *)points
                                            width:(CGFloat)width
                                           height:(CGFloat)height {
  NSMutableArray *result = [NSMutableArray arrayWithCapacity:points.count];
  for (NSValue *point in points) {
    CGPoint cgPointValue = point.CGPointValue;
    CGPoint normalizedPoint = CGPointMake(cgPointValue.x / width, cgPointValue.y / height);
    CGPoint cgPoint = [self.previewLayer pointForCaptureDevicePointOfInterest:normalizedPoint];
    [result addObject:[NSValue valueWithCGPoint:cgPoint]];
  }
  return result;
}

#pragma mark - AVCaptureVideoDataOutputSampleBufferDelegate

- (void)captureOutput:(AVCaptureOutput *)output
    didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
           fromConnection:(AVCaptureConnection *)connection {
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
  if (imageBuffer == nil) {
    NSLog(@"%@", @"Failed to get image buffer from sample buffer.");
    return;
  }

  // Evaluate `self.currentDetector` once to ensure consistency throughout this method since it
  // can be concurrently modified from the main thread.
  Detector activeDetector = self.currentDetector;
  self.lastFrame = sampleBuffer;
  MLKVisionImage *visionImage = [[MLKVisionImage alloc] initWithBuffer:sampleBuffer];
  UIImageOrientation orientation = [UIUtilities
      imageOrientationFromDevicePosition:self.isUsingFrontCamera ? AVCaptureDevicePositionFront
                                                                 : AVCaptureDevicePositionBack];

  visionImage.orientation = orientation;
  CGFloat imageWidth = CVPixelBufferGetWidth(imageBuffer);
  CGFloat imageHeight = CVPixelBufferGetHeight(imageBuffer);

  BOOL shouldEnableClassification = NO;
  BOOL shouldEnableMultipleObjects = NO;
  switch (activeDetector) {
    case DetectorObjectsAutoMLSingleWithClassifier:
    case DetectorObjectsAutoMLMultipleWithClassifier:
      shouldEnableClassification = YES;
    default:
      break;
  }
  switch (activeDetector) {
    case DetectorObjectsAutoMLMultipleNoClassifier:
    case DetectorObjectsAutoMLMultipleWithClassifier:
      shouldEnableMultipleObjects = YES;
    default:
      break;
  }

  switch (activeDetector) {
    case DetectorImageLabelsAutoML:
      [self detectImageLabelsInImage:visionImage];
      break;
    case DetectorObjectsAutoMLSingleNoClassifier:
    case DetectorObjectsAutoMLSingleWithClassifier:
    case DetectorObjectsAutoMLMultipleNoClassifier:
    case DetectorObjectsAutoMLMultipleWithClassifier:
      [self detectObjectsInImage:visionImage
                                width:imageWidth
                               height:imageHeight
           shouldEnableClassification:shouldEnableClassification
          shouldEnableMultipleObjects:shouldEnableMultipleObjects];
      break;
  }
}

@end

NS_ASSUME_NONNULL_END
