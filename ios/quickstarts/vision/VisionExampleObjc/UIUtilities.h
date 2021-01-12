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

#import <AVFoundation/AVFoundation.h>
#import <UIKit/UIKit.h>

@import MLKit;

NS_ASSUME_NONNULL_BEGIN

@interface UIUtilities : NSObject

+ (void)addCircleAtPoint:(CGPoint)point
                  toView:(UIView *)view
                   color:(UIColor *)color
                  radius:(CGFloat)radius;

+ (void)addLineSegmentFromPoint:(CGPoint)fromPoint
                        toPoint:(CGPoint)toPoint
                         inView:(UIView *)view
                          color:(UIColor *)color
                          width:(CGFloat)width;

+ (void)addRectangle:(CGRect)rectangle toView:(UIView *)view color:(UIColor *)color;
+ (void)addShapeWithPoints:(NSArray<NSValue *> *)points
                    toView:(UIView *)view
                     color:(UIColor *)color;
+ (UIImageOrientation)imageOrientation;
+ (UIImageOrientation)imageOrientationFromDevicePosition:(AVCaptureDevicePosition)devicePosition;
+ (UIDeviceOrientation)currentUIOrientation;

/**
 * Returns an overlay view for visualizing a given `pose`.
 *
 * @param pose The pose which will be visualized.
 * @param bounds The bounds of the view to which this overlay will be added. The overlay view's
 *     bounds will match this value.
 * @param lineWidth The width of the lines connecting the landmark dots.
 * @param dotRadius The radius of the landmark dots.
 * @param positionTransformationBlock Block which transforms a landmark `position` to the
 *     `UIView` `CGPoint` coordinate where it should be shown on-screen.
 */
+ (UIView *)poseOverlayViewForPose:(MLKPose *)pose
                  inViewWithBounds:(CGRect)bounds
                         lineWidth:(CGFloat)lineWidth
                         dotRadius:(CGFloat)dotRadius
       positionTransformationBlock:(CGPoint (^)(MLKVisionPoint *))positionTransformationBlock;

@end

NS_ASSUME_NONNULL_END
