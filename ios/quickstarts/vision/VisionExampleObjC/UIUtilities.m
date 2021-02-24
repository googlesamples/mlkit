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

#import "UIUtilities.h"

@import MLKit;

static CGFloat const circleViewAlpha = 0.7;
static CGFloat const rectangleViewAlpha = 0.3;
static CGFloat const shapeViewAlpha = 0.3;
static CGFloat const rectangleViewCornerRadius = 10.0;

NS_ASSUME_NONNULL_BEGIN

@implementation UIUtilities

+ (void)addCircleAtPoint:(CGPoint)point
                  toView:(UIView *)view
                   color:(UIColor *)color
                  radius:(CGFloat)radius {
  CGFloat divisor = 2.0;
  CGFloat xCoord = point.x - radius / divisor;
  CGFloat yCoord = point.y - radius / divisor;
  CGRect circleRect = CGRectMake(xCoord, yCoord, radius, radius);
  UIView *circleView = [[UIView alloc] initWithFrame:circleRect];
  circleView.layer.cornerRadius = radius / divisor;
  circleView.alpha = circleViewAlpha;
  circleView.backgroundColor = color;
  [view addSubview:circleView];
}

+ (void)addLineSegmentFromPoint:(CGPoint)fromPoint
                        toPoint:(CGPoint)toPoint
                         inView:(UIView *)view
                          color:(UIColor *)color
                          width:(CGFloat)width {
  UIBezierPath *path = [UIBezierPath bezierPath];
  [path moveToPoint:fromPoint];
  [path addLineToPoint:toPoint];
  CAShapeLayer *lineLayer = [CAShapeLayer layer];
  lineLayer.path = path.CGPath;
  lineLayer.strokeColor = color.CGColor;
  lineLayer.fillColor = nil;
  lineLayer.opacity = 1.0f;
  lineLayer.lineWidth = width;
  UIView *lineView = [[UIView alloc] initWithFrame:view.bounds];
  [lineView.layer addSublayer:lineLayer];
  [view addSubview:lineView];
}

+ (void)addRectangle:(CGRect)rectangle toView:(UIView *)view color:(UIColor *)color {
  UIView *rectangleView = [[UIView alloc] initWithFrame:rectangle];
  rectangleView.layer.cornerRadius = rectangleViewCornerRadius;
  rectangleView.alpha = rectangleViewAlpha;
  rectangleView.backgroundColor = color;
  [view addSubview:rectangleView];
}

+ (void)addShapeWithPoints:(NSArray<NSValue *> *)points
                    toView:(UIView *)view
                     color:(UIColor *)color {
  UIBezierPath *path = [UIBezierPath new];
  for (int i = 0; i < [points count]; i++) {
    CGPoint point = points[i].CGPointValue;
    if (i == 0) {
      [path moveToPoint:point];
    } else {
      [path addLineToPoint:point];
    }
    if (i == points.count - 1) {
      [path closePath];
    }
  }
  CAShapeLayer *shapeLayer = [CAShapeLayer new];
  shapeLayer.path = path.CGPath;
  shapeLayer.fillColor = color.CGColor;
  CGRect rect = CGRectMake(0, 0, view.frame.size.width, view.frame.size.height);
  UIView *shapeView = [[UIView alloc] initWithFrame:rect];
  shapeView.alpha = shapeViewAlpha;
  [shapeView.layer addSublayer:shapeLayer];
  [view addSubview:shapeView];
}

+ (UIImageOrientation)imageOrientation {
  return [self imageOrientationFromDevicePosition:AVCaptureDevicePositionBack];
}

+ (UIImageOrientation)imageOrientationFromDevicePosition:(AVCaptureDevicePosition)devicePosition {
  UIDeviceOrientation deviceOrientation = UIDevice.currentDevice.orientation;
  if (deviceOrientation == UIDeviceOrientationFaceDown ||
      deviceOrientation == UIDeviceOrientationFaceUp ||
      deviceOrientation == UIDeviceOrientationUnknown) {
    deviceOrientation = [self currentUIOrientation];
  }
  switch (deviceOrientation) {
    case UIDeviceOrientationPortrait:
      return devicePosition == AVCaptureDevicePositionFront ? UIImageOrientationLeftMirrored
                                                            : UIImageOrientationRight;
    case UIDeviceOrientationLandscapeLeft:
      return devicePosition == AVCaptureDevicePositionFront ? UIImageOrientationDownMirrored
                                                            : UIImageOrientationUp;
    case UIDeviceOrientationPortraitUpsideDown:
      return devicePosition == AVCaptureDevicePositionFront ? UIImageOrientationRightMirrored
                                                            : UIImageOrientationLeft;
    case UIDeviceOrientationLandscapeRight:
      return devicePosition == AVCaptureDevicePositionFront ? UIImageOrientationUpMirrored
                                                            : UIImageOrientationDown;
    case UIDeviceOrientationFaceDown:
    case UIDeviceOrientationFaceUp:
    case UIDeviceOrientationUnknown:
      return UIImageOrientationUp;
  }
}

+ (UIDeviceOrientation)currentUIOrientation {
  UIDeviceOrientation (^deviceOrientation)(void) = ^UIDeviceOrientation(void) {
    switch (UIApplication.sharedApplication.statusBarOrientation) {
      case UIInterfaceOrientationLandscapeLeft:
        return UIDeviceOrientationLandscapeRight;
      case UIInterfaceOrientationLandscapeRight:
        return UIDeviceOrientationLandscapeLeft;
      case UIInterfaceOrientationPortraitUpsideDown:
        return UIDeviceOrientationPortraitUpsideDown;
      case UIInterfaceOrientationPortrait:
      case UIInterfaceOrientationUnknown:
        return UIDeviceOrientationPortrait;
    }
  };

  if (NSThread.isMainThread) {
    return deviceOrientation();
  } else {
    __block UIDeviceOrientation currentOrientation = UIDeviceOrientationPortrait;
    dispatch_sync(dispatch_get_main_queue(), ^{
      currentOrientation = deviceOrientation();
    });
    return currentOrientation;
  }
}

+ (void)applySegmentationMask:(MLKSegmentationMask *)mask
                toImageBuffer:(CVImageBufferRef)imageBuffer
          withBackgroundColor:(nullable UIColor *)backgroundColor
              foregroundColor:(nullable UIColor *)foregroundColor {
  NSAssert(CVPixelBufferGetPixelFormatType(imageBuffer) == kCVPixelFormatType_32BGRA,
           @"Image buffer must have 32BGRA pixel format type");
  size_t width = CVPixelBufferGetWidth(mask.buffer);
  size_t height = CVPixelBufferGetHeight(mask.buffer);
  NSAssert(CVPixelBufferGetWidth(imageBuffer) == width, @"Height must match");
  NSAssert(CVPixelBufferGetHeight(imageBuffer) == height, @"Width must match");

  if (backgroundColor == nil && foregroundColor == nil) {
    return;
  }

  CVPixelBufferLockBaseAddress(imageBuffer, 0);
  CVPixelBufferLockBaseAddress(mask.buffer, kCVPixelBufferLock_ReadOnly);

  float *maskAddress = (float *)CVPixelBufferGetBaseAddress(mask.buffer);
  size_t maskBytesPerRow = CVPixelBufferGetBytesPerRow(mask.buffer);

  unsigned char *imageAddress = (unsigned char *)CVPixelBufferGetBaseAddress(imageBuffer);
  size_t bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer);
  static const int kBGRABytesPerPixel = 4;

  foregroundColor = foregroundColor ?: UIColor.clearColor;
  backgroundColor = backgroundColor ?: UIColor.clearColor;
  CGFloat redFG, greenFG, blueFG, alphaFG;
  CGFloat redBG, greenBG, blueBG, alphaBG;
  [foregroundColor getRed:&redFG green:&greenFG blue:&blueFG alpha:&alphaFG];
  [backgroundColor getRed:&redBG green:&greenBG blue:&blueBG alpha:&alphaBG];

  static const float kMaxColorComponentValue = 255.0f;

  for (int row = 0; row < height; ++row) {
    for (int col = 0; col < width; ++col) {
      int pixelOffset = col * kBGRABytesPerPixel;
      int blueOffset = pixelOffset;
      int greenOffset = pixelOffset + 1;
      int redOffset = pixelOffset + 2;
      int alphaOffset = pixelOffset + 3;

      float maskValue = maskAddress[col];
      float backgroundRegionRatio = 1.0f - maskValue;
      float foregroundRegionRatio = maskValue;

      float originalPixelRed = imageAddress[redOffset] / kMaxColorComponentValue;
      float originalPixelGreen = imageAddress[greenOffset] / kMaxColorComponentValue;
      float originalPixelBlue = imageAddress[blueOffset] / kMaxColorComponentValue;
      float originalPixelAlpha = imageAddress[alphaOffset] / kMaxColorComponentValue;

      float redOverlay = redBG * backgroundRegionRatio + redFG * foregroundRegionRatio;
      float greenOverlay = greenBG * backgroundRegionRatio + greenFG * foregroundRegionRatio;
      float blueOverlay = blueBG * backgroundRegionRatio + blueFG * foregroundRegionRatio;
      float alphaOverlay = alphaBG * backgroundRegionRatio + alphaFG * foregroundRegionRatio;

      // Calculate composite color component values.
      // Derived from https://en.wikipedia.org/wiki/Alpha_compositing#Alpha_blending
      float compositeAlpha = ((1.0f - alphaOverlay) * originalPixelAlpha) + alphaOverlay;
      float compositeRed = 0.0f;
      float compositeGreen = 0.0f;
      float compositeBlue = 0.0f;
      // Only perform rgb blending calculations if the output alpha is > 0. A zero-value alpha
      // means none of the color channels actually matter, and would introduce division by 0.
      if (fabs(compositeAlpha) > FLT_EPSILON) {
        compositeRed = (((1.0f - alphaOverlay) * originalPixelAlpha * originalPixelRed) +
                        (alphaOverlay * redOverlay)) /
                       compositeAlpha;
        compositeGreen = (((1.0f - alphaOverlay) * originalPixelAlpha * originalPixelGreen) +
                          (alphaOverlay * greenOverlay)) /
                         compositeAlpha;
        compositeBlue = (((1.0f - alphaOverlay) * originalPixelAlpha * originalPixelBlue) +
                         (alphaOverlay * blueOverlay)) /
                        compositeAlpha;
      }

      imageAddress[blueOffset] = compositeBlue * kMaxColorComponentValue;
      imageAddress[greenOffset] = compositeGreen * kMaxColorComponentValue;
      imageAddress[redOffset] = compositeRed * kMaxColorComponentValue;
      imageAddress[alphaOffset] = compositeAlpha * kMaxColorComponentValue;
    }
    imageAddress += bytesPerRow / sizeof(unsigned char);
    maskAddress += maskBytesPerRow / sizeof(float);
  }

  CVPixelBufferUnlockBaseAddress(imageBuffer, 0);
  CVPixelBufferUnlockBaseAddress(mask.buffer, kCVPixelBufferLock_ReadOnly);
}

+ (UIImage *)UIImageFromImageBuffer:(CVImageBufferRef)imageBuffer
                        orientation:(UIImageOrientation)orientation {
  CIImage *CIImg = [CIImage imageWithCVPixelBuffer:imageBuffer];
  CIContext *context = [[CIContext alloc] initWithOptions:nil];
  CGImageRef CGImg = [context createCGImage:CIImg fromRect:CIImg.extent];
  UIImage *image = [UIImage imageWithCGImage:CGImg scale:1.0f orientation:orientation];
  CGImageRelease(CGImg);
  return image;
}

+ (CVImageBufferRef)imageBufferFromUIImage:(UIImage *)image {
  size_t width = CGImageGetWidth(image.CGImage);
  size_t height = CGImageGetHeight(image.CGImage);

  CVPixelBufferRef imageBuffer;
  CVPixelBufferCreate(kCFAllocatorDefault, width, height, kCVPixelFormatType_32BGRA,
                      (__bridge CFDictionaryRef) @{}, &imageBuffer);

  CVPixelBufferLockBaseAddress(imageBuffer, 0);

  void *baseAddress = CVPixelBufferGetBaseAddress(imageBuffer);
  CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
  size_t bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer);
  CGContextRef context = CGBitmapContextCreate(
      baseAddress, width, height, /*bitsPerComponent=*/8, bytesPerRow, colorSpace,
      kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst);

  CGRect rect = CGRectMake(0, 0, width, height);
  CGContextClearRect(context, rect);
  CGContextDrawImage(context, rect, image.CGImage);

  CGContextRelease(context);
  CGColorSpaceRelease(colorSpace);
  CVPixelBufferUnlockBaseAddress(imageBuffer, 0);

  return imageBuffer;
}

+ (UIView *)poseOverlayViewForPose:(MLKPose *)pose
                  inViewWithBounds:(CGRect)bounds
                         lineWidth:(CGFloat)lineWidth
                         dotRadius:(CGFloat)dotRadius
       positionTransformationBlock:
           (CGPoint (^)(MLKVisionPoint *position))positionTransformationBlock {
  UIView *overlayView = [[UIView alloc] initWithFrame:bounds];

  CGFloat lowerBodyHeight =
      [UIUtilities distanceFromPoint:[pose landmarkOfType:MLKPoseLandmarkTypeLeftAnkle].position
                             toPoint:[pose landmarkOfType:MLKPoseLandmarkTypeLeftKnee].position] +
      [UIUtilities distanceFromPoint:[pose landmarkOfType:MLKPoseLandmarkTypeLeftKnee].position
                             toPoint:[pose landmarkOfType:MLKPoseLandmarkTypeLeftHip].position];

  // Pick arbitrary z extents to form a range of z values mapped to our colors. Red = close, blue
  // = far. Assume that the z values will roughly follow physical extents of the human body, but
  // apply an adjustment ratio to increase this color-coded z-range because this is not always the
  // case.
  static const CGFloat kAdjustmentRatio = 1.2f;
  CGFloat nearZExtent = -lowerBodyHeight * kAdjustmentRatio;
  CGFloat farZExtent = lowerBodyHeight * kAdjustmentRatio;
  CGFloat zColorRange = farZExtent - nearZExtent;
  UIColor *nearZColor = UIColor.redColor;
  UIColor *farZColor = UIColor.blueColor;

  NSDictionary<MLKPoseLandmarkType, NSArray<MLKPoseLandmarkType> *> *connections =
      [UIUtilities poseConnections];

  for (MLKPoseLandmarkType landmarkType in connections) {
    for (MLKPoseLandmarkType connectedLandmarkType in connections[landmarkType]) {
      MLKPoseLandmark *landmark = [pose landmarkOfType:landmarkType];
      MLKPoseLandmark *connectedLandmark = [pose landmarkOfType:connectedLandmarkType];
      CGPoint landmarkPosition = positionTransformationBlock(landmark.position);
      CGPoint connectedLandmarkPosition = positionTransformationBlock(connectedLandmark.position);

      CGFloat landmarkZRatio = (landmark.position.z - nearZExtent) / zColorRange;
      CGFloat connectedLandmarkZRatio = (connectedLandmark.position.z - nearZExtent) / zColorRange;

      UIColor *startColor = [UIUtilities colorInterpolatedFromColor:nearZColor
                                                            toColor:farZColor
                                                              ratio:landmarkZRatio];
      UIColor *endColor = [UIUtilities colorInterpolatedFromColor:nearZColor
                                                          toColor:farZColor
                                                            ratio:connectedLandmarkZRatio];
      [UIUtilities addLineSegmentFromPoint:landmarkPosition
                                   toPoint:connectedLandmarkPosition
                                    inView:overlayView
                                    colors:@[ startColor, endColor ]
                                     width:lineWidth];
    }
  }
  for (MLKPoseLandmark *landmark in pose.landmarks) {
    CGPoint position = positionTransformationBlock(landmark.position);
    [UIUtilities addCircleAtPoint:position
                           toView:overlayView
                            color:UIColor.blueColor
                           radius:dotRadius];
  }
  return overlayView;
}

/**
 * Adds a gradient-colored line segment subview in a given `view`.
 *
 * @param fromPoint The starting point of the line, in the view's coordinate space.
 * @param toPoint The end point of the line, in the view's coordinate space.
 * @param view The view to which the line should be added as a subview.
 * @param colors The colors that the gradient should traverse over. Must be non-empty.
 * @param width The width of the line segment.
 */
+ (void)addLineSegmentFromPoint:(CGPoint)fromPoint
                        toPoint:(CGPoint)toPoint
                         inView:(UIView *)view
                         colors:(NSArray<UIColor *> *)colors
                          width:(CGFloat)width {
  CGFloat viewWidth = CGRectGetWidth(view.bounds);
  CGFloat viewHeight = CGRectGetHeight(view.bounds);
  if (viewWidth == 0.0f || viewHeight == 0.0f) {
    return;
  }

  UIBezierPath *path = [UIBezierPath bezierPath];
  [path moveToPoint:fromPoint];
  [path addLineToPoint:toPoint];
  CAShapeLayer *lineMaskLayer = [CAShapeLayer layer];
  lineMaskLayer.path = path.CGPath;
  lineMaskLayer.strokeColor = UIColor.blackColor.CGColor;
  lineMaskLayer.fillColor = nil;
  lineMaskLayer.opacity = 1.0f;
  lineMaskLayer.lineWidth = width;

  CAGradientLayer *gradientLayer = [CAGradientLayer layer];
  gradientLayer.startPoint = CGPointMake(fromPoint.x / viewWidth, fromPoint.y / viewHeight);
  gradientLayer.endPoint = CGPointMake(toPoint.x / viewWidth, toPoint.y / viewHeight);
  gradientLayer.frame = view.bounds;
  NSMutableArray<id> *CGColors = [NSMutableArray arrayWithCapacity:colors.count];
  for (UIColor *color in colors) {
    [CGColors addObject:(id)color.CGColor];
  }
  if (colors.count == 1) {
    // Single-colored lines must still supply a start and end color for the gradient layer to render
    // anything. Just add the single color to the colors list again to fulfill this requirement.
    [CGColors addObject:(id)colors.firstObject.CGColor];
  }
  gradientLayer.colors = CGColors;
  gradientLayer.mask = lineMaskLayer;

  UIView *lineView = [[UIView alloc] initWithFrame:view.bounds];
  [lineView.layer addSublayer:gradientLayer];
  [view addSubview:lineView];
}

/**
 * Returns a color interpolated between two other colors.
 *
 * @param fromColor The start color of the interpolation.
 * @param toColor The end color of the interpolation.
 * @param ratio The ratio in range [0, 1] by which the colors should be interpolated. Passing 0
 *     results in `fromColor` and passing 1 results in `toColor`, whereas passing 0.5 results in a
 *     color that is half-way between `fromColor` and `startColor`. Values are clamped between 0 and
 *     1.
 */
+ (UIColor *)colorInterpolatedFromColor:(UIColor *)fromColor
                                toColor:(UIColor *)toColor
                                  ratio:(CGFloat)ratio {
  CGFloat fromR, fromG, fromB, fromA;
  [fromColor getRed:&fromR green:&fromG blue:&fromB alpha:&fromA];

  CGFloat toR, toG, toB, toA;
  [toColor getRed:&toR green:&toG blue:&toB alpha:&toA];

  // Clamp ratio to [0, 1]
  ratio = MAX(0.0, MIN(ratio, 1.0));

  CGFloat interpolatedR = fromR + (toR - fromR) * ratio;
  CGFloat interpolatedG = fromG + (toG - fromG) * ratio;
  CGFloat interpolatedB = fromB + (toB - fromB) * ratio;
  CGFloat interpolatedA = fromA + (toA - fromA) * ratio;

  return [UIColor colorWithRed:interpolatedR
                         green:interpolatedG
                          blue:interpolatedB
                         alpha:interpolatedA];
}

/**
 * Returns the distance between two 3D points.
 *
 * @param fromPoint The start point.
 * @param toPoint The end point.
 */
+ (CGFloat)distanceFromPoint:(MLKVision3DPoint *)fromPoint toPoint:(MLKVision3DPoint *)toPoint {
  CGFloat xDiff = fromPoint.x - toPoint.x;
  CGFloat yDiff = fromPoint.y - toPoint.y;
  CGFloat zDiff = fromPoint.z - toPoint.z;
  return sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
}

/**
 * Returns the minimum subset of all connected pose landmarks. Each key represents a start landmark,
 * and each value in the key's value array represents an end landmark which is connected to the
 * start landmark. These connections may be used for visualizing the landmark positions on a pose
 * object.
 */
+ (NSDictionary<MLKPoseLandmarkType, NSArray<MLKPoseLandmarkType> *> *)poseConnections {
  static dispatch_once_t onceToken;
  static NSDictionary<MLKPoseLandmarkType, NSArray<MLKPoseLandmarkType> *> *connections;
  dispatch_once(&onceToken, ^{
    connections = @{
      MLKPoseLandmarkTypeLeftEar : @[MLKPoseLandmarkTypeLeftEyeOuter],
      MLKPoseLandmarkTypeLeftEyeOuter : @[MLKPoseLandmarkTypeLeftEye],
      MLKPoseLandmarkTypeLeftEye : @[MLKPoseLandmarkTypeLeftEyeInner],
      MLKPoseLandmarkTypeLeftEyeInner : @[MLKPoseLandmarkTypeNose],
      MLKPoseLandmarkTypeNose : @[MLKPoseLandmarkTypeRightEyeInner],
      MLKPoseLandmarkTypeRightEyeInner : @[MLKPoseLandmarkTypeRightEye],
      MLKPoseLandmarkTypeRightEye : @[MLKPoseLandmarkTypeRightEyeOuter],
      MLKPoseLandmarkTypeRightEyeOuter : @[MLKPoseLandmarkTypeRightEar],
      MLKPoseLandmarkTypeMouthLeft : @[MLKPoseLandmarkTypeMouthRight],
      MLKPoseLandmarkTypeLeftShoulder: @[MLKPoseLandmarkTypeRightShoulder,
                                         MLKPoseLandmarkTypeLeftHip],
      MLKPoseLandmarkTypeRightShoulder : @[MLKPoseLandmarkTypeRightHip,
                                           MLKPoseLandmarkTypeRightElbow],
      MLKPoseLandmarkTypeRightWrist : @[MLKPoseLandmarkTypeRightElbow,
                                        MLKPoseLandmarkTypeRightThumb,
                                        MLKPoseLandmarkTypeRightIndexFinger,
                                        MLKPoseLandmarkTypeRightPinkyFinger],
      MLKPoseLandmarkTypeLeftHip : @[MLKPoseLandmarkTypeRightHip, MLKPoseLandmarkTypeLeftKnee],
      MLKPoseLandmarkTypeRightHip : @[MLKPoseLandmarkTypeRightKnee],
      MLKPoseLandmarkTypeRightKnee : @[MLKPoseLandmarkTypeRightAnkle],
      MLKPoseLandmarkTypeLeftKnee : @[MLKPoseLandmarkTypeLeftAnkle],
      MLKPoseLandmarkTypeLeftElbow : @[MLKPoseLandmarkTypeLeftShoulder],
      MLKPoseLandmarkTypeLeftWrist : @[MLKPoseLandmarkTypeLeftElbow, MLKPoseLandmarkTypeLeftThumb,
                                       MLKPoseLandmarkTypeLeftIndexFinger,
                                       MLKPoseLandmarkTypeLeftPinkyFinger],
      MLKPoseLandmarkTypeLeftAnkle : @[MLKPoseLandmarkTypeLeftHeel, MLKPoseLandmarkTypeLeftToe],
      MLKPoseLandmarkTypeRightAnkle : @[MLKPoseLandmarkTypeRightHeel, MLKPoseLandmarkTypeRightToe],
      MLKPoseLandmarkTypeRightHeel : @[MLKPoseLandmarkTypeRightToe],
      MLKPoseLandmarkTypeLeftHeel : @[MLKPoseLandmarkTypeLeftToe],
      MLKPoseLandmarkTypeRightIndexFinger : @[MLKPoseLandmarkTypeRightPinkyFinger],
      MLKPoseLandmarkTypeLeftIndexFinger : @[MLKPoseLandmarkTypeLeftPinkyFinger],
    };
  });
  return connections;
}

@end

NS_ASSUME_NONNULL_END
