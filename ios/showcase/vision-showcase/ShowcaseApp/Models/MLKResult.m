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

#import "MLKResult.h"

// Key for wrapped data in product search response.
static NSString *const kSearchResponseKeyData = @"data";
static NSString *const kSearchResponseKeySearchResults = @"productSearchResults";
static NSString *const kSearchResponseKeyProducts = @"products";

// Key for product properties in product search response.
static NSString *const kProductNameKey = @"productName";
static NSString *const kProductScoreKey = @"score";
static NSString *const kProductImageURLKey = @"imageUrl";
static NSString *const kProductItemNumberKey = @"itemNo";
static NSString *const kProductPriceTextKey = @"priceFullText";
static NSString *const kProductTypeNameKey = @"productTypeName";

NS_ASSUME_NONNULL_BEGIN

@implementation MLKResult

+ (nullable NSArray<MLKResult *> *)resultsFromResponse:(nullable NSData *)response
                                       forDetectortype:(MLKDetectorType)type {
  if (response == nil) {
    return nil;
  }
  NSError *JSONError;
  NSDictionary<NSObject *, NSData *> *responseJSONObject =
      [NSJSONSerialization JSONObjectWithData:response options:0 error:&JSONError];
  if (JSONError != nil) {
    NSLog(@"Error in parsing a response: %@", JSONError);
    return nil;
  }


  switch (type) {
    case MLKDetectorTypeODTDefaultModel: {
      NSData *_Nullable responseData = [responseJSONObject valueForKey:kSearchResponseKeyData];
      if (responseData == nil || ![responseData isKindOfClass:[NSDictionary class]]) {
        return nil;
      }
      NSDictionary<NSObject *, NSData *> *responseDataDictionary = (NSDictionary *)responseData;
      NSData *productSearchResultsData =
          [responseDataDictionary valueForKey:kSearchResponseKeySearchResults];

      if (productSearchResultsData == nil ||
          ![productSearchResultsData isKindOfClass:[NSDictionary class]]) {
        return nil;
      }

      NSArray<NSObject *> *productSearchResultsArray = (NSArray *)(
          [(NSDictionary *)productSearchResultsData valueForKey:kSearchResponseKeyProducts]);
      NSMutableArray<MLKResult *> *results =
          [NSMutableArray arrayWithCapacity:productSearchResultsArray.count];
      for (NSData *resultData in productSearchResultsArray) {
        [results addObject:[self productFromData:resultData]];
      }
      return results;
      break;
    }
    case MLKDetectorTypeODTBirdModel: {
      if (JSONError != nil) {
        NSLog(@"Error in parsing a response: %@", JSONError);
        return nil;
      }
      NSDictionary<NSString*,NSDictionary<NSString * , NSDictionary *>*>*body = (NSDictionary<NSString*,NSDictionary<NSString * , NSDictionary *>*>*)responseJSONObject[@"query"];
      NSDictionary<NSString *, NSString *> *values = body[@"pages"].allValues[0];
      NSString *extract = values[@"extract"];
      NSString *title = values[@"title"];
      if (title.length > 0 && ![title isEqual:@"Null"]) {
        MLKResult *result = [[MLKResult alloc] init];
        result.title = title;
        result.details = extract;
        return @[result];
      }
      return nil;
    }
    default: {
      return nil;
    }
  }


}

- (NSString *)description {
  return [NSString stringWithFormat:@"Product name: %@, type: %@, price:%@, item Number: %@",
                                    self.title, self.details, self.priceFullText,
                                    self.itemNumber];
}

#pragma mark - Private

+ (nullable MLKResult *)productFromData:(NSData *)data {
  if (data == nil || ![data isKindOfClass:NSDictionary.class]) {
    return nil;
  }
  MLKResult *product = [[MLKResult alloc] init];
  NSDictionary<NSString *, NSString *> *dictionary = (NSDictionary *)data;
  product.title = dictionary[kProductNameKey];
  product.score = @(dictionary[kProductScoreKey].doubleValue);
  product.itemNumber = dictionary[kProductItemNumberKey];
  product.imageURL = dictionary[kProductImageURLKey];
  product.priceFullText = dictionary[kProductPriceTextKey];
  product.details = dictionary[kProductTypeNameKey];
  return product;
}

@end

NS_ASSUME_NONNULL_END
