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

#import "UIImage+VisionDetection.h"

NS_ASSUME_NONNULL_BEGIN

@implementation UIImage (VisionDetection)

/**
 * Returns a scaled image to the given size.
 *
 * @param size Maximum size of the returned image.
 * @return Image scaled according to the give size or `nil` if image resize fails.
 */
- (UIImage *)scaledImageWithSize:(CGSize)size {
  UIGraphicsBeginImageContextWithOptions(size, NO, self.scale);
  [self drawInRect:CGRectMake(0, 0, size.width, size.height)];
  UIImage *scaledImage = UIGraphicsGetImageFromCurrentImageContext();
  UIGraphicsEndImageContext();

  // Attempt to convert the scaled image to PNG or JPEG data to preserve the bitmap info.
  if (!scaledImage) {
    return nil;
  }
  NSData *imageData = UIImagePNGRepresentation(scaledImage);
  if (!imageData) {
    imageData = UIImageJPEGRepresentation(scaledImage, 0.8);
  }
  return [UIImage imageWithData:imageData];
}

@end

NS_ASSUME_NONNULL_END
