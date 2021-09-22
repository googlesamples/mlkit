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

#import "MainViewController.h"
#import "NSDate+Format.h"
#import "UITextView+Placeholder.h"

@import MLKit;
@import MaterialComponents;

NS_ASSUME_NONNULL_BEGIN

@interface UIView (Constraints)
- (void)addConstraintsWithFormat:(NSString *)format views:(NSArray<UIView *> *)views;
@end

@implementation UIView (Constraints)
- (void)addConstraintsWithFormat:(NSString *)format views:(NSArray<UIView *> *)views {
  NSMutableDictionary *viewsDictionary = [[NSMutableDictionary alloc] initWithCapacity:views.count];
  for (int i = 0; i < views.count; i++) {
    UIView *view = views[i];
    NSString *key = [NSString stringWithFormat:@"v%d", i];
    view.translatesAutoresizingMaskIntoConstraints = false;
    viewsDictionary[key] = view;
  }
  [self addConstraints:[NSLayoutConstraint constraintsWithVisualFormat:format
                                                               options:0
                                                               metrics:nil
                                                                 views:viewsDictionary]];
}
@end

@interface MainViewController ()
@property(strong, nonatomic) NSMutableArray<MLKTextMessage *> *messages;
@property(nonatomic) BOOL isLocalUser;
@property(strong, nonatomic) NSLayoutConstraint *bottomConstraint;
@property(strong, nonatomic) NSLayoutConstraint *heightConstraint;
@property(strong, nonatomic) NSLayoutConstraint *inputBottomConstraint;
@property(strong, nonatomic) NSLayoutConstraint *sendBottomConstraint;
@property(nonatomic) BOOL isKeyboardShown;
@property(strong, nonatomic) UIView *messageInputContainerView;

@property(strong, nonatomic) MLKSmartReply *smartReply;

@property(nonatomic) CGFloat bottomAreaInset;

@property(strong, nonatomic) UITextView *inputTextView;
@property(strong, nonatomic) UIStackView *smartReplyView;
@property(strong, nonatomic) UILabel *updatedLabel;
@property(strong, nonatomic) MDCFloatingButton *sendButton;
@property(nonatomic, strong) UIAlertController *moreAlert;
@end

@implementation MainViewController

- (UIAlertController *)moreAlert {
  if (_moreAlert == nil) {
    _moreAlert = [UIAlertController alertControllerWithTitle:nil
                                                     message:nil
                                              preferredStyle:UIAlertControllerStyleActionSheet];
    [_moreAlert addAction:[UIAlertAction actionWithTitle:@"Generate basic history"
                                                   style:UIAlertActionStyleDefault
                                                 handler:^(UIAlertAction *action) {
                                                   [self generateChatHistoryBasic];
                                                 }]];
    [_moreAlert addAction:[UIAlertAction actionWithTitle:@"Generate history with sensitive content"
                                                   style:UIAlertActionStyleDefault
                                                 handler:^(UIAlertAction *action) {
                                                   [self generateChatHistoryWithSensitiveContent];
                                                 }]];
    [_moreAlert addAction:[UIAlertAction actionWithTitle:@"Clear chat history"
                                                   style:UIAlertActionStyleDestructive
                                                 handler:^(UIAlertAction *action) {
                                                   self.messages = [NSMutableArray new];
                                                   [self updateReplies];
                                                   [self.collectionView reloadData];
                                                   [self.collectionView
                                                           .collectionViewLayout invalidateLayout];
                                                 }]];
    [_moreAlert addAction:[UIAlertAction actionWithTitle:@"Cancel"
                                                   style:UIAlertActionStyleCancel
                                                 handler:nil]];
  }
  return _moreAlert;
}

- (void)viewDidLoad {
  [super viewDidLoad];
  self.messages = [NSMutableArray new];
  self.isLocalUser = YES;
  self.messageInputContainerView = [UIView new];
  _messageInputContainerView.backgroundColor = UIColor.whiteColor;
  self.smartReply = [MLKSmartReply smartReply];
  self.bottomAreaInset = 0;
  self.inputTextView = [[UITextView alloc] initWithPlaceholder:@"Write a message"];
  _inputTextView.font = [UIFont preferredFontForTextStyle:UIFontTextStyleCallout];
  _inputTextView.scrollEnabled = NO;
  self.smartReplyView = [UIStackView new];
  _smartReplyView.distribution = UIStackViewDistributionEqualSpacing;

  for (int i = 0; i < 3; i++) {
    MDCChipView *chipView = [MDCChipView new];
    chipView.hidden = YES;
    [chipView setTitleColor:UIColor.redColor forState:UIControlStateSelected];
    NSLayoutConstraint *widthConstraint = [chipView.widthAnchor constraintEqualToConstant:0];
    widthConstraint.identifier = @"width";
    widthConstraint.active = YES;
    [chipView addTarget:self
                  action:@selector(replySelected:)
        forControlEvents:UIControlEventTouchUpInside];
    [_smartReplyView addArrangedSubview:chipView];
  }

  self.sendButton = [[MDCFloatingButton alloc] initWithFrame:CGRectNull
                                                       shape:MDCFloatingButtonShapeMini];
  [_sendButton setImage:[UIImage imageNamed:@"ic_send"] forState:UIControlStateNormal];
  _sendButton.tintColor = UIColor.blueColor;
  _sendButton.backgroundColor = UIColor.whiteColor;
  _sendButton.accessibilityLabel = @"Send";
  _sendButton.enabled = NO;
  [_sendButton addTarget:self
                  action:@selector(enterPressed)
        forControlEvents:UIControlEventTouchUpInside];

  self.navigationController.navigationBar.barTintColor = UIColor.blueColor;
  [self.collectionView registerClass:MDCSelfSizingStereoCell.class
          forCellWithReuseIdentifier:@"cell"];
  if (@available(iOS 11, *)) {
    _bottomAreaInset = UIApplication.sharedApplication.keyWindow.safeAreaInsets.bottom;
  }
  self.isKeyboardShown = NO;

  _inputTextView.delegate = self;
  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(handleKeyboardNotification:)
                                             name:UIKeyboardWillShowNotification
                                           object:nil];
  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(handleKeyboardNotification:)
                                             name:UIKeyboardWillHideNotification
                                           object:nil];
  ((UICollectionViewFlowLayout *)self.collectionViewLayout).estimatedItemSize =
      CGSizeMake(self.collectionView.bounds.size.width, 52);
  [self.view addSubview:_messageInputContainerView];
  [self.view addConstraintsWithFormat:@"H:|[v0]|" views:@[ _messageInputContainerView ]];
  self.heightConstraint =
      [_messageInputContainerView.heightAnchor constraintEqualToConstant:88 + _bottomAreaInset];
  self.bottomConstraint = [NSLayoutConstraint constraintWithItem:_messageInputContainerView
                                                       attribute:NSLayoutAttributeBottom
                                                       relatedBy:NSLayoutRelationEqual
                                                          toItem:self.view
                                                       attribute:NSLayoutAttributeBottom
                                                      multiplier:1
                                                        constant:0];
  [self.view addConstraint:_bottomConstraint];
  [self.view addConstraint:_heightConstraint];
  [self setupInputComponents];
}

- (void)setupInputComponents {
  UIView *topBorderView = [UIView new];
  topBorderView.backgroundColor = [UIColor colorWithWhite:0.5 alpha:0.5];
  [_messageInputContainerView addSubview:_inputTextView];
  [_messageInputContainerView addSubview:_sendButton];
  [_messageInputContainerView addSubview:topBorderView];
  [_messageInputContainerView addSubview:_smartReplyView];

  [_messageInputContainerView addConstraintsWithFormat:@"H:|-8-[v0][v1(40)]-16-|"
                                                 views:@[ _inputTextView, _sendButton ]];
  [_messageInputContainerView addConstraintsWithFormat:@"H:|[v0]|" views:@[ topBorderView ]];
  [_messageInputContainerView addConstraintsWithFormat:@"H:|-16-[v0]-16-|"
                                                 views:@[ _smartReplyView ]];

  [_smartReplyView.topAnchor constraintEqualToAnchor:_messageInputContainerView.topAnchor
                                            constant:6]
      .active = YES;
  [_smartReplyView.bottomAnchor constraintEqualToAnchor:_inputTextView.topAnchor constant:-6]
      .active = YES;

  self.inputBottomConstraint =
      [_messageInputContainerView.bottomAnchor constraintEqualToAnchor:_inputTextView.bottomAnchor
                                                              constant:_bottomAreaInset];
  _inputBottomConstraint.active = YES;

  self.sendBottomConstraint =
      [_messageInputContainerView.bottomAnchor constraintEqualToAnchor:_sendButton.bottomAnchor
                                                              constant:_bottomAreaInset + 6];
  _sendBottomConstraint.active = YES;

  [_messageInputContainerView addConstraintsWithFormat:@"V:|[v0(0.5)]" views:@[ topBorderView ]];
}

- (void)viewWillAppear:(BOOL)animated {
  [super viewWillAppear:animated];
  [_inputTextView becomeFirstResponder];
}

- (void)viewWillDisappear:(BOOL)animated {
  [_inputTextView endEditing:YES];
  [super viewWillDisappear:animated];
}

- (void)viewDidLayoutSubviews {
  [super viewDidLayoutSubviews];
  [_inputTextView resizePlaceholder];
}

- (void)enterPressed {
  NSString *text = _inputTextView.text;
  if (text.length > 0) {
    MLKTextMessage *message =
        [[MLKTextMessage alloc] initWithText:text
                                   timestamp:[NSDate date].timeIntervalSince1970
                                      userID:@""
                                 isLocalUser:_isLocalUser];
    [_messages addObject:message];
    [self.collectionView
        insertItemsAtIndexPaths:@[ [NSIndexPath indexPathForItem:_messages.count - 1
                                                       inSection:0] ]];
    _inputTextView.text = nil;
    [_inputTextView textViewDidChange:_inputTextView];
    [self clearChips];
  }
  [_inputTextView endEditing:YES];
}

- (IBAction)didTapMore:(UIBarButtonItem *)sender {
  self.moreAlert.popoverPresentationController.barButtonItem = sender;
  [self presentViewController:self.moreAlert animated:YES completion:nil];
}

- (void)clearChips {
  for (UIView *view in self.smartReplyView.arrangedSubviews) {
    view.hidden = YES;
  }
}

- (void)updateReplies {
  // SmartReply for users' own messages is a non-use-case.
  MLKTextMessage *lastMessage = _messages.lastObject;
  if (lastMessage == nil || lastMessage.isLocalUser == _isLocalUser) {
    [self clearChips];
    return;
  }

  NSMutableArray<MLKTextMessage *> *chat = _messages;

  // Revert isLocalUser field in text messages to simulate the remote user for the sample.
  if (!_isLocalUser) {
    chat = [[NSMutableArray alloc] initWithCapacity:10];
    for (int i = (_messages.count <= 10 ? 0 : _messages.count - 10); i < _messages.count; i++) {
      MLKTextMessage *textMessage = _messages[i];
      textMessage = [[MLKTextMessage alloc] initWithText:textMessage.text
                                               timestamp:textMessage.timestamp
                                                  userID:textMessage.userID
                                             isLocalUser:!textMessage.isLocalUser];
      [chat addObject:textMessage];
    }
  }

  [_smartReply
      suggestRepliesForMessages:chat
                     completion:^(MLKSmartReplySuggestionResult *_Nullable result,
                                  NSError *_Nullable error) {
                       if (error != nil || result == nil || result.suggestions.count == 0) {
                         [self clearChips];
                         return;
                       }
                       for (int i = 0; i < 3; i++) {
                         MDCChipView *chip = self.smartReplyView.arrangedSubviews[i];
                         chip.titleLabel.text = result.suggestions[i].text;
                         [chip sizeToFit];
                         chip.hidden = NO;
                         for (NSLayoutConstraint *constraint in chip.constraints) {
                           if ([constraint.identifier isEqualToString:@"width"]) {
                             constraint.constant = chip.bounds.size.width;
                             break;
                           }
                         }
                       }
                     }];
}

- (void)handleKeyboardNotification:(NSNotification *)notification {
  CGRect keyboardSize =
      ((NSValue *)notification.userInfo[UIKeyboardFrameEndUserInfoKey]).CGRectValue;
  BOOL isKeyboardShowing = notification.name == UIKeyboardWillShowNotification;
  if (_isKeyboardShown == isKeyboardShowing) {
    _bottomConstraint.constant = isKeyboardShowing ? -keyboardSize.size.height : 0;
    if (self.messages.count > 0) {
      NSIndexPath *indexPath = [NSIndexPath indexPathForItem:self.messages.count - 1 inSection:0];
      [self.collectionView scrollToItemAtIndexPath:indexPath
                                  atScrollPosition:UICollectionViewScrollPositionTop
                                          animated:YES];
    }
    return;
  }
  _isKeyboardShown = isKeyboardShowing;
  _bottomConstraint.constant = isKeyboardShowing ? -keyboardSize.size.height : 0;
  double inset = isKeyboardShowing ? -_bottomAreaInset : _bottomAreaInset;
  _heightConstraint.constant += inset;
  _inputBottomConstraint.constant = isKeyboardShowing ? 0 : _bottomAreaInset;
  _sendBottomConstraint.constant = isKeyboardShowing ? 6 : (6 + _bottomAreaInset);
  NSTimeInterval animationDuration =
      ((NSNumber *)notification.userInfo[UIKeyboardAnimationDurationUserInfoKey]).doubleValue;
  [UIView animateWithDuration:animationDuration
      delay:0
      options:UIViewAnimationOptionCurveEaseOut
      animations:^{
        [self.view layoutIfNeeded];
      }
      completion:^(BOOL finished) {
        if (self.messages.count > 0) {
          NSIndexPath *indexPath = [NSIndexPath indexPathForItem:self.messages.count - 1
                                                       inSection:0];
          [self.collectionView scrollToItemAtIndexPath:indexPath
                                      atScrollPosition:UICollectionViewScrollPositionTop
                                              animated:YES];
        }
      }];
}

- (void)textViewDidBeginEditing:(UITextView *)textView {
  [self textViewDidChange:textView];
}

- (void)collectionView:(UICollectionView *)collectionView
    didSelectItemAtIndexPath:(NSIndexPath *)indexPath {
  [_inputTextView endEditing:YES];
}

- (UICollectionViewCell *)collectionView:(UICollectionView *)collectionView
                  cellForItemAtIndexPath:(NSIndexPath *)indexPath {
  MDCSelfSizingStereoCell *cell = [collectionView dequeueReusableCellWithReuseIdentifier:@"cell"
                                                                            forIndexPath:indexPath];
  MLKTextMessage *item = _messages[indexPath.item];
  cell.leadingImageView.image = [UIImage imageNamed:@"ic_account_circle_36pt"];
  cell.leadingImageView.tintColor = item.isLocalUser ? UIColor.blueColor : UIColor.redColor;
  cell.titleLabel.text = item.text;
  cell.detailLabel.text = [[NSDate dateWithTimeIntervalSince1970:item.timestamp] timeAgo];
  return cell;
}

- (void)replySelected:(MDCChipView *)reply {
  [_inputTextView insertText:reply.titleLabel.text];
}

- (IBAction)switchUser:(id)sender {
  self.isLocalUser = !_isLocalUser;
  UIColor *color = _isLocalUser ? UIColor.blueColor : UIColor.redColor;
  _sendButton.tintColor = color;
  self.navigationController.navigationBar.barTintColor = color;
  [self updateReplies];
}

- (void)generateChatHistoryBasic {
  NSDate *date = [NSCalendar.currentCalendar dateByAddingUnit:NSCalendarUnitDay
                                                        value:-1
                                                       toDate:[NSDate date]
                                                      options:0];
  NSTimeInterval dateAfter = [NSCalendar.currentCalendar dateByAddingUnit:NSCalendarUnitMinute
                                                                    value:10
                                                                   toDate:date
                                                                  options:0]
                                 .timeIntervalSince1970;
  _messages =
      @[
        [[MLKTextMessage alloc] initWithText:@"Hello"
                                   timestamp:date.timeIntervalSince1970
                                      userID:@""
                                 isLocalUser:YES],
        [[MLKTextMessage alloc] initWithText:@"Hey" timestamp:dateAfter userID:@"" isLocalUser:NO]
      ]
          .mutableCopy;
  [self updateReplies];
  [self.collectionView reloadData];
  [self.collectionView.collectionViewLayout invalidateLayout];
}

- (void)generateChatHistoryWithSensitiveContent {
  NSDate *date = [NSCalendar.currentCalendar dateByAddingUnit:NSCalendarUnitDay
                                                        value:-1
                                                       toDate:[NSDate date]
                                                      options:0];
  NSTimeInterval dateAfter = [NSCalendar.currentCalendar dateByAddingUnit:NSCalendarUnitMinute
                                                                    value:10
                                                                   toDate:date
                                                                  options:0]
                                 .timeIntervalSince1970;
  NSTimeInterval dateAfterAfter = [NSCalendar.currentCalendar dateByAddingUnit:NSCalendarUnitMinute
                                                                         value:20
                                                                        toDate:date
                                                                       options:0]
                                      .timeIntervalSince1970;
  _messages =
      @[
        [[MLKTextMessage alloc] initWithText:@"Hi"
                                   timestamp:date.timeIntervalSince1970
                                      userID:@""
                                 isLocalUser:NO],
        [[MLKTextMessage alloc] initWithText:@"How are you?"
                                   timestamp:dateAfter
                                      userID:@""
                                 isLocalUser:YES],
        [[MLKTextMessage alloc] initWithText:@"My cat died"
                                   timestamp:dateAfterAfter
                                      userID:@""
                                 isLocalUser:NO]
      ]
          .mutableCopy;
  [self updateReplies];
  [self.collectionView reloadData];
  [self.collectionView.collectionViewLayout invalidateLayout];
}

- (NSInteger)numberOfSectionsInCollectionView:(UICollectionView *)collectionView {
  return 1;
}

- (void)textViewDidEndEditing:(UITextView *)textView {
  _sendButton.enabled = NO;
  _heightConstraint.constant = 88 + _bottomAreaInset;
}

- (void)textViewDidChange:(UITextView *)textView {
  _sendButton.enabled = textView.text.length != 0;
  CGSize size = CGSizeMake(self.view.frame.size.width - 60, INFINITY);
  CGSize estimatedSize = [textView sizeThatFits:size];
  _heightConstraint.constant =
      estimatedSize.height + 54 + (self.isKeyboardShown ? 0 : _bottomAreaInset);
}

- (NSInteger)collectionView:(UICollectionView *)collectionView
     numberOfItemsInSection:(NSInteger)section {
  return _messages.count;
}

@end

NS_ASSUME_NONNULL_END
