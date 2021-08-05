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

#import "ModelManagementViewController.h"
#import "MLKEntityExtractionModelIdentifier+Extensions.h"

@import MLKitCommon;
@import MLKitEntityExtraction;

NS_ASSUME_NONNULL_BEGIN

@interface ModelManagementViewController ()

@property(nonatomic, strong) NSArray<MLKEntityExtractionModelIdentifier> *languages;
@property(nonatomic, strong) MLKModelManager *modelManager;
@property(nonatomic, strong) NSSet<MLKEntityExtractionModelIdentifier> *downloadedLanguages;

@end

@implementation ModelManagementViewController

- (void)viewDidLoad {
  [super viewDidLoad];
  self.languages = MLKEntityExtractionAllModelIdentifiersSorted();
  self.modelManager = [MLKModelManager modelManager];
  [self refresh];

  [NSNotificationCenter.defaultCenter
      addObserver:self
         selector:@selector(receiveModelLoadingDidCompleteNotification:)
             name:MLKModelDownloadDidSucceedNotification
           object:nil];
  [NSNotificationCenter.defaultCenter
      addObserver:self
         selector:@selector(receiveModelLoadingDidCompleteNotification:)
             name:MLKModelDownloadDidFailNotification
           object:nil];
}

- (void)refresh {
  NSSet<MLKEntityExtractionRemoteModel *> *downloadedModels =
      [self.modelManager downloadedEntityExtractionModels];
  NSMutableSet<MLKEntityExtractionModelIdentifier> *languages = [[NSMutableSet alloc] init];
  for (MLKEntityExtractionRemoteModel *model in downloadedModels) {
    [languages addObject:model.modelIdentifier];
  }
  self.downloadedLanguages = languages;
  [self.tableView reloadData];
}

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
  return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
  return self.languages.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView
         cellForRowAtIndexPath:(NSIndexPath *)indexPath {
  static NSString *cellIdentifier = @"language";

  UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:cellIdentifier];
  if (cell == nil) {
    cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleDefault
                                  reuseIdentifier:cellIdentifier];
  }

  MLKEntityExtractionModelIdentifier language = self.languages[indexPath.row];
  NSString *code = MLKEntityExtractionLanguageTagForModelIdentifier(language);
  cell.textLabel.text = [NSLocale.currentLocale localizedStringForLanguageCode:code];
  if ([self.downloadedLanguages containsObject:language]) {
    cell.imageView.image = [UIImage imageNamed:@"delete_24pt"];
  } else {
    cell.imageView.image = [UIImage imageNamed:@"cloud_download_24pt"];
  }
  return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath {
  MLKEntityExtractionModelIdentifier language = self.languages[indexPath.row];
  MLKEntityExtractionRemoteModel *model =
      [MLKEntityExtractionRemoteModel entityExtractorRemoteModelWithIdentifier:language];
  if ([self.downloadedLanguages containsObject:language]) {
    [self.modelManager deleteDownloadedModel:model
                                  completion:^(NSError *_Nullable error) {
                                    [self refresh];
                                  }];
  } else {
    MLKModelDownloadConditions *conditions =
        [[MLKModelDownloadConditions alloc] initWithAllowsCellularAccess:YES
                                             allowsBackgroundDownloading:YES];
    [self.modelManager downloadModel:model conditions:conditions];
  }
}

- (void)receiveModelLoadingDidCompleteNotification:(NSNotification *)notification {
  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    [weakSelf refresh];
  });
}

@end

NS_ASSUME_NONNULL_END
