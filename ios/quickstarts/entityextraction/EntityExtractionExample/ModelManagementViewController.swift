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

import UIKit

import MLKitCommon
import MLKitEntityExtraction

@objc(ModelManagementViewController)
class ModelManagementViewController: UITableViewController {
  let languages = EntityExtractionModelIdentifier.allModelIdentifiersSorted()
  let modelManager = ModelManager.modelManager()
  var downloadedLanguages: Set<EntityExtractionModelIdentifier> = []

  override func viewDidLoad() {
    NotificationCenter.default.addObserver(
      self, selector: #selector(receiveModelLoadingDidCompleteNotification(notification:)),
      name: .mlkitModelDownloadDidSucceed, object: nil)
    NotificationCenter.default.addObserver(
      self, selector: #selector(receiveModelLoadingDidCompleteNotification(notification:)),
      name: .mlkitModelDownloadDidFail, object: nil)
    refresh()
  }

  func refresh() {
    self.downloadedLanguages = Set(
      modelManager.downloadedEntityExtractionModels.map { $0.modelIdentifier })
    self.tableView.reloadData()
  }

  override func numberOfSections(in tableView: UITableView) -> Int {
    return 1
  }

  override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
    return languages.count
  }

  override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath)
    -> UITableViewCell
  {
    let cellIdentifier = "language"
    let cell =
      tableView.dequeueReusableCell(withIdentifier: cellIdentifier)
      ?? UITableViewCell.init(
        style: UITableViewCell.CellStyle.default, reuseIdentifier: cellIdentifier)
    let language = languages[indexPath.row]
    let code = language.toLanguageTag()
    cell.textLabel!.text = Locale.current.localizedString(forLanguageCode: code)
    if downloadedLanguages.contains(language) {
      cell.imageView!.image = UIImage.init(named: "delete_24pt")
    } else {
      cell.imageView!.image = UIImage.init(named: "cloud_download_24pt")
    }
    return cell
  }

  func showError(title: String, message: String) {
    let alert = UIAlertController.init(
      title: title, message: message,
      preferredStyle: UIAlertController.Style.alert)
    let action = UIAlertAction.init(
      title: "OK", style: UIAlertAction.Style.default,
      handler: {
        action in
      })
    alert.addAction(action)
    self.present(alert, animated: false, completion: nil)
  }

  override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
    let language = languages[indexPath.row]
    let model = EntityExtractorRemoteModel.entityExtractorRemoteModel(identifier: language)
    if downloadedLanguages.contains(language) {
      weak var weakSelf = self
      modelManager.deleteDownloadedModel(model) {
        error in
        guard let strongSelf = weakSelf else {
          print("Self is nil!")
          return
        }
        if error != nil {
          strongSelf.showError(title: "Deleting model failed", message: error!.localizedDescription)
        }
        strongSelf.refresh()
      }
    } else {
      let conditions = ModelDownloadConditions(
        allowsCellularAccess: true,
        allowsBackgroundDownloading: true
      )
      modelManager.download(model, conditions: conditions)
    }
  }

  @objc
  func receiveModelLoadingDidCompleteNotification(notification: NSNotification!) {
    weak var weakSelf = self
    if notification.name == NSNotification.Name.mlkitModelDownloadDidFail {
      let userInfo = notification.userInfo!
      let error = userInfo[ModelDownloadUserInfoKey.error.rawValue] as! NSError
      DispatchQueue.main.async {
        weakSelf?.showError(title: "Downloading model failed", message: error.localizedDescription)
      }
    }
    DispatchQueue.main.async { weakSelf?.refresh() }
  }
}
