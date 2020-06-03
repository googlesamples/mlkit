//
//  Copyright (c) 2019 Google Inc.
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

#import <Foundation/Foundation.h>
@import AVFoundation;
@import MLKitVision;

@interface ImagePreparation : NSObject

- (MLKVisionImage *)createImageWithUIImage:(UIImage *)uiImage;
- (MLKVisionImageOrientation)
    imageOrientationFromDeviceOrientation:(UIDeviceOrientation)deviceOrientation
                           cameraPosition:(AVCaptureDevicePosition)cameraPosition;
- (MLKVisionImage *)createImageWithBuffer:(CMSampleBufferRef)sampleBuffer;

@end

@implementation ImagePreparation

- (MLKVisionImage *)createImageWithUIImage:(UIImage *)uiImage {
  // [START create_image_with_uiimage]
  MLKVisionImage *image = [[MLKVisionImage alloc] initWithImage:uiImage];
  // [END create_image_with_uiimage]
  return image;
}

// [START image_orientation_from_device_orientation]
- (MLKVisionImageOrientation)
    imageOrientationFromDeviceOrientation:(UIDeviceOrientation)deviceOrientation
                           cameraPosition:(AVCaptureDevicePosition)cameraPosition {
  switch (deviceOrientation) {
    case UIDeviceOrientationPortrait:
      if (cameraPosition == AVCaptureDevicePositionFront) {
        return MLKVisionImageOrientationLeftTop;
      } else {
        return MLKVisionImageOrientationRightTop;
      }
    case UIDeviceOrientationLandscapeLeft:
      if (cameraPosition == AVCaptureDevicePositionFront) {
        return MLKVisionImageOrientationBottomLeft;
      } else {
        return MLKVisionImageOrientationTopLeft;
      }
    case UIDeviceOrientationPortraitUpsideDown:
      if (cameraPosition == AVCaptureDevicePositionFront) {
        return MLKVisionImageOrientationRightBottom;
      } else {
        return MLKVisionImageOrientationLeftBottom;
      }
    case UIDeviceOrientationLandscapeRight:
      if (cameraPosition == AVCaptureDevicePositionFront) {
        return MLKVisionImageOrientationTopRight;
      } else {
        return MLKVisionImageOrientationBottomRight;
      }
    default:
      return MLKVisionImageOrientationTopLeft;
  }
}
// [END image_orientation_from_device_orientation]

- (MLKVisionImage *)createImageWithBuffer:(CMSampleBufferRef)sampleBuffer {
  // [START create_image_metadata]
  MLKVisionImageMetadata *metadata = [[MLKVisionImageMetadata alloc] init];
  AVCaptureDevicePosition cameraPosition =
      AVCaptureDevicePositionBack;  // Set to the capture device you used.
  metadata.orientation =
      [self imageOrientationFromDeviceOrientation:UIDevice.currentDevice.orientation
                                   cameraPosition:cameraPosition];
  // [END create_image_metadata]

  // [START create_image_with_buffer]
  MLKVisionImage *image = [[MLKVisionImage alloc] initWithBuffer:sampleBuffer];
  image.metadata = metadata;
  // [END create_image_with_buffer]

  return image;
}

@end
