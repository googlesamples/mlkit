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

@objc(CameraViewController)
class CameraViewController: UIViewController {

  private var isUsingFrontCamera = true
  private var previewLayer: AVCaptureVideoPreviewLayer!
  private lazy var captureSession = AVCaptureSession()
  private lazy var sessionQueue = DispatchQueue(label: Constant.sessionQueueLabel)
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

    startSession()
  }

  override func viewDidDisappear(_ animated: Bool) {
    super.viewDidDisappear(animated)

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

  // MARK: - On-Device AutoML Detections

  private func detectImageLabelsAutoMLOndevice(
    in visionImage: VisionImage,
    width: CGFloat,
    height: CGFloat
  ) {
    requestAutoMLRemoteModelIfNeeded()

    let remoteModel = AutoMLImageLabelerRemoteModel(name: Constant.remoteAutoMLModelName)
    guard
      let localModelFilePath = Bundle.main.path(
        forResource: Constant.localModelManifestFileName,
        ofType: Constant.autoMLManifestFileType
      )
    else {
      print("Failed to find AutoML local model manifest file.")
      return
    }
    let localModel = AutoMLImageLabelerLocalModel(manifestPath: localModelFilePath)
    let isModelDownloaded = modelManager.isModelDownloaded(remoteModel)
    let options =
      isModelDownloaded
      ? AutoMLImageLabelerOptions(remoteModel: remoteModel)
      : AutoMLImageLabelerOptions(localModel: localModel)
    print("Use AutoML \(isModelDownloaded ? "remote" : "local") model.")
    options.confidenceThreshold = NSNumber(value: Constant.labelConfidenceThreshold)
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
        x: annotationFrame.origin.x + Constant.padding,
        y: annotationFrame.size.height - Constant.padding - Constant.resultsLabelHeight,
        width: annotationFrame.width - 2 * Constant.padding,
        height: Constant.resultsLabelHeight
      )
      let resultsLabel = UILabel(frame: resultsRect)
      resultsLabel.textColor = .yellow
      resultsLabel.text = labels.map { label -> String in
        return "Label: \(label.text), Confidence: \(label.confidence ?? 0)"
      }.joined(separator: "\n")
      resultsLabel.adjustsFontSizeToFitWidth = true
      resultsLabel.numberOfLines = Constant.resultsLabelLines
      strongSelf.annotationOverlayView.addSubview(resultsLabel)
    }

    group.wait()
  }

  private func requestAutoMLRemoteModelIfNeeded() {
    let remoteModel = AutoMLImageLabelerRemoteModel(name: Constant.remoteAutoMLModelName)
    if modelManager.isModelDownloaded(remoteModel) {
      return
    }
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
      let outputQueue = DispatchQueue(label: Constant.videoDataOutputQueueLabel)
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
    let rotatedImage = UIImage(cgImage: cgImage, scale: Constant.originalScale, orientation: .right)
    if isUsingFrontCamera {
      guard let rotatedCGImage = rotatedImage.cgImage else {
        return
      }
      let mirroredImage = UIImage(
        cgImage: rotatedCGImage, scale: Constant.originalScale, orientation: .leftMirrored)
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
    lastFrame = sampleBuffer
    let visionImage = VisionImage(buffer: sampleBuffer)
    let orientation = UIUtilities.imageOrientation(
      fromDevicePosition: isUsingFrontCamera ? .front : .back
    )
    visionImage.orientation = orientation
    let imageWidth = CGFloat(CVPixelBufferGetWidth(imageBuffer))
    let imageHeight = CGFloat(CVPixelBufferGetHeight(imageBuffer))

    detectImageLabelsAutoMLOndevice(in: visionImage, width: imageWidth, height: imageHeight)
  }
}

// MARK: - Constants

private enum Constant {
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
