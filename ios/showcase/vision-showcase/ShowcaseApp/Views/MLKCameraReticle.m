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

#import "MLKCameraReticle.h"

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

static const CGFloat kRippleRingFinalScale = 2.f;
static const CGFloat kRippleRingFinalLineWidth = 2.f;
static const CFTimeInterval kRippleRingFadeInDuration = 0.333f;
static const CFTimeInterval kRippleRingExpandDuration = 0.833f;
static const CFTimeInterval kRippleRingFadeOutBeginTime = 0.333f;
static const CFTimeInterval kRippleRingFadeOutDuration = 0.5f;
static const CFTimeInterval kHibernationDuration = 1.167f;

static NSString *const kOpacityKeyPath = @"opacity";
static NSString *const kPathKeyPath = @"path";
static NSString *const kPositionKeyPath = @"position";
static NSString *const kLineWidthKeyPath = @"lineWidth";

/**
 * The reticle consists of 3 rings, an inner ring and an outer ring of fixed size, and an animating
 * ripple ring that can change in opacity, line width, and radius. They all live in their own
 * layers.
 */
@interface MLKCameraReticle ()

/** Whether the reticle is currently animating. */
@property(nonatomic) BOOL isAnimating;
/** The layer hosting the fixed inner ring. */
@property(nonatomic, readonly) CAShapeLayer *innerRingLayer;
/** The layer hosting the fixed outer ring. */
@property(nonatomic, readonly) CAShapeLayer *outerRingLayer;
/** The layer hosting the animating ripple ring. */
@property(nonatomic, readonly) CAShapeLayer *rippleRingLayer;

@end

@implementation MLKCameraReticle

#pragma mark - Public

- (instancetype)initWithFrame:(CGRect)frame {
  self = [super initWithFrame:frame];
  if (self != nil) {
    _outerRingLayer = [CAShapeLayer layer];
    _outerRingLayer.opacity = kOuterRingStrokeAlpha;
    [self createRingInRect:CGRectMake(0, 0, kOuterRingDiameter, kOuterRingDiameter)
                     layer:_outerRingLayer
                 lineWidth:kOuterRingLineWidth
               strokeColor:UIColor.whiteColor
                 fillColor:[UIColor.blackColor colorWithAlphaComponent:kOuterRingFillAlpha]];
    [self.layer addSublayer:_outerRingLayer];

    _rippleRingLayer = [CAShapeLayer layer];
    _rippleRingLayer.opacity = 0;
    [self createRingInRect:CGRectMake(0, 0, kOuterRingDiameter, kOuterRingDiameter)
                     layer:_rippleRingLayer
                 lineWidth:kOuterRingLineWidth
               strokeColor:UIColor.whiteColor
                 fillColor:UIColor.clearColor];
    [self.layer addSublayer:_rippleRingLayer];

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

- (void)startAnimating {
  if (self.isAnimating) {
    return;
  }

  self.isAnimating = YES;
  [self fadeInRippleRing];
}

- (void)stopAnimating {
  if (!self.isAnimating) {
    return;
  }
  self.isAnimating = NO;
  [self hibernate];
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

  self.rippleRingLayer.frame = outerRingRect;
  self.rippleRingLayer.bounds = outerRingRect;
  self.rippleRingLayer.position = outerRingRect.origin;

  CGRect innerRingRect = CGRectMake(centerX - kInnerRingRadius, centerY - kInnerRingRadius,
                                    kInnerRingDiameter, kInnerRingDiameter);
  self.innerRingLayer.frame = innerRingRect;
  self.innerRingLayer.bounds = innerRingRect;
  self.innerRingLayer.position = innerRingRect.origin;
}

- (void)setHidden:(BOOL)hidden {
  [super setHidden:hidden];
  if (hidden) {
    [self stopAnimating];
  } else {
    [self startAnimating];
  }
}

- (void)willMoveToWindow:(nullable UIWindow *)newWindow {
  [super willMoveToWindow:newWindow];
  // If the reticle is removed from the window, we should stop animating to avoid chewing up CPU.
  if (!newWindow) {
    [self stopAnimating];
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
  layer.strokeStart = 0.f;
  layer.strokeEnd = 1.f;
  layer.strokeColor = strokeColor.CGColor;
  layer.fillColor = fillColor.CGColor;
}

/** Fades in the ripple ring with a linear timing function. */
- (void)fadeInRippleRing {
  [CATransaction begin];
  __weak typeof(self) weakSelf = self;
  [CATransaction setCompletionBlock:^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf expandRippleRing];
  }];
  CABasicAnimation *fadeIn = [[CABasicAnimation alloc] init];
  fadeIn.keyPath = kOpacityKeyPath;
  fadeIn.fromValue = @0.f;
  fadeIn.toValue = @(kOuterRingStrokeAlpha);
  fadeIn.duration = kRippleRingFadeInDuration;
  fadeIn.timingFunction = [CAMediaTimingFunction functionWithName:kCAMediaTimingFunctionLinear];
  fadeIn.fillMode = kCAFillModeForwards;
  fadeIn.removedOnCompletion = NO;
  [self.rippleRingLayer addAnimation:fadeIn forKey:nil];
  [CATransaction commit];
}

/** Expands and fades out the ripple ring while thinning the line width. */
- (void)expandRippleRing {
  [CATransaction begin];
  __weak typeof(self) weakSelf = self;
  [CATransaction setCompletionBlock:^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf hibernate];
  }];

  CGRect finalRect = CGRectMake(0, 0, kRippleRingFinalScale * kOuterRingDiameter,
                                kRippleRingFinalScale * kOuterRingDiameter);
  CGPathRef finalPath = [UIBezierPath bezierPathWithOvalInRect:finalRect].CGPath;
  CABasicAnimation *scale = [[CABasicAnimation alloc] init];
  scale.keyPath = kPathKeyPath;
  scale.fromValue = (__bridge id _Nullable)(self.rippleRingLayer.path);
  scale.toValue = (__bridge id _Nullable)(finalPath);

  CABasicAnimation *recenter = [[CABasicAnimation alloc] init];
  recenter.keyPath = kPositionKeyPath;
  recenter.fromValue = @(self.rippleRingLayer.position);
  recenter.toValue =
      @(CGPointMake([self centerX] - kOuterRingDiameter, [self centerY] - kOuterRingDiameter));

  CABasicAnimation *thin = [[CABasicAnimation alloc] init];
  thin.keyPath = kLineWidthKeyPath;
  thin.fromValue = @(kOuterRingLineWidth);
  thin.toValue = @(kRippleRingFinalLineWidth);

  CABasicAnimation *fadeOut = [[CABasicAnimation alloc] init];
  fadeOut.keyPath = kOpacityKeyPath;
  fadeOut.fromValue = @(kOuterRingStrokeAlpha);
  fadeOut.toValue = @0.f;
  fadeOut.beginTime = kRippleRingFadeOutBeginTime;
  fadeOut.duration = kRippleRingFadeOutDuration;
  fadeOut.timingFunction = [CAMediaTimingFunction functionWithName:kCAMediaTimingFunctionLinear];
  fadeOut.fillMode = kCAFillModeForwards;

  CAAnimationGroup *expand = [[CAAnimationGroup alloc] init];
  expand.animations = [[NSArray alloc] initWithObjects:scale, recenter, thin, fadeOut, nil];
  expand.duration = kRippleRingExpandDuration;
  // Animation begins and ends with easing.
  expand.timingFunction = [[CAMediaTimingFunction alloc] initWithControlPoints:0.4f:0.0f:0.2f:1.0f];
  expand.fillMode = kCAFillModeForwards;
  expand.removedOnCompletion = NO;
  [self.rippleRingLayer addAnimation:expand forKey:nil];
  [CATransaction commit];
}

/** Hibernates and prepares for the next cycle of animation, or stops the animation upon request. */
- (void)hibernate {
  if (!self.isAnimating) {
    [self.rippleRingLayer removeAllAnimations];
    return;
  }
  [CATransaction begin];
  __weak typeof(self) weakSelf = self;
  [CATransaction setCompletionBlock:^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    [strongSelf fadeInRippleRing];
  }];

  CGRect outerRect = CGRectMake(0, 0, kOuterRingDiameter, kOuterRingDiameter);
  CGPathRef path = [UIBezierPath bezierPathWithOvalInRect:outerRect].CGPath;
  CABasicAnimation *scale = [[CABasicAnimation alloc] init];
  scale.keyPath = kPathKeyPath;
  scale.fromValue = (__bridge id _Nullable)(self.rippleRingLayer.path);
  scale.toValue = (__bridge id _Nullable)(path);

  CABasicAnimation *thicken = [[CABasicAnimation alloc] init];
  thicken.keyPath = kLineWidthKeyPath;
  thicken.fromValue = @(kRippleRingFinalLineWidth);
  thicken.toValue = @(kOuterRingLineWidth);

  CABasicAnimation *recenter = [[CABasicAnimation alloc] init];
  recenter.keyPath = kPositionKeyPath;
  recenter.fromValue = @(self.rippleRingLayer.position);
  recenter.toValue =
      @(CGPointMake([self centerX] - kOuterRingRadius, [self centerY] - kOuterRingRadius));

  CAAnimationGroup *hibernate = [[CAAnimationGroup alloc] init];
  hibernate.animations = [[NSArray alloc] initWithObjects:scale, recenter, thicken, nil];
  hibernate.duration = kHibernationDuration;
  hibernate.timingFunction = [CAMediaTimingFunction functionWithName:kCAMediaTimingFunctionLinear];
  hibernate.fillMode = kCAFillModeForwards;
  hibernate.removedOnCompletion = NO;
  [self.rippleRingLayer addAnimation:hibernate forKey:nil];

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
