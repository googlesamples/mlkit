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

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/** Overlay view that shows on top of the object detection window. */
@interface MLKDetectionOverlayView : UIView

/** Thumbnail image of the result to be searched. */
@property(nonatomic, readonly) UIImageView *image;

/**
 * Shows a box in the given rect. It also shows a scrim background outside of the box area.
 *
 * @param rect The given area of the box.
 */
- (void)showBoxInRect:(CGRect)rect;

/**
 * Shows image in the given area of given alpha. It also shows a border around the image as well as
 * a dark background.
 *
 * @param rect The frame of the image.
 * @param alpha The alpha value of the image view.
 */
- (void)showImageInRect:(CGRect)rect alpha:(CGFloat)alpha;

/** Clears all elements in the view. */
- (void)hideSubviews;

@end

NS_ASSUME_NONNULL_END
