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

NS_ASSUME_NONNULL_BEGIN

/**
 * A camera reticle that locates at the center of screen and uses ambient ripple to indicate that
 * the system is active but has not detected an object yet.
 */
@interface MLKCameraReticle : UIView

/** Starts animating the reticle. Does nothing if the reticle is already animating. */
- (void)startAnimating;

/** Stops animating the reticle. Does nothing if the reticle is not animating. */
- (void)stopAnimating;

@end

NS_ASSUME_NONNULL_END
