/**
 * Copyright 2020 Google ML Kit team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * A utility for creating UI.
 */
@interface MLKUIUtilities : NSObject

/**
 * Converts given `AVCaptureDevicePosition` and `UIDeviceOrientation` into `UIImageOrientation`.
 */
+ (UIImageOrientation)imageOrientationFromOrientation:(UIDeviceOrientation)deviceOrientation
                            withCaptureDevicePosition:(AVCaptureDevicePosition)position;

/**
 * Rotates the given image, based on the current device orientation, so its orientation is `.up`.
 *
 * @param image The image that comes from camera.
 * @return Image with orientation adjusted to upright.
 */
+ (UIImage *)orientedUpImageFromImage:(UIImage *)image;

/** Returns safe area insets of the view. */
+ (UIEdgeInsets)safeAreaInsets;

@end

NS_ASSUME_NONNULL_END
