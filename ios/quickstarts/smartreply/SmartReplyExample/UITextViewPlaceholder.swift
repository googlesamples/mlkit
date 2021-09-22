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

import UIKit

/// Extend UITextView and implemented UITextViewDelegate to listen for changes
extension UITextView {

  public convenience init(placeholder: String) {
    self.init()
    self.placeholder = placeholder
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(textViewDidChange),
      name: UITextView.textDidChangeNotification,
      object: nil)
  }

  /// Resize the placeholder when the UITextView bounds change
  override open var bounds: CGRect {
    didSet {
      self.resizePlaceholder()
    }
  }

  /// The UITextView placeholder text
  public var placeholder: String? {
    get {
      var placeholderText: String?

      if let placeholderLabel = self.viewWithTag(100) as? UILabel {
        placeholderText = placeholderLabel.text
      }

      return placeholderText
    }
    set {
      if let placeholderLabel = self.viewWithTag(100) as? UILabel {
        placeholderLabel.text = newValue
        placeholderLabel.sizeToFit()
      } else {
        self.addPlaceholder(newValue!)
      }
    }
  }

  /// When the UITextView did change, show or hide the label based on if the UITextView is empty or not
  ///
  /// - Parameter textView: The UITextView that got updated
  @objc public func textViewDidChange(_ textView: UITextView) {
    if let placeholderLabel = self.viewWithTag(100) as? UILabel {
      placeholderLabel.isHidden = !self.text.isEmpty
    }
  }

  /// Resize the placeholder UILabel to make sure it's in the same position as the UITextView text
  func resizePlaceholder() {
    if let placeholderLabel = self.viewWithTag(100) as? UILabel {
      let labelX = self.textContainer.lineFragmentPadding
      let labelY = self.textContainerInset.top - 2
      let labelWidth = self.frame.width - (labelX * 2)
      let labelHeight = placeholderLabel.frame.height

      placeholderLabel.frame = CGRect(x: labelX, y: labelY, width: labelWidth, height: labelHeight)
    }
  }

  /// Adds a placeholder UILabel to this UITextView
  private func addPlaceholder(_ placeholderText: String) {
    let placeholderLabel = UILabel()

    placeholderLabel.text = placeholderText
    placeholderLabel.sizeToFit()

    placeholderLabel.font = UIFont.preferredFont(forTextStyle: .callout)
    placeholderLabel.textColor = UIColor.lightGray
    placeholderLabel.tag = 100

    placeholderLabel.isHidden = !self.text.isEmpty

    self.addSubview(placeholderLabel)
    self.resizePlaceholder()
  }
}
