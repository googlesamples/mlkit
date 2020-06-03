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

#import "MLKDetectionOverlayView.h"

// Box related values.
static const CGFloat kBoxBorderWidth = 2.0f;
static const CGFloat kImageBorderWidth = 4.0f;
static const CGFloat kBoxBackgroundAlpha = 0.12f;
static const CGFloat kBoxCornerRadius = 12.0f;

// Image related values.
static const CGFloat kImageBackgroundAlpha = 0.6f;

static const CGFloat kStrokeStartValueZero = 0.0f;
static const CGFloat kStrokeEndValueOne = 1.0f;

NS_ASSUME_NONNULL_BEGIN

@interface MLKDetectionOverlayView ()

/** Layer to show a box in the view. */
@property(nonatomic, readonly) CAShapeLayer *boxLayer;

/** Layer to show a mask outside of the box in the view. */
@property(nonatomic, readonly) CAShapeLayer *boxMaskLayer;

@end

@implementation MLKDetectionOverlayView

#pragma mark - Public

- (instancetype)initWithFrame:(CGRect)frame {
  self = [super initWithFrame:frame];
  if (self != nil) {
    _boxMaskLayer = [[CAShapeLayer alloc] init];
    [self.layer addSublayer:_boxMaskLayer];

    _boxLayer = [CAShapeLayer layer];
    _boxLayer.cornerRadius = kBoxCornerRadius;
    [self.layer addSublayer:_boxLayer];

    _image = [[UIImageView alloc] init];
    _image.layer.cornerRadius = kBoxCornerRadius;
    _image.layer.borderWidth = kImageBorderWidth;
    _image.layer.masksToBounds = YES;
    _image.layer.borderColor = UIColor.whiteColor.CGColor;
    [self addSubview:_image];
  }
  return self;
}

- (void)showBoxInRect:(CGRect)rect {
  [self.boxMaskLayer setHidden:NO];
  UIBezierPath *maskPath = [UIBezierPath bezierPathWithRect:self.bounds];
  UIBezierPath *boxPath =
      [[UIBezierPath bezierPathWithRoundedRect:rect
                                  cornerRadius:kBoxCornerRadius] bezierPathByReversingPath];
  [maskPath appendPath:boxPath];

  self.boxMaskLayer.frame = self.frame;
  self.boxMaskLayer.path = maskPath.CGPath;
  self.boxMaskLayer.strokeStart = kStrokeStartValueZero;
  self.boxMaskLayer.strokeEnd = kStrokeEndValueOne;
  self.layer.backgroundColor =
      [UIColor.blackColor colorWithAlphaComponent:kBoxBackgroundAlpha].CGColor;
  self.layer.mask = self.boxMaskLayer;

  [self.boxLayer setHidden:NO];
  self.boxLayer.path =
      [UIBezierPath bezierPathWithRoundedRect:rect cornerRadius:kBoxCornerRadius].CGPath;
  self.boxLayer.lineWidth = kBoxBorderWidth;
  self.boxLayer.strokeStart = kStrokeStartValueZero;
  self.boxLayer.strokeEnd = kStrokeEndValueOne;
  self.boxLayer.strokeColor = UIColor.whiteColor.CGColor;
  self.boxLayer.fillColor = nil;
}

- (void)showImageInRect:(CGRect)rect alpha:(CGFloat)alpha {
  [self.image setHidden:NO];
  self.image.alpha = alpha;
  self.image.frame = rect;
  self.layer.backgroundColor =
      [UIColor.blackColor colorWithAlphaComponent:kImageBackgroundAlpha].CGColor;
}

- (void)hideSubviews {
  [self hideBox];
  [self hideImage];
}

#pragma mark - Private

/** Hides box in the view. */
- (void)hideBox {
  self.layer.mask = nil;
  [self.boxMaskLayer setHidden:YES];
  [self.boxLayer setHidden:YES];
}

/** Hides image in the view. */
- (void)hideImage {
  [self.image setHidden:YES];
  self.layer.backgroundColor = nil;
}

@end

NS_ASSUME_NONNULL_END
