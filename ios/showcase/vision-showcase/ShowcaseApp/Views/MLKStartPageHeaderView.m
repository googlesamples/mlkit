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

#import "MLKStartPageHeaderView.h"

@import MaterialComponents;

NS_ASSUME_NONNULL_BEGIN

/** Layout constants. */
static CGFloat const kHorizontalPadding = 32;
static CGFloat const kVerticalPadding = 48;

@implementation MLKStartPageHeaderView

#pragma mark - Public

- (instancetype)initWithFrame:(CGRect)frame {
  self = [super initWithFrame:frame];
  if (self != nil) {
    _titleLabel = [[UILabel alloc] init];
    _titleLabel.font = [[MDCBasicFontScheme alloc] init].headline3;
    _titleLabel.textColor = UIColor.whiteColor;
    _titleLabel.backgroundColor = UIColor.blackColor;
    [self addSubview:_titleLabel];

    self.backgroundColor = UIColor.blackColor;
  }

  return self;
}

- (CGFloat)maxHeaderHeightForWidth:(CGFloat)width {
  CGSize labelSize =
      [self.titleLabel sizeThatFits:CGSizeMake(width - 2 * kHorizontalPadding, CGFLOAT_MAX)];
  return 2 * kVerticalPadding + labelSize.height;
}

- (CGFloat)minHeaderHeightForWidth:(CGFloat)width {
  CGSize labelSize =
      [self.titleLabel sizeThatFits:CGSizeMake(width - 2 * kHorizontalPadding, CGFLOAT_MAX)];
  return 2 * kVerticalPadding + labelSize.height;
}

#pragma mark - UIView

- (void)layoutSubviews {
  [super layoutSubviews];
  CGFloat currentHeight = self.frame.size.height;
  CGFloat contentWidth = self.frame.size.width - 2 * kHorizontalPadding;
  CGSize labelSize = [self.titleLabel sizeThatFits:CGSizeMake(contentWidth, CGFLOAT_MAX)];
  CGFloat startX = (self.frame.size.width - labelSize.width) / 2.0f;
  currentHeight -= kVerticalPadding + labelSize.height;
  self.titleLabel.frame = CGRectMake(startX, currentHeight, contentWidth, labelSize.height);
}

@end

NS_ASSUME_NONNULL_END
