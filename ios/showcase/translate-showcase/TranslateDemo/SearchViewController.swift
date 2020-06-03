//
//  Copyright (c) 2019 Google Inc.
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

import MLKit
import MaterialComponents.MaterialList

private let reuseIdentifier = "Cell"

class SearchViewController: UICollectionViewController, UISearchBarDelegate,
  UISearchControllerDelegate
{
  private var allLanguages: [TranslateLanguage] = {
    return TranslateLanguage.allLanguages().sorted {
      $0.localizedName() < $1.localizedName()
    }
  }()

  private lazy var languages = self.allLanguages

  let searchController = UISearchController(searchResultsController: nil)
  let emptyLabel: UILabel = {
    let messageLabel = UILabel()
    messageLabel.text = "No language found."
    messageLabel.textColor = UIColor.black
    messageLabel.numberOfLines = 0
    messageLabel.textAlignment = .center
    messageLabel.font = UIFont.preferredFont(forTextStyle: .title3)
    messageLabel.sizeToFit()
    return messageLabel
  }()
  // We keep track of the pending work item as a property
  private var pendingRequestWorkItem: DispatchWorkItem?

  override func viewDidLoad() {
    super.viewDidLoad()

    self.collectionView!.register(
      MDCSelfSizingStereoCell.self, forCellWithReuseIdentifier: reuseIdentifier)
    guard let col = collectionViewLayout as? UICollectionViewFlowLayout else { return }
    col.estimatedItemSize = CGSize.init(width: collectionView!.bounds.width, height: 52)

    searchController.searchResultsUpdater = self
    searchController.searchBar.delegate = self
    searchController.delegate = self
    searchController.obscuresBackgroundDuringPresentation = false
    searchController.hidesNavigationBarDuringPresentation = false
    searchController.searchBar.placeholder = "Search for a language"

    UITextField.appearance(whenContainedInInstancesOf: [UISearchBar.self]).leftViewMode = .never
    searchController.searchBar.setImage(
      #imageLiteral(resourceName: "ic_close"), for: .clear, state: .normal)
    UIImageView.appearance(whenContainedInInstancesOf: [UISearchBar.self]).bounds = CGRect(
      x: 0, y: 0, width: 24, height: 24)

    let x = UIButton.init()
    x.setImage(#imageLiteral(resourceName: "ic_arrow_back"), for: .normal)
    x.addTarget(self, action: #selector(back), for: .touchUpInside)

    UIButton.appearance(whenContainedInInstancesOf: [UINavigationBar.self])
      .translatesAutoresizingMaskIntoConstraints = false
    UIButton.appearance(whenContainedInInstancesOf: [UINavigationBar.self]).contentEdgeInsets =
      UIEdgeInsets(top: 0, left: 0, bottom: 0, right: -4)

    navigationItem.leftBarButtonItem = UIBarButtonItem(customView: x)

    navigationItem.titleView = searchController.searchBar
    navigationItem.hidesBackButton = true
    definesPresentationContext = true
    searchController.searchBar.showsCancelButton = false

    UITextField.appearance(whenContainedInInstancesOf: [UISearchBar.self]).tintColor = UIColor.init(
      red: 0, green: 137 / 255, blue: 249 / 255, alpha: 1)
  }

  @objc func back() {
    navigationController?.popViewController(animated: true)
  }

  func searchBarTextDidBeginEditing(_ searchBar: UISearchBar) {
    searchController.searchBar.showsCancelButton = false
    self.searchController.searchBar.becomeFirstResponder()
  }

  func didPresentSearchController(_ searchController: UISearchController) {
    searchController.searchBar.showsCancelButton = false
    self.searchController.searchBar.becomeFirstResponder()
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
  }

  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    navigationController?.setNavigationBarHidden(false, animated: false)

    navigationController?.navigationBar.barTintColor = .white
    navigationController?.navigationBar.tintColor = .gray
  }

  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    navigationController?.navigationBar.barTintColor = UIColor(
      red: 0.01, green: 0.53, blue: 0.82, alpha: 1.0)
    navigationController?.navigationBar.tintColor = .white
  }

  // MARK: UICollectionViewDataSource

  override func numberOfSections(in collectionView: UICollectionView) -> Int {
    return 1
  }

  func filterContentForSearchText(_ searchText: String, scope: String = "All") {
    if searchText.isEmpty {
      self.languages = self.allLanguages
      collectionView?.reloadData()
      return
    }
    // Cancel the currently pending item
    pendingRequestWorkItem?.cancel()

    // Wrap our request in a work item
    let requestWorkItem = DispatchWorkItem { [weak self] in
      guard let self = self else { return }
      self.languages =
        self.allLanguages.filter { $0.localizedName().localizedStandardContains(searchText) }
      self.collectionView?.reloadData()
    }

    // Save the new work item and execute it after 250 ms
    pendingRequestWorkItem = requestWorkItem
    DispatchQueue.main.asyncAfter(
      deadline: .now() + .milliseconds(250),
      execute: requestWorkItem)
  }

  override func collectionView(
    _ collectionView: UICollectionView, numberOfItemsInSection section: Int
  ) -> Int {
    collectionView.backgroundView = languages.isEmpty ? emptyLabel : nil
    return languages.count
  }

  override func collectionView(
    _ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath
  ) -> UICollectionViewCell {
    guard
      let cell = collectionView.dequeueReusableCell(
        withReuseIdentifier: reuseIdentifier,
        for: indexPath) as? MDCSelfSizingStereoCell
    else {
      return UICollectionViewCell()
    }
    cell.titleLabel.text = languages[indexPath.item].localizedName()
    return cell
  }

  override func collectionView(
    _ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath
  ) {
    guard let cameraController = self.parent?.childViewControllers[0] as? CameraViewController
    else { return }
    let recents = cameraController.recentOutputLanguages
    let selected = languages[indexPath.item]
    if selected != recents[0] {
      cameraController.recentOutputLanguages[1] = recents[0]
      cameraController.recentOutputLanguages[0] = selected
    }
    cameraController.selectedItem = 0
    back()
  }
}

extension SearchViewController: UISearchResultsUpdating {
  func updateSearchResults(for searchController: UISearchController) {
    filterContentForSearchText(searchController.searchBar.text!)
  }
}
