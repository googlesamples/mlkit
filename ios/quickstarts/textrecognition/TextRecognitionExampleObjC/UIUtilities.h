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

#import <AVFoundation/AVFoundation.h>
#import <CoreVideo/CoreVideo.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

@interface UIUtilities : NSObject

+ (void)addRectangle:(CGRect)rectangle toView:(UIView *)view color:(UIColor *)color;
+ (void)addShapeWithPoints:(NSArray<NSValue *> *)points
                    toView:(UIView *)view
                     color:(UIColor *)color;
+ (UIImageOrientation)imageOrientation;
+ (UIImageOrientation)imageOrientationFromDevicePosition:(AVCaptureDevicePosition)devicePosition;

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
