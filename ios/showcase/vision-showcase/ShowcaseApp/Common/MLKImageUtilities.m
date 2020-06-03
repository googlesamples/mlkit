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

#import "MLKImageUtilities.h"

#import <MetalKit/MetalKit.h>

#ifdef __ARM_NEON__
#import "arm_neon.h"
#endif

NS_ASSUME_NONNULL_BEGIN

/** `CIContext` to render pixel buffer to images. */
static CIContext *gCIContext;

@implementation MLKImageUtilities

#pragma mark - NSObject

+ (void)initialize {
  if ([self isKindOfClass:[MLKImageUtilities class]]) {
    gCIContext = [CIContext contextWithMTLDevice:MTLCreateSystemDefaultDevice()];
  }
}

#pragma mark - Public

+ (nullable UIImage *)imageFromSampleBuffer:(CMSampleBufferRef)sampleBuffer {
  if (sampleBuffer == NULL) {
    NSLog(@"Sample buffer is NULL.");
    return nil;
  }
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
  if (imageBuffer == NULL) {
    NSLog(@"Invalid sample buffer.");
    return nil;
  }

  CVPixelBufferLockBaseAddress(imageBuffer, kCVPixelBufferLock_ReadOnly);

  void *baseAddress = CVPixelBufferGetBaseAddress(imageBuffer);
  size_t bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer);
  size_t bitPerComponent = 8;  // TODO(zhoumi): This may vary on other formats.

  size_t width = CVPixelBufferGetWidth(imageBuffer);
  size_t height = CVPixelBufferGetHeight(imageBuffer);

  // TODO(zhoumi): Add more support for non-RGB color space.
  CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();

  if (colorSpace == NULL) {
    NSLog(@"Failed to create RGB color space");
    CVPixelBufferUnlockBaseAddress(imageBuffer, kCVPixelBufferLock_ReadOnly);
    return nil;
  }

  // TODO(zhoumi): Add more support for other formats.
  CGContextRef context =
      CGBitmapContextCreate(baseAddress, width, height, bitPerComponent, bytesPerRow, colorSpace,
                            kCGBitmapByteOrder32Little | kCGImageAlphaPremultipliedFirst);
  if (context == NULL) {
    NSLog(@"Failed to create CGContextRef");
    CGColorSpaceRelease(colorSpace);
    CVPixelBufferUnlockBaseAddress(imageBuffer, 0);
    return nil;
  }

  CGImageRef cgImage = CGBitmapContextCreateImage(context);
  if (cgImage == NULL) {
    NSLog(@"Failed to create CGImage");
    CGColorSpaceRelease(colorSpace);
    CGContextRelease(context);
    CVPixelBufferUnlockBaseAddress(imageBuffer, 0);
    return nil;
  }

  CVPixelBufferUnlockBaseAddress(imageBuffer, 0);
  CGContextRelease(context);
  CGColorSpaceRelease(colorSpace);

  UIImage *image = [UIImage imageWithCGImage:cgImage];
  CGImageRelease(cgImage);
  return image;
}

+ (CMSampleBufferRef)croppedSampleBuffer:(CMSampleBufferRef)sampleBuffer withRect:(CGRect)rect {
  CVImageBufferRef imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);

  CVPixelBufferLockBaseAddress(imageBuffer, 0);

  size_t bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer);
  size_t width = CVPixelBufferGetWidth(imageBuffer);
  size_t bytesPerPixel = bytesPerRow / width;
  void *baseAddressStart = CVPixelBufferGetBaseAddress(imageBuffer);

  NSUInteger cropX = rect.origin.x;
  NSUInteger cropY = rect.origin.y;

  // Start pixel in RGB color space can't be odd.
  if (cropX % 2 != 0) {
    cropX++;
  }

  NSUInteger cropStartOffset = cropY * bytesPerRow + cropX * bytesPerPixel;

  CVPixelBufferRef pixelBuffer = NULL;
  CVReturn error;

  // Initiates pixelBuffer.
  OSType pixelFormat = CVPixelBufferGetPixelFormatType(imageBuffer);
  NSDictionary<NSString *, NSNumber *> *options = @{
    (__bridge NSString *)kCVPixelBufferCGImageCompatibilityKey : @YES,
    (__bridge NSString *)kCVPixelBufferCGBitmapContextCompatibilityKey : @YES,
    (__bridge NSString *)kCVPixelBufferWidthKey : @(rect.size.width),
    (__bridge NSString *)kCVPixelBufferHeightKey : @(rect.size.height),
  };

  error = CVPixelBufferCreateWithBytes(kCFAllocatorDefault,                 // allocator
                                       rect.size.width,                     // width
                                       rect.size.height,                    // height
                                       pixelFormat,                         // pixelFormatType
                                       &baseAddressStart[cropStartOffset],  // baseAddress
                                       bytesPerRow,                         // bytesPerRow
                                       NULL,                                // releaseCallback
                                       NULL,                                // releaseRefCon
                                       (__bridge CFDictionaryRef)options,   // pixelBufferAttributes
                                       &pixelBuffer);                       // pixelBuffer
  if (error != kCVReturnSuccess) {
    NSLog(@"Crop CVPixelBufferCreateWithBytes error %d", (int)error);
    return NULL;
  }

  // Cropping using CIImage.
  CIImage *ciImage = [CIImage imageWithCVImageBuffer:imageBuffer];
  ciImage = [ciImage imageByCroppingToRect:rect];
  // CIImage is not in the original point after cropping. So we need to pan.
  ciImage = [ciImage imageByApplyingTransform:CGAffineTransformMakeTranslation(-cropX, -cropY)];

  [gCIContext render:ciImage toCVPixelBuffer:pixelBuffer];

  // Prepares sample timing info.
  CMSampleTimingInfo sampleTime = {
      .duration = CMSampleBufferGetDuration(sampleBuffer),
      .presentationTimeStamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer),
      .decodeTimeStamp = CMSampleBufferGetDecodeTimeStamp(sampleBuffer)};

  CMVideoFormatDescriptionRef videoInfo;
  error =
      CMVideoFormatDescriptionCreateForImageBuffer(kCFAllocatorDefault, pixelBuffer, &videoInfo);
  if (error != kCVReturnSuccess) {
    NSLog(@"CMVideoFormatDescriptionCreateForImageBuffer error %d", (int)error);
    CVPixelBufferRelease(pixelBuffer);
    CVPixelBufferUnlockBaseAddress(imageBuffer, kCVPixelBufferLock_ReadOnly);
    return NULL;
  }

  // Creates `CMSampleBufferRef`.
  CMSampleBufferRef resultBuffer = NULL;
  error = CMSampleBufferCreateForImageBuffer(kCFAllocatorDefault, pixelBuffer, true, NULL, NULL,
                                             videoInfo, &sampleTime, &resultBuffer);
  if (error != kCVReturnSuccess) {
    NSLog(@"CMSampleBufferCreateForImageBuffer error %d", (int)error);
  }
  CFRelease(videoInfo);
  CVPixelBufferRelease(pixelBuffer);
  CVPixelBufferUnlockBaseAddress(imageBuffer, kCVPixelBufferLock_ReadOnly);
  return resultBuffer;
}

@end

NS_ASSUME_NONNULL_END
