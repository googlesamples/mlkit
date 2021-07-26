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
#import <CoreVideo/CoreVideo.h>
#import "UIUtilities.h"

@import MLImage;
@import MLKit;

NS_ASSUME_NONNULL_BEGIN

static NSString *const alertControllerTitle = @"Vision Detectors";
static NSString *const alertControllerMessage = @"Select a detector";
static NSString *const cancelActionTitleText = @"Cancel";
static NSString *const videoDataOutputQueueLabel =
    @"com.google.mlkit.visiondetector.VideoDataOutputQueue";
static NSString *const sessionQueueLabel = @"com.google.mlkit.visiondetector.SessionQueue";
static NSString *const noResultsMessage = @"No Results";
static NSString *const localModelFileName = @"bird";
static NSString *const localModelFileType = @"tflite";

static float const MLKImageLabelConfidenceThreshold = 0.75;
static const CGFloat MLKSmallDotRadius = 4.0;
static const CGFloat MLKconstantScale = 1.0;
static const CGFloat MLKImageLabelResultFrameX = 0.4;
static const CGFloat MLKImageLabelResultFrameY = 0.1;
static const CGFloat MLKImageLabelResultFrameWidth = 0.5;
static const CGFloat MLKImageLabelResultFrameHeight = 0.8;
static const CGFloat MLKSegmentationMaskAlpha = 0.5;

@interface CameraViewController () <AVCaptureVideoDataOutputSampleBufferDelegate>

typedef NS_ENUM(NSInteger, Detector) {
  DetectorOnDeviceBarcode,
  DetectorOnDeviceFace,
  DetectorOnDeviceText,
  DetectorOnDeviceTextChinese,
  DetectorOnDeviceTextDevanagari,
  DetectorOnDeviceTextJapanese,
  DetectorOnDeviceTextKorean,
  DetectorOnDeviceImageLabels,
  DetectorOnDeviceImageLabelsCustom,
  DetectorOnDeviceObjectProminentNoClassifier,
  DetectorOnDeviceObjectProminentWithClassifier,
  DetectorOnDeviceObjectMultipleNoClassifier,
  DetectorOnDeviceObjectMultipleWithClassifier,
  DetectorOnDeviceObjectCustomProminentNoClassifier,
  DetectorOnDeviceObjectCustomProminentWithClassifier,
  DetectorOnDeviceObjectCustomMultipleNoClassifier,
  DetectorOnDeviceObjectCustomMultipleWithClassifier,
  DetectorPose,
  DetectorPoseAccurate,
  DetectorSegmentationSelfie,
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

/** Initialized when one of the pose detector rows are chosen. Reset to `nil` when neither are. */
@property(nonatomic, nullable) MLKPoseDetector *poseDetector;

/** Initialized when a segmentation detector row is chosen. Reset to `nil` otherwise. */
@property(nonatomic, nullable) MLKSegmenter *segmenter;

/**
 * The detector mode with which detection was most recently run. Only used on the video output
 * queue. Useful for inferring when to reset detector instances which use a conventional lifecycle
 * paradigm.
 */
@property(nonatomic) Detector lastDetector;

@end

@implementation CameraViewController

- (NSString *)stringForDetector:(Detector)detector {
  switch (detector) {
    case DetectorOnDeviceBarcode:
      return @"Barcode Scanning";
    case DetectorOnDeviceFace:
      return @"Face Detection";
    case DetectorOnDeviceImageLabels:
      return @"Image Labeling";
    case DetectorOnDeviceImageLabelsCustom:
      return @"Image Labeling Custom";
    case DetectorOnDeviceText:
      return @"Text Recognition";
    case DetectorOnDeviceTextChinese:
      return @"Text Recognition Chinese";
    case DetectorOnDeviceTextDevanagari:
      return @"Text Recognition Devanagari";
    case DetectorOnDeviceTextJapanese:
      return @"Text Recognition Japanese";
    case DetectorOnDeviceTextKorean:
      return @"Text Recognition Korean";
    case DetectorOnDeviceObjectProminentNoClassifier:
      return @"ODT, single, no labeling";
    case DetectorOnDeviceObjectProminentWithClassifier:
      return @"ODT, single, labeling";
    case DetectorOnDeviceObjectMultipleNoClassifier:
      return @"ODT, multiple, no labeling";
    case DetectorOnDeviceObjectMultipleWithClassifier:
      return @"ODT, multiple, labeling";
    case DetectorOnDeviceObjectCustomProminentNoClassifier:
      return @"ODT, custom, single, no labeling";
    case DetectorOnDeviceObjectCustomProminentWithClassifier:
      return @"ODT, custom, single, labeling";
    case DetectorOnDeviceObjectCustomMultipleNoClassifier:
      return @"ODT, custom, multiple, no labeling";
    case DetectorOnDeviceObjectCustomMultipleWithClassifier:
      return @"ODT, custom, multiple, labeling";
    case DetectorPose:
      return @"Pose Detection";
    case DetectorPoseAccurate:
      return @"Pose Detection, accurate";
    case DetectorSegmentationSelfie:
      return @"Selfie Segmentation";
  }
}

- (void)viewDidLoad {
  [super viewDidLoad];
  _detectors = @[
    @(DetectorOnDeviceBarcode),
    @(DetectorOnDeviceFace),
    @(DetectorOnDeviceText),
    @(DetectorOnDeviceTextChinese),
    @(DetectorOnDeviceTextDevanagari),
    @(DetectorOnDeviceTextJapanese),
    @(DetectorOnDeviceTextKorean),
    @(DetectorOnDeviceImageLabels),
    @(DetectorOnDeviceImageLabelsCustom),
    @(DetectorOnDeviceObjectProminentNoClassifier),
    @(DetectorOnDeviceObjectProminentWithClassifier),
    @(DetectorOnDeviceObjectMultipleNoClassifier),
    @(DetectorOnDeviceObjectMultipleWithClassifier),
    @(DetectorOnDeviceObjectCustomProminentNoClassifier),
    @(DetectorOnDeviceObjectCustomProminentWithClassifier),
    @(DetectorOnDeviceObjectCustomMultipleNoClassifier),
    @(DetectorOnDeviceObjectCustomMultipleWithClassifier),
    @(DetectorPose),
    @(DetectorPoseAccurate),
    @(DetectorSegmentationSelfie),
  ];
  self.currentDetector = DetectorOnDeviceFace;
  _isUsingFrontCamera = YES;
  _captureSession = [[AVCaptureSession alloc] init];
  _sessionQueue = dispatch_queue_create(sessionQueueLabel.UTF8String, nil);
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

- (IBAction)selectDetector:(id)sender {
  [self presentDetectorsAlertController];
}

- (IBAction)switchCamera:(id)sender {
  self.isUsingFrontCamera = !_isUsingFrontCamera;
  [self removeDetectionAnnotations];
  [self setUpCaptureSessionInput];
}

#pragma mark - On-Device Detections

- (void)detectFacesOnDeviceInImage:(MLKVisionImage *)image
                             width:(CGFloat)width
                            height:(CGFloat)height {
  // When performing latency tests to determine ideal detection settings, run the app in 'release'
  // mode to get accurate performance metrics.
  MLKFaceDetectorOptions *options = [[MLKFaceDetectorOptions alloc] init];
  options.performanceMode = MLKFaceDetectorPerformanceModeFast;
  options.contourMode = MLKFaceDetectorContourModeAll;
  options.landmarkMode = MLKFaceDetectorLandmarkModeNone;
  options.classificationMode = MLKFaceDetectorClassificationModeNone;
  MLKFaceDetector *faceDetector = [MLKFaceDetector faceDetectorWithOptions:options];
  NSError *error;
  NSArray<MLKFace *> *faces = [faceDetector resultsInImage:image error:&error];
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf updatePreviewOverlayViewWithLastFrame];
    [strongSelf removeDetectionAnnotations];
    if (error != nil) {
      NSLog(@"Failed to detect faces with error: %@", error.localizedDescription);
      return;
    }
    if (faces.count == 0) {
      NSLog(@"On-Device face detector returned no results.");
      return;
    }
    for (MLKFace *face in faces) {
      CGRect normalizedRect =
          CGRectMake(face.frame.origin.x / width, face.frame.origin.y / height,
                     face.frame.size.width / width, face.frame.size.height / height);
      CGRect standardizedRect = CGRectStandardize(
          [strongSelf.previewLayer rectForMetadataOutputRectOfInterest:normalizedRect]);
      [UIUtilities addRectangle:standardizedRect
                         toView:strongSelf.annotationOverlayView
                          color:UIColor.greenColor];
      [strongSelf addContoursForFace:face width:width height:height];
    }
  });
}

- (void)recognizeTextOnDeviceInImage:(MLKVisionImage *)image
                               width:(CGFloat)width
                              height:(CGFloat)height
                        detectorType:(Detector)detectorType {
  MLKCommonTextRecognizerOptions *options;
  if (detectorType == DetectorOnDeviceText) {
    options = [[MLKTextRecognizerOptions alloc] init];
  } else if (detectorType == DetectorOnDeviceTextChinese) {
    options = [[MLKChineseTextRecognizerOptions alloc] init];
  } else if (detectorType == DetectorOnDeviceTextDevanagari) {
    options = [[MLKDevanagariTextRecognizerOptions alloc] init];
  } else if (detectorType == DetectorOnDeviceTextJapanese) {
    options = [[MLKJapaneseTextRecognizerOptions alloc] init];
  } else if (detectorType == DetectorOnDeviceTextKorean) {
    options = [[MLKKoreanTextRecognizerOptions alloc] init];
  }
  MLKTextRecognizer *textRecognizer = [MLKTextRecognizer textRecognizerWithOptions:options];
  NSError *error;
  MLKText *text = [textRecognizer resultsInImage:image error:&error];
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf removeDetectionAnnotations];
    [strongSelf updatePreviewOverlayViewWithLastFrame];
    if (error != nil) {
      NSLog(@"Failed to recognize text with error: %@", error.localizedDescription);
      return;
    }
    // Blocks.
    for (MLKTextBlock *block in text.blocks) {
      NSArray<NSValue *> *points = [strongSelf convertedPointsFromPoints:block.cornerPoints
                                                                   width:width
                                                                  height:height];
      [UIUtilities addShapeWithPoints:points
                               toView:strongSelf.annotationOverlayView
                                color:UIColor.purpleColor];

      // Lines.
      for (MLKTextLine *line in block.lines) {
        points = [strongSelf convertedPointsFromPoints:line.cornerPoints width:width height:height];
        [UIUtilities addShapeWithPoints:points
                                 toView:strongSelf.annotationOverlayView
                                  color:UIColor.purpleColor];

        // Elements.
        for (MLKTextElement *element in line.elements) {
          CGRect normalizedRect =
              CGRectMake(element.frame.origin.x / width, element.frame.origin.y / height,
                         element.frame.size.width / width, element.frame.size.height / height);
          CGRect convertedRect =
              [strongSelf.previewLayer rectForMetadataOutputRectOfInterest:normalizedRect];
          [UIUtilities addRectangle:convertedRect
                             toView:strongSelf.annotationOverlayView
                              color:UIColor.greenColor];
          UILabel *label = [[UILabel alloc] initWithFrame:convertedRect];
          label.text = element.text;
          label.adjustsFontSizeToFitWidth = YES;
          [strongSelf rotateView:label orientation:image.orientation];
          [strongSelf.annotationOverlayView addSubview:label];
        }
      }
    }
  });
}

- (void)detectLabelsInImage:(MLKVisionImage *)image useCustomModel:(BOOL)useCustomModel {
  MLKCommonImageLabelerOptions *options;
  if (useCustomModel) {
    NSString *localModelPath = [[NSBundle mainBundle] pathForResource:localModelFileName
                                                               ofType:localModelFileType];
    MLKLocalModel *localModel = [[MLKLocalModel alloc] initWithPath:localModelPath];
    options = [[MLKCustomImageLabelerOptions alloc] initWithLocalModel:localModel];
  } else {
    options = [[MLKImageLabelerOptions alloc] init];
  }
  options.confidenceThreshold = @(MLKImageLabelConfidenceThreshold);
  NSError *error;
  MLKImageLabeler *onDeviceLabeler = [MLKImageLabeler imageLabelerWithOptions:options];
  NSArray<MLKImageLabel *> *labels = [onDeviceLabeler resultsInImage:image error:&error];
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf updatePreviewOverlayViewWithLastFrame];
    [strongSelf removeDetectionAnnotations];
    if (labels.count == 0) {
      NSString *errorString = error != nil ? error.localizedDescription : noResultsMessage;
      NSLog(@"On-Device label detection failed with error: %@", errorString);
      return;
    }
    NSMutableString *description = [[NSMutableString alloc] init];
    CGRect normalizedRect =
        CGRectMake(MLKImageLabelResultFrameX, MLKImageLabelResultFrameY,
                   MLKImageLabelResultFrameWidth, MLKImageLabelResultFrameHeight);
    CGRect standardizedRect = CGRectStandardize(
        [strongSelf.previewLayer rectForMetadataOutputRectOfInterest:normalizedRect]);
    [UIUtilities addRectangle:standardizedRect
                       toView:strongSelf.annotationOverlayView
                        color:UIColor.grayColor];
    UILabel *uiLabel = [[UILabel alloc] initWithFrame:standardizedRect];

    [description appendString:@"Labels:\n"];
    for (MLKImageLabel *label in labels) {
      NSString *labelString =
          [NSString stringWithFormat:@"Label: %@, Confidence: %f, Index: %lu\n", label.text,
                                     label.confidence, (unsigned long)label.index];
      [description appendString:labelString];
    }
    uiLabel.text = description;
    uiLabel.numberOfLines = 0;
    uiLabel.adjustsFontSizeToFitWidth = YES;
    [strongSelf rotateView:uiLabel orientation:image.orientation];
    [strongSelf.annotationOverlayView addSubview:uiLabel];
  });
}

- (void)detectPoseInImage:(GMLImage *)image width:(CGFloat)width height:(CGFloat)height {
  NSError *error;
  NSArray<MLKPose *> *poses = [self.poseDetector resultsInImage:image error:&error];
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf updatePreviewOverlayViewWithLastFrame];
    [strongSelf removeDetectionAnnotations];

    if (poses.count == 0) {
      if (error != nil) {
        NSLog(@"Failed to detect pose with error: %@", error.localizedDescription);
      }
      return;
    }

    // Pose detection currently only supports single pose.
    MLKPose *pose = poses.firstObject;

    UIView *poseOverlay = [UIUtilities poseOverlayViewForPose:pose
                                             inViewWithBounds:self.annotationOverlayView.bounds
                                                    lineWidth:3.0f
                                                    dotRadius:MLKSmallDotRadius
                                  positionTransformationBlock:^(MLKVisionPoint *position) {
                                    return [strongSelf normalizedPointFromVisionPoint:position
                                                                                width:width
                                                                               height:height];
                                  }];

    [strongSelf.annotationOverlayView addSubview:poseOverlay];
  });
}

- (void)detectSegmentationMaskInImage:(MLKVisionImage *)image
                         sampleBuffer:(CMSampleBufferRef)sampleBuffer {
  NSError *error;
  MLKSegmentationMask *mask = [self.segmenter resultsInImage:image error:&error];
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);

  if (mask != nil) {
    UIColor *backgroundColor =
        [UIColor.purpleColor colorWithAlphaComponent:MLKSegmentationMaskAlpha];
    [UIUtilities applySegmentationMask:mask
                         toImageBuffer:imageBuffer
                   withBackgroundColor:backgroundColor
                       foregroundColor:nil];
  } else {
    NSLog(@"Failed to segment image with error: %@", error.localizedDescription);
  }

  __weak __typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf updatePreviewOverlayViewWithImageBuffer:imageBuffer];
    [strongSelf removeDetectionAnnotations];
  });
}

- (void)scanBarcodesOnDeviceInImage:(MLKVisionImage *)image
                              width:(CGFloat)width
                             height:(CGFloat)height
                            options:(MLKBarcodeScannerOptions *)options {
  MLKBarcodeScanner *scanner = [MLKBarcodeScanner barcodeScannerWithOptions:options];
  NSError *error;
  NSArray<MLKBarcode *> *barcodes = [scanner resultsInImage:image error:&error];
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf removeDetectionAnnotations];
    [strongSelf updatePreviewOverlayViewWithLastFrame];
    if (error != nil) {
      NSLog(@"Failed to scan barcodes with error: %@", error.localizedDescription);
      return;
    }
    if (barcodes.count == 0) {
      NSLog(@"On-Device barcode scanner returned no results.");
      return;
    }
    for (MLKBarcode *barcode in barcodes) {
      CGRect normalizedRect = CGRectMake(barcode.frame.origin.x / width,       // X
                                         barcode.frame.origin.y / height,      // Y
                                         barcode.frame.size.width / width,     // Width
                                         barcode.frame.size.height / height);  // Height
      CGRect standardizedRect = CGRectStandardize(
          [strongSelf.previewLayer rectForMetadataOutputRectOfInterest:normalizedRect]);
      [UIUtilities addRectangle:standardizedRect
                         toView:strongSelf.annotationOverlayView
                          color:UIColor.greenColor];
      UILabel *label = [[UILabel alloc] initWithFrame:standardizedRect];
      label.numberOfLines = 0;
      NSMutableString *description = [NSMutableString new];
      if (barcode.displayValue) {
        [description appendString:barcode.displayValue];
      }
      label.text = description;
      label.adjustsFontSizeToFitWidth = YES;
      [strongSelf rotateView:label orientation:image.orientation];
      [strongSelf.annotationOverlayView addSubview:label];
    }
  });
}

- (void)detectObjectsOnDeviceInImage:(MLKVisionImage *)image
                               width:(CGFloat)width
                              height:(CGFloat)height
                             options:(MLKCommonObjectDetectorOptions *)options {
  MLKObjectDetector *detector = [MLKObjectDetector objectDetectorWithOptions:options];

  NSError *error;
  NSArray *objects = [detector resultsInImage:image error:&error];
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf updatePreviewOverlayViewWithLastFrame];
    [strongSelf removeDetectionAnnotations];
    if (error != nil) {
      NSLog(@"Failed to detect object with error: %@", error.localizedDescription);
      return;
    }
    if (objects.count == 0) {
      NSLog(@"On-Device object detector returned no results.");
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
      [strongSelf rotateView:label orientation:image.orientation];
      [strongSelf.annotationOverlayView addSubview:label];
    }
  });
}

#pragma mark - Private

- (void)setUpCaptureSessionOutput {
  __weak typeof(self) weakSelf = self;
  dispatch_async(_sessionQueue, ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    if (strongSelf == nil) {
      NSLog(@"Failed to setUpCaptureSessionOutput because self was deallocated");
      return;
    }
    [strongSelf.captureSession beginConfiguration];
    // When performing latency tests to determine ideal capture settings,
    // run the app in 'release' mode to get accurate performance metrics
    strongSelf.captureSession.sessionPreset = AVCaptureSessionPresetMedium;

    AVCaptureVideoDataOutput *output = [[AVCaptureVideoDataOutput alloc] init];
    output.videoSettings = @{
      (id)
      kCVPixelBufferPixelFormatTypeKey : [NSNumber numberWithUnsignedInt:kCVPixelFormatType_32BGRA]
    };
    output.alwaysDiscardsLateVideoFrames = YES;
    dispatch_queue_t outputQueue = dispatch_queue_create(videoDataOutputQueueLabel.UTF8String, nil);
    [output setSampleBufferDelegate:self queue:outputQueue];
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
    if (strongSelf == nil) {
      NSLog(@"Failed to setUpCaptureSessionInput because self was deallocated");
      return;
    }
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
  dispatch_async(_sessionQueue, ^{
    [weakSelf.captureSession startRunning];
  });
}

- (void)stopSession {
  __weak typeof(self) weakSelf = self;
  dispatch_async(_sessionQueue, ^{
    [weakSelf.captureSession stopRunning];
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

- (void)presentDetectorsAlertController {
  UIAlertController *alertController =
      [UIAlertController alertControllerWithTitle:alertControllerTitle
                                          message:alertControllerMessage
                                   preferredStyle:UIAlertControllerStyleAlert];
  for (NSNumber *detectorType in _detectors) {
    NSInteger detector = detectorType.integerValue;
    UIAlertAction *action = [UIAlertAction actionWithTitle:[self stringForDetector:detector]
                                                     style:UIAlertActionStyleDefault
                                                   handler:^(UIAlertAction *_Nonnull actionArg) {
                                                     self.currentDetector = detector;
                                                     [self removeDetectionAnnotations];
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
  for (UIView *annotationView in _annotationOverlayView.subviews) {
    [annotationView removeFromSuperview];
  }
}

- (void)updatePreviewOverlayViewWithLastFrame {
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(_lastFrame);
  [self updatePreviewOverlayViewWithImageBuffer:imageBuffer];
}

- (void)updatePreviewOverlayViewWithImageBuffer:(CVImageBufferRef)imageBuffer {
  if (imageBuffer == nil) {
    return;
  }
  UIImageOrientation orientation =
      _isUsingFrontCamera ? UIImageOrientationLeftMirrored : UIImageOrientationRight;
  UIImage *image = [UIUtilities UIImageFromImageBuffer:imageBuffer orientation:orientation];
  _previewOverlayView.image = image;
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

- (CGPoint)normalizedPointFromVisionPoint:(MLKVisionPoint *)point
                                    width:(CGFloat)width
                                   height:(CGFloat)height {
  CGPoint cgPointValue = CGPointMake(point.x, point.y);
  CGPoint normalizedPoint = CGPointMake(cgPointValue.x / width, cgPointValue.y / height);
  CGPoint cgPoint = [_previewLayer pointForCaptureDevicePointOfInterest:normalizedPoint];
  return cgPoint;
}

- (void)addContoursForFace:(MLKFace *)face width:(CGFloat)width height:(CGFloat)height {
  // Face
  MLKFaceContour *faceContour = [face contourOfType:MLKFaceContourTypeFace];
  for (MLKVisionPoint *point in faceContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.blueColor
                           radius:MLKSmallDotRadius];
  }

  // Eyebrows
  MLKFaceContour *leftEyebrowTopContour = [face contourOfType:MLKFaceContourTypeLeftEyebrowTop];
  for (MLKVisionPoint *point in leftEyebrowTopContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.orangeColor
                           radius:MLKSmallDotRadius];
  }
  MLKFaceContour *leftEyebrowBottomContour =
      [face contourOfType:MLKFaceContourTypeLeftEyebrowBottom];
  for (MLKVisionPoint *point in leftEyebrowBottomContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.orangeColor
                           radius:MLKSmallDotRadius];
  }
  MLKFaceContour *rightEyebrowTopContour = [face contourOfType:MLKFaceContourTypeRightEyebrowTop];
  for (MLKVisionPoint *point in rightEyebrowTopContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.orangeColor
                           radius:MLKSmallDotRadius];
  }
  MLKFaceContour *rightEyebrowBottomContour =
      [face contourOfType:MLKFaceContourTypeRightEyebrowBottom];
  for (MLKVisionPoint *point in rightEyebrowBottomContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.orangeColor
                           radius:MLKSmallDotRadius];
  }

  // Eyes
  MLKFaceContour *leftEyeContour = [face contourOfType:MLKFaceContourTypeLeftEye];
  for (MLKVisionPoint *point in leftEyeContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.cyanColor
                           radius:MLKSmallDotRadius];
  }
  MLKFaceContour *rightEyeContour = [face contourOfType:MLKFaceContourTypeRightEye];
  for (MLKVisionPoint *point in rightEyeContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.cyanColor
                           radius:MLKSmallDotRadius];
  }

  // Lips
  MLKFaceContour *upperLipTopContour = [face contourOfType:MLKFaceContourTypeUpperLipTop];
  for (MLKVisionPoint *point in upperLipTopContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.redColor
                           radius:MLKSmallDotRadius];
  }
  MLKFaceContour *upperLipBottomContour = [face contourOfType:MLKFaceContourTypeUpperLipBottom];
  for (MLKVisionPoint *point in upperLipBottomContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.redColor
                           radius:MLKSmallDotRadius];
  }
  MLKFaceContour *lowerLipTopContour = [face contourOfType:MLKFaceContourTypeLowerLipTop];
  for (MLKVisionPoint *point in lowerLipTopContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.redColor
                           radius:MLKSmallDotRadius];
  }
  MLKFaceContour *lowerLipBottomContour = [face contourOfType:MLKFaceContourTypeLowerLipBottom];
  for (MLKVisionPoint *point in lowerLipBottomContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.redColor
                           radius:MLKSmallDotRadius];
  }

  // Nose
  MLKFaceContour *noseBridgeContour = [face contourOfType:MLKFaceContourTypeNoseBridge];
  for (MLKVisionPoint *point in noseBridgeContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.yellowColor
                           radius:MLKSmallDotRadius];
  }
  MLKFaceContour *noseBottomContour = [face contourOfType:MLKFaceContourTypeNoseBottom];
  for (MLKVisionPoint *point in noseBottomContour.points) {
    CGPoint cgPoint = [self normalizedPointFromVisionPoint:point width:width height:height];
    [UIUtilities addCircleAtPoint:cgPoint
                           toView:self->_annotationOverlayView
                            color:UIColor.yellowColor
                           radius:MLKSmallDotRadius];
  }
}

- (void)rotateView:(UIView *)view orientation:(UIImageOrientation)orientation {
  CGFloat degree = 0.0;
  switch (orientation) {
    case UIImageOrientationUp:
    case UIImageOrientationUpMirrored:
      degree = 90.0;
      break;
    case UIImageOrientationRightMirrored:
    case UIImageOrientationLeft:
      degree = 180.0;
      break;
    case UIImageOrientationDown:
    case UIImageOrientationDownMirrored:
      degree = 270.0;
      break;
    case UIImageOrientationLeftMirrored:
    case UIImageOrientationRight:
      degree = 0.0;
      break;
  }
  view.transform = CGAffineTransformMakeRotation(degree * 3.141592654 / 180);
}

#pragma mark - AVCaptureVideoDataOutputSampleBufferDelegate

- (void)captureOutput:(AVCaptureOutput *)output
    didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
           fromConnection:(AVCaptureConnection *)connection {
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
  if (imageBuffer) {
    // Evaluate `self.currentDetector` once to ensure consistency throughout this method since it
    // can be concurrently modified from the main thread.
    Detector activeDetector = self.currentDetector;
    [self resetManagedLifecycleDetectorsForActiveDetector:activeDetector];

    _lastFrame = sampleBuffer;
    MLKVisionImage *visionImage = [[MLKVisionImage alloc] initWithBuffer:sampleBuffer];
    UIImageOrientation orientation = [UIUtilities
        imageOrientationFromDevicePosition:_isUsingFrontCamera ? AVCaptureDevicePositionFront
                                                               : AVCaptureDevicePositionBack];

    visionImage.orientation = orientation;

    GMLImage *inputImage = [[GMLImage alloc] initWithSampleBuffer:sampleBuffer];
    inputImage.orientation = orientation;

    CGFloat imageWidth = CVPixelBufferGetWidth(imageBuffer);
    CGFloat imageHeight = CVPixelBufferGetHeight(imageBuffer);
    BOOL shouldEnableClassification = NO;
    BOOL shouldEnableMultipleObjects = NO;
    switch (activeDetector) {
      case DetectorOnDeviceObjectCustomMultipleWithClassifier:
      case DetectorOnDeviceObjectCustomProminentWithClassifier:
      case DetectorOnDeviceObjectMultipleWithClassifier:
      case DetectorOnDeviceObjectProminentWithClassifier:
        shouldEnableClassification = YES;
      default:
        break;
    }
    switch (activeDetector) {
      case DetectorOnDeviceObjectCustomMultipleNoClassifier:
      case DetectorOnDeviceObjectCustomMultipleWithClassifier:
      case DetectorOnDeviceObjectMultipleNoClassifier:
      case DetectorOnDeviceObjectMultipleWithClassifier:
        shouldEnableMultipleObjects = YES;
      default:
        break;
    }

    switch (activeDetector) {
      case DetectorOnDeviceBarcode: {
        MLKBarcodeScannerOptions *options = [[MLKBarcodeScannerOptions alloc] init];
        [self scanBarcodesOnDeviceInImage:visionImage
                                    width:imageWidth
                                   height:imageHeight
                                  options:options];
        break;
      }
      case DetectorOnDeviceFace:
        [self detectFacesOnDeviceInImage:visionImage width:imageWidth height:imageHeight];
        break;
      case DetectorOnDeviceText:            // Falls through
      case DetectorOnDeviceTextChinese:     // Falls through
      case DetectorOnDeviceTextDevanagari:  // Falls through
      case DetectorOnDeviceTextJapanese:    // Falls through
      case DetectorOnDeviceTextKorean:
        [self recognizeTextOnDeviceInImage:visionImage
                                     width:imageWidth
                                    height:imageHeight
                              detectorType:activeDetector];
        break;
      case DetectorOnDeviceImageLabels:
        [self detectLabelsInImage:visionImage useCustomModel:NO];
        break;
      case DetectorOnDeviceImageLabelsCustom:
        [self detectLabelsInImage:visionImage useCustomModel:YES];
        break;
      case DetectorPose:
      case DetectorPoseAccurate:
        [self detectPoseInImage:inputImage width:imageWidth height:imageHeight];
        break;
      case DetectorSegmentationSelfie:
        [self detectSegmentationMaskInImage:visionImage sampleBuffer:_lastFrame];
        break;
      case DetectorOnDeviceObjectProminentNoClassifier:
      case DetectorOnDeviceObjectProminentWithClassifier:
      case DetectorOnDeviceObjectMultipleNoClassifier:
      case DetectorOnDeviceObjectMultipleWithClassifier: {
        // The `options.detectorMode` defaults to `MLKObjectDetectorModeStream`.
        MLKObjectDetectorOptions *options = [MLKObjectDetectorOptions new];
        options.shouldEnableClassification = shouldEnableClassification;
        options.shouldEnableMultipleObjects = shouldEnableMultipleObjects;
        [self detectObjectsOnDeviceInImage:visionImage
                                     width:imageWidth
                                    height:imageHeight
                                   options:options];
        break;
      }
      case DetectorOnDeviceObjectCustomProminentNoClassifier:
      case DetectorOnDeviceObjectCustomProminentWithClassifier:
      case DetectorOnDeviceObjectCustomMultipleNoClassifier:
      case DetectorOnDeviceObjectCustomMultipleWithClassifier: {
        NSString *localModelFilePath = [[NSBundle mainBundle] pathForResource:localModelFileName
                                                                       ofType:localModelFileType];
        if (localModelFilePath == nil) {
          NSLog(@"Failed to find custom local model file: %@.%@", localModelFileName,
                localModelFileType);
          return;
        }
        MLKLocalModel *localModel = [[MLKLocalModel alloc] initWithPath:localModelFilePath];
        // The `options.detectorMode` defaults to `MLKObjectDetectorModeStream`.
        MLKCustomObjectDetectorOptions *options =
            [[MLKCustomObjectDetectorOptions alloc] initWithLocalModel:localModel];
        options.shouldEnableClassification = shouldEnableClassification;
        options.shouldEnableMultipleObjects = shouldEnableMultipleObjects;
        [self detectObjectsOnDeviceInImage:visionImage
                                     width:imageWidth
                                    height:imageHeight
                                   options:options];
        break;
      }
    }
  } else {
    NSLog(@"%@", @"Failed to get image buffer from sample buffer.");
  }
}

#pragma mark - Private

/**
 * Resets any detector instances which use a conventional lifecycle paradigm. This method is
 * expected to be invoked on the AVCaptureOutput queue - the same queue on which detection is run.
 *
 * @param activeDetector The detector mode for which detection will be run.
 */
- (void)resetManagedLifecycleDetectorsForActiveDetector:(Detector)activeDetector {
  if (activeDetector == self.lastDetector) {
    // Same row as before, no need to reset any detectors.
    return;
  }
  // Clear the old detector, if applicable.
  switch (self.lastDetector) {
    case DetectorPose:
    case DetectorPoseAccurate:
      self.poseDetector = nil;
      break;
    case DetectorSegmentationSelfie:
      self.segmenter = nil;
    default:
      break;
  }
  // Initialize the new detector, if applicable.
  switch (activeDetector) {
    case DetectorPose:
    case DetectorPoseAccurate: {
      // The `options.detectorMode` defaults to `MLKPoseDetectorModeStream`.
      MLKCommonPoseDetectorOptions *options = activeDetector == DetectorPose
          ? [[MLKPoseDetectorOptions alloc] init] : [[MLKAccuratePoseDetectorOptions alloc] init];
      self.poseDetector = [MLKPoseDetector poseDetectorWithOptions:options];
      break;
    }
    case DetectorSegmentationSelfie: {
      // The `options.segmenterMode` defaults to `MLKSegmenterModeStream`.
      MLKSelfieSegmenterOptions *options = [[MLKSelfieSegmenterOptions alloc] init];
      self.segmenter = [MLKSegmenter segmenterWithOptions:options];
      break;
    }
    default:
      break;
  }
  self.lastDetector = activeDetector;
}

@end

NS_ASSUME_NONNULL_END
