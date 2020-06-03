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

@end

NS_ASSUME_NONNULL_END
