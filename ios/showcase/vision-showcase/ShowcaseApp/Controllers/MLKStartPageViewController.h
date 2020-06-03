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

@class MDCFlexibleHeaderViewController;
@class MLKStartPageHeaderView;

NS_ASSUME_NONNULL_BEGIN

/** An enum for the type of showcase app. */
typedef NS_ENUM(NSInteger, MLKDetectorType) {
  // Object Detection and Tracking showcase app with default model.
  MLKDetectorTypeODTDefaultModel = 0,
  // Object Detection and Tracking showcase app with bird model.
  MLKDetectorTypeODTBirdModel,
  // Total count of this enum value.
  MLKDetectorTypeCount,
};

@interface MLKStartPageViewController : UICollectionViewController

/** Header of the list, it stays on top of the list and contents will be scrolled underneath it. */
@property(nonatomic) MDCFlexibleHeaderViewController *headerViewController;

/** Header view for the list. */
@property(nonatomic) MLKStartPageHeaderView *headerView;

@end

NS_ASSUME_NONNULL_END
