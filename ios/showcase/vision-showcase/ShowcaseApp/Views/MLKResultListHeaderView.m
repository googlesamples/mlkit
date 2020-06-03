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

#import "MLKResultListHeaderView.h"

@import MaterialComponents;

NS_ASSUME_NONNULL_BEGIN

/** Layout constants. */
static CGFloat const kHorizontalPadding = 16;
static CGFloat const kVerticalPadding = 16;

@implementation MLKResultListHeaderView

#pragma mark - Public

- (instancetype)initWithFrame:(CGRect)frame {
  self = [super initWithFrame:frame];
  if (self != nil) {
    _resultLabel = [[UILabel alloc] init];
    _resultLabel.font = [[MDCBasicFontScheme alloc] init].subtitle1;
    _resultLabel.backgroundColor = UIColor.whiteColor;
    [self addSubview:_resultLabel];

    self.backgroundColor = UIColor.whiteColor;
  }

  return self;
}

- (CGFloat)maxHeaderHeightForWidth:(CGFloat)width {
  CGSize labelSize =
      [self.resultLabel sizeThatFits:CGSizeMake(width - 2 * kHorizontalPadding, CGFLOAT_MAX)];
  return 2 * kVerticalPadding + labelSize.height;
}

- (CGFloat)minHeaderHeightForWidth:(CGFloat)width {
  CGSize labelSize =
      [self.resultLabel sizeThatFits:CGSizeMake(width - 2 * kHorizontalPadding, CGFLOAT_MAX)];
  return 2 * kVerticalPadding + labelSize.height;
}

#pragma mark - UIView

- (void)layoutSubviews {
  [super layoutSubviews];
  CGFloat currentHeight = self.frame.size.height;
  CGFloat contentWidth = self.frame.size.width - 2 * kHorizontalPadding;
  CGSize labelSize = [self.resultLabel sizeThatFits:CGSizeMake(contentWidth, CGFLOAT_MAX)];
  currentHeight -= kVerticalPadding + labelSize.height;
  self.resultLabel.frame =
      CGRectMake(kHorizontalPadding, currentHeight, contentWidth, labelSize.height);
}

@end

NS_ASSUME_NONNULL_END
