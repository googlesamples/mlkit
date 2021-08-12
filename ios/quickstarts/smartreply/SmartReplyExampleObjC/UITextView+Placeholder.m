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

#import "UITextView+Placeholder.h"

/// Extend UITextView and implemented UITextViewDelegate to listen for changes
@implementation UITextView (Placeholder)
- (instancetype)initWithPlaceholder:(NSString *)placeholder {
  self = [self init];
  if (self) {
    self.placeholder = placeholder;
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector:@selector(textViewDidChange:)
                                               name:UITextViewTextDidChangeNotification
                                             object:nil];
  }
  return self;
}

/// Resize the placeholder when the UITextView bounds change
- (void)setBounds:(CGRect)bounds {
  [super setBounds:bounds];
  [self resizePlaceholder];
}

- (void)setPlaceholder:(NSString *)placeholder {
  UILabel *placeholderLabel = [self viewWithTag:100];
  if (placeholderLabel == nil) {
    [self addPlaceholder:placeholder];
  } else {
    placeholderLabel.text = placeholder;
    [placeholderLabel sizeToFit];
  }
}

- (nullable NSString *)getPlaceholder {
  UILabel *placeholderLabel = [self viewWithTag:100];
  if (placeholderLabel == nil) {
    return nil;
  }
  return placeholderLabel.text;
}

/// When the UITextView did change, show or hide the label based on if the UITextView is empty or
/// not
///
/// - Parameter textView: The UITextView that got updated
- (void)textViewDidChange:(UITextView *)textView {
  UILabel *placeholderLabel = [self viewWithTag:100];
  placeholderLabel.hidden = self.text.length != 0;
}

/// Resize the placeholder UILabel to make sure it's in the same position as the UITextView text
- (void)resizePlaceholder {
  UILabel *placeholderLabel = [self viewWithTag:100];
  CGFloat labelX = self.textContainer.lineFragmentPadding;
  CGFloat labelY = self.textContainerInset.top - 2;
  CGFloat labelWidth = self.frame.size.width - (labelX * 2);
  CGFloat labelHeight = placeholderLabel.frame.size.height;

  placeholderLabel.frame = CGRectMake(labelX, labelY, labelWidth, labelHeight);
}

/// Adds a placeholder UILabel to this UITextView
- (void)addPlaceholder:(NSString *)placeholderText {
  UILabel *placeholderLabel = [UILabel new];

  placeholderLabel.text = placeholderText;
  [placeholderLabel sizeToFit];

  placeholderLabel.font = [UIFont preferredFontForTextStyle:UIFontTextStyleCallout];
  placeholderLabel.textColor = UIColor.lightGrayColor;
  placeholderLabel.tag = 100;

  placeholderLabel.hidden = self.text.length != 0;

  [self addSubview:placeholderLabel];
  [self resizePlaceholder];
}

@end
