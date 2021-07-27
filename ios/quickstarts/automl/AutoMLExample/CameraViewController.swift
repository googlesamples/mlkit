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

import AVFoundation
import CoreVideo
import MLKit
import UIKit

@objc(CameraViewController)
class CameraViewController: UIViewController {
  private let detectors: [DetectorType] = [
    .detectorImageLabels,
    .detectorObjectsSingleNoClassifier,
    .detectorObjectsSingleWithClassifier,
    .detectorObjectsMultipleNoClassifier,
    .detectorObjectsMultipleWithClassifier,
  ]

  private var currentDetector: DetectorType = .detectorImageLabels
  private var isUsingFrontCamera = true
  private var previewLayer: AVCaptureVideoPreviewLayer!
  private lazy var captureSession = AVCaptureSession()
  private lazy var sessionQueue = DispatchQueue(label: Constants.sessionQueueLabel)
  private var lastFrame: CMSampleBuffer?
  private lazy var modelManager = ModelManager.modelManager()
  @IBOutlet var downloadProgressView: UIProgressView!

  private lazy var previewOverlayView: UIImageView = {
    precondition(isViewLoaded)
    let previewOverlayView = UIImageView(frame: .zero)
    previewOverlayView.contentMode = UIView.ContentMode.scaleAspectFill
    previewOverlayView.translatesAutoresizingMaskIntoConstraints = false
    return previewOverlayView
  }()

  private lazy var annotationOverlayView: UIView = {
    precondition(isViewLoaded)
    let annotationOverlayView = UIView(frame: .zero)
    annotationOverlayView.translatesAutoresizingMaskIntoConstraints = false
    return annotationOverlayView
  }()

  // MARK: - IBOutlets

  @IBOutlet private weak var cameraView: UIView!

  // MARK: - UIViewController

  override func viewDidLoad() {
    super.viewDidLoad()

    previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
    setUpPreviewOverlayView()
    setUpAnnotationOverlayView()
    setUpCaptureSessionOutput()
    setUpCaptureSessionInput()
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)

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
    startSession()
  }

  override func viewDidDisappear(_ animated: Bool) {
    super.viewDidDisappear(animated)

    // We wouldn't have needed to remove the observers if iOS 9.0+ had cleaned up the observer "the
    // next time it would have posted to it" as documented here:
    // https://developer.apple.com/documentation/foundation/nsnotificationcenter/1413994-removeobserver
    NotificationCenter.default.removeObserver(
      self,
      name: .mlkitModelDownloadDidSucceed,
      object: nil)
    NotificationCenter.default.removeObserver(self, name: .mlkitModelDownloadDidFail, object: nil)
    stopSession()
  }

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()

    previewLayer.frame = cameraView.frame
  }

  // MARK: - IBActions

  @IBAction func switchCamera(_ sender: Any) {
    isUsingFrontCamera = !isUsingFrontCamera
    removeDetectionAnnotations()
    setUpCaptureSessionInput()
  }

  @IBAction func selectDetector(_ sender: Any) {
    presentDetectorsAlertController()
  }

  // MARK: - AutoML Detections

  func detectObjects(
    in visionImage: VisionImage,
    width: CGFloat,
    height: CGFloat,
    shouldEnableClassification: Bool,
    shouldEnableMultipleObjects: Bool
  ) {
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
    // Due to the UI space, We will only display one label per detected object.
    options.maxPerObjectLabelCount = 1
    options.detectorMode = .stream

    let objectDetector = ObjectDetector.objectDetector(options: options)
    var objects: [Object]
    do {
      objects = try objectDetector.results(in: visionImage)
    } catch let error {
      print("Failed to detect objects with error: \(error.localizedDescription).")
      return
    }
    weak var weakSelf = self
    DispatchQueue.main.sync {
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      strongSelf.updatePreviewOverlayView()
      strongSelf.removeDetectionAnnotations()
    }
    guard !objects.isEmpty else {
      print("Object detector returned no results.")
      return
    }

    DispatchQueue.main.sync {
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      for object in objects {
        let normalizedRect = CGRect(
          x: object.frame.origin.x / width,
          y: object.frame.origin.y / height,
          width: object.frame.size.width / width,
          height: object.frame.size.height / height
        )
        let standardizedRect = strongSelf.previewLayer.layerRectConverted(
          fromMetadataOutputRect: normalizedRect
        ).standardized
        UIUtilities.addRectangle(
          standardizedRect,
          to: strongSelf.annotationOverlayView,
          color: UIColor.green
        )
        let label = UILabel(frame: standardizedRect)
        var description = ""
        if let trackingID = object.trackingID {
          description += "Object ID: " + trackingID.stringValue + "\n"
        }
        description += object.labels.enumerated().map { (index, label) in
          "Label \(index): \(label.text), \(label.confidence), \(label.index)"
        }.joined(separator: "\n")

        label.text = description
        label.numberOfLines = 0
        label.adjustsFontSizeToFitWidth = true
        strongSelf.annotationOverlayView.addSubview(label)
      }
    }
  }

  private func detectImageLabels(
    in visionImage: VisionImage,
    width: CGFloat,
    height: CGFloat
  ) {
    requestAutoMLRemoteModelIfNeeded()

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
    print("Use AutoML \(isModelDownloaded ? "remote" : "local") model.")
    options.confidenceThreshold = NSNumber(value: Constants.labelConfidenceThreshold)
    let autoMLImageLabeler = ImageLabeler.imageLabeler(options: options)
    print("labeler: \(autoMLImageLabeler)\n")

    let group = DispatchGroup()
    group.enter()

    weak var weakSelf = self
    autoMLImageLabeler.process(visionImage) { detectedLabels, error in
      defer { group.leave() }
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      strongSelf.updatePreviewOverlayView()
      strongSelf.removeDetectionAnnotations()

      if let error = error {
        print("Failed to detect labels with error: \(error.localizedDescription).")
        return
      }

      guard let labels = detectedLabels, !labels.isEmpty else {
        return
      }

      let annotationFrame = strongSelf.annotationOverlayView.frame
      let resultsRect = CGRect(
        x: annotationFrame.origin.x + Constants.padding,
        y: annotationFrame.size.height - Constants.padding - Constants.resultsLabelHeight,
        width: annotationFrame.width - 2 * Constants.padding,
        height: Constants.resultsLabelHeight
      )
      let resultsLabel = UILabel(frame: resultsRect)
      resultsLabel.textColor = .yellow
      resultsLabel.text = labels.map { label -> String in
        return "Label: \(label.text), Confidence: \(label.confidence)"
      }.joined(separator: "\n")
      resultsLabel.adjustsFontSizeToFitWidth = true
      resultsLabel.numberOfLines = Constants.resultsLabelLines
      strongSelf.annotationOverlayView.addSubview(resultsLabel)
    }

    group.wait()
  }

  private func requestAutoMLRemoteModelIfNeeded() {
    let remoteModel = self.remoteModel()
    if modelManager.isModelDownloaded(remoteModel) {
      return
    }
    weak var weakSelf = self
    DispatchQueue.main.async {
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      strongSelf.downloadProgressView.isHidden = false
      let conditions = ModelDownloadConditions(
        allowsCellularAccess: true,
        allowsBackgroundDownloading: true)
      strongSelf.downloadProgressView.observedProgress = strongSelf.modelManager.download(
        remoteModel,
        conditions: conditions)
    }

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
      guard let userInfo = notification.userInfo,
        let remoteModel = userInfo[ModelDownloadUserInfoKey.remoteModel.rawValue] as? RemoteModel
      else {
        print(
          "MLKitModelDownloadDidSucceed notification posted without a RemoteModel instance.")
        return
      }
      print(
        "Successfully downloaded the remote model with name: \(remoteModel.name). The model "
          + "is ready for detection.")
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
        print(
          "MLKitModelDownloadDidFail notification posted without a RemoteModel instance or error."
        )
        return
      }
      print("Failed to download the remote model with name: \(remoteModel.name), error: \(error).")
    }
    if Thread.isMainThread {
      notificationHandler()
      return
    }
    DispatchQueue.main.async { notificationHandler() }
  }

  // MARK: - Private

  private func remoteModel() -> RemoteModel {
    let firebaseModelSource = FirebaseModelSource(name: Constants.remoteAutoMLModelName)
    return CustomRemoteModel(remoteModelSource: firebaseModelSource)
  }

  private func setUpCaptureSessionOutput() {
    weak var weakSelf = self
    sessionQueue.async {
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      strongSelf.captureSession.beginConfiguration()
      // When performing latency tests to determine ideal capture settings,
      // run the app in 'release' mode to get accurate performance metrics
      strongSelf.captureSession.sessionPreset = AVCaptureSession.Preset.medium

      let output = AVCaptureVideoDataOutput()
      output.videoSettings = [
        (kCVPixelBufferPixelFormatTypeKey as String): kCVPixelFormatType_32BGRA
      ]
      let outputQueue = DispatchQueue(label: Constants.videoDataOutputQueueLabel)
      output.setSampleBufferDelegate(self, queue: outputQueue)
      guard strongSelf.captureSession.canAddOutput(output) else {
        print("Failed to add capture session output.")
        return
      }
      strongSelf.captureSession.addOutput(output)
      strongSelf.captureSession.commitConfiguration()
    }
  }

  private func setUpCaptureSessionInput() {
    weak var weakSelf = self
    sessionQueue.async {
      guard let strongSelf = weakSelf else {
        print("Self is nil!")
        return
      }
      let cameraPosition: AVCaptureDevice.Position = strongSelf.isUsingFrontCamera ? .front : .back
      guard let device = strongSelf.captureDevice(forPosition: cameraPosition) else {
        print("Failed to get capture device for camera position: \(cameraPosition)")
        return
      }
      do {
        strongSelf.captureSession.beginConfiguration()
        let currentInputs = strongSelf.captureSession.inputs
        for input in currentInputs {
          strongSelf.captureSession.removeInput(input)
        }

        let input = try AVCaptureDeviceInput(device: device)
        guard strongSelf.captureSession.canAddInput(input) else {
          print("Failed to add capture session input.")
          return
        }
        strongSelf.captureSession.addInput(input)
        strongSelf.captureSession.commitConfiguration()
      } catch {
        print("Failed to create capture device input: \(error.localizedDescription)")
      }
    }
  }

  private func startSession() {
    weak var weakSelf = self
    sessionQueue.async {
      weakSelf?.captureSession.startRunning()
    }
  }

  private func stopSession() {
    weak var weakSelf = self
    sessionQueue.async {
      weakSelf?.captureSession.stopRunning()
    }
  }

  private func setUpPreviewOverlayView() {
    cameraView.addSubview(previewOverlayView)
    NSLayoutConstraint.activate([
      previewOverlayView.centerXAnchor.constraint(equalTo: cameraView.centerXAnchor),
      previewOverlayView.centerYAnchor.constraint(equalTo: cameraView.centerYAnchor),
      previewOverlayView.leadingAnchor.constraint(equalTo: cameraView.leadingAnchor),
      previewOverlayView.trailingAnchor.constraint(equalTo: cameraView.trailingAnchor),

    ])
  }

  private func setUpAnnotationOverlayView() {
    cameraView.addSubview(annotationOverlayView)
    NSLayoutConstraint.activate([
      annotationOverlayView.topAnchor.constraint(equalTo: cameraView.topAnchor),
      annotationOverlayView.leadingAnchor.constraint(equalTo: cameraView.leadingAnchor),
      annotationOverlayView.trailingAnchor.constraint(equalTo: cameraView.trailingAnchor),
      annotationOverlayView.bottomAnchor.constraint(equalTo: cameraView.bottomAnchor),
    ])
  }

  private func captureDevice(forPosition position: AVCaptureDevice.Position) -> AVCaptureDevice? {
    if #available(iOS 10.0, *) {
      let discoverySession = AVCaptureDevice.DiscoverySession(
        deviceTypes: [.builtInWideAngleCamera],
        mediaType: .video,
        position: .unspecified
      )
      return discoverySession.devices.first { $0.position == position }
    }
    return nil
  }

  private func presentDetectorsAlertController() {
    let alertController = UIAlertController(
      title: Constants.alertControllerTitle,
      message: Constants.alertControllerMessage,
      preferredStyle: .alert
    )
    detectors.forEach { detectorType in
      let action = UIAlertAction(title: detectorType.rawValue, style: .default) {
        [weak self] (action) in
        guard let value = action.title else { return }
        guard let detector = DetectorType(rawValue: value) else { return }
        guard let strongSelf = self else {
          print("Self is nil!")
          return
        }
        strongSelf.currentDetector = detector
        strongSelf.removeDetectionAnnotations()
      }
      if detectorType.rawValue == self.currentDetector.rawValue { action.isEnabled = false }
      alertController.addAction(action)
    }
    alertController.addAction(UIAlertAction(title: Constants.cancelActionTitleText, style: .cancel))
    present(alertController, animated: true)
  }

  private func removeDetectionAnnotations() {
    for annotationView in annotationOverlayView.subviews {
      annotationView.removeFromSuperview()
    }
  }

  private func updatePreviewOverlayView() {
    guard let lastFrame = lastFrame,
      let imageBuffer = CMSampleBufferGetImageBuffer(lastFrame)
    else {
      return
    }
    let ciImage = CIImage(cvPixelBuffer: imageBuffer)
    let context = CIContext(options: nil)
    guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else {
      return
    }
    let rotatedImage = UIImage(
      cgImage: cgImage, scale: Constants.originalScale, orientation: .right)
    if isUsingFrontCamera {
      guard let rotatedCGImage = rotatedImage.cgImage else {
        return
      }
      let mirroredImage = UIImage(
        cgImage: rotatedCGImage, scale: Constants.originalScale, orientation: .leftMirrored)
      previewOverlayView.image = mirroredImage
    } else {
      previewOverlayView.image = rotatedImage
    }
  }
}

// MARK: AVCaptureVideoDataOutputSampleBufferDelegate

extension CameraViewController: AVCaptureVideoDataOutputSampleBufferDelegate {

  func captureOutput(
    _ output: AVCaptureOutput,
    didOutput sampleBuffer: CMSampleBuffer,
    from connection: AVCaptureConnection
  ) {
    guard let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
      print("Failed to get image buffer from sample buffer.")
      return
    }
    // Evaluate `self.currentDetector` once to ensure consistency throughout this method since it
    // can be concurrently modified from the main thread.
    let activeDetector = self.currentDetector

    lastFrame = sampleBuffer
    let visionImage = VisionImage(buffer: sampleBuffer)
    let orientation = UIUtilities.imageOrientation(
      fromDevicePosition: isUsingFrontCamera ? .front : .back
    )
    visionImage.orientation = orientation
    let imageWidth = CGFloat(CVPixelBufferGetWidth(imageBuffer))
    let imageHeight = CGFloat(CVPixelBufferGetHeight(imageBuffer))

    let shouldEnableClassification =
      activeDetector == .detectorObjectsSingleWithClassifier
      || activeDetector == .detectorObjectsMultipleWithClassifier
    let shouldEnableMultipleObjects =
      activeDetector == .detectorObjectsMultipleNoClassifier
      || activeDetector == .detectorObjectsMultipleWithClassifier

    switch activeDetector {
    case .detectorImageLabels:
      detectImageLabels(in: visionImage, width: imageWidth, height: imageHeight)
    case .detectorObjectsSingleNoClassifier, .detectorObjectsSingleWithClassifier,
      .detectorObjectsMultipleNoClassifier, .detectorObjectsMultipleWithClassifier:
      detectObjects(
        in: visionImage, width: imageWidth, height: imageHeight,
        shouldEnableClassification: shouldEnableClassification,
        shouldEnableMultipleObjects: shouldEnableMultipleObjects)
    }
  }
}

// MARK: - Constants

private enum DetectorType: String {
  case detectorImageLabels = "AutoML Image Labeling"
  case detectorObjectsSingleNoClassifier = "AutoML ODT, single, no labeling"
  case detectorObjectsSingleWithClassifier = "AutoML ODT, single, labeling"
  case detectorObjectsMultipleNoClassifier = "AutoML ODT, multiple, no labeling"
  case detectorObjectsMultipleWithClassifier = "AutoML ODT, multiple, labeling"
}

private enum Constants {
  static let alertControllerTitle = "AutoML Detectors"
  static let alertControllerMessage = "Select a detector"
  static let cancelActionTitleText = "Cancel"
  static let videoDataOutputQueueLabel = "com.google.mlkit.automl.VideoDataOutputQueue"
  static let sessionQueueLabel = "com.google.mlkit.automl.SessionQueue"
  static let noResultsMessage = "No Results"
  static let remoteAutoMLModelName = "remote_automl_model"
  static let localModelManifestFileName = "automl_labeler_manifest"
  static let autoMLManifestFileType = "json"
  static let labelConfidenceThreshold: Float = 0.75
  static let smallDotRadius: CGFloat = 4.0
  static let originalScale: CGFloat = 1.0
  static let padding: CGFloat = 10.0
  static let resultsLabelHeight: CGFloat = 200.0
  static let resultsLabelLines = 5
}
