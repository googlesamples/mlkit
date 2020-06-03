//
//  Copyright (c) 2019 Google Inc.
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

#import <Foundation/Foundation.h>
@import MLKitImageClassificationAutoML;

@interface AutoMLVision : NSObject

- (void)classifyImage:(MLKVisionImage *)image;

@end

@implementation AutoMLVision

- (void)classifyImage:(MLKVisionImage *)image {
  // [START get_classifier]
  NSBundle *bundle = [NSBundle bundleForClass:[self class]];
  NSString *manifestPath = [bundle pathForResource:@"manifest" ofType:@"json"];
  MLKAutoMLLocalModel *localModel = [[MLKAutoMLLocalModel alloc] initWithManifestPath:manifestPath];
  MLKAutoMLImageClassifierOptions *classifierOptions =
      [[MLKAutoMLImageClassifierOptions alloc]
          initWithLocalModel:localModel];
  classifierOptions.confidenceThreshold = 0;  // Evaluate your model in the Firebase console
                                           // to determine an appropriate value.
  MLKImageClassifier *classifier =
      [MLKImageClassifier autoMLImageClassifierWithOptions:classifierOptions];
  // [END get_classifier]

  // [START process_image]

  [classifier
      processImage:image
   completion:^(NSArray<MLKImageClassification *> *_Nullable classifications, NSError *_Nullable error) {
    if (error != nil || classifications == nil) {
      return;
    }
    // Task succeeded.
    // [START_EXCLUDE]
    // [START get_classfications]
    for (MLKImageClassification *classification in classifications) {
      NSString *classificationText = classification.text;
      float confidence = classification.confidence;
    }
    // [END get_classfications]
    // [END_EXCLUDE]
  }];
}

@end
