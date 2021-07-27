//
//  Copyright (c) 2018 Google Inc.
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
import UIKit

/// Main view controller class.
@objc(ViewController)
class ViewController: UIViewController, UINavigationControllerDelegate {

  /// Manager for local and remote models.
  lazy var modelManager = ModelManager.modelManager()

  /// A string holding current results from detection.
  var resultsText = ""

  /// An overlay view that displays detection annotations.
  private lazy var annotationOverlayView: UIView = {
    precondition(isViewLoaded)
    let annotationOverlayView = UIView(frame: .zero)
    annotationOverlayView.translatesAutoresizingMaskIntoConstraints = false
    return annotationOverlayView
  }()

  /// An image picker for accessing the photo library or camera.
  var imagePicker = UIImagePickerController()

  // Image counter.
  var currentImage = 0

  // MARK: - IBOutlets

  @IBOutlet fileprivate weak var detectorPicker: UIPickerView!

  @IBOutlet fileprivate weak var imageView: UIImageView!
  @IBOutlet fileprivate weak var photoCameraButton: UIBarButtonItem!
  @IBOutlet fileprivate weak var videoCameraButton: UIBarButtonItem!
  @IBOutlet fileprivate weak var downloadOrDeleteModelButton: UIBarButtonItem!
  @IBOutlet weak var detectButton: UIBarButtonItem!
  @IBOutlet var downloadProgressView: UIProgressView!

  // MARK: - UIViewController

  override func viewDidLoad() {
    super.viewDidLoad()

    downloadOrDeleteModelButton.image =
      modelManager.isModelDownloaded(remoteModel())
      ? #imageLiteral(resourceName: "delete") : #imageLiteral(resourceName: "cloud_download")
    imageView.image = UIImage(named: Constants.images[currentImage])
    imageView.addSubview(annotationOverlayView)
    NSLayoutConstraint.activate([
      annotationOverlayView.topAnchor.constraint(equalTo: imageView.topAnchor),
      annotationOverlayView.leadingAnchor.constraint(equalTo: imageView.leadingAnchor),
      annotationOverlayView.trailingAnchor.constraint(equalTo: imageView.trailingAnchor),
      annotationOverlayView.bottomAnchor.constraint(equalTo: imageView.bottomAnchor),
    ])

    imagePicker.delegate = self
    imagePicker.sourceType = .photoLibrary

    detectorPicker.delegate = self
    detectorPicker.dataSource = self

    let isCameraAvailable =
      UIImagePickerController.isCameraDeviceAvailable(.front)
      || UIImagePickerController.isCameraDeviceAvailable(.rear)
    if isCameraAvailable {
      // `CameraViewController` uses `AVCaptureDevice.DiscoverySession` which is only supported for
      // iOS 10 or newer.
      if #available(iOS 10.0, *) {
        videoCameraButton.isEnabled = true
      }
    } else {
      photoCameraButton.isEnabled = false
    }

    let defaultRow = (DetectorPickerRow.rowsCount / 2) - 1
    detectorPicker.selectRow(defaultRow, inComponent: 0, animated: false)
  }

  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)

    navigationController?.navigationBar.isHidden = true
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(remoteModelDownloadDidSucceed(_:)),
      name: .mlkitModelDownloadDidSucceed,
      object: nil
    )
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(remoteModelDownloadDidFail(_:)),
      name: .mlkitModelDownloadDidFail,
      object: nil
    )
  }

  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)

    navigationController?.navigationBar.isHidden = false
    // We wouldn't have needed to remove the observers if iOS 9.0+ had cleaned up the observer "the
    // next time it would have posted to it" as documented here:
    // https://developer.apple.com/documentation/foundation/nsnotificationcenter/1413994-removeobserver
    NotificationCenter.default.removeObserver(
      self,
      name: .mlkitModelDownloadDidSucceed,
      object: nil)
    NotificationCenter.default.removeObserver(self, name: .mlkitModelDownloadDidFail, object: nil)
  }

  // MARK: - IBActions

  @IBAction func detect(_ sender: Any) {
    clearResults()
    let row = detectorPicker.selectedRow(inComponent: 0)
    let shouldEnableClassification =
      row == DetectorPickerRow.detectorObjectsSingleWithClassifier.rawValue
      || row == DetectorPickerRow.detectorObjectsMultipleWithClassifier.rawValue
    let shouldEnableMultipleObjects =
      row == DetectorPickerRow.detectorObjectsMultipleNoClassifier.rawValue
      || row == DetectorPickerRow.detectorObjectsMultipleWithClassifier.rawValue

    if let rowIndex = DetectorPickerRow(rawValue: row) {
      switch rowIndex {
      case .detectorImageLabels: detectImageLabels(image: imageView.image)
      case .detectorObjectsSingleNoClassifier, .detectorObjectsSingleWithClassifier,
        .detectorObjectsMultipleNoClassifier, .detectorObjectsMultipleWithClassifier:
        detectObjects(
          image: imageView.image, shouldEnableClassification: shouldEnableClassification,
          shouldEnableMultipleObjects: shouldEnableMultipleObjects)
      }
    } else {
      print("No such item at row \(row) in detector picker.")
    }
  }

  @IBAction func openPhotoLibrary(_ sender: Any) {
    imagePicker.sourceType = .photoLibrary
    present(imagePicker, animated: true)
  }

  @IBAction func openCamera(_ sender: Any) {
    guard
      UIImagePickerController.isCameraDeviceAvailable(.front)
        || UIImagePickerController
          .isCameraDeviceAvailable(.rear)
    else {
      return
    }
    imagePicker.sourceType = .camera
    present(imagePicker, animated: true)
  }

  @IBAction func changeImage(_ sender: Any) {
    clearResults()
    currentImage = (currentImage + 1) % Constants.images.count
    imageView.image = UIImage(named: Constants.images[currentImage])
  }

  @IBAction func downloadOrDeleteModel(_ sender: Any) {
    clearResults()
    let remoteModel = self.remoteModel()
    if modelManager.isModelDownloaded(remoteModel) {
      weak var weakSelf = self
      modelManager.deleteDownloadedModel(remoteModel) { error in
        guard error == nil else { preconditionFailure("Failed to delete the AutoML model.") }
        print("The downloaded remote model has been successfully deleted.\n")
        weakSelf?.downloadOrDeleteModelButton.image = #imageLiteral(resourceName: "cloud_download")
      }
    } else {
      downloadAutoMLRemoteModel(remoteModel)
    }
  }

  // MARK: - Private

  private func remoteModel() -> RemoteModel {
    let firebaseModelSource = FirebaseModelSource(name: Constants.remoteAutoMLModelName)
    return CustomRemoteModel(remoteModelSource: firebaseModelSource)
  }

  /// Removes the detection annotations from the annotation overlay view.
  private func removeDetectionAnnotations() {
    for annotationView in annotationOverlayView.subviews {
      annotationView.removeFromSuperview()
    }
  }

  /// Clears the results text view and removes any frames that are visible.
  private func clearResults() {
    removeDetectionAnnotations()
    self.resultsText = ""
  }

  private func showResults() {
    let resultsAlertController = UIAlertController(
      title: "Detection Results",
      message: nil,
      preferredStyle: .actionSheet
    )
    resultsAlertController.addAction(
      UIAlertAction(title: "OK", style: .destructive) { _ in
        resultsAlertController.dismiss(animated: true, completion: nil)
      }
    )
    resultsAlertController.message = resultsText
    resultsAlertController.popoverPresentationController?.barButtonItem = detectButton
    resultsAlertController.popoverPresentationController?.sourceView = self.view
    present(resultsAlertController, animated: true, completion: nil)
    print(resultsText)
  }

  /// Updates the image view with a scaled version of the given image.
  private func updateImageView(with image: UIImage) {
    let orientation = UIApplication.shared.statusBarOrientation
    var scaledImageWidth: CGFloat = 0.0
    var scaledImageHeight: CGFloat = 0.0
    switch orientation {
    case .portrait, .portraitUpsideDown, .unknown:
      scaledImageWidth = imageView.bounds.size.width
      scaledImageHeight = image.size.height * scaledImageWidth / image.size.width
    case .landscapeLeft, .landscapeRight:
      scaledImageWidth = image.size.width * scaledImageHeight / image.size.height
      scaledImageHeight = imageView.bounds.size.height
    }
    DispatchQueue.global(qos: .userInitiated).async {
      // Scale image while maintaining aspect ratio so it displays better in the UIImageView.
      var scaledImage = image.scaledImage(
        with: CGSize(width: scaledImageWidth, height: scaledImageHeight)
      )
      scaledImage = scaledImage ?? image
      guard let finalImage = scaledImage else { return }
      weak var weakSelf = self
      DispatchQueue.main.async {
        weakSelf?.imageView.image = finalImage
      }
    }
  }

  private func transformMatrix() -> CGAffineTransform {
    guard let image = imageView.image else { return CGAffineTransform() }
    let imageViewWidth = imageView.frame.size.width
    let imageViewHeight = imageView.frame.size.height
    let imageWidth = image.size.width
    let imageHeight = image.size.height

    let imageViewAspectRatio = imageViewWidth / imageViewHeight
    let imageAspectRatio = imageWidth / imageHeight
    let scale =
      (imageViewAspectRatio > imageAspectRatio)
      ? imageViewHeight / imageHeight : imageViewWidth / imageWidth

    // Image view's `contentMode` is `scaleAspectFit`, which scales the image to fit the size of the
    // image view by maintaining the aspect ratio. Multiple by `scale` to get image's original size.
    let scaledImageWidth = imageWidth * scale
    let scaledImageHeight = imageHeight * scale
    let xValue = (imageViewWidth - scaledImageWidth) / CGFloat(2.0)
    let yValue = (imageViewHeight - scaledImageHeight) / CGFloat(2.0)

    var transform = CGAffineTransform.identity.translatedBy(x: xValue, y: yValue)
    transform = transform.scaledBy(x: scale, y: scale)
    return transform
  }

  private func requestAutoMLRemoteModelIfNeeded() {
    let remoteModel = self.remoteModel()
    if modelManager.isModelDownloaded(remoteModel) {
      return
    }
    downloadAutoMLRemoteModel(remoteModel)
  }

  private func downloadAutoMLRemoteModel(_ remoteModel: RemoteModel) {
    downloadProgressView.isHidden = false
    let conditions = ModelDownloadConditions(
      allowsCellularAccess: true,
      allowsBackgroundDownloading: true)
    downloadProgressView.observedProgress = modelManager.download(
      remoteModel,
      conditions: conditions)
    print("Start downloading AutoML remote model")
  }

  // MARK: - Notifications

  @objc
  private func remoteModelDownloadDidSucceed(_ notification: Notification) {
    weak var weakSelf = self
    let notificationHandler = {
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      strongSelf.downloadProgressView.isHidden = true
      strongSelf.downloadOrDeleteModelButton.image = #imageLiteral(resourceName: "delete")
      guard let userInfo = notification.userInfo,
        let remoteModel = userInfo[ModelDownloadUserInfoKey.remoteModel.rawValue] as? RemoteModel
      else {
        strongSelf.resultsText +=
          "MLKitModelDownloadDidSucceed notification posted without a RemoteModel instance."
        return
      }
      strongSelf.resultsText +=
        "Successfully downloaded the remote model with name: \(remoteModel.name)."
      strongSelf.resultsText += "The model is ready for detection."
      print("Sucessfully downloaded AutoML remote model.")
    }
    if Thread.isMainThread {
      notificationHandler()
      return
    }
    DispatchQueue.main.async { notificationHandler() }
  }

  @objc
  private func remoteModelDownloadDidFail(_ notification: Notification) {
    weak var weakSelf = self
    let notificationHandler = {
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      strongSelf.downloadProgressView.isHidden = true
      guard let userInfo = notification.userInfo,
        let remoteModel = userInfo[ModelDownloadUserInfoKey.remoteModel.rawValue] as? RemoteModel,
        let error = userInfo[ModelDownloadUserInfoKey.error.rawValue] as? NSError
      else {
        strongSelf.resultsText +=
          "MLKitModelDownloadDidFail notification posted without a RemoteModel instance or error."
        return
      }
      strongSelf.resultsText +=
        "Failed to download the remote model with name: \(remoteModel.name), error: \(error)."
      print("Failed to download AutoML remote model.")
    }
    if Thread.isMainThread {
      notificationHandler()
      return
    }
    DispatchQueue.main.async { notificationHandler() }
  }
}

extension ViewController: UIPickerViewDataSource, UIPickerViewDelegate {

  // MARK: - UIPickerViewDataSource

  func numberOfComponents(in pickerView: UIPickerView) -> Int {
    return DetectorPickerRow.componentsCount
  }

  func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
    return DetectorPickerRow.rowsCount
  }

  // MARK: - UIPickerViewDelegate

  func pickerView(
    _ pickerView: UIPickerView,
    titleForRow row: Int,
    forComponent component: Int
  ) -> String? {
    return DetectorPickerRow(rawValue: row)?.description
  }

  func pickerView(_ pickerView: UIPickerView, didSelectRow row: Int, inComponent component: Int) {
    clearResults()
  }
}

// MARK: - UIImagePickerControllerDelegate

extension ViewController: UIImagePickerControllerDelegate {

  func imagePickerController(
    _ picker: UIImagePickerController,
    didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
  ) {
    // Local variable inserted by Swift 4.2 migrator.
    let info = convertFromUIImagePickerControllerInfoKeyDictionary(info)

    clearResults()
    if let pickedImage =
      info[
        convertFromUIImagePickerControllerInfoKey(UIImagePickerController.InfoKey.originalImage)]
      as? UIImage
    {
      updateImageView(with: pickedImage)
    }
    dismiss(animated: true)
  }
}

/// Extension of ViewController for AutoML image labeling.
extension ViewController {

  // MARK: - AutoML Image Labeling

  /// Detects labels on the specified image using AutoML Image Labeling API.
  ///
  /// - Parameter image: The image.
  func detectImageLabels(image: UIImage?) {
    guard let image = image else { return }
    requestAutoMLRemoteModelIfNeeded()

    // [START config_automl_label]
    let remoteModel = self.remoteModel()
    guard
      let localModelFilePath = Bundle.main.path(
        forResource: Constants.localModelManifestFileName,
        ofType: Constants.autoMLManifestFileType
      )
    else {
      print("Failed to find AutoML local model manifest file.")
      return
    }
    let isModelDownloaded = modelManager.isModelDownloaded(remoteModel)
    var options: CommonImageLabelerOptions!
    guard let localModel = LocalModel(manifestPath: localModelFilePath) else { return }
    options =
      isModelDownloaded
      ? CustomImageLabelerOptions(remoteModel: remoteModel as! CustomRemoteModel)
      : CustomImageLabelerOptions(localModel: localModel)
    print("Use AutoML \(isModelDownloaded ? "remote" : "local") in detector picker.")
    options.confidenceThreshold = NSNumber(value: Constants.labelConfidenceThreshold)
    // [END config_automl_label]

    // [START init_automl_label]
    let autoMLImageLabeler = ImageLabeler.imageLabeler(options: options)
    // [END init_automl_label]

    // Initialize a VisionImage object with the given UIImage.
    let visionImage = VisionImage(image: image)
    visionImage.orientation = image.imageOrientation

    // [START detect_automl_label]
    weak var weakSelf = self
    autoMLImageLabeler.process(visionImage) { labels, error in
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      guard error == nil, let labels = labels, !labels.isEmpty else {
        // [START_EXCLUDE]
        let errorString = error?.localizedDescription ?? Constants.detectionNoResultsMessage
        strongSelf.resultsText = "AutoML image labeling failed with error: \(errorString)"
        strongSelf.showResults()
        // [END_EXCLUDE]
        return
      }

      // [START_EXCLUDE]
      strongSelf.resultsText = labels.map { label -> String in
        return "Label: \(label.text), Confidence: \(label.confidence)"
      }.joined(separator: "\n")
      strongSelf.showResults()
      // [END_EXCLUDE]
    }
    // [END detect_automl_label]
  }

  /// Detects objects on the specified image using image classification models trained by AutoML
  /// via Custom Object Detection API.
  ///
  /// - Parameter image: The image.
  /// - Parameter shouldEnableClassification: Whether image classification should be enabled.
  /// - Parameter shouldEnableMultipleObjects: Whether multi-object detection should be enabled.
  func detectObjects(
    image: UIImage?, shouldEnableClassification: Bool, shouldEnableMultipleObjects: Bool
  ) {
    guard let image = image else { return }
    requestAutoMLRemoteModelIfNeeded()

    let remoteModel = self.remoteModel()
    var options: CustomObjectDetectorOptions!
    if modelManager.isModelDownloaded(remoteModel) {
      print("Use AutoML remote model.")
      options = CustomObjectDetectorOptions(remoteModel: remoteModel as! CustomRemoteModel)
    } else {
      print("Use AutoML local model.")
      guard
        let localModelFilePath = Bundle.main.path(
          forResource: Constants.localModelManifestFileName,
          ofType: Constants.autoMLManifestFileType
        )
      else {
        print(
          "Failed to find AutoML local model manifest file: \(Constants.localModelManifestFileName)"
        )
        return
      }
      guard let localModel = LocalModel(manifestPath: localModelFilePath) else { return }
      options = CustomObjectDetectorOptions(localModel: localModel)
    }
    options.shouldEnableClassification = shouldEnableClassification
    options.shouldEnableMultipleObjects = shouldEnableMultipleObjects
    options.detectorMode = .singleImage

    let autoMLObjectDetector = ObjectDetector.objectDetector(options: options)

    // Initialize a VisionImage object with the given UIImage.
    let visionImage = VisionImage(image: image)
    visionImage.orientation = image.imageOrientation

    weak var weakSelf = self
    autoMLObjectDetector.process(visionImage) { objects, error in
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      guard error == nil, let objects = objects, !objects.isEmpty else {
        let errorString = error?.localizedDescription ?? Constants.detectionNoResultsMessage
        strongSelf.resultsText = "AutoML object detection failed with error: \(errorString)"
        strongSelf.showResults()
        return
      }

      strongSelf.resultsText = objects.map { object -> String in
        let transform = strongSelf.transformMatrix()
        let transformedRect = object.frame.applying(transform)
        UIUtilities.addRectangle(
          transformedRect, to: strongSelf.annotationOverlayView, color: .green)
        let labels = object.labels.enumerated().map { (index, label) -> String in
          return "Label \(index): \(label.text), \(label.confidence), \(label.index)"
        }.joined(separator: "\n")
        return
          "Frame: \(object.frame)\nObject ID: \(String(describing: object.trackingID))\nLabels:\(labels)"
      }.joined(separator: "\n")
      strongSelf.showResults()
    }
  }
}

// MARK: - Enums

private enum DetectorPickerRow: Int {
  // AutoML image label detector.
  case detectorImageLabels
  // AutoML object detector, single, only tracking.
  case detectorObjectsSingleNoClassifier
  // AutoML object detector, single, with classification.
  case detectorObjectsSingleWithClassifier
  // AutoML object detector, multiple, only tracking.
  case detectorObjectsMultipleNoClassifier
  // AutoML object detector, multiple, with classification.
  case detectorObjectsMultipleWithClassifier

  static let rowsCount = 5
  static let componentsCount = 1

  public var description: String {
    switch self {
    case .detectorImageLabels:
      return "AutoML Image Labeling"
    case .detectorObjectsSingleNoClassifier:
      return "AutoML ODT, single, no labeling"
    case .detectorObjectsSingleWithClassifier:
      return "AutoML ODT, single, labeling"
    case .detectorObjectsMultipleNoClassifier:
      return "AutoML ODT, multiple, no labeling"
    case .detectorObjectsMultipleWithClassifier:
      return "AutoML ODT, multiple, labeling"
    }
  }
}

private enum Constants {
  static let images = [
    "dandelion.jpg", "sunflower.jpg", "tulips.jpeg", "daisy.jpeg", "roses.jpeg",
  ]

  static let modelExtension = "tflite"
  static let localModelName = "mobilenet"
  static let quantizedModelFilename = "mobilenet_quant_v1_224"

  static let detectionNoResultsMessage = "No results returned."
  static let sparseTextModelName = "Sparse"
  static let denseTextModelName = "Dense"

  static let remoteAutoMLModelName = "remote_automl_model"
  static let localModelManifestFileName = "automl_labeler_manifest"
  static let autoMLManifestFileType = "json"

  static let labelConfidenceThreshold: Float = 0.75
  static let smallDotRadius: CGFloat = 5.0
  static let largeDotRadius: CGFloat = 10.0
  static let lineColor = UIColor.yellow.cgColor
  static let fillColor = UIColor.clear.cgColor
}

// Helper function inserted by Swift 4.2 migrator.
private func convertFromUIImagePickerControllerInfoKeyDictionary(
  _ input: [UIImagePickerController.InfoKey: Any]
) -> [String: Any] {
  return Dictionary(uniqueKeysWithValues: input.map { key, value in (key.rawValue, value) })
}

// Helper function inserted by Swift 4.2 migrator.
private func convertFromUIImagePickerControllerInfoKey(_ input: UIImagePickerController.InfoKey)
  -> String
{
  return input.rawValue
}
