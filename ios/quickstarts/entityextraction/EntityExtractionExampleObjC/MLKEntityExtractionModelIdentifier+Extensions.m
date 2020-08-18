//
// Copyright (c) 2020 Google LLC.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

#import "MLKEntityExtractionModelIdentifier+Extensions.h"

NSArray<MLKEntityExtractionModelIdentifier> *MLKEntityExtractionAllModelIdentifiersSorted(void) {
  NSLocale *currentLocale = NSLocale.currentLocale;
  return [MLKEntityExtractionAllModelIdentifiers()
                        .allObjects sortedArrayUsingComparator:^NSComparisonResult(
                                        NSString *_Nonnull model1, NSString *_Nonnull model2) {
    return [[currentLocale
        localizedStringForLanguageCode:MLKEntityExtractionLanguageTagForModelIdentifier(model1)]
        compare:[currentLocale
                    localizedStringForLanguageCode:MLKEntityExtractionLanguageTagForModelIdentifier(
                                                       model2)]];
  }];
}
