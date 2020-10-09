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

static NSString *const videoDataOutputQueueLabel = @"com.google.mlkit.automl.VideoDataOutputQueue";
static NSString *const sessionQueueLabel = @"com.google.mlkit.automl.SessionQueue";
static NSString *const noResultsMessage = @"No Results";

/** Name of the remote AutoML model. */
static NSString *const MLKRemoteAutoMLModelName = @"remote_automl_model";

/** Filename of AutoML local model manifest in the main resource bundle. */
static NSString *const MLKAutoMLLocalModelManifestFilename = @"automl_labeler_manifest";

/** File type of AutoML local model manifest in the main resource bundle. */
static NSString *const MLKAutoMLManifestFileType = @"json";

static const float kLabelConfidenceThreshold = 0.75;
static const CGFloat kImageScale = 1.0;
static const CGFloat kLayoutPadding = 10.0;
static const CGFloat kResultsLabelHeight = 200.0;
static const int kResultsLabelLines = 5;

@interface CameraViewController () <AVCaptureVideoDataOutputSampleBufferDelegate>

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

- (void)viewDidLoad {
  [super viewDidLoad];
  _isUsingFrontCamera = YES;
  _captureSession = [[AVCaptureSession alloc] init];
  _sessionQueue = dispatch_queue_create(sessionQueueLabel.UTF8String, nil);
  _modelManager = [MLKModelManager modelManager];
  _previewOverlayView = [[UIImageView alloc] initWithFrame:CGRectZero];
  _previewOverlayView.contentMode = UIViewContentModeScaleAspectFill;
  _previewOverlayView.translatesAutoresizingMaskIntoConstraints = NO;
  _annotationOverlayView = [[UIView alloc] initWithFrame:CGRectZero];
  _annotationOverlayView.translatesAutoresizingMaskIntoConstraints = NO;

  self.previewLayer = [AVCaptureVideoPreviewLayer layerWithSession:_captureSession];
  [self setUpPreviewOverlayView];
  [self setUpAnnotationOverlayView];
  [self setUpCaptureSessionOutput];
  [self setUpCaptureSessionInput];
}

- (void)viewDidAppear:(BOOL)animated {
  [super viewDidAppear:animated];
  [self startSession];
}

- (void)viewDidDisappear:(BOOL)animated {
  [super viewDidDisappear:animated];
  [self stopSession];
}

- (void)viewDidLayoutSubviews {
  [super viewDidLayoutSubviews];
  _previewLayer.frame = _cameraView.frame;
}

- (IBAction)switchCamera:(id)sender {
  self.isUsingFrontCamera = !_isUsingFrontCamera;
  [self removeDetectionAnnotations];
  [self setUpCaptureSessionInput];
}

#pragma mark - AutoML Image Classification

/// Detects labels on the specified image using AutoML Image Classification API.
///
/// - Parameter image: The image.
- (void)detectImageLabelsInImage:(MLKVisionImage *)image
                           width:(CGFloat)width
                          height:(CGFloat)height {
  [self requestAutoMLRemoteModelIfNeeded];

  // [START config_automl_label]
  MLKCommonImageLabelerOptions *options;
  MLKAutoMLImageLabelerRemoteModel *remoteModel =
      (MLKAutoMLImageLabelerRemoteModel *)[self remoteModel];
  if ([self.modelManager isModelDownloaded:remoteModel]) {
    NSLog(@"Use AutoML remote model.");
    options = [[MLKAutoMLImageLabelerOptions alloc] initWithRemoteModel:remoteModel];
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
    MLKAutoMLImageLabelerLocalModel *localModel =
        [[MLKAutoMLImageLabelerLocalModel alloc] initWithManifestPath:localModelFilePath];
    options = [[MLKAutoMLImageLabelerOptions alloc] initWithLocalModel:localModel];
  }
  options.confidenceThreshold = @(kLabelConfidenceThreshold);
  // [END config_automl_label]

  // [START init_automl_label]
  MLKImageLabeler *autoMLImageLabeler = [MLKImageLabeler imageLabelerWithOptions:options];
  // [END init_automl_label]

  dispatch_group_t group = dispatch_group_create();
  dispatch_group_enter(group);

  // [START detect_automl_label]
  __weak typeof(self) weakSelf = self;
  [autoMLImageLabeler
      processImage:image
        completion:^(NSArray<MLKImageLabel *> *_Nullable labels, NSError *_Nullable error) {
          __strong typeof(weakSelf) strongSelf = weakSelf;
          // [START_EXCLUDE]
          [strongSelf updatePreviewOverlayView];
          [strongSelf removeDetectionAnnotations];
          // [END_EXCLUDE]
          if (error != nil) {
            // [START_EXCLUDE]
            NSLog(@"Failed to detect labels with error: %@.", error.localizedDescription);
            dispatch_group_leave(group);
            // [END_EXCLUDE]
            return;
          }

          if (!labels || labels.count == 0) {
            // [START_EXCLUDE]
            dispatch_group_leave(group);
            // [END_EXCLUDE]
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
            [labelStrings addObject:[NSString stringWithFormat:@"Label: %@, Confidence: %f",
                                                               label.text, label.confidence]];
          }
          resultsLabel.text = [labelStrings componentsJoinedByString:@"\n"];
          resultsLabel.adjustsFontSizeToFitWidth = YES;
          resultsLabel.numberOfLines = kResultsLabelLines;
          [strongSelf.annotationOverlayView addSubview:resultsLabel];
          dispatch_group_leave(group);
          // [END_EXCLUDE]
        }];
  // [END detect_automl_label]
  dispatch_group_wait(group, DISPATCH_TIME_FOREVER);
}

- (void)requestAutoMLRemoteModelIfNeeded {
  MLKRemoteModel *remoteModel = [self remoteModel];
  if ([self.modelManager isModelDownloaded:remoteModel]) {
    return;
  }

  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(remoteModelDownloadDidSucceed:)
                                             name:MLKModelDownloadDidSucceedNotification
                                           object:nil];
  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(remoteModelDownloadDidFail:)
                                             name:MLKModelDownloadDidFailNotification
                                           object:nil];

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
  return [[MLKAutoMLImageLabelerRemoteModel alloc] initWithName:MLKRemoteAutoMLModelName];
}

- (void)setUpCaptureSessionOutput {
  __weak typeof(self) weakSelf = self;
  dispatch_async(_sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf->_captureSession beginConfiguration];
    // When performing latency tests to determine ideal capture settings,
    // run the app in 'release' mode to get accurate performance metrics
    strongSelf->_captureSession.sessionPreset = AVCaptureSessionPresetMedium;

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
  dispatch_async(_sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    AVCaptureDevicePosition cameraPosition =
        strongSelf.isUsingFrontCamera ? AVCaptureDevicePositionFront : AVCaptureDevicePositionBack;
    AVCaptureDevice *device = [strongSelf captureDeviceForPosition:cameraPosition];
    if (device) {
      [strongSelf->_captureSession beginConfiguration];
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
  dispatch_async(_sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf->_captureSession startRunning];
  });
}

- (void)stopSession {
  __weak typeof(self) weakSelf = self;
  dispatch_async(_sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf->_captureSession stopRunning];
  });
}

- (void)setUpPreviewOverlayView {
  [_cameraView addSubview:_previewOverlayView];
  [NSLayoutConstraint activateConstraints:@[
    [_previewOverlayView.centerYAnchor constraintEqualToAnchor:_cameraView.centerYAnchor],
    [_previewOverlayView.centerXAnchor constraintEqualToAnchor:_cameraView.centerXAnchor],
    [_previewOverlayView.leadingAnchor constraintEqualToAnchor:_cameraView.leadingAnchor],
    [_previewOverlayView.trailingAnchor constraintEqualToAnchor:_cameraView.trailingAnchor]
  ]];
}
- (void)setUpAnnotationOverlayView {
  [_cameraView addSubview:_annotationOverlayView];
  [NSLayoutConstraint activateConstraints:@[
    [_annotationOverlayView.topAnchor constraintEqualToAnchor:_cameraView.topAnchor],
    [_annotationOverlayView.leadingAnchor constraintEqualToAnchor:_cameraView.leadingAnchor],
    [_annotationOverlayView.trailingAnchor constraintEqualToAnchor:_cameraView.trailingAnchor],
    [_annotationOverlayView.bottomAnchor constraintEqualToAnchor:_cameraView.bottomAnchor]
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

- (void)removeDetectionAnnotations {
  for (UIView *annotationView in _annotationOverlayView.subviews) {
    [annotationView removeFromSuperview];
  }
}

- (void)updatePreviewOverlayView {
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(_lastFrame);
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
  if (_isUsingFrontCamera) {
    CGImageRef rotatedCGImage = rotatedImage.CGImage;
    if (rotatedCGImage == nil) {
      return;
    }
    UIImage *mirroredImage = [UIImage imageWithCGImage:rotatedCGImage
                                                 scale:kImageScale
                                           orientation:UIImageOrientationLeftMirrored];
    _previewOverlayView.image = mirroredImage;
  } else {
    _previewOverlayView.image = rotatedImage;
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
    CGPoint cgPoint = [_previewLayer pointForCaptureDevicePointOfInterest:normalizedPoint];
    [result addObject:[NSValue valueWithCGPoint:cgPoint]];
  }
  return result;
}

#pragma mark - AVCaptureVideoDataOutputSampleBufferDelegate

- (void)captureOutput:(AVCaptureOutput *)output
    didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
           fromConnection:(AVCaptureConnection *)connection {
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
  if (imageBuffer) {
    _lastFrame = sampleBuffer;
    MLKVisionImage *visionImage = [[MLKVisionImage alloc] initWithBuffer:sampleBuffer];
    UIImageOrientation orientation = [UIUtilities
        imageOrientationFromDevicePosition:_isUsingFrontCamera ? AVCaptureDevicePositionFront
                                                               : AVCaptureDevicePositionBack];

    visionImage.orientation = orientation;
    CGFloat imageWidth = CVPixelBufferGetWidth(imageBuffer);
    CGFloat imageHeight = CVPixelBufferGetHeight(imageBuffer);
    [self detectImageLabelsInImage:visionImage width:imageWidth height:imageHeight];
  } else {
    NSLog(@"%@", @"Failed to get image buffer from sample buffer.");
  }
}

@end

NS_ASSUME_NONNULL_END
