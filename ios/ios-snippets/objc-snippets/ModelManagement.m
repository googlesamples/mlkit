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
@import MLKitCommon;

@interface ModelManagementSnippets : NSObject

- (void)setUpRemoteModel;
- (void)setUpModelDownloadNotifications;
- (void)setUpLocalModel;

@end

@implementation ModelManagementSnippets

- (void)setUpRemoteModel {
  // [START setup_remote_model]
  MLKModelDownloadConditions *downloadConditions =
      [[MLKModelDownloadConditions alloc] initWithAllowsCellularAccess:YES
                                           allowsBackgroundDownloading:YES];
  // Instantiate a concrete subclass of MLKRemoteModel.
  MLKRemoteModel *remoteModel =
      [[MLKAutoMLRemoteModel alloc] initWithName:@"your_remote_model"];  // The name you assigned in the console.
  // [END setup_remote_model]

  // [START start_download]
  NSProgress *downloadProgress = [[MLKModelManager modelManager] downloadModel:remoteModel
                                                                    conditions:downloadConditions];

  // ...

  if (downloadProgress.isFinished) {
    // The model is available on the device
  }
  // [END start_download]
}

- (void)setUpModelDownloadNotifications {
  // [START setup_notifications]
  __weak typeof(self) weakSelf = self;

  [NSNotificationCenter.defaultCenter
      addObserverForName:MLKModelDownloadDidSucceedNotification
                  object:nil
                   queue:nil
              usingBlock:^(NSNotification *_Nonnull note) {
                if (weakSelf == nil | note.userInfo == nil) {
                  return;
                }
                __strong typeof(self) strongSelf = weakSelf;

                MLKRemoteModel *model = note.userInfo[MLKModelDownloadUserInfoKeyRemoteModel];
                if ([model.name isEqualToString:@"your_remote_model"]) {
                  // The model was downloaded and is available on the device
                }
              }];

  [NSNotificationCenter.defaultCenter
      addObserverForName:MLKModelDownloadDidFailNotification
                  object:nil
                   queue:nil
              usingBlock:^(NSNotification *_Nonnull note) {
                if (weakSelf == nil | note.userInfo == nil) {
                  return;
                }
                __strong typeof(self) strongSelf = weakSelf;

                NSError *error = note.userInfo[MLKModelDownloadUserInfoKeyError];
              }];
  // [END setup_notifications]
}

- (void)setUpLocalModel {
  // [START setup_local_model]
  NSString *manifestPath = [NSBundle.mainBundle pathForResource:@"manifest"
                                                         ofType:@"json"
                                                    inDirectory:@"my_model"];
  // Instantiate a concrete subclass of MLKLocalModel.
  MLKLocalModel *localModel = [[MLKAutoMLLocalModel alloc]
                               initWithManifestPath:manifestPath];
  // [END setup_local_model]
}

@end
