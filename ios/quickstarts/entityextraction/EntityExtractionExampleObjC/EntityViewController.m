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

#import "EntityViewController.h"
#import "MLKEntityExtractionModelIdentifier+Extensions.h"

@import MLKitCommon;
@import MLKitEntityExtraction;

NS_ASSUME_NONNULL_BEGIN

@interface EntityViewController ()

@property(weak, nonatomic) IBOutlet UITextView *inputTextView;
@property(weak, nonatomic) IBOutlet UITextView *outputTextView;
@property(weak, nonatomic) IBOutlet UIPickerView *languagePicker;

@property(nonatomic, strong) MLKEntityExtractor *entityExtractor;
@property(nonatomic, strong) MLKEntityExtractionModelIdentifier modelForExtractor;
@property(nonatomic, strong) NSArray<UIColor *> *colorPalette;
@property(nonatomic, strong) NSArray<MLKEntityExtractionModelIdentifier> *languages;

@end

@implementation EntityViewController

+ (NSArray<UIColor *> *)simplePalette {
  NSMutableArray<UIColor *> *palette = [NSMutableArray array];
  [palette addObject:[[UIColor cyanColor] colorWithAlphaComponent:0.25]];
  [palette addObject:[[UIColor orangeColor] colorWithAlphaComponent:0.25]];
  [palette addObject:[[UIColor greenColor] colorWithAlphaComponent:0.25]];
  [palette addObject:[[UIColor cyanColor] colorWithAlphaComponent:0.25]];
  [palette addObject:[[UIColor magentaColor] colorWithAlphaComponent:0.25]];
  [palette addObject:[[UIColor yellowColor] colorWithAlphaComponent:0.25]];
  return palette;
}

- (void)viewDidLoad {
  [super viewDidLoad];
  self.languages = MLKEntityExtractionAllModelIdentifiersSorted();
  self.colorPalette = [self.class simplePalette];
  self.modelForExtractor = @"";

  self.inputTextView.delegate = self;
  self.inputTextView.returnKeyType = UIReturnKeyDone;
  self.languagePicker.delegate = self;
  self.languagePicker.dataSource = self;

  NSUInteger languageRow = [self.languages indexOfObject:MLKEntityExtractionModelIdentifierEnglish];
  [self.languagePicker selectRow:languageRow inComponent:0 animated:NO];

  [self downloadModelAndAnnotate];
}

- (void)downloadModelAndAnnotate {
  MLKEntityExtractionModelIdentifier model =
      self.languages[[self.languagePicker selectedRowInComponent:0]];

  NSLocale *locale = [NSLocale currentLocale];  // Use system locale or use a locale of your choice.
  if (![model isEqualToString:self.modelForExtractor]) {
    self.modelForExtractor = model;
    self.entityExtractor =
        [MLKEntityExtractor entityExtractorWithOptions:[[MLKEntityExtractorOptions alloc]
                                                           initWithModelIdentifier:model]];
  }
  MLKEntityExtractor *extractor = self.entityExtractor;
  NSAttributedString *text = [self.inputTextView.attributedText copy];
  __weak typeof(self) weakSelf = self;
  [extractor downloadModelIfNeededWithCompletion:^(NSError *_Nullable error) {
    typeof(self) strongSelf = weakSelf;
    if (strongSelf == nil) return;
    if (error != nil) {
      strongSelf.outputTextView.text = [NSString
          stringWithFormat:@"Model downloading failed with error: %@", error.localizedDescription];
    } else {
      [strongSelf annotateText:text withExtractor:extractor locale:locale];
    }
  }];
}

+ (NSString *)stringFromPaymentCardNetwork:(MLKPaymentCardNetwork)network {
  switch (network) {
    case MLKPaymentCardNetworkUnknown:
      return @"unknown";
    case MLKPaymentCardNetworkAmex:
      return @"Amex";
    case MLKPaymentCardNetworkDinersClub:
      return @"DinersClub";
    case MLKPaymentCardNetworkDiscover:
      return @"Discover";
    case MLKPaymentCardNetworkInterPayment:
      return @"InterPayment";
    case MLKPaymentCardNetworkJCB:
      return @"JCB";
    case MLKPaymentCardNetworkMaestro:
      return @"Maestro";
    case MLKPaymentCardNetworkMastercard:
      return @"Mastercard";
    case MLKPaymentCardNetworkMir:
      return @"Mir";
    case MLKPaymentCardNetworkTroy:
      return @"Troy";
    case MLKPaymentCardNetworkUnionpay:
      return @"Unionpay";
    case MLKPaymentCardNetworkVisa:
      return @"Visa";
  }
  return @"unknown";
}

+ (NSString *)stringFromGranularity:(MLKDateTimeGranularity)granularity {
  switch (granularity) {
    case MLKDateTimeGranularityUnknown:
      return @"unknown";
    case MLKDateTimeGranularityYear:
      return @"year";
    case MLKDateTimeGranularityMonth:
      return @"month";
    case MLKDateTimeGranularityWeek:
      return @"week";
    case MLKDateTimeGranularityDay:
      return @"day";
    case MLKDateTimeGranularityHour:
      return @"hour";
    case MLKDateTimeGranularityMinute:
      return @"minute";
    case MLKDateTimeGranularitySecond:
      return @"second";
  }
}

+ (NSString *)stringFromCarrier:(MLKParcelTrackingCarrier)carrier {
  switch (carrier) {
    case MLKParcelTrackingCarrierUnknown:
      return @"unknown";
    case MLKParcelTrackingCarrierFedEx:
      return @"FedEx";
    case MLKParcelTrackingCarrierUPS:
      return @"UPS";
    case MLKParcelTrackingCarrierDHL:
      return @"DHL";
    case MLKParcelTrackingCarrierUSPS:
      return @"USPS";
    case MLKParcelTrackingCarrierOntrac:
      return @"Ontrac";
    case MLKParcelTrackingCarrierLasership:
      return @"Lasership";
    case MLKParcelTrackingCarrierIsraelPost:
      return @"IsraelPost";
    case MLKParcelTrackingCarrierSwissPost:
      return @"SwissPost";
    case MLKParcelTrackingCarrierMSC:
      return @"MSC";
    case MLKParcelTrackingCarrierAmazon:
      return @"Amazon";
    case MLKParcelTrackingCarrierIParcel:
      return @"IParcel";
  }
  return @"unknown";
}

+ (NSString *)stringFromAnnotation:(MLKEntityAnnotation *)annotation {
  NSMutableArray *outputs = [NSMutableArray array];
  for (MLKEntity *entity in annotation.entities) {
    NSMutableString *output = [entity.entityType mutableCopy];
    if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypeAddress]) {
      // Identifies a physical address.
      // No structured data available.
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypeDateTime]) {
      // Identifies a date and time reference that may include a specific time. May be absolute such
      // as "01/01/2000 5:30pm" or relative like "tomorrow at 5:30pm".
      NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
      NSTimeZone *timeZone = [NSTimeZone localTimeZone];
      [formatter setTimeZone:timeZone];
      [formatter setDateFormat:@"yyyy-MM-dd HH:mm:ss"];
      [output appendFormat:@": %@ (granularity %@)",
                           [formatter stringFromDate:entity.dateTimeEntity.dateTime],
                           [self stringFromGranularity:entity.dateTimeEntity.dateTimeGranularity]];
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypeEmail]) {
      // Identifies an e-mail address.
      // No structured data available.
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypeFlightNumber]) {
      // Identifies a flight number in IATA format.
      [output appendFormat:@": %@ %@", entity.flightNumberEntity.airlineCode,
                           entity.flightNumberEntity.flightNumber];
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypeIBAN]) {
      // Identifies an International Bank Account Number (IBAN).
      [output appendFormat:@": %@ %@", entity.IBANEntity.countryCode, entity.IBANEntity.IBAN];
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypeISBN]) {
      // Identifies an International Standard Book Number (ISBN).
      [output appendFormat:@": %@", entity.ISBNEntity.ISBN];
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypePaymentCard]) {
      // Identifies a payment card.
      [output appendFormat:@": %@ %@",
                           [self stringFromPaymentCardNetwork:entity.paymentCardEntity
                                                                  .paymentCardNetwork],
                           entity.paymentCardEntity.paymentCardNumber];
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypePhone]) {
      // Identifies a phone number.
      // No structured data available.
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypeTrackingNumber]) {
      // Identifies a shipment tracking number.
      [output appendFormat:@": %@ %@",
                           [self stringFromCarrier:entity.trackingNumberEntity.parcelCarrier],
                           entity.trackingNumberEntity.parcelTrackingNumber];
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypeURL]) {
      // Identifies a URL.
      // No structured data available.
    } else if ([entity.entityType isEqualToString:MLKEntityExtractionEntityTypeMoney]) {
      // Identifies currency.
      [output appendFormat:@": %@", entity.moneyEntity];
    }
    [outputs addObject:output];
  }

  return [NSString stringWithFormat:@"[%@]\n", [outputs componentsJoinedByString:@", "]];
}

- (void)annotateText:(NSAttributedString *)text
       withExtractor:(MLKEntityExtractor *)extractor
              locale:(NSLocale *)locale {
  __weak typeof(self) weakSelf = self;
  MLKEntityExtractionParams *params = [[MLKEntityExtractionParams alloc] init];
  params.preferredLocale = locale;
  [extractor
      annotateText:text.string
        withParams:params
        completion:^(NSArray<MLKEntityAnnotation *> *_Nullable result, NSError *_Nullable error) {
          typeof(self) strongSelf = weakSelf;
          if (strongSelf == nil) return;
          NSDictionary *outputAttributes = @{NSFontAttributeName : self.outputTextView.font};
          NSMutableAttributedString *output = [[NSMutableAttributedString alloc] init];
          NSMutableAttributedString *input = [text mutableCopy];
          [input removeAttribute:NSBackgroundColorAttributeName
                           range:NSMakeRange(0, input.string.length)];
          NSUInteger i = 0;
          if (error != nil) {
            NSString *message =
                [NSString stringWithFormat:@"Entity Extractor failed with error: %@",
                                           error.localizedDescription];
            strongSelf.outputTextView.attributedText =
                [[NSAttributedString alloc] initWithString:message attributes:outputAttributes];
          }
          if (result.count == 0) {
            [output appendAttributedString:[[NSAttributedString alloc]
                                               initWithString:@"No results returned."
                                                   attributes:outputAttributes]];
          } else {
            for (MLKEntityAnnotation *annotation in result) {
              UIColor *color =
                  [strongSelf.colorPalette objectAtIndex:(i++ % strongSelf.colorPalette.count)];
              [input addAttributes:@{NSBackgroundColorAttributeName : color}
                             range:annotation.range];
              NSString *annotationString = [strongSelf.class stringFromAnnotation:annotation];
              NSMutableAttributedString *annotationAttributedString =
                  [[NSMutableAttributedString alloc] initWithString:annotationString
                                                         attributes:outputAttributes];
              [annotationAttributedString addAttributes:@{NSBackgroundColorAttributeName : color}
                                                  range:NSMakeRange(0, annotationString.length)];
              [output appendAttributedString:annotationAttributedString];
            }
          }
          strongSelf.outputTextView.attributedText = output;
          strongSelf.inputTextView.attributedText = input;
        }];
}

- (NSInteger)numberOfComponentsInPickerView:(UIPickerView *)pickerView {
  return 1;
}

- (nullable NSString *)pickerView:(UIPickerView *)pickerView
                      titleForRow:(NSInteger)row
                     forComponent:(NSInteger)component {
  if (pickerView == self.languagePicker) {
    NSString *code = MLKEntityExtractionLanguageTagForModelIdentifier(self.languages[row]);
    return [NSLocale.currentLocale localizedStringForLanguageCode:code];
  }
  return @"";
}

- (NSInteger)pickerView:(UIPickerView *)pickerView numberOfRowsInComponent:(NSInteger)component {
  if (pickerView == self.languagePicker) {
    return self.languages.count;
  }
  return 0;
}

- (void)pickerView:(UIPickerView *)pickerView
      didSelectRow:(NSInteger)row
       inComponent:(NSInteger)component {
  [self downloadModelAndAnnotate];
}

#pragma mark - UITextViewDelegate

- (BOOL)textView:(UITextView *)textView
    shouldChangeTextInRange:(NSRange)range
            replacementText:(NSString *)text {
  if ([text isEqualToString:@"\n"]) {
    [textView resignFirstResponder];
    return NO;
  }
  return YES;
}

- (void)textViewDidChange:(UITextView *)textView {
  [self downloadModelAndAnnotate];
}

@end

NS_ASSUME_NONNULL_END
