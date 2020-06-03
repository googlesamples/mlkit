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

NS_ASSUME_NONNULL_BEGIN

/** Object detection statuses. */
typedef NS_ENUM(NSInteger, MLKODTStatus) {
  /** Object detection hasn't started yet. */
  MLKODTStatus_NotStarted,
  /** Object detection started detecting on new objects. */
  MLKODTStatus_Detecting,
  /** Object detection is confirming on the same object. */
  MLKODTStatus_Confirming,
  /** Object detection is searching the detected object. */
  MLKODTStatus_Searching,
  /** Object detection has got search results on detected object. */
  MLKODTStatus_Searched,
};

NS_ASSUME_NONNULL_END
