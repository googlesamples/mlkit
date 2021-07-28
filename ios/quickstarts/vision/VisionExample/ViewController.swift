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

import MLImage
import MLKit
import UIKit

/// Main view controller class.
@objc(ViewController)
class ViewController: UIViewController, UINavigationControllerDelegate {

  /// A string holding current results from detection.
  var resultsText = ""

  /// An overlay view that displays detection annotations.
  private lazy var annotationOverlayView: UIView = {
    precondition(isViewLoaded)
    let annotationOverlayView = UIView(frame: .zero)
    annotationOverlayView.translatesAutoresizingMaskIntoConstraints = false
    annotationOverlayView.clipsToBounds = true
    return annotationOverlayView
  }()

  /// An image picker for accessing the photo library or camera.
  var imagePicker = UIImagePickerController()

  // Image counter.
  var currentImage = 0

  /// Initialized when one of the pose detector rows are chosen. Reset to `nil` when neither are.
  private var poseDetector: PoseDetector? = nil

  /// Initialized when a segmentation row is chosen. Reset to `nil` otherwise.
  private var segmenter: Segmenter? = nil

  /// The detector row with which detection was most recently run. Useful for inferring when to
  /// reset detector instances which use a conventional lifecyle paradigm.
  private var lastDetectorRow: DetectorPickerRow?

  // MARK: - IBOutlets

  @IBOutlet fileprivate weak var detectorPicker: UIPickerView!

  @IBOutlet fileprivate weak var imageView: UIImageView!
  @IBOutlet fileprivate weak var photoCameraButton: UIBarButtonItem!
  @IBOutlet fileprivate weak var videoCameraButton: UIBarButtonItem!
  @IBOutlet weak var detectButton: UIBarButtonItem!

  // MARK: - UIViewController

  override func viewDidLoad() {
    super.viewDidLoad()

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
  }

  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)

    navigationController?.navigationBar.isHidden = false
  }

  // MARK: - IBActions

  @IBAction func detect(_ sender: Any) {
    clearResults()
    let row = detectorPicker.selectedRow(inComponent: 0)
    if let rowIndex = DetectorPickerRow(rawValue: row) {
      resetManagedLifecycleDetectors(activeDetectorRow: rowIndex)

      let shouldEnableClassification =
        (rowIndex == .detectObjectsProminentWithClassifier)
        || (rowIndex == .detectObjectsMultipleWithClassifier)
        || (rowIndex == .detectObjectsCustomProminentWithClassifier)
        || (rowIndex == .detectObjectsCustomMultipleWithClassifier)
      let shouldEnableMultipleObjects =
        (rowIndex == .detectObjectsMultipleWithClassifier)
        || (rowIndex == .detectObjectsMultipleNoClassifier)
        || (rowIndex == .detectObjectsCustomMultipleWithClassifier)
        || (rowIndex == .detectObjectsCustomMultipleNoClassifier)
      switch rowIndex {
      case .detectFaceOnDevice:
        detectFaces(image: imageView.image)
      case .detectTextOnDevice, .detectTextChineseOnDevice, .detectTextDevanagariOnDevice,
        .detectTextJapaneseOnDevice, .detectTextKoreanOnDevice:
        detectTextOnDevice(
          image: imageView.image, detectorType: rowIndex)
      case .detectBarcodeOnDevice:
        detectBarcodes(image: imageView.image)
      case .detectImageLabelsOnDevice:
        detectLabels(image: imageView.image, shouldUseCustomModel: false)
      case .detectImageLabelsCustomOnDevice:
        detectLabels(image: imageView.image, shouldUseCustomModel: true)
      case .detectObjectsProminentNoClassifier, .detectObjectsProminentWithClassifier,
        .detectObjectsMultipleNoClassifier, .detectObjectsMultipleWithClassifier:
        let options = ObjectDetectorOptions()
        options.shouldEnableClassification = shouldEnableClassification
        options.shouldEnableMultipleObjects = shouldEnableMultipleObjects
        options.detectorMode = .singleImage
        detectObjectsOnDevice(in: imageView.image, options: options)
      case .detectObjectsCustomProminentNoClassifier, .detectObjectsCustomProminentWithClassifier,
        .detectObjectsCustomMultipleNoClassifier, .detectObjectsCustomMultipleWithClassifier:
        guard
          let localModelFilePath = Bundle.main.path(
            forResource: Constants.localModelFile.name,
            ofType: Constants.localModelFile.type
          )
        else {
          print("Failed to find custom local model file.")
          return
        }
        let localModel = LocalModel(path: localModelFilePath)
        let options = CustomObjectDetectorOptions(localModel: localModel)
        options.shouldEnableClassification = shouldEnableClassification
        options.shouldEnableMultipleObjects = shouldEnableMultipleObjects
        options.detectorMode = .singleImage
        detectObjectsOnDevice(in: imageView.image, options: options)
      case .detectPose, .detectPoseAccurate:
        detectPose(image: imageView.image)
      case .detectSegmentationMaskSelfie:
        detectSegmentationMask(image: imageView.image)
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
  }

  // MARK: - Private

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
    @unknown default:
      fatalError()
    }
    weak var weakSelf = self
    DispatchQueue.global(qos: .userInitiated).async {
      // Scale image while maintaining aspect ratio so it displays better in the UIImageView.
      var scaledImage = image.scaledImage(
        with: CGSize(width: scaledImageWidth, height: scaledImageHeight)
      )
      scaledImage = scaledImage ?? image
      guard let finalImage = scaledImage else { return }
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

  private func pointFrom(_ visionPoint: VisionPoint) -> CGPoint {
    return CGPoint(x: visionPoint.x, y: visionPoint.y)
  }

  private func addContours(forFace face: Face, transform: CGAffineTransform) {
    // Face
    if let faceContour = face.contour(ofType: .face) {
      for point in faceContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }

    // Eyebrows
    if let topLeftEyebrowContour = face.contour(ofType: .leftEyebrowTop) {
      for point in topLeftEyebrowContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }
    if let bottomLeftEyebrowContour = face.contour(ofType: .leftEyebrowBottom) {
      for point in bottomLeftEyebrowContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }
    if let topRightEyebrowContour = face.contour(ofType: .rightEyebrowTop) {
      for point in topRightEyebrowContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }
    if let bottomRightEyebrowContour = face.contour(ofType: .rightEyebrowBottom) {
      for point in bottomRightEyebrowContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }

    // Eyes
    if let leftEyeContour = face.contour(ofType: .leftEye) {
      for point in leftEyeContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius)
      }
    }
    if let rightEyeContour = face.contour(ofType: .rightEye) {
      for point in rightEyeContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }

    // Lips
    if let topUpperLipContour = face.contour(ofType: .upperLipTop) {
      for point in topUpperLipContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }
    if let bottomUpperLipContour = face.contour(ofType: .upperLipBottom) {
      for point in bottomUpperLipContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }
    if let topLowerLipContour = face.contour(ofType: .lowerLipTop) {
      for point in topLowerLipContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }
    if let bottomLowerLipContour = face.contour(ofType: .lowerLipBottom) {
      for point in bottomLowerLipContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }

    // Nose
    if let noseBridgeContour = face.contour(ofType: .noseBridge) {
      for point in noseBridgeContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }
    if let noseBottomContour = face.contour(ofType: .noseBottom) {
      for point in noseBottomContour.points {
        let transformedPoint = pointFrom(point).applying(transform)
        UIUtilities.addCircle(
          atPoint: transformedPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constants.smallDotRadius
        )
      }
    }
  }

  private func addLandmarks(forFace face: Face, transform: CGAffineTransform) {
    // Mouth
    if let bottomMouthLandmark = face.landmark(ofType: .mouthBottom) {
      let point = pointFrom(bottomMouthLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.red,
        radius: Constants.largeDotRadius
      )
    }
    if let leftMouthLandmark = face.landmark(ofType: .mouthLeft) {
      let point = pointFrom(leftMouthLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.red,
        radius: Constants.largeDotRadius
      )
    }
    if let rightMouthLandmark = face.landmark(ofType: .mouthRight) {
      let point = pointFrom(rightMouthLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.red,
        radius: Constants.largeDotRadius
      )
    }

    // Nose
    if let noseBaseLandmark = face.landmark(ofType: .noseBase) {
      let point = pointFrom(noseBaseLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.yellow,
        radius: Constants.largeDotRadius
      )
    }

    // Eyes
    if let leftEyeLandmark = face.landmark(ofType: .leftEye) {
      let point = pointFrom(leftEyeLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.cyan,
        radius: Constants.largeDotRadius
      )
    }
    if let rightEyeLandmark = face.landmark(ofType: .rightEye) {
      let point = pointFrom(rightEyeLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.cyan,
        radius: Constants.largeDotRadius
      )
    }

    // Ears
    if let leftEarLandmark = face.landmark(ofType: .leftEar) {
      let point = pointFrom(leftEarLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.purple,
        radius: Constants.largeDotRadius
      )
    }
    if let rightEarLandmark = face.landmark(ofType: .rightEar) {
      let point = pointFrom(rightEarLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.purple,
        radius: Constants.largeDotRadius
      )
    }

    // Cheeks
    if let leftCheekLandmark = face.landmark(ofType: .leftCheek) {
      let point = pointFrom(leftCheekLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.orange,
        radius: Constants.largeDotRadius
      )
    }
    if let rightCheekLandmark = face.landmark(ofType: .rightCheek) {
      let point = pointFrom(rightCheekLandmark.position)
      let transformedPoint = point.applying(transform)
      UIUtilities.addCircle(
        atPoint: transformedPoint,
        to: annotationOverlayView,
        color: UIColor.orange,
        radius: Constants.largeDotRadius
      )
    }
  }

  private func process(_ visionImage: VisionImage, with textRecognizer: TextRecognizer?) {
    weak var weakSelf = self
    textRecognizer?.process(visionImage) { text, error in
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      guard error == nil, let text = text else {
        let errorString = error?.localizedDescription ?? Constants.detectionNoResultsMessage
        strongSelf.resultsText = "Text recognizer failed with error: \(errorString)"
        strongSelf.showResults()
        return
      }
      // Blocks.
      for block in text.blocks {
        let transformedRect = block.frame.applying(strongSelf.transformMatrix())
        UIUtilities.addRectangle(
          transformedRect,
          to: strongSelf.annotationOverlayView,
          color: UIColor.purple
        )

        // Lines.
        for line in block.lines {
          let transformedRect = line.frame.applying(strongSelf.transformMatrix())
          UIUtilities.addRectangle(
            transformedRect,
            to: strongSelf.annotationOverlayView,
            color: UIColor.orange
          )

          // Elements.
          for element in line.elements {
            let transformedRect = element.frame.applying(strongSelf.transformMatrix())
            UIUtilities.addRectangle(
              transformedRect,
              to: strongSelf.annotationOverlayView,
              color: UIColor.green
            )
            let label = UILabel(frame: transformedRect)
            label.text = element.text
            label.adjustsFontSizeToFitWidth = true
            strongSelf.annotationOverlayView.addSubview(label)
          }
        }
      }
      strongSelf.resultsText += "\(text.text)\n"
      strongSelf.showResults()
    }
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

/// Extension of ViewController for On-Device detection.
extension ViewController {

  // MARK: - Vision On-Device Detection

  /// Detects faces on the specified image and draws a frame around the detected faces using
  /// On-Device face API.
  ///
  /// - Parameter image: The image.
  func detectFaces(image: UIImage?) {
    guard let image = image else { return }

    // Create a face detector with options.
    // [START config_face]
    let options = FaceDetectorOptions()
    options.landmarkMode = .all
    options.classificationMode = .all
    options.performanceMode = .accurate
    options.contourMode = .all
    // [END config_face]

    // [START init_face]
    let faceDetector = FaceDetector.faceDetector(options: options)
    // [END init_face]

    // Initialize a `VisionImage` object with the given `UIImage`.
    let visionImage = VisionImage(image: image)
    visionImage.orientation = image.imageOrientation

    // [START detect_faces]
    weak var weakSelf = self
    faceDetector.process(visionImage) { faces, error in
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      guard error == nil, let faces = faces, !faces.isEmpty else {
        // [START_EXCLUDE]
        let errorString = error?.localizedDescription ?? Constants.detectionNoResultsMessage
        strongSelf.resultsText = "On-Device face detection failed with error: \(errorString)"
        strongSelf.showResults()
        // [END_EXCLUDE]
        return
      }

      // Faces detected
      // [START_EXCLUDE]
      faces.forEach { face in
        let transform = strongSelf.transformMatrix()
        let transformedRect = face.frame.applying(transform)
        UIUtilities.addRectangle(
          transformedRect,
          to: strongSelf.annotationOverlayView,
          color: UIColor.green
        )
        strongSelf.addLandmarks(forFace: face, transform: transform)
        strongSelf.addContours(forFace: face, transform: transform)
      }
      strongSelf.resultsText = faces.map { face in
        let headEulerAngleX = face.hasHeadEulerAngleX ? face.headEulerAngleX.description : "NA"
        let headEulerAngleY = face.hasHeadEulerAngleY ? face.headEulerAngleY.description : "NA"
        let headEulerAngleZ = face.hasHeadEulerAngleZ ? face.headEulerAngleZ.description : "NA"
        let leftEyeOpenProbability =
          face.hasLeftEyeOpenProbability
          ? face.leftEyeOpenProbability.description : "NA"
        let rightEyeOpenProbability =
          face.hasRightEyeOpenProbability
          ? face.rightEyeOpenProbability.description : "NA"
        let smilingProbability =
          face.hasSmilingProbability
          ? face.smilingProbability.description : "NA"
        let output = """
          Frame: \(face.frame)
          Head Euler Angle X: \(headEulerAngleX)
          Head Euler Angle Y: \(headEulerAngleY)
          Head Euler Angle Z: \(headEulerAngleZ)
          Left Eye Open Probability: \(leftEyeOpenProbability)
          Right Eye Open Probability: \(rightEyeOpenProbability)
          Smiling Probability: \(smilingProbability)
          """
        return "\(output)"
      }.joined(separator: "\n")
      strongSelf.showResults()
      // [END_EXCLUDE]
    }
    // [END detect_faces]
  }

  func detectSegmentationMask(image: UIImage?) {
    guard let image = image else { return }

    // Initialize a `VisionImage` object with the given `UIImage`.
    let visionImage = VisionImage(image: image)
    visionImage.orientation = image.imageOrientation

    guard let segmenter = self.segmenter else {
      return
    }

    weak var weakSelf = self
    segmenter.process(visionImage) { mask, error in
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }

      guard error == nil, let mask = mask else {
        let errorString = error?.localizedDescription ?? Constants.detectionNoResultsMessage
        strongSelf.resultsText = "Segmentation failed with error: \(errorString)"
        strongSelf.showResults()
        return
      }

      guard let imageBuffer = UIUtilities.createImageBuffer(from: image) else {
        let errorString = "Failed to create image buffer from UIImage"
        strongSelf.resultsText = "Segmentation failed with error: \(errorString)"
        strongSelf.showResults()
        return
      }

      UIUtilities.applySegmentationMask(
        mask: mask, to: imageBuffer,
        backgroundColor: UIColor.purple.withAlphaComponent(Constants.segmentationMaskAlpha),
        foregroundColor: nil)
      let maskedImage = UIUtilities.createUIImage(from: imageBuffer, orientation: .up)

      let imageView = UIImageView()
      imageView.frame = strongSelf.annotationOverlayView.bounds
      imageView.contentMode = .scaleAspectFit
      imageView.image = maskedImage

      strongSelf.annotationOverlayView.addSubview(imageView)
      strongSelf.resultsText = "Segmentation succeeded"
      strongSelf.showResults()
    }
  }

  /// Detects poses on the specified image and draw pose landmark points and line segments using
  /// the On-Device face API.
  ///
  /// - Parameter image: The image.
  func detectPose(image: UIImage?) {
    guard let image = image else { return }

    guard let inputImage = MLImage(image: image) else {
      print("Failed to create MLImage from UIImage.")
      return
    }
    inputImage.orientation = image.imageOrientation

    if let poseDetector = self.poseDetector {
      poseDetector.process(inputImage) { poses, error in
        guard error == nil, let poses = poses, !poses.isEmpty else {
          let errorString = error?.localizedDescription ?? Constants.detectionNoResultsMessage
          self.resultsText = "Pose detection failed with error: \(errorString)"
          self.showResults()
          return
        }
        let transform = self.transformMatrix()

        // Pose detected. Currently, only single person detection is supported.
        poses.forEach { pose in
          let poseOverlayView = UIUtilities.createPoseOverlayView(
            forPose: pose,
            inViewWithBounds: self.annotationOverlayView.bounds,
            lineWidth: Constants.lineWidth,
            dotRadius: Constants.smallDotRadius,
            positionTransformationClosure: { (position) -> CGPoint in
              return self.pointFrom(position).applying(transform)
            }
          )
          self.annotationOverlayView.addSubview(poseOverlayView)
          self.resultsText = "Pose Detected"
          self.showResults()
        }
      }
    }
  }

  /// Detects barcodes on the specified image and draws a frame around the detected barcodes using
  /// On-Device barcode API.
  ///
  /// - Parameter image: The image.
  func detectBarcodes(image: UIImage?) {
    guard let image = image else { return }

    // Define the options for a barcode detector.
    // [START config_barcode]
    let format = BarcodeFormat.all
    let barcodeOptions = BarcodeScannerOptions(formats: format)
    // [END config_barcode]

    // Create a barcode scanner.
    // [START init_barcode]
    let barcodeScanner = BarcodeScanner.barcodeScanner(options: barcodeOptions)
    // [END init_barcode]

    // Initialize a `VisionImage` object with the given `UIImage`.
    let visionImage = VisionImage(image: image)
    visionImage.orientation = image.imageOrientation

    // [START detect_barcodes]
    weak var weakSelf = self
    barcodeScanner.process(visionImage) { features, error in
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      guard error == nil, let features = features, !features.isEmpty else {
        // [START_EXCLUDE]
        let errorString = error?.localizedDescription ?? Constants.detectionNoResultsMessage
        strongSelf.resultsText = "On-Device barcode detection failed with error: \(errorString)"
        strongSelf.showResults()
        // [END_EXCLUDE]
        return
      }

      // [START_EXCLUDE]
      features.forEach { feature in
        let transformedRect = feature.frame.applying(strongSelf.transformMatrix())
        UIUtilities.addRectangle(
          transformedRect,
          to: strongSelf.annotationOverlayView,
          color: UIColor.green
        )
      }
      strongSelf.resultsText = features.map { feature in
        return "DisplayValue: \(feature.displayValue ?? ""), RawValue: "
          + "\(feature.rawValue ?? ""), Frame: \(feature.frame)"
      }.joined(separator: "\n")
      strongSelf.showResults()
      // [END_EXCLUDE]
    }
    // [END detect_barcodes]
  }

  /// Detects labels on the specified image using On-Device label API.
  ///
  /// - Parameter image: The image.
  /// - Parameter shouldUseCustomModel: Whether to use the custom image labeling model.
  func detectLabels(image: UIImage?, shouldUseCustomModel: Bool) {
    guard let image = image else { return }

    // [START config_label]
    var options: CommonImageLabelerOptions!
    if shouldUseCustomModel {
      guard
        let localModelFilePath = Bundle.main.path(
          forResource: Constants.localModelFile.name,
          ofType: Constants.localModelFile.type
        )
      else {
        self.resultsText = "On-Device label detection failed because custom model was not found."
        self.showResults()
        return
      }
      let localModel = LocalModel(path: localModelFilePath)
      options = CustomImageLabelerOptions(localModel: localModel)
    } else {
      options = ImageLabelerOptions()
    }
    options.confidenceThreshold = NSNumber(floatLiteral: Constants.labelConfidenceThreshold)
    // [END config_label]

    // [START init_label]
    let onDeviceLabeler = ImageLabeler.imageLabeler(options: options)
    // [END init_label]

    // Initialize a `VisionImage` object with the given `UIImage`.
    let visionImage = VisionImage(image: image)
    visionImage.orientation = image.imageOrientation

    // [START detect_label]
    weak var weakSelf = self
    onDeviceLabeler.process(visionImage) { labels, error in
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      guard error == nil, let labels = labels, !labels.isEmpty else {
        // [START_EXCLUDE]
        let errorString = error?.localizedDescription ?? Constants.detectionNoResultsMessage
        strongSelf.resultsText = "On-Device label detection failed with error: \(errorString)"
        strongSelf.showResults()
        // [END_EXCLUDE]
        return
      }

      // [START_EXCLUDE]
      strongSelf.resultsText = labels.map { label -> String in
        return "Label: \(label.text), Confidence: \(label.confidence), Index: \(label.index)"
      }.joined(separator: "\n")
      strongSelf.showResults()
      // [END_EXCLUDE]
    }
    // [END detect_label]
  }

  /// Detects text on the specified image and draws a frame around the recognized text using the
  /// On-Device text recognizer.
  ///
  /// - Parameter image: The image.
  private func detectTextOnDevice(image: UIImage?, detectorType: DetectorPickerRow) {
    guard let image = image else { return }

    // [START init_text]
    var options: CommonTextRecognizerOptions
    if detectorType == .detectTextChineseOnDevice {
      options = ChineseTextRecognizerOptions.init()
    } else if detectorType == .detectTextDevanagariOnDevice {
      options = DevanagariTextRecognizerOptions.init()
    } else if detectorType == .detectTextJapaneseOnDevice {
      options = JapaneseTextRecognizerOptions.init()
    } else if detectorType == .detectTextKoreanOnDevice {
      options = KoreanTextRecognizerOptions.init()
    } else {
      options = TextRecognizerOptions.init()
    }

    let onDeviceTextRecognizer = TextRecognizer.textRecognizer(options: options)
    // [END init_text]

    // Initialize a `VisionImage` object with the given `UIImage`.
    let visionImage = VisionImage(image: image)
    visionImage.orientation = image.imageOrientation

    self.resultsText += "Running On-Device Text Recognition...\n"
    process(visionImage, with: onDeviceTextRecognizer)
  }

  /// Detects objects on the specified image and draws a frame around them.
  ///
  /// - Parameter image: The image.
  /// - Parameter options: The options for object detector.
  private func detectObjectsOnDevice(in image: UIImage?, options: CommonObjectDetectorOptions) {
    guard let image = image else { return }

    // Initialize a `VisionImage` object with the given `UIImage`.
    let visionImage = VisionImage(image: image)
    visionImage.orientation = image.imageOrientation

    // [START init_object_detector]
    // Create an objects detector with options.
    let detector = ObjectDetector.objectDetector(options: options)
    // [END init_object_detector]

    // [START detect_object]
    weak var weakSelf = self
    detector.process(visionImage) { objects, error in
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      guard error == nil else {
        // [START_EXCLUDE]
        let errorString = error?.localizedDescription ?? Constants.detectionNoResultsMessage
        strongSelf.resultsText = "Object detection failed with error: \(errorString)"
        strongSelf.showResults()
        // [END_EXCLUDE]
        return
      }
      guard let objects = objects, !objects.isEmpty else {
        // [START_EXCLUDE]
        strongSelf.resultsText = "On-Device object detector returned no results."
        strongSelf.showResults()
        // [END_EXCLUDE]
        return
      }

      objects.forEach { object in
        // [START_EXCLUDE]
        let transform = strongSelf.transformMatrix()
        let transformedRect = object.frame.applying(transform)
        UIUtilities.addRectangle(
          transformedRect,
          to: strongSelf.annotationOverlayView,
          color: .green
        )
        // [END_EXCLUDE]
      }

      // [START_EXCLUDE]
      strongSelf.resultsText = objects.map { object in
        var description = "Frame: \(object.frame)\n"
        if let trackingID = object.trackingID {
          description += "Object ID: " + trackingID.stringValue + "\n"
        }
        description += object.labels.enumerated().map { (index, label) in
          "Label \(index): \(label.text), \(label.confidence), \(label.index)"
        }.joined(separator: "\n")
        return description
      }.joined(separator: "\n")

      strongSelf.showResults()
      // [END_EXCLUDE]
    }
    // [END detect_object]
  }

  /// Resets any detector instances which use a conventional lifecycle paradigm. This method should
  /// be invoked immediately prior to performing detection. This approach is advantageous to tearing
  /// down old detectors in the `UIPickerViewDelegate` method because that method isn't actually
  /// invoked in-sync with when the selected row changes and can result in tearing down the wrong
  /// detector in the event of a race condition.
  private func resetManagedLifecycleDetectors(activeDetectorRow: DetectorPickerRow) {
    if activeDetectorRow == self.lastDetectorRow {
      // Same row as before, no need to reset any detectors.
      return
    }
    // Clear the old detector, if applicable.
    switch self.lastDetectorRow {
    case .detectPose, .detectPoseAccurate:
      self.poseDetector = nil
      break
    case .detectSegmentationMaskSelfie:
      self.segmenter = nil
      break
    default:
      break
    }
    // Initialize the new detector, if applicable.
    switch activeDetectorRow {
    case .detectPose, .detectPoseAccurate:
      let options =
        activeDetectorRow == .detectPose
        ? PoseDetectorOptions()
        : AccuratePoseDetectorOptions()
      options.detectorMode = .singleImage
      self.poseDetector = PoseDetector.poseDetector(options: options)
      break
    case .detectSegmentationMaskSelfie:
      let options = SelfieSegmenterOptions()
      options.segmenterMode = .singleImage
      self.segmenter = Segmenter.segmenter(options: options)
      break
    default:
      break
    }
    self.lastDetectorRow = activeDetectorRow
  }
}

// MARK: - Enums

private enum DetectorPickerRow: Int {
  case detectFaceOnDevice = 0

  case
    detectTextOnDevice,
    detectTextChineseOnDevice,
    detectTextDevanagariOnDevice,
    detectTextJapaneseOnDevice,
    detectTextKoreanOnDevice,
    detectBarcodeOnDevice,
    detectImageLabelsOnDevice,
    detectImageLabelsCustomOnDevice,
    detectObjectsProminentNoClassifier,
    detectObjectsProminentWithClassifier,
    detectObjectsMultipleNoClassifier,
    detectObjectsMultipleWithClassifier,
    detectObjectsCustomProminentNoClassifier,
    detectObjectsCustomProminentWithClassifier,
    detectObjectsCustomMultipleNoClassifier,
    detectObjectsCustomMultipleWithClassifier,
    detectPose,
    detectPoseAccurate,
    detectSegmentationMaskSelfie

  static let rowsCount = 20
  static let componentsCount = 1

  public var description: String {
    switch self {
    case .detectFaceOnDevice:
      return "Face Detection"
    case .detectTextOnDevice:
      return "Text Recognition"
    case .detectTextChineseOnDevice:
      return "Text Recognition Chinese"
    case .detectTextDevanagariOnDevice:
      return "Text Recognition Devanagari"
    case .detectTextJapaneseOnDevice:
      return "Text Recognition Japanese"
    case .detectTextKoreanOnDevice:
      return "Text Recognition Korean"
    case .detectBarcodeOnDevice:
      return "Barcode Scanning"
    case .detectImageLabelsOnDevice:
      return "Image Labeling"
    case .detectImageLabelsCustomOnDevice:
      return "Image Labeling Custom"
    case .detectObjectsProminentNoClassifier:
      return "ODT, single, no labeling"
    case .detectObjectsProminentWithClassifier:
      return "ODT, single, labeling"
    case .detectObjectsMultipleNoClassifier:
      return "ODT, multiple, no labeling"
    case .detectObjectsMultipleWithClassifier:
      return "ODT, multiple, labeling"
    case .detectObjectsCustomProminentNoClassifier:
      return "ODT, custom, single, no labeling"
    case .detectObjectsCustomProminentWithClassifier:
      return "ODT, custom, single, labeling"
    case .detectObjectsCustomMultipleNoClassifier:
      return "ODT, custom, multiple, no labeling"
    case .detectObjectsCustomMultipleWithClassifier:
      return "ODT, custom, multiple, labeling"
    case .detectPose:
      return "Pose Detection"
    case .detectPoseAccurate:
      return "Pose Detection, accurate"
    case .detectSegmentationMaskSelfie:
      return "Selfie Segmentation"
    }
  }
}

private enum Constants {
  static let images = [
    "grace_hopper.jpg", "image_has_text.jpg", "chinese_sparse.png", "devanagari_sparse.png",
    "japanese_sparse.png", "korean_sparse.png", "barcode_128.png", "qr_code.jpg", "beach.jpg",
    "liberty.jpg", "bird.jpg",
  ]

  static let detectionNoResultsMessage = "No results returned."
  static let failedToDetectObjectsMessage = "Failed to detect objects in image."
  static let localModelFile = (name: "bird", type: "tflite")
  static let labelConfidenceThreshold = 0.75
  static let smallDotRadius: CGFloat = 5.0
  static let largeDotRadius: CGFloat = 10.0
  static let lineColor = UIColor.yellow.cgColor
  static let lineWidth: CGFloat = 3.0
  static let fillColor = UIColor.clear.cgColor
  static let segmentationMaskAlpha: CGFloat = 0.5
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
