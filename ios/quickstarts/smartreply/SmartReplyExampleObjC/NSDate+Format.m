//
//  Copyright (c) 2020 Google Inc.
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

#import "NSDate+Format.h"

@implementation NSDate (Format)
- (NSString *)timeAgo {
  NSCalendarUnit components = NSCalendarUnitYear | NSCalendarUnitDay | NSCalendarUnitHour |
                              NSCalendarUnitMinute | NSCalendarUnitSecond;
  NSDateComponents *interval = [NSCalendar.currentCalendar components:components
                                                             fromDate:self
                                                               toDate:[NSDate new]
                                                              options:0];

  if (interval.year > 0) {
    return [NSDateFormatter localizedStringFromDate:self
                                          dateStyle:NSDateFormatterLongStyle
                                          timeStyle:NSDateFormatterNoStyle];
  } else if (interval.day > 6) {
    NSString *format = [NSDateFormatter dateFormatFromTemplate:@"MMMMd"
                                                       options:0
                                                        locale:NSLocale.currentLocale];
    NSDateFormatter *formatter = [NSDateFormatter new];
    formatter.dateFormat = format;
    return [formatter stringFromDate:self];
  } else if (interval.day > 0) {
    NSString *format = interval.day == 1 ? @"%d day ago" : @"%d days ago";
    return [NSString stringWithFormat:format, interval.day];
  } else if (interval.hour > 0) {
    NSString *format = interval.hour == 1 ? @"%d hour ago" : @"%d hours ago";
    return [NSString stringWithFormat:format, interval.hour];
  } else if (interval.minute > 0) {
    NSString *format = interval.minute == 1 ? @"%d minute ago" : @"%d minutes ago";
    return [NSString stringWithFormat:format, interval.minute];
  } else if (interval.second > 0) {
    NSString *format = interval.second == 1 ? @"%d second ago" : @"%d seconds ago";
    return [NSString stringWithFormat:format, interval.second];
  } else {
    return @"just now";
  }
}

@end
