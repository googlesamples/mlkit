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
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, eitheimputVir express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

import MLKit
import MaterialComponents

@objc(MainViewController)
class MainViewController: UICollectionViewController, UITextViewDelegate {
  private lazy var messages = [TextMessage]()
  private var isLocalUser = true

  private var bottomConstraint: NSLayoutConstraint!
  private var heightConstraint: NSLayoutConstraint!
  private var inputBottomConstraint: NSLayoutConstraint!
  private var sendBottomConstraint: NSLayoutConstraint!
  private var isKeyboardShown = false
  private let messageInputContainerView: UIView = {
    let view = UIView()
    view.backgroundColor = .white
    return view
  }()

  lazy var smartReply = SmartReply.smartReply()

  var bottomAreaInset: CGFloat = 0

  let inputTextView: UITextView = {
    let textView = UITextView(placeholder: "Write a message")
    textView.font = UIFont.preferredFont(forTextStyle: UIFont.TextStyle.callout)
    textView.isScrollEnabled = false
    return textView
  }()

  let smartReplyView: UIStackView = {
    let view = UIStackView()
    view.distribution = .equalSpacing
    for index in 0..<3 {
      let chipView = MDCChipView()
      chipView.isHidden = true
      chipView.setTitleColor(UIColor.red, for: .selected)
      let widthConstraint = chipView.widthAnchor.constraint(equalToConstant: 0)
      widthConstraint.identifier = "width"
      widthConstraint.isActive = true
      chipView.addTarget(self, action: #selector(replySelected(reply:)), for: .touchUpInside)
      view.addArrangedSubview(chipView)
    }
    return view
  }()

  let sendButton: MDCFloatingButton = {
    let button = MDCFloatingButton(shape: .mini)
    button.setImage(#imageLiteral(resourceName: "ic_send"), for: .normal)
    button.tintColor = .blue
    button.backgroundColor = .white
    button.accessibilityLabel = "Send"
    button.isEnabled = false
    button.addTarget(self, action: #selector(enterPressed), for: .touchUpInside)
    return button
  }()

  lazy var moreAlert: UIAlertController = {
    let alert = UIAlertController(title: nil, message: nil, preferredStyle: .actionSheet)
    alert.addAction(
      UIAlertAction(title: "Generate basic history", style: .default) { _ in
        self.generateChatHistoryBasic()
      })
    alert.addAction(
      UIAlertAction(
        title: "Generate history with sensitive content",
        style: .default
      ) { _ in
        self.generateChatHistoryWithSensitiveContent()
      })
    alert.addAction(
      UIAlertAction(title: "Clear chat history", style: .destructive) { _ in
        self.messages = []
        self.updateReplies()
        self.collectionView.reloadData()
        self.collectionView.collectionViewLayout.invalidateLayout()
      })
    alert.addAction(UIAlertAction(title: "Cancel", style: .cancel, handler: nil))
    return alert
  }()

  override func viewDidLoad() {
    super.viewDidLoad()
    navigationController?.navigationBar.barTintColor = .blue

    guard let collectionView = collectionView else {
      return
    }

    // registering the cell
    collectionView.register(MDCSelfSizingStereoCell.self, forCellWithReuseIdentifier: "cell")

    if #available(iOS 11.0, *) {
      bottomAreaInset = UIApplication.shared.keyWindow!.safeAreaInsets.bottom
    }

    inputTextView.delegate = self

    NotificationCenter.default.addObserver(
      self, selector: #selector(handleKeyboardNotification),
      name: UIResponder.keyboardWillShowNotification, object: nil)
    NotificationCenter.default.addObserver(
      self, selector: #selector(handleKeyboardNotification),
      name: UIResponder.keyboardWillHideNotification, object: nil)

    guard let col = collectionViewLayout as? UICollectionViewFlowLayout else { return }
    col.estimatedItemSize = CGSize.init(width: collectionView.bounds.width, height: 52)

    view.addSubview(messageInputContainerView)

    view.addConstraintsWithFormat(format: "H:|[v0]|", views: messageInputContainerView)

    heightConstraint = messageInputContainerView.heightAnchor.constraint(
      equalToConstant: 88 + bottomAreaInset)

    bottomConstraint = NSLayoutConstraint(
      item: messageInputContainerView, attribute: .bottom, relatedBy: .equal,
      toItem: view, attribute: .bottom, multiplier: 1, constant: 0)
    view.addConstraint(bottomConstraint)
    view.addConstraint(heightConstraint)
    setupInputComponents()
  }

  private func setupInputComponents() {
    let topBorderView = UIView()
    topBorderView.backgroundColor = UIColor(white: 0.5, alpha: 0.5)
    messageInputContainerView.addSubview(inputTextView)
    messageInputContainerView.addSubview(sendButton)
    messageInputContainerView.addSubview(topBorderView)
    messageInputContainerView.addSubview(smartReplyView)

    messageInputContainerView.addConstraintsWithFormat(
      format: "H:|-8-[v0][v1(40)]-16-|",
      views: inputTextView, sendButton)
    messageInputContainerView.addConstraintsWithFormat(format: "H:|[v0]|", views: topBorderView)
    messageInputContainerView.addConstraintsWithFormat(
      format: "H:|-16-[v0]-16-|", views: smartReplyView)

    smartReplyView.topAnchor.constraint(equalTo: messageInputContainerView.topAnchor, constant: 6)
      .isActive = true
    smartReplyView.bottomAnchor.constraint(equalTo: inputTextView.topAnchor, constant: -6).isActive =
      true

    inputBottomConstraint = messageInputContainerView.bottomAnchor.constraint(
      equalTo: inputTextView.bottomAnchor,
      constant: bottomAreaInset)
    inputBottomConstraint.isActive = true

    sendBottomConstraint = messageInputContainerView.bottomAnchor.constraint(
      equalTo: sendButton.bottomAnchor,
      constant: bottomAreaInset + 6)
    sendBottomConstraint.isActive = true

    messageInputContainerView.addConstraintsWithFormat(format: "V:|[v0(0.5)]", views: topBorderView)
  }

  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    inputTextView.becomeFirstResponder()
  }

  override func viewWillDisappear(_ animated: Bool) {
    inputTextView.endEditing(true)
    super.viewWillDisappear(animated)
  }

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    inputTextView.resizePlaceholder()
  }

  @objc func replySelected(reply: MDCChipView) {
    guard let title = reply.titleLabel.text else { return }
    inputTextView.insertText(title)
  }

  @IBAction func switchUser(_ sender: Any) {
    isLocalUser.toggle()
    let color: UIColor = isLocalUser ? .blue : .red
    sendButton.tintColor = color
    navigationController?.navigationBar.barTintColor = color
    updateReplies()
  }

  private func generateChatHistoryBasic() {
    guard let date = Calendar.current.date(byAdding: .day, value: -1, to: Date()) else { return }
    guard
      let dateAfter = Calendar.current.date(
        byAdding: .minute,
        value: 10, to: date)?.timeIntervalSince1970
    else { return }
    messages = [
      TextMessage(
        text: "Hello", timestamp: date.timeIntervalSince1970, userID: "", isLocalUser: true),
      TextMessage(text: "Hey", timestamp: dateAfter, userID: "", isLocalUser: false),
    ]
    self.updateReplies()
    self.collectionView.reloadData()
    self.collectionView.collectionViewLayout.invalidateLayout()
  }

  private func generateChatHistoryWithSensitiveContent() {
    guard let date = Calendar.current.date(byAdding: .day, value: -1, to: Date()) else { return }
    guard
      let dateAfter = Calendar.current.date(
        byAdding: .minute,
        value: 10, to: date)?.timeIntervalSince1970
    else { return }
    guard
      let dateAfterAfter = Calendar.current.date(
        byAdding: .minute,
        value: 20, to: date)?.timeIntervalSince1970
    else { return }
    messages = [
      TextMessage(
        text: "Hi", timestamp: date.timeIntervalSince1970, userID: "", isLocalUser: false),
      TextMessage(text: "How are you?", timestamp: dateAfter, userID: "", isLocalUser: true),
      TextMessage(text: "My cat died", timestamp: dateAfterAfter, userID: "", isLocalUser: false),
    ]
    self.updateReplies()
    self.collectionView.reloadData()
    self.collectionView.collectionViewLayout.invalidateLayout()
  }

  func textViewDidEndEditing(_ textView: UITextView) {
    sendButton.isEnabled = false
    heightConstraint.constant = 88 + bottomAreaInset
  }

  func textViewDidChange(_ textView: UITextView) {
    sendButton.isEnabled = !textView.text.isEmpty
    let size = CGSize(width: view.frame.width - 60, height: .infinity)
    let estimatedSize = textView.sizeThatFits(size)
    heightConstraint.constant =
      estimatedSize.height + 54 + (self.isKeyboardShown ? 0 : bottomAreaInset)
  }

  @IBAction func didTapMore(_ sender: UIBarButtonItem) {
    moreAlert.popoverPresentationController?.barButtonItem = sender
    present(moreAlert, animated: true, completion: nil)
  }

  @objc func enterPressed() {
    inputTextView.endEditing(true)
    guard let text = inputTextView.text, !text.isEmpty else { return }
    let message = TextMessage(
      text: text, timestamp: Date().timeIntervalSince1970,
      userID: "", isLocalUser: isLocalUser)
    messages.append(message)
    collectionView.insertItems(at: [IndexPath(item: messages.count - 1, section: 0)])
    inputTextView.text = nil
    inputTextView.textViewDidChange(inputTextView)
    self.smartReplyView.arrangedSubviews.compactMap { $0 as? MDCChipView }.forEach {
      $0.isHidden = true
    }
  }

  private func updateReplies() {
    // SmartReply for users' own messages is a non-use-case.
    guard let lastMessage = messages.last, lastMessage.isLocalUser != isLocalUser else {
      self.smartReplyView.subviews.compactMap { $0 as? MDCChipView }.forEach { $0.isHidden = true }
      return
    }
    var chat = messages

    // Revert isLocalUser field in text messages to simulate the remote user for the sample.
    if !isLocalUser {
      chat = []
      for textMessage in messages.suffix(10) {
        chat.append(
          TextMessage(
            text: textMessage.text, timestamp: textMessage.timestamp,
            userID: textMessage.userID, isLocalUser: !textMessage.isLocalUser))
      }
    }

    smartReply.suggestReplies(for: chat) { result, error in
      let suggestionChips = self.smartReplyView.subviews.compactMap { $0 as? MDCChipView }
      guard error == nil, let suggestions = result?.suggestions, !suggestions.isEmpty else {
        suggestionChips.forEach { $0.isHidden = true }
        return
      }
      zip(suggestionChips, suggestions).forEach { chip, suggestion in
        chip.isHidden = false
        chip.titleLabel.text = suggestion.text
        chip.sizeToFit()
        chip.constraints.first { $0.identifier == "width" }?.constant = chip.bounds.width
      }
    }
  }

  @objc func handleKeyboardNotification(notification: NSNotification) {
    guard
      let keyboardSize =
        (notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey]
        as? NSValue)?.cgRectValue
    else { return }
    let isKeyboardShowing = notification.name == UIResponder.keyboardWillShowNotification
    guard self.isKeyboardShown != isKeyboardShowing else {
      bottomConstraint?.constant = isKeyboardShowing ? -keyboardSize.height : 0
      if !self.messages.isEmpty {
        let indexPath = IndexPath(item: self.messages.count - 1, section: 0)
        self.collectionView?.scrollToItem(at: indexPath, at: .top, animated: false)
      }
      return
    }
    self.isKeyboardShown = isKeyboardShowing
    bottomConstraint?.constant = isKeyboardShowing ? -keyboardSize.height : 0
    let inset = isKeyboardShowing ? -bottomAreaInset : bottomAreaInset
    heightConstraint?.constant += inset
    inputBottomConstraint?.constant = isKeyboardShowing ? 0 : bottomAreaInset
    sendBottomConstraint?.constant = isKeyboardShowing ? 6 : (6 + bottomAreaInset)
    if let animationDuration =
      notification.userInfo![UIResponder.keyboardAnimationDurationUserInfoKey] as? Double
    {
      UIView.animate(
        withDuration: animationDuration, delay: 0, options: .curveEaseOut,
        animations: {
          self.view.layoutIfNeeded()
        },
        completion: { _ in
          if !self.messages.isEmpty {
            let indexPath = IndexPath(item: self.messages.count - 1, section: 0)
            self.collectionView?.scrollToItem(at: indexPath, at: .top, animated: false)
          }
        })
    }
  }

  func textViewDidBeginEditing(_ textView: UITextView) {
    textViewDidChange(textView)
  }

  override func numberOfSections(in collectionView: UICollectionView) -> Int {
    return 1
  }

  override func collectionView(
    _ collectionView: UICollectionView, numberOfItemsInSection section: Int
  ) -> Int {
    return messages.count
  }

  override func collectionView(
    _ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath
  ) {
    inputTextView.endEditing(true)
  }

  override func collectionView(
    _ collectionView: UICollectionView,
    cellForItemAt indexPath: IndexPath
  ) -> UICollectionViewCell {
    guard
      let cell = collectionView.dequeueReusableCell(
        withReuseIdentifier: "cell",
        for: indexPath) as? MDCSelfSizingStereoCell
    else {
      return UICollectionViewCell()
    }
    let item = messages[indexPath.item]
    cell.leadingImageView.image = #imageLiteral(resourceName: "ic_account_circle_36pt")
    cell.leadingImageView.tintColor = item.isLocalUser ? .blue : .red
    cell.titleLabel.text = item.text
    cell.detailLabel.text = Date(timeIntervalSince1970: item.timestamp).timeAgo()
    return cell
  }
}

extension UIView {
  func addConstraintsWithFormat(format: String, views: UIView...) {
    var viewsDictionary = [String: UIView]()
    for (index, view) in views.enumerated() {
      let key = "v\(index)"
      view.translatesAutoresizingMaskIntoConstraints = false
      viewsDictionary[key] = view
    }
    addConstraints(
      NSLayoutConstraint.constraints(
        withVisualFormat: format, options: NSLayoutConstraint.FormatOptions(),
        metrics: nil, views: viewsDictionary))
  }
}
