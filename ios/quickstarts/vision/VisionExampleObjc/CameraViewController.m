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

static const CGFloat MLKSmallDotRadius = 4.0;
static const CGFloat MLKconstantScale = 1.0;

@interface CameraViewController () <AVCaptureVideoDataOutputSampleBufferDelegate>

typedef NS_ENUM(NSInteger, Detector) {
  DetectorOnDeviceBarcode,
  DetectorOnDeviceFace,
  DetectorOnDeviceText,
  DetectorOnDeviceObjectProminentNoClassifier,
  DetectorOnDeviceObjectProminentWithClassifier,
  DetectorOnDeviceObjectMultipleNoClassifier,
  DetectorOnDeviceObjectMultipleWithClassifier,
  DetectorOnDeviceObjectCustomProminentNoClassifier,
  DetectorOnDeviceObjectCustomProminentWithClassifier,
  DetectorOnDeviceObjectCustomMultipleNoClassifier,
  DetectorOnDeviceObjectCustomMultipleWithClassifier,
  DetectorPoseAccurate,
  DetectorPoseFast,
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

/** Lazily-initialized in an atomic getter. Reset to `nil` when a new detector is chosen. */
@property(nonatomic, nullable) MLKPoseDetector *poseDetector;

@end

@implementation CameraViewController

@synthesize poseDetector = _poseDetector;

- (NSString *)stringForDetector:(Detector)detector {
  switch (detector) {
    case DetectorOnDeviceBarcode:
      return @"On-Device Barcode Scanner";
    case DetectorOnDeviceFace:
      return @"On-Device Face Detection";
    case DetectorOnDeviceText:
      return @"On-Device Text Recognition";
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
    case DetectorPoseFast:
      return @"Pose, fast";
    case DetectorPoseAccurate:
      return @"Pose, accurate";
  }
}

- (void)viewDidLoad {
  [super viewDidLoad];
  _detectors = @[
    @(DetectorOnDeviceBarcode),
    @(DetectorOnDeviceFace),
    @(DetectorOnDeviceText),
    @(DetectorOnDeviceObjectProminentNoClassifier),
    @(DetectorOnDeviceObjectProminentWithClassifier),
    @(DetectorOnDeviceObjectMultipleNoClassifier),
    @(DetectorOnDeviceObjectMultipleWithClassifier),
    @(DetectorOnDeviceObjectCustomProminentNoClassifier),
    @(DetectorOnDeviceObjectCustomProminentWithClassifier),
    @(DetectorOnDeviceObjectCustomMultipleNoClassifier),
    @(DetectorOnDeviceObjectCustomMultipleWithClassifier),
    @(DetectorPoseAccurate),
    @(DetectorPoseFast),
  ];
  _currentDetector = DetectorOnDeviceFace;
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
    [strongSelf updatePreviewOverlayView];
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
                              height:(CGFloat)height {
  MLKTextRecognizer *textRecognizer = [MLKTextRecognizer textRecognizer];
  NSError *error;
  MLKText *text = [textRecognizer resultsInImage:image error:&error];
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf removeDetectionAnnotations];
    [strongSelf updatePreviewOverlayView];
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
        NSArray<NSValue *> *points = [strongSelf convertedPointsFromPoints:line.cornerPoints
                                                                     width:width
                                                                    height:height];
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
          [strongSelf.annotationOverlayView addSubview:label];
        }
      }
    }
  });
}

- (void)detectPoseInImage:(MLKVisionImage *)image width:(CGFloat)width height:(CGFloat)height {
  NSError *error;
  NSArray<MLKPose *> *poses = [self.poseDetector resultsInImage:image error:&error];
  __weak typeof(self) weakSelf = self;
  dispatch_sync(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf updatePreviewOverlayView];
    [strongSelf removeDetectionAnnotations];

    if (poses.count == 0) {
      if (error != nil) {
        NSLog(@"Failed to detect pose with error: %@", error.localizedDescription);
      }
      return;
    }

    // Pose detection currently only supports single pose.
    MLKPose *pose = poses.firstObject;

    NSDictionary<MLKPoseLandmarkType, NSArray<MLKPoseLandmarkType> *> *connections =
        [UIUtilities poseConnections];

    for (MLKPoseLandmarkType landmarkType in connections) {
      for (MLKPoseLandmarkType connectedLandmarkType in connections[landmarkType]) {
        MLKPoseLandmark *landmark = [pose landmarkOfType:landmarkType];
        MLKPoseLandmark *connectedLandmark = [pose landmarkOfType:connectedLandmarkType];
        CGPoint landmarkPosition = [strongSelf normalizedPointFromVisionPoint:landmark.position
                                                                        width:width
                                                                       height:height];
        CGPoint connectedLandmarkPosition =
            [strongSelf normalizedPointFromVisionPoint:connectedLandmark.position
                                                 width:width
                                                height:height];
        [UIUtilities addLineSegmentFromPoint:landmarkPosition
                                     toPoint:connectedLandmarkPosition
                                      inView:strongSelf.annotationOverlayView
                                       color:UIColor.greenColor
                                       width:3.0f];
      }
    }
    for (MLKPoseLandmark *landmark in pose.landmarks) {
      CGPoint position = [strongSelf normalizedPointFromVisionPoint:landmark.position
                                                              width:width
                                                             height:height];
      [UIUtilities addCircleAtPoint:position
                             toView:strongSelf.annotationOverlayView
                              color:UIColor.blueColor
                             radius:MLKSmallDotRadius];
    }
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
    [strongSelf updatePreviewOverlayView];
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
      [description appendString:barcode.rawValue];
      label.text = description;

      label.adjustsFontSizeToFitWidth = YES;
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
    [strongSelf updatePreviewOverlayView];
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
                                                   handler:^(UIAlertAction *_Nonnull action) {
                                                     self.currentDetector = detector;
                                                     [self removeDetectionAnnotations];

                                                     // Reset the pose detector to `nil` when a new
                                                     // detector row is chosen. The detector will be
                                                     // re-initialized via its getter when it is
                                                     // needed for detection again.
                                                     self.poseDetector = nil;
                                                   }];
    if (detector == _currentDetector) {
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
                                              scale:MLKconstantScale
                                        orientation:UIImageOrientationRight];
  if (_isUsingFrontCamera) {
    CGImageRef rotatedCGImage = rotatedImage.CGImage;
    if (rotatedCGImage == nil) {
      return;
    }
    UIImage *mirroredImage = [UIImage imageWithCGImage:rotatedCGImage
                                                 scale:MLKconstantScale
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
    BOOL shouldEnableClassification = NO;
    BOOL shouldEnableMultipleObjects = NO;
    switch (_currentDetector) {
      case DetectorOnDeviceObjectCustomMultipleWithClassifier:
      case DetectorOnDeviceObjectCustomProminentWithClassifier:
      case DetectorOnDeviceObjectMultipleWithClassifier:
      case DetectorOnDeviceObjectProminentWithClassifier:
        shouldEnableClassification = YES;
      default:
        break;
    }
    switch (_currentDetector) {
      case DetectorOnDeviceObjectCustomMultipleNoClassifier:
      case DetectorOnDeviceObjectCustomMultipleWithClassifier:
      case DetectorOnDeviceObjectMultipleNoClassifier:
      case DetectorOnDeviceObjectMultipleWithClassifier:
        shouldEnableMultipleObjects = YES;
      default:
        break;
    }

    switch (_currentDetector) {
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
      case DetectorOnDeviceText:
        [self recognizeTextOnDeviceInImage:visionImage width:imageWidth height:imageHeight];
        break;
      case DetectorPoseAccurate:
      case DetectorPoseFast:
        [self detectPoseInImage:visionImage width:imageWidth height:imageHeight];
        break;
      case DetectorOnDeviceObjectProminentNoClassifier:
      case DetectorOnDeviceObjectProminentWithClassifier:
      case DetectorOnDeviceObjectMultipleNoClassifier:
      case DetectorOnDeviceObjectMultipleWithClassifier: {
        MLKObjectDetectorOptions *options = [MLKObjectDetectorOptions new];
        options.shouldEnableClassification = shouldEnableClassification;
        options.shouldEnableMultipleObjects = shouldEnableMultipleObjects;
        options.detectorMode = MLKObjectDetectorModeStream;
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
        MLKCustomObjectDetectorOptions *options =
            [[MLKCustomObjectDetectorOptions alloc] initWithLocalModel:localModel];
        options.shouldEnableClassification = shouldEnableClassification;
        options.shouldEnableMultipleObjects = shouldEnableMultipleObjects;
        options.detectorMode = MLKObjectDetectorModeStream;
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

#pragma mark - Getter / setter overrides

- (nullable MLKPoseDetector *)poseDetector {
  // Synchronize access to ensure thread-safety of the underlying ivar since it is modified on the
  // main thread and used for processing on the video output queue.
  @synchronized(self) {
    if (_poseDetector == nil) {
      MLKPoseDetectorOptions *options = [[MLKPoseDetectorOptions alloc] init];
      options.detectorMode = MLKPoseDetectorModeStream;
      options.performanceMode = self.currentDetector == DetectorPoseFast
                                    ? MLKPoseDetectorPerformanceModeFast
                                    : MLKPoseDetectorPerformanceModeAccurate;
      _poseDetector = [MLKPoseDetector poseDetectorWithOptions:options];
    }
    return _poseDetector;
  }
}

- (void)setPoseDetector:(MLKPoseDetector *_Nullable)poseDetector {
  @synchronized(self) {
    _poseDetector = poseDetector;
  }
}

@end

NS_ASSUME_NONNULL_END
