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

@class MLKResult;
@class MLKResultListHeaderView;
@class MDCFlexibleHeaderViewController;

NS_ASSUME_NONNULL_BEGIN

/** View controller showing a list of products. */
@interface MLKResultListViewController : UICollectionViewController

/**
 * Header of the list, it stays on top of the screen when it expands to the whole screen and
 * contents will be scrolled underneath it.
 */
@property(nonatomic) MDCFlexibleHeaderViewController *headerViewController;

/** Header view for this panel view. */
@property(nonatomic) MLKResultListHeaderView *headerView;

/**
 * Initializes and returns a `ProductListViewController` object using the provided product list.
 *
 * @param products List of the products that serves as the model to this view.
 * @return An instance of the `ProductListViewController`.
 */
- (instancetype)initWithResults:(NSArray<MLKResult *> *)products;

/** Calculates and updates minimum and maximum height for header view. */
- (void)updateMinMaxHeightForHeaderView;

@end

NS_ASSUME_NONNULL_END
