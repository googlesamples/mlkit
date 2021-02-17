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
#import <CoreVideo/CoreVideo.h>
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

/**
 * Applies a segmentation mask to an image buffer by replacing colors in the segmented regions.
 *
 * @param The mask output from a segmentation operation.
 * @param imageBuffer The image buffer on which segmentation was performed. Must have pixel format
 *     type `kCVPixelFormatType_32BGRA`.
 * @param backgroundColor Optional color to render into the background region (i.e. outside of the
 *     segmented region of interest).
 * @param foregroundColor Optional color to render into the foreground region (i.e. inside the
 *     segmented region of interest).
 */
+ (void)applySegmentationMask:(MLKSegmentationMask *)mask
                toImageBuffer:(CVImageBufferRef)imageBuffer
          withBackgroundColor:(nullable UIColor *)backgroundColor
              foregroundColor:(nullable UIColor *)foregroundColor;

/**
 * Converts an image buffer to a `UIImage`.
 *
 * @param imageBuffer The image buffer which should be converted.
 * @param orientation The orientation already applied to the image.
 * @return A new `UIImage` instance.
 */
+ (UIImage *)UIImageFromImageBuffer:(CVImageBufferRef)imageBuffer
                        orientation:(UIImageOrientation)orientation;

/**
 * Converts a `UIImage` to an image buffer.
 *
 * @param image The `UIImage` which should be converted.
 * @return The image buffer. Callers own the returned buffer and are responsible for releasing it
 *     when it is no longer needed. Additionally, the image orientation will not be accounted for
 *     in the returned buffer, so callers must keep track of the orientation separately.
 */
+ (CVImageBufferRef)imageBufferFromUIImage:(UIImage *)image;

@end

NS_ASSUME_NONNULL_END
