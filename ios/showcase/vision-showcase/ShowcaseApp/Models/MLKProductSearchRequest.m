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

#import "MLKProductSearchRequest.h"

#import "UIImage+MLKShowcase.h"

NS_ASSUME_NONNULL_BEGIN

// Key-value pairs in the product search request.
static NSString *const kJSONBodyImageContentKey = @"content";

static NSString *const kJSONBodyRetailUnitKey = @"RU";
static NSString *const kJSONBodyRetailUnitValue = @"US";

static NSString *const kURLKey = @"URL";
static NSString *const kHeaderAcceptTypeKey = @"Accept";

static NSString *const kHeaderContentTypeKey = @"Content-Type";
static NSString *const kHeaderContentTypeValue = @"application/json";

static NSString *const kHeaderAPIKeyKey = @"X-IVS-APIKey";

static NSString *const kHTTPMethodPost = @"POST";

static NSString *const kKeyFileName = @"key";
static NSString *const kKeyFileType = @"plist";

@interface MLKProductSearchRequest ()

/** Product search backend server information. */
@property(nonatomic) NSDictionary<NSString *, NSString *> *serverInfo;

@end

@implementation MLKProductSearchRequest

#pragma mark - Public

- (instancetype)initWithUIImage:(UIImage *)image {
  self = [super initWithURL:[NSURL URLWithString:[self productSearchURL]]];
  if (self != nil) {
    super.cachePolicy = NSURLRequestReloadIgnoringLocalCacheData;
    super.HTTPMethod = kHTTPMethodPost;

    // Set body.
    NSMutableDictionary<NSString *, id> *JSONDictionary = [NSMutableDictionary dictionary];
    NSString *encodedString = image.mlk_base64EncodedString;
    JSONDictionary[kJSONBodyImageContentKey] = encodedString;
    JSONDictionary[kJSONBodyRetailUnitKey] = kJSONBodyRetailUnitValue;

    NSError *JSONError;

    NSData *JSONData = [NSJSONSerialization dataWithJSONObject:JSONDictionary
                                                       options:0
                                                         error:&JSONError];
    if (JSONData == nil) {
      NSLog(@"Unable to generate JSONData from JSONDictionary: %@", JSONError.description);
    }

    super.HTTPBody = JSONData;

    // Set headers.
    [self setValue:[self APIKey] forHTTPHeaderField:kHeaderAPIKeyKey];
    [self setValue:kHeaderContentTypeValue forHTTPHeaderField:kHeaderContentTypeKey];
    [self setValue:[self acceptType] forHTTPHeaderField:kHeaderAcceptTypeKey];
  }
  return self;
}

#pragma mark - Private

/** API Key from a resource file. */
- (nullable NSString *)APIKey {
  return self.serverInfo[kHeaderAPIKeyKey];
}

/** Product search URL from a resource file. */
- (nullable NSString *)productSearchURL {
  return self.serverInfo[kURLKey];
}

/** `acceptType` from a resource file. */
- (nullable NSString *)acceptType {
  return self.serverInfo[kHeaderAcceptTypeKey];
}

/** Overrides getter of `serverInfo`. */
- (NSDictionary *)serverInfo {
  if (_serverInfo == nil) {
    NSString *serverInfoFileName = [NSBundle.mainBundle pathForResource:kKeyFileName
                                                                 ofType:kKeyFileType];

    _serverInfo = [[NSDictionary alloc] initWithContentsOfFile:serverInfoFileName];
  }
  return _serverInfo;
}

@end

NS_ASSUME_NONNULL_END
