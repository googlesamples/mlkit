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

#import "MLKStartPageViewController.h"

NS_ASSUME_NONNULL_BEGIN

/** Delegate to handle interactions in the  view.*/
@protocol MLKLiveObjectDetectionViewControllerDelegate <NSObject>

/** Called when the close button is tapped in the view. */
- (void)didTapBackButton;

@end

/**
 * The camera mode view controller that displays a rear facing live feed.
 */
@interface MLKLiveObjectDetectionViewController : UIViewController

/** Delegate to handle interactions in the view. */
@property(weak) id<MLKLiveObjectDetectionViewControllerDelegate> delegate;

/** Initates the live view controller with the given detector type. */
- (instancetype)initWithDetectorType:(MLKDetectorType)detectorType;

@end

NS_ASSUME_NONNULL_END
