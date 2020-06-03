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

#import "MLKResultCell.h"

#import "MLKResult.h"

@import MaterialComponents;
@import PINRemoteImage;

/** Layout values. */
static CGFloat const kHorizontalPadding = 16.0f;
static CGFloat const kVerticalPadding = 16.0f;
static CGFloat const kVerticalPaddingSmall = 6.0f;
static CGFloat const kThumbnailSize = 80.0f;

NS_ASSUME_NONNULL_BEGIN

@implementation MLKResultCell {
  BOOL _hasImage;
}

#pragma mark - Public

- (instancetype)initWithFrame:(CGRect)frame {
  self = [super initWithFrame:frame];
  if (self != nil) {
    _thumbnailImage = [[UIImageView alloc] init];
    [self addSubview:_thumbnailImage];

    _titleLabel = [[UILabel alloc] init];
    _titleLabel.numberOfLines = 0;
    _titleLabel.font = [[MDCBasicFontScheme alloc] init].subtitle1;
    [self addSubview:_titleLabel];

    _detailsLabel = [[UILabel alloc] init];
    _detailsLabel.numberOfLines = 0;
    _detailsLabel.font = [[MDCBasicFontScheme alloc] init].body2;
    _detailsLabel.textColor = MDCPalette.greyPalette.tint700;
    [self addSubview:_detailsLabel];

    _priceLabel = [[UILabel alloc] init];
    _priceLabel.numberOfLines = 0;
    _priceLabel.font = [[MDCBasicFontScheme alloc] init].body1;
    [self addSubview:_priceLabel];

    _itemNumberLabel = [[UILabel alloc] init];
    _itemNumberLabel.numberOfLines = 0;
    _itemNumberLabel.font = [[MDCBasicFontScheme alloc] init].body1;
    [self addSubview:_itemNumberLabel];
  }
  return self;
}

- (BOOL)isCellPopulatedWithResult:(nullable MLKResult *)result {
  if (result == nil) {
    return NO;
  }
  if (result.imageURL.length > 0) {
    [self.thumbnailImage pin_setImageFromURL:[NSURL URLWithString: result.imageURL]];
    _hasImage = YES;
  }
  self.titleLabel.text = result.title;
  self.detailsLabel.text = result.details;
  self.priceLabel.text = result.priceFullText;
  self.itemNumberLabel.text = result.itemNumber;
  return YES;
}

#pragma mark - UICollectionReusableView

- (void)prepareForReuse {
  [super prepareForReuse];
  self.thumbnailImage.image = nil;
  self.titleLabel.text = nil;
  self.priceLabel.text = nil;
  self.detailsLabel.text = nil;
  self.itemNumberLabel.text = nil;
}

#pragma mark - UIView

- (void)layoutSubviews {
  [super layoutSubviews];
  [self layoutSubviewsForWidth:self.frame.size.width shouldSetFrame:YES];
}

- (CGSize)sizeThatFits:(CGSize)size {
  CGFloat width = size.width;
  CGFloat height = [self layoutSubviewsForWidth:width shouldSetFrame:NO];
  return CGSizeMake(width, height);
}

#pragma mark - Private

/**
 * Calculates the height that best fits the specified width for subviews.
 *
 * @param width The available width for the view.
 * @param shouldSetFrame Whether to set frames for subviews.
 *    If it is set to NO, this function will simply measure the space without affecting subviews,
 *    otherwise, subviews will be laid out accordingly.
 * @return The best height of the view that fits the width.
 */
- (CGFloat)layoutSubviewsForWidth:(CGFloat)width shouldSetFrame:(BOOL)shouldSetFrame {
  CGFloat contentWidth = width - 2 * kHorizontalPadding;

  CGFloat currentHeight = kVerticalPadding;
  CGFloat startX = kHorizontalPadding;

  if (_hasImage) {
    if (shouldSetFrame) {
      self.thumbnailImage.frame = CGRectMake(startX, currentHeight, kThumbnailSize, kThumbnailSize);
    }
    startX += kThumbnailSize + kHorizontalPadding;
    contentWidth -= kThumbnailSize + kHorizontalPadding;
  }

  CGSize nameLabelSize = [self.titleLabel sizeThatFits:CGSizeMake(contentWidth, CGFLOAT_MAX)];
  if (shouldSetFrame) {
    self.titleLabel.frame = CGRectMake(startX, currentHeight, contentWidth, nameLabelSize.height);
  }
  currentHeight += nameLabelSize.height + kVerticalPaddingSmall;

  if (self.detailsLabel.text.length > 0) {
    CGSize detailsLabelSize =
        [self.detailsLabel sizeThatFits:CGSizeMake(contentWidth, CGFLOAT_MAX)];
    if (shouldSetFrame) {
      self.detailsLabel.frame =
          CGRectMake(startX, currentHeight, contentWidth, detailsLabelSize.height);
    }

    currentHeight += detailsLabelSize.height + kVerticalPadding;
  }

  if (self.priceLabel.text.length > 0) {
    CGSize priceLabelSize = [self.priceLabel sizeThatFits:CGSizeMake(contentWidth, CGFLOAT_MAX)];
    if (shouldSetFrame) {
      self.priceLabel.frame =
          CGRectMake(startX, currentHeight, priceLabelSize.width, priceLabelSize.height);
    }
    currentHeight += priceLabelSize.height + kVerticalPadding;
  }
  return _hasImage ? MAX(currentHeight, kThumbnailSize + 2 * kVerticalPadding) : currentHeight;
}

@end

NS_ASSUME_NONNULL_END
