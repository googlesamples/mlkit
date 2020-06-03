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

#import <CoreMedia/CoreMedia.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/** Provides image related utility APIs. */
@interface MLKImageUtilities : NSObject

/**
 * Converts a `CMSampleBuffer` to a `UIImage`, returns `nil` when `sampleBuffer` is unsupported.
 * Currently this method only handles `CMSampleBufferRef` with RGB color space.
 *
 * @param sampleBuffer The given `CMSampleBufferRef`.
 * @return Converted `UIImage`.
 */
+ (nullable UIImage *)imageFromSampleBuffer:(CMSampleBufferRef)sampleBuffer;

/**
 * Crops `CMSampleBuffer` to a specified rect. This will not alter the original data. Currently this
 * method only handles `CMSampleBufferRef` with RGB color space.
 *
 * @param sampleBuffer The original `CMSampleBuffer`.
 * @param rect The rect to crop to.
 * @return A `CMSampleBuffer` cropped to the given rect.
 */
+ (CMSampleBufferRef)croppedSampleBuffer:(CMSampleBufferRef)sampleBuffer withRect:(CGRect)rect;

@end

NS_ASSUME_NONNULL_END
