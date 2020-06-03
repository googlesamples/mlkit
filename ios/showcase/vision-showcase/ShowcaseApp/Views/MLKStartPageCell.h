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

NS_ASSUME_NONNULL_BEGIN

/** Cell that shows  details of a item on the start page. */
@interface MLKStartPageCell : MDCBaseCell

/** Label showing the name of the item. */
@property(nonatomic, readonly) UILabel *nameLabel;

/** Label showing the details of the item. */
@property(nonatomic, readonly) UILabel *detailLabel;

/** Separator line that lives at the bottom of each cell. */
@property(nonatomic, readonly) UIView *separator;

/**
 * Populates the content of the cell with `name` and `details`.
 *
 * @param name The name of the item;
 * @param details The details of the item;
 * @return YES if populated successfully, otherwise, NO.
 */
- (BOOL)isCellPopulatedWithName:(nullable NSString *)name
                        details:(nullable NSString*)details;

@end

NS_ASSUME_NONNULL_END
