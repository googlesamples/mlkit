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

#import "AppDelegate.h"

#import "MLKLiveObjectDetectionViewController.h"
#import "MLKResultListViewController.h"
#import "MLKStartPageViewController.h"
#import "MLKResult.h"

NS_ASSUME_NONNULL_BEGIN

@implementation AppDelegate

#pragma mark - UIApplicationDelegate

- (BOOL)application:(UIApplication *)application
    didFinishLaunchingWithOptions:(nullable NSDictionary *)launchOptions {
  MLKStartPageViewController *startPageViewController =
      [[MLKStartPageViewController alloc] init];

  UIApplication.sharedApplication.idleTimerDisabled = YES;

  self.window = [[UIWindow alloc] initWithFrame:UIScreen.mainScreen.bounds];
  self.window.backgroundColor = UIColor.blackColor;
  self.window.rootViewController = startPageViewController;
  [self.window makeKeyAndVisible];
  return YES;
}

- (NSArray<MLKResult *> *)fakeResultss {
  NSMutableArray<MLKResult *> *fakeResultss = [NSMutableArray array];
  for (NSInteger index = 0; index < 10; index++) {

    MLKResult *result = [[MLKResult alloc] init];
    result.title = @"Name";
    result.details = @"Type";
    result.priceFullText = @"Price";
    [fakeResultss addObject:result];
  }
  return fakeResultss;
}


@end

NS_ASSUME_NONNULL_END
