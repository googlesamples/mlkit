//
//  Copyright (c) 2021 Google Inc.
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

static NSString *const videoDataOutputQueueLabel =
    @"com.google.mlkit.textrecognition.VideoDataOutputQueue";
static NSString *const sessionQueueLabel = @"com.google.mlkit.textrecognition.SessionQueue";

@interface CameraViewController () <AVCaptureVideoDataOutputSampleBufferDelegate>

@property(nonatomic) bool isUsingFrontCamera;
@property(nonatomic, nonnull) AVCaptureVideoPreviewLayer *previewLayer;
@property(nonatomic) AVCaptureSession *captureSession;
@property(nonatomic) dispatch_queue_t sessionQueue;
@property(nonatomic) UIView *annotationOverlayView;
@property(nonatomic) UIImageView *previewOverlayView;
@property(weak, nonatomic) IBOutlet UIView *cameraView;
@property(nonatomic) CMSampleBufferRef lastFrame;

@end

@implementation CameraViewController

- (void)viewDidLoad {
  [super viewDidLoad];
  _isUsingFrontCamera = NO;
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

- (IBAction)switchCamera:(id)sender {
  self.isUsingFrontCamera = !_isUsingFrontCamera;
  [self removeDetectionAnnotations];
  [self setUpCaptureSessionInput];
}

#pragma mark - Detections

- (void)recognizeTextInImage:(MLKVisionImage *)image width:(CGFloat)width height:(CGFloat)height {
  MLKTextRecognizer *textRecognizer = [MLKTextRecognizer textRecognizer];
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
    [self recognizeTextInImage:visionImage width:imageWidth height:imageHeight];
  }
}

@end

NS_ASSUME_NONNULL_END
