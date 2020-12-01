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

import UIKit

import MLKit

@objc(ViewController)
class ViewController: UIViewController, UITextViewDelegate, UIPickerViewDataSource,
  UIPickerViewDelegate
{

  @IBOutlet var inputTextView: UITextView!
  @IBOutlet var outputTextView: UITextView!
  @IBOutlet var statusTextView: UITextView!
  @IBOutlet var inputPicker: UIPickerView!
  @IBOutlet var outputPicker: UIPickerView!
  @IBOutlet var sourceDownloadDeleteButton: UIButton!
  @IBOutlet var targetDownloadDeleteButton: UIButton!

  var translator: Translator!
  let locale = Locale.current
  lazy var allLanguages = TranslateLanguage.allLanguages().sorted {
    return locale.localizedString(forLanguageCode: $0.rawValue)!
      < locale.localizedString(forLanguageCode: $1.rawValue)!
  }

  override func viewDidLoad() {
    inputPicker.dataSource = self
    outputPicker.dataSource = self
    inputPicker.selectRow(
      allLanguages.firstIndex(of: TranslateLanguage.english) ?? 0, inComponent: 0, animated: false)
    outputPicker.selectRow(
      allLanguages.firstIndex(of: TranslateLanguage.spanish) ?? 0, inComponent: 0, animated: false)
    inputPicker.delegate = self
    outputPicker.delegate = self
    inputTextView.delegate = self
    inputTextView.returnKeyType = .done
    pickerView(inputPicker, didSelectRow: 0, inComponent: 0)
    setDownloadDeleteButtonLabels()

    NotificationCenter.default.addObserver(
      self, selector: #selector(remoteModelDownloadDidComplete(notification:)),
      name: .mlkitModelDownloadDidSucceed, object: nil)
    NotificationCenter.default.addObserver(
      self, selector: #selector(remoteModelDownloadDidComplete(notification:)),
      name: .mlkitModelDownloadDidFail, object: nil)
  }

  func numberOfComponents(in pickerView: UIPickerView) -> Int {
    return 1
  }

  func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int)
    -> String?
  {
    return Locale.current.localizedString(forLanguageCode: allLanguages[row].rawValue)
  }

  func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
    return allLanguages.count
  }

  func textView(
    _ textView: UITextView, shouldChangeTextIn range: NSRange,
    replacementText text: String
  ) -> Bool {
    // Hide the keyboard when "Done" is pressed.
    // See: https://stackoverflow.com/questions/26600359/dismiss-keyboard-with-a-uitextview
    if text == "\n" {
      textView.resignFirstResponder()
      return false
    }
    return true
  }

  func textViewDidChange(_ textView: UITextView) {
    translate()
  }

  @IBAction func didTapSwap() {
    let inputSelectedRow = inputPicker.selectedRow(inComponent: 0)
    inputPicker.selectRow(outputPicker.selectedRow(inComponent: 0), inComponent: 0, animated: false)
    outputPicker.selectRow(inputSelectedRow, inComponent: 0, animated: false)
    inputTextView.text = outputTextView.text
    pickerView(inputPicker, didSelectRow: 0, inComponent: 0)
    self.setDownloadDeleteButtonLabels()
  }

  func model(forLanguage: TranslateLanguage) -> TranslateRemoteModel {
    return TranslateRemoteModel.translateRemoteModel(language: forLanguage)
  }

  func isLanguageDownloaded(_ language: TranslateLanguage) -> Bool {
    let model = self.model(forLanguage: language)
    let modelManager = ModelManager.modelManager()
    return modelManager.isModelDownloaded(model)
  }

  func handleDownloadDelete(picker: UIPickerView, button: UIButton) {
    let language = allLanguages[picker.selectedRow(inComponent: 0)]
    if language == .english {
      return
    }
    button.setTitle("working...", for: .normal)
    let model = self.model(forLanguage: language)
    let modelManager = ModelManager.modelManager()
    let languageName = Locale.current.localizedString(forLanguageCode: language.rawValue)!
    if modelManager.isModelDownloaded(model) {
      self.statusTextView.text = "Deleting \(languageName)"
      modelManager.deleteDownloadedModel(model) { error in
        self.statusTextView.text = "Deleted \(languageName)"
        self.setDownloadDeleteButtonLabels()
      }
    } else {
      self.statusTextView.text = "Downloading \(languageName)"
      let conditions = ModelDownloadConditions(
        allowsCellularAccess: true,
        allowsBackgroundDownloading: true
      )
      modelManager.download(model, conditions: conditions)
    }
  }

  @IBAction func didTapDownloadDeleteSourceLanguage() {
    self.handleDownloadDelete(picker: inputPicker, button: self.sourceDownloadDeleteButton)
  }

  @IBAction func didTapDownloadDeleteTargetLanguage() {
    self.handleDownloadDelete(picker: outputPicker, button: self.targetDownloadDeleteButton)
  }

  @IBAction func listDownloadedModels() {
    let msg =
      "Downloaded models:"
      + ModelManager.modelManager()
      .downloadedTranslateModels
      .map { model in Locale.current.localizedString(forLanguageCode: model.language.rawValue)! }
      .joined(separator: ", ")
    self.statusTextView.text = msg
  }

  @objc
  func remoteModelDownloadDidComplete(notification: NSNotification) {
    let userInfo = notification.userInfo!
    guard
      let remoteModel =
        userInfo[ModelDownloadUserInfoKey.remoteModel.rawValue] as? TranslateRemoteModel
    else {
      return
    }
    weak var weakSelf = self
    DispatchQueue.main.async {
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      let languageName = Locale.current.localizedString(
        forLanguageCode: remoteModel.language.rawValue)!
      if notification.name == .mlkitModelDownloadDidSucceed {
        strongSelf.statusTextView.text =
          "Download succeeded for \(languageName)"
      } else {
        strongSelf.statusTextView.text =
          "Download failed for \(languageName)"
      }
      strongSelf.setDownloadDeleteButtonLabels()
    }
  }

  func setDownloadDeleteButtonLabels() {
    let inputLanguage = allLanguages[inputPicker.selectedRow(inComponent: 0)]
    let outputLanguage = allLanguages[outputPicker.selectedRow(inComponent: 0)]
    if self.isLanguageDownloaded(inputLanguage) {
      self.sourceDownloadDeleteButton.setTitle("Delete model", for: .normal)
    } else {
      self.sourceDownloadDeleteButton.setTitle("Download model", for: .normal)
    }
    self.sourceDownloadDeleteButton.isHidden = inputLanguage == .english
    if self.isLanguageDownloaded(outputLanguage) {
      self.targetDownloadDeleteButton.setTitle("Delete model", for: .normal)
    } else {
      self.targetDownloadDeleteButton.setTitle("Download model", for: .normal)
    }
    self.targetDownloadDeleteButton.isHidden = outputLanguage == .english
  }

  func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
    let inputLanguage = allLanguages[inputPicker.selectedRow(inComponent: 0)]
    let outputLanguage = allLanguages[outputPicker.selectedRow(inComponent: 0)]
    self.setDownloadDeleteButtonLabels()
    let options = TranslatorOptions(sourceLanguage: inputLanguage, targetLanguage: outputLanguage)
    translator = Translator.translator(options: options)
    translate()
  }

  func translate() {
    let translatorForDownloading = self.translator!

    translatorForDownloading.downloadModelIfNeeded { error in
      guard error == nil else {
        self.outputTextView.text = "Failed to ensure model downloaded with error \(error!)"
        return
      }
      self.setDownloadDeleteButtonLabels()
      if translatorForDownloading == self.translator {
        translatorForDownloading.translate(self.inputTextView.text ?? "") { result, error in
          guard error == nil else {
            self.outputTextView.text = "Failed with error \(error!)"
            return
          }
          if translatorForDownloading == self.translator {
            self.outputTextView.text = result
          }
        }
      }
    }
  }
}
