//
// Copyright (c) 2020 Google Inc.
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

#import "ViewController.h"

@import MLKit;

NS_ASSUME_NONNULL_BEGIN

@interface ViewController ()

@property(weak, nonatomic) IBOutlet UITextView *inputTextView;
@property(weak, nonatomic) IBOutlet UITextView *outputTextView;
@property(weak, nonatomic) IBOutlet UITextView *statusTextView;
@property(weak, nonatomic) IBOutlet UIPickerView *inputPicker;
@property(weak, nonatomic) IBOutlet UIPickerView *outputPicker;
@property(weak, nonatomic) IBOutlet UIButton *sourceDownloadDeleteButton;
@property(weak, nonatomic) IBOutlet UIButton *targetDownloadDeleteButton;

@property(nonatomic, strong) MLKTranslator *translator;
@property(nonatomic, strong) NSArray<MLKTranslateLanguage> *allLanguages;

@end

@implementation ViewController

- (void)viewDidLoad {
  [super viewDidLoad];
  NSLocale *currentLocale = NSLocale.currentLocale;
  self.allLanguages = [MLKTranslateAllLanguages().allObjects
      sortedArrayUsingComparator:^NSComparisonResult(NSString *_Nonnull lang1,
                                                     NSString *_Nonnull lang2) {
        return [[currentLocale localizedStringForLanguageCode:lang1]
            compare:[currentLocale localizedStringForLanguageCode:lang2]];
      }];
  self.inputPicker.dataSource = self;
  self.outputPicker.dataSource = self;
  [self.inputPicker selectRow:[self.allLanguages indexOfObject:MLKTranslateLanguageEnglish]
                  inComponent:0
                     animated:NO];
  [self.outputPicker selectRow:[self.allLanguages indexOfObject:MLKTranslateLanguageSpanish]
                   inComponent:0
                      animated:NO];
  self.inputPicker.delegate = self;
  self.outputPicker.delegate = self;
  self.inputTextView.delegate = self;
  self.inputTextView.returnKeyType = UIReturnKeyDone;

  [self pickerView:self.inputPicker didSelectRow:0 inComponent:0];
  [self updateDownloadDeleteButtonLabels];

  [NSNotificationCenter.defaultCenter
      addObserver:self
         selector:@selector(modelDownloadDidCompleteWithNotification:)
             name:MLKModelDownloadDidSucceedNotification
           object:nil];
  [NSNotificationCenter.defaultCenter
      addObserver:self
         selector:@selector(modelDownloadDidCompleteWithNotification:)
             name:MLKModelDownloadDidFailNotification
           object:nil];
}

- (NSInteger)numberOfComponentsInPickerView:(UIPickerView *)pickerView {
  return 1;
}

- (nullable NSString *)pickerView:(UIPickerView *)pickerView
                      titleForRow:(NSInteger)row
                     forComponent:(NSInteger)component {
  return [NSLocale.currentLocale localizedStringForLanguageCode:self.allLanguages[row]];
}

- (NSInteger)pickerView:(UIPickerView *)pickerView numberOfRowsInComponent:(NSInteger)component {
  return self.allLanguages.count;
}

- (void)textViewDidChange:(UITextView *)textView {
  [self translate];
}

- (BOOL)textView:(UITextView *)textView
    shouldChangeTextInRange:(NSRange)range
            replacementText:(NSString *)text {
  // Hide the keyboard when "Done" is pressed.
  // See: https://stackoverflow.com/questions/26600359/dismiss-keyboard-with-a-uitextview
  if ([text isEqualToString:@"\n"]) {
    [textView resignFirstResponder];
    return NO;
  }
  return YES;
}

- (IBAction)didTapSwap {
  NSInteger inputSelectedRow = [self.inputPicker selectedRowInComponent:0];
  [self.inputPicker selectRow:[self.outputPicker selectedRowInComponent:0]
                  inComponent:0
                     animated:NO];
  [self.outputPicker selectRow:inputSelectedRow inComponent:0 animated:NO];
  self.inputTextView.text = self.outputTextView.text;
  [self pickerView:self.inputPicker didSelectRow:0 inComponent:0];
}

- (void)pickerView:(UIPickerView *)pickerView
      didSelectRow:(NSInteger)row
       inComponent:(NSInteger)component {
  MLKTranslatorOptions *options = [[MLKTranslatorOptions alloc]
      initWithSourceLanguage:self.allLanguages[[self.inputPicker selectedRowInComponent:0]]
              targetLanguage:self.allLanguages[[self.outputPicker selectedRowInComponent:0]]];
  [self updateDownloadDeleteButtonLabels];
  self.translator = [MLKTranslator translatorWithOptions:options];
  [self translate];
}

- (void)translate {
  MLKTranslator *translatorForDownload = self.translator;
  __weak typeof(self) weakSelf = self;
  [self.translator downloadModelIfNeededWithCompletion:^(NSError *_Nullable error) {
    __strong typeof(weakSelf) strongSelf = weakSelf;
    if (error != nil) {
      strongSelf.outputTextView.text =
          [NSString stringWithFormat:@"Failed to ensure model downloaded with error %@",
                                     error.localizedDescription];
      return;
    }
    [strongSelf updateDownloadDeleteButtonLabels];
    NSString *text = strongSelf.inputTextView.text;
    if (text == nil) {
      text = @"";
    }
    strongSelf.outputTextView.text = @"";
    if (translatorForDownload != self.translator) {
      return;
    }
    [strongSelf.translator
        translateText:text
           completion:^(NSString *_Nullable result, NSError *_Nullable error) {
             __strong typeof(weakSelf) strongSelf2 = weakSelf;
             if (error != nil) {
               strongSelf2.outputTextView.text =
                   [NSString stringWithFormat:@"Failed to ensure model downloaded with error %@",
                                              error.localizedDescription];
               return;
             }
             if (translatorForDownload != strongSelf2.translator) {
               return;
             }
             strongSelf2.outputTextView.text = result;
           }];
  }];
}

- (void)handleDownloadDeleteWithPicker:(UIPickerView *)picker button:(UIButton *)button {
  MLKTranslateLanguage language = self.allLanguages[[picker selectedRowInComponent:0]];
  if (language == MLKTranslateLanguageEnglish) {
    return;
  }
  NSString *languageName = [NSLocale.currentLocale localizedStringForLanguageCode:language];

  [button setTitle:@"Working..." forState:UIControlStateNormal];
  MLKTranslateRemoteModel *model = [self modelForLanguage:language];
  MLKModelManager *modelManager = [MLKModelManager modelManager];

  if ([modelManager isModelDownloaded:model]) {
    self.statusTextView.text = [NSString stringWithFormat:@"Deleting %@", languageName];
    [modelManager deleteDownloadedModel:model
                             completion:^(NSError *_Nullable error) {
                               [self updateDownloadDeleteButtonLabels];
                               self.statusTextView.text =
                                   [NSString stringWithFormat:@"Deleted %@", languageName];
                             }];
  } else {
    self.statusTextView.text = [NSString stringWithFormat:@"Downloading %@", languageName];
    MLKModelDownloadConditions *conditions =
        [[MLKModelDownloadConditions alloc] initWithAllowsCellularAccess:YES
                                             allowsBackgroundDownloading:YES];
    [modelManager downloadModel:model conditions:conditions];
  }
}

- (void)updateDownloadDeleteButtonLabels {
  MLKTranslateLanguage inputLanguage =
      self.allLanguages[[self.inputPicker selectedRowInComponent:0]];
  MLKTranslateLanguage outputLanguage =
      self.allLanguages[[self.outputPicker selectedRowInComponent:0]];

  if ([self isLanguageDownloaded:inputLanguage]) {
    [self.sourceDownloadDeleteButton setTitle:@"Delete Model" forState:UIControlStateNormal];
  } else {
    [self.sourceDownloadDeleteButton setTitle:@"Download Model" forState:UIControlStateNormal];
  }
  self.sourceDownloadDeleteButton.hidden = inputLanguage == MLKTranslateLanguageEnglish;
  if ([self isLanguageDownloaded:outputLanguage]) {
    [self.targetDownloadDeleteButton setTitle:@"Delete Model" forState:UIControlStateNormal];
  } else {
    [self.targetDownloadDeleteButton setTitle:@"Download Model" forState:UIControlStateNormal];
  }
  self.targetDownloadDeleteButton.hidden = outputLanguage == MLKTranslateLanguageEnglish;
}

- (BOOL)isLanguageDownloaded:(MLKTranslateLanguage)language {
  MLKTranslateRemoteModel *model = [self modelForLanguage:language];
  MLKModelManager *modelManager = [MLKModelManager modelManager];
  return [modelManager isModelDownloaded:model];
}

- (IBAction)listDownloadedModels {
  MLKModelManager *modelManager = [MLKModelManager modelManager];
  NSMutableString *listOfLanguages = [NSMutableString string];
  for (MLKTranslateRemoteModel *model in modelManager.downloadedTranslateModels) {
    if (listOfLanguages.length > 0) {
      [listOfLanguages appendString:@", "];
    }

    [listOfLanguages
        appendString:[NSLocale.currentLocale localizedStringForLanguageCode:model.language]];
  }
  self.statusTextView.text = [NSString stringWithFormat:@"Downloaded models: %@", listOfLanguages];
}

- (IBAction)didTapDownloadDeleteSourceLanguage {
  [self handleDownloadDeleteWithPicker:self.inputPicker button:self.sourceDownloadDeleteButton];
}

- (IBAction)didTapDownloadDeleteTargetLanguage {
  [self handleDownloadDeleteWithPicker:self.outputPicker button:self.targetDownloadDeleteButton];
}

- (MLKTranslateRemoteModel *)modelForLanguage:(MLKTranslateLanguage)language {
  return [MLKTranslateRemoteModel translateRemoteModelWithLanguage:language];
}

- (void)modelDownloadDidCompleteWithNotification:(NSNotification *)notification {
  MLKTranslateRemoteModel *model = notification.userInfo[MLKModelDownloadUserInfoKeyRemoteModel];
  if (![model isKindOfClass:MLKTranslateRemoteModel.class]) {
    return;
  }
  NSString *languageName = [NSLocale.currentLocale localizedStringForLanguageCode:model.language];

  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    if (notification.name == MLKModelDownloadDidSucceedNotification) {
      strongSelf.statusTextView.text =
          [NSString stringWithFormat:@"Download succeeded for %@", languageName];
    } else {
      strongSelf.statusTextView.text =
          [NSString stringWithFormat:@"Download failed for%@", languageName];
    }
    [strongSelf updateDownloadDeleteButtonLabels];
  });
}

@end

NS_ASSUME_NONNULL_END
