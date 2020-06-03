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

#import "MLKConfirmationSpinner.h"

#import <CoreFoundation/CoreFoundation.h>
#import <QuartzCore/QuartzCore.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

// Layout values.
static const CGFloat kInnerRingRadius = 14.f;
static const CGFloat kInnerRingDiameter = 2.f * kInnerRingRadius;
static const CGFloat kInnerRingLineWidth = 2.f;

static const CGFloat kOuterRingRadius = 24.f;
static const CGFloat kOuterRingDiameter = 2.f * kOuterRingRadius;
static const CGFloat kOuterRingLineWidth = 4.f;
static const CGFloat kOuterRingStrokeAlpha = 0.6f;
static const CGFloat kOuterRingFillAlpha = 0.12f;
static const CGFloat kSpinnerRingStartAngle = -1.57079632679f;  // -0.5 * pi in radians
static const CGFloat kSpinnerRingEndAngle = 4.71238898038f;     // 1.5 * pi in radians

static const CGFloat kStartValueZero = 0.f;
static const CGFloat kEndValueFull = 1.f;

static const CFTimeInterval kDefaultConfirmingDuration = 1.5f;

static NSString *const kStrokeEndKeyPath = @"strokeEnd";

/**
 * The spinner consists of 3 rings, an inner ring and an outer ring of fixed size, and an animating
 * spinner ring that animates. They all live in their own layers.
 */
@interface MLKConfirmationSpinner ()

/** The duration of the confirming period. */
@property(nonatomic, readonly) CFTimeInterval duration;
/** Whether the spinner is currently confirming. */
@property(nonatomic) BOOL isConfirming;
/** The layer hosting the fixed inner ring. */
@property(nonatomic, readonly) CAShapeLayer *innerRingLayer;
/** The layer hosting the fixed outer ring. */
@property(nonatomic, readonly) CAShapeLayer *outerRingLayer;
/** The layer hosting the animating spinner ring. */
@property(nonatomic, readonly) CAShapeLayer *spinnerRingLayer;

@end

@implementation MLKConfirmationSpinner

#pragma mark - Public

- (instancetype)initWithFrame:(CGRect)frame {
  self = [super initWithFrame:frame];
  if (self != nil) {
    _duration = kDefaultConfirmingDuration;
    _outerRingLayer = [CAShapeLayer layer];
    _outerRingLayer.opacity = kOuterRingStrokeAlpha;
    [self createRingInRect:CGRectMake(0, 0, kOuterRingDiameter, kOuterRingDiameter)
                     layer:_outerRingLayer
                 lineWidth:kOuterRingLineWidth
               strokeColor:UIColor.whiteColor
                 fillColor:[UIColor.blackColor colorWithAlphaComponent:kOuterRingFillAlpha]];
    [self.layer addSublayer:_outerRingLayer];

    _spinnerRingLayer = [CAShapeLayer layer];
    _spinnerRingLayer.opacity = kOuterRingStrokeAlpha;
    [self.layer addSublayer:_spinnerRingLayer];
    [self reset];

    _innerRingLayer = [CAShapeLayer layer];
    [self createRingInRect:CGRectMake(0, 0, kInnerRingDiameter, kInnerRingDiameter)
                     layer:_innerRingLayer
                 lineWidth:kInnerRingLineWidth
               strokeColor:UIColor.whiteColor
                 fillColor:UIColor.clearColor];
    [self.layer addSublayer:_innerRingLayer];
  }
  return self;
}

- (instancetype)initWithDuration:(CFTimeInterval)duration {
  self = [self initWithFrame:CGRectZero];
  if (self != nil) {
    _duration = duration;
  }
  return self;
}

- (void)startConfirming {
  if (self.isConfirming) {
    return;
  }

  self.isConfirming = YES;
  [self startAnimation];
}

- (void)reset {
  [self.spinnerRingLayer removeAllAnimations];
  UIBezierPath *circle =
      [UIBezierPath bezierPathWithArcCenter:CGPointMake(kOuterRingRadius, kOuterRingRadius)
                                     radius:kOuterRingRadius
                                 startAngle:kSpinnerRingStartAngle
                                   endAngle:kSpinnerRingEndAngle
                                  clockwise:YES];
  self.spinnerRingLayer.path = circle.CGPath;
  self.spinnerRingLayer.lineWidth = kOuterRingLineWidth;
  self.spinnerRingLayer.strokeStart = kStartValueZero;
  self.spinnerRingLayer.strokeEnd = kStartValueZero;
  self.spinnerRingLayer.strokeColor = UIColor.whiteColor.CGColor;
  self.spinnerRingLayer.fillColor = UIColor.clearColor.CGColor;
  self.isConfirming = NO;
}

- (void)setHidden:(BOOL)hidden {
  [super setHidden:hidden];
  if (!hidden) {
    [self startConfirming];
  } else {
    [self reset];
  }
}

#pragma mark - UIView(UIViewHierarchy)

- (void)layoutSubviews {
  [super layoutSubviews];
  CGFloat centerX = [self centerX];
  CGFloat centerY = [self centerY];
  CGRect outerRingRect = CGRectMake(centerX - kOuterRingRadius, centerY - kOuterRingRadius,
                                    kOuterRingDiameter, kOuterRingDiameter);
  self.outerRingLayer.frame = outerRingRect;
  self.outerRingLayer.bounds = outerRingRect;
  self.outerRingLayer.position = outerRingRect.origin;

  self.spinnerRingLayer.frame = outerRingRect;
  self.spinnerRingLayer.bounds = outerRingRect;
  self.spinnerRingLayer.position = outerRingRect.origin;

  CGRect innerRingRect = CGRectMake(centerX - kInnerRingRadius, centerY - kInnerRingRadius,
                                    kInnerRingDiameter, kInnerRingDiameter);
  self.innerRingLayer.frame = innerRingRect;
  self.innerRingLayer.bounds = innerRingRect;
  self.innerRingLayer.position = innerRingRect.origin;
}

- (void)willMoveToWindow:(nullable UIWindow *)newWindow {
  [super willMoveToWindow:newWindow];
  // If the reticle is removed from the window, we should stop animating to avoid chewing up CPU.
  if (!newWindow) {
    [self reset];
  }
}

#pragma mark - Private

/**
 * Creates a ring inscribed in the given rectangle with the given line width, stroke color, and
 * fill color.
 *
 * @param rect The rectangle in which to inscribe the ring.
 * @param layer The layer in which to draw the ring.
 * @param lineWidth The line width of the ring.
 * @param strokeColor The color of the ring.
 * @param fillColor The color to fill inside the ring.
 */
- (void)createRingInRect:(CGRect)rect
                   layer:(CAShapeLayer *)layer
               lineWidth:(CGFloat)lineWidth
             strokeColor:(UIColor *)strokeColor
               fillColor:(UIColor *)fillColor {
  layer.path = [UIBezierPath bezierPathWithOvalInRect:rect].CGPath;
  layer.lineWidth = lineWidth;
  layer.strokeStart = kStartValueZero;
  layer.strokeEnd = kEndValueFull;
  layer.strokeColor = strokeColor.CGColor;
  layer.fillColor = fillColor.CGColor;
}

/** Fills in the spinner ring with a linear timing function. */
- (void)startAnimation {
  [CATransaction begin];
  CABasicAnimation *fill = [[CABasicAnimation alloc] init];
  fill.keyPath = kStrokeEndKeyPath;
  fill.fromValue = @0.0f;
  fill.toValue = @1.0f;
  fill.duration = self.duration;
  fill.timingFunction = [CAMediaTimingFunction functionWithName:kCAMediaTimingFunctionLinear];
  fill.fillMode = kCAFillModeForwards;
  fill.removedOnCompletion = NO;
  [self.spinnerRingLayer addAnimation:fill forKey:nil];
  [CATransaction commit];
}

/** Determines whether the main screen is in the portrait mode. */
- (BOOL)isPortraitMode {
  CGSize screenSize = UIScreen.mainScreen.bounds.size;
  return screenSize.height > screenSize.width;
}

/** Returns the center X coordinate of the main screen. */
- (CGFloat)centerX {
  return [self isPortraitMode] ? self.center.x : self.center.y;
}

/** Returns the center Y coordinate of the main screen. */
- (CGFloat)centerY {
  return [self isPortraitMode] ? self.center.y : self.center.x;
}

@end

NS_ASSUME_NONNULL_END
