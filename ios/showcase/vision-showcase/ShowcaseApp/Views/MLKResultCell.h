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

@import MaterialComponents;

@class MLKResult;

NS_ASSUME_NONNULL_BEGIN

/** Cell that shows details of a search result. */
@interface MLKResultCell : MDCBaseCell

/** Thumbnail of the result. */
@property(nonatomic, readonly) UIImageView *thumbnailImage;

/** Label showing the title of the result. */
@property(nonatomic, readonly) UILabel *titleLabel;

/** Label showing the details of the results. */
@property(nonatomic, readonly) UILabel *detailsLabel;

/** Label showing the item number of the result. */
@property(nonatomic, readonly) UILabel *itemNumberLabel;

/** Label showing the price of the result. */
@property(nonatomic, readonly) UILabel *priceLabel;

/**
 * Populates the content of the cell with a `Result` model.
 *
 * @param result The result info to populate the cell with.
 * @return YES if product is not nil, otherwise, NO.
 */
- (BOOL)isCellPopulatedWithResult:(nullable MLKResult *)result;

@end

NS_ASSUME_NONNULL_END
