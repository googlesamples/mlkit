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

#import "MLKStartPageCell.h"

@import MaterialComponents;

/** Layout values. */
static CGFloat const kHorizontalPadding = 16.0f;
static CGFloat const kVerticalPadding = 16.0f;
static CGFloat const kVerticalPaddingSmall = 6.0f;
static CGFloat const kSeparatorHeight = 1.0f;

NS_ASSUME_NONNULL_BEGIN

@implementation MLKStartPageCell

#pragma mark - Public

- (instancetype)initWithFrame:(CGRect)frame {
  self = [super initWithFrame:frame];
  if (self != nil) {
    self.backgroundColor = UIColor.blackColor;

    _nameLabel = [[UILabel alloc] init];
    _nameLabel.numberOfLines = 0;
    _nameLabel.backgroundColor = UIColor.blackColor;
    _nameLabel.textColor = MDCPalette.greyPalette.tint200;
    _nameLabel.font = [[MDCBasicFontScheme alloc] init].headline1;
    [self addSubview:_nameLabel];

    _detailLabel = [[UILabel alloc] init];
    _detailLabel.numberOfLines = 0;
    _detailLabel.backgroundColor = UIColor.blackColor;
    _detailLabel.font = [[MDCBasicFontScheme alloc] init].body2;
    _detailLabel.textColor = MDCPalette.greyPalette.tint600;
    [self addSubview:_detailLabel];

    _separator = [[UIView alloc] init];
    _separator.backgroundColor = MDCPalette.greyPalette.tint200;
    [self addSubview:_separator];
  }
  return self;
}

- (BOOL)isCellPopulatedWithName:(nullable NSString *)name
                        details:(nullable NSString*)details {
  if (name.length == 0 && details.length == 0) {
    return NO;
  }
  self.nameLabel.text = name;
  self.detailLabel.text = details;
  return YES;
}

#pragma mark - UICollectionReusableView

- (void)prepareForReuse {
  [super prepareForReuse];
  self.nameLabel.text = nil;
  self.detailLabel.text = nil;
}

#pragma mark - UIView

- (void)layoutSubviews {
  [super layoutSubviews];
  [self layoutSubviewsForWidth:self.frame.size.width shouldSetFrame:YES];
}

- (CGSize)sizeThatFits:(CGSize)size {
  CGFloat width = self.frame.size.width;
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

  CGSize nameLabelSize = [self.nameLabel sizeThatFits:CGSizeMake(contentWidth, CGFLOAT_MAX)];
  if (shouldSetFrame) {
    self.nameLabel.frame = CGRectMake(startX, currentHeight, contentWidth, nameLabelSize.height);
  }
  currentHeight += nameLabelSize.height + kVerticalPaddingSmall;

  CGSize detailLabelSize =
      [self.detailLabel sizeThatFits:CGSizeMake(contentWidth, CGFLOAT_MAX)];
  if (shouldSetFrame) {
    self.detailLabel.frame =
        CGRectMake(startX, currentHeight, contentWidth, detailLabelSize.height);
  }

  currentHeight += detailLabelSize.height + kVerticalPadding;

  if (shouldSetFrame) {
    self.separator.frame = CGRectMake(startX,
                                      currentHeight,
                                      contentWidth,
                                      kSeparatorHeight);
  }
  currentHeight += kSeparatorHeight;
  return currentHeight;
}

@end

NS_ASSUME_NONNULL_END
