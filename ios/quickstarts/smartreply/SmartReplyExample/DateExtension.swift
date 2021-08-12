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

import Foundation

extension Date {

  func timeAgo() -> String {

    let interval = Calendar.current.dateComponents(
      [.year, .day, .hour, .minute, .second], from: self, to: Date())

    if let year = interval.year, year > 0 {
      return DateFormatter.localizedString(from: self, dateStyle: .long, timeStyle: .none)
    } else if let day = interval.day, day > 6 {
      let format = DateFormatter.dateFormat(
        fromTemplate: "MMMMd", options: 0, locale: NSLocale.current)
      let formatter = DateFormatter()
      formatter.dateFormat = format
      return formatter.string(from: self)
    } else if let day = interval.day, day > 0 {
      return day == 1 ? "\(day) day ago" : "\(day) days ago"
    } else if let hour = interval.hour, hour > 0 {
      return hour == 1 ? "\(hour) hour ago" : "\(hour) hours ago"
    } else if let minute = interval.minute, minute > 0 {
      return minute == 1 ? "\(minute) minute ago" : "\(minute) minutes ago"
    } else if let second = interval.second, second > 0 {
      return second == 1 ? "\(second) second ago" : "\(second) seconds ago"
    } else {
      return "just now"
    }
  }
}
