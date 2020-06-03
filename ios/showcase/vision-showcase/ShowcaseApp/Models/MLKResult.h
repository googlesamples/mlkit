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

#import <Foundation/Foundation.h>

#import "MLKLiveObjectDetectionViewController.h"

NS_ASSUME_NONNULL_BEGIN

/** Model for a product search results. */
@interface MLKResult : NSObject

@property(nonatomic, copy, nullable) NSString *title;
@property(nonatomic, copy, nullable) NSString *imageURL;
@property(nonatomic, nullable) NSNumber *score;
@property(nonatomic, copy, nullable) NSString *itemNumber;
@property(nonatomic, copy, nullable) NSString *priceFullText;
@property(nonatomic, copy, nullable) NSString *details;

/**
 * Generates a list of results from given  search response.
 *
 * @param response The search response.
 * @param type The detector type.
 * @return Generated list of results.
 */
+ (nullable NSArray<MLKResult *> *)resultsFromResponse:(nullable NSData *)response
                                       forDetectortype:(MLKDetectorType)type;

@end

NS_ASSUME_NONNULL_END
