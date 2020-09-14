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
  private let detectors: [Detector] = [
    .onDeviceBarcode,
    .onDeviceFace,
    .onDeviceText,
    .onDeviceObjectProminentNoClassifier,
    .onDeviceObjectProminentWithClassifier,
    .onDeviceObjectMultipleNoClassifier,
    .onDeviceObjectMultipleWithClassifier,
    .onDeviceObjectCustomProminentNoClassifier,
    .onDeviceObjectCustomProminentWithClassifier,
    .onDeviceObjectCustomMultipleNoClassifier,
    .onDeviceObjectCustomMultipleWithClassifier,
    .pose,
    .poseAccurate,
  ]

  private var currentDetector: Detector = .onDeviceFace
  private var isUsingFrontCamera = true
  private var previewLayer: AVCaptureVideoPreviewLayer!
  private lazy var captureSession = AVCaptureSession()
  private lazy var sessionQueue = DispatchQueue(label: Constant.sessionQueueLabel)
  private var lastFrame: CMSampleBuffer?

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

  /// Serial queue used for synchronizing access to `_poseDetector`. This is needed because Swift
  /// lacks ObjC-style synchronization and the detector is accessed on different threads across
  /// initialization, usage, and deallocation. Note that just using the main queue for
  /// synchronization from the getter/setter overrides is unsafe because it could allow a deadlock
  /// if the `poseDetector` property were accessed on the main thread.
  private let poseDetectorQueue = DispatchQueue(label: "com.google.mlkit.pose")

  /// The detector used for detecting poses. The pose detector's lifecycle is managed manually, so
  /// it is initialized on-demand via the getter override and set to `nil` when a new detector is
  /// chosen.
  private var _poseDetector: PoseDetector? = nil
  private var poseDetector: PoseDetector? {
    get {
      var detector: PoseDetector? = nil
      poseDetectorQueue.sync {
        if _poseDetector == nil {
          let options = currentDetector == .pose ? PoseDetectorOptions()
                                                 : AccuratePoseDetectorOptions()
          options.detectorMode = .stream
          _poseDetector = PoseDetector.poseDetector(options: options)
        }
        detector = _poseDetector
      }
      return detector
    }
    set(newDetector) {
      poseDetectorQueue.sync {
        _poseDetector = newDetector
      }
    }
  }

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

  @IBAction func selectDetector(_ sender: Any) {
    presentDetectorsAlertController()
  }

  @IBAction func switchCamera(_ sender: Any) {
    isUsingFrontCamera = !isUsingFrontCamera
    removeDetectionAnnotations()
    setUpCaptureSessionInput()
  }

  // MARK: On-Device Detections

  private func scanBarcodesOnDevice(in image: VisionImage, width: CGFloat, height: CGFloat) {
    // Define the options for a barcode detector.
    let format = BarcodeFormat.all
    let barcodeOptions = BarcodeScannerOptions(formats: format)

    // Create a barcode scanner.
    let barcodeScanner = BarcodeScanner.barcodeScanner(options: barcodeOptions)
    var barcodes: [Barcode]
    do {
      barcodes = try barcodeScanner.results(in: image)
    } catch let error {
      print("Failed to scan barcodes with error: \(error.localizedDescription).")
      return
    }
    DispatchQueue.main.sync {
      self.updatePreviewOverlayView()
      self.removeDetectionAnnotations()
    }
    guard !barcodes.isEmpty else {
      print("Barcode scanner returrned no results.")
      return
    }
    DispatchQueue.main.sync {
      for barcode in barcodes {
        let normalizedRect = CGRect(
          x: barcode.frame.origin.x / width,
          y: barcode.frame.origin.y / height,
          width: barcode.frame.size.width / width,
          height: barcode.frame.size.height / height
        )
        let convertedRect = self.previewLayer.layerRectConverted(
          fromMetadataOutputRect: normalizedRect
        )
        UIUtilities.addRectangle(
          convertedRect,
          to: self.annotationOverlayView,
          color: UIColor.green
        )
        let label = UILabel(frame: convertedRect)
        label.text = barcode.rawValue
        label.adjustsFontSizeToFitWidth = true
        self.annotationOverlayView.addSubview(label)
      }
    }
  }

  private func detectFacesOnDevice(in image: VisionImage, width: CGFloat, height: CGFloat) {
    // When performing latency tests to determine ideal detection settings, run the app in 'release'
    // mode to get accurate performance metrics.
    let options = FaceDetectorOptions()
    options.landmarkMode = .none
    options.contourMode = .all
    options.classificationMode = .none
    options.performanceMode = .fast
    let faceDetector = FaceDetector.faceDetector(options: options)
    var faces: [Face]
    do {
      faces = try faceDetector.results(in: image)
    } catch let error {
      print("Failed to detect faces with error: \(error.localizedDescription).")
      return
    }
    DispatchQueue.main.sync {
      self.updatePreviewOverlayView()
      self.removeDetectionAnnotations()
    }
    guard !faces.isEmpty else {
      print("On-Device face detector returned no results.")
      return
    }
    DispatchQueue.main.sync {
      for face in faces {
        let normalizedRect = CGRect(
          x: face.frame.origin.x / width,
          y: face.frame.origin.y / height,
          width: face.frame.size.width / width,
          height: face.frame.size.height / height
        )
        let standardizedRect = self.previewLayer.layerRectConverted(
          fromMetadataOutputRect: normalizedRect
        ).standardized
        UIUtilities.addRectangle(
          standardizedRect,
          to: self.annotationOverlayView,
          color: UIColor.green
        )
        self.addContours(for: face, width: width, height: height)
      }
    }
  }

  private func detectPose(in image: VisionImage, width: CGFloat, height: CGFloat) {
    if let poseDetector = self.poseDetector {
      var poses: [Pose]
      do {
        poses = try poseDetector.results(in: image)
      } catch let error {
        print("Failed to detect poses with error: \(error.localizedDescription).")
        return
      }
      DispatchQueue.main.sync {
        self.updatePreviewOverlayView()
        self.removeDetectionAnnotations()
      }
      guard !poses.isEmpty else {
        print("Pose detector returned no results.")
        return
      }
      DispatchQueue.main.sync {
        // Pose detected. Currently, only single person detection is supported.
        poses.forEach { pose in
          for (startLandmarkType, endLandmarkTypesArray) in UIUtilities.poseConnections() {
            let startLandmark = pose.landmark(ofType: startLandmarkType)
            for endLandmarkType in endLandmarkTypesArray {
              let endLandmark = pose.landmark(ofType: endLandmarkType)
              let startLandmarkPoint = normalizedPoint(
                fromVisionPoint: startLandmark.position, width: width, height: height)
              let endLandmarkPoint = normalizedPoint(
                fromVisionPoint: endLandmark.position, width: width, height: height)
              UIUtilities.addLineSegment(
                fromPoint: startLandmarkPoint,
                toPoint: endLandmarkPoint,
                inView: self.annotationOverlayView,
                color: UIColor.green,
                width: Constant.lineWidth
              )
            }
          }
          for landmark in pose.landmarks {
            let landmarkPoint = normalizedPoint(
              fromVisionPoint: landmark.position, width: width, height: height)
            UIUtilities.addCircle(
              atPoint: landmarkPoint,
              to: self.annotationOverlayView,
              color: UIColor.blue,
              radius: Constant.smallDotRadius
            )
          }
        }
      }
    }
  }

  private func recognizeTextOnDevice(in image: VisionImage, width: CGFloat, height: CGFloat) {
    var recognizedText: Text
    do {
      recognizedText = try TextRecognizer.textRecognizer().results(in: image)
    } catch let error {
      print("Failed to recognize text with error: \(error.localizedDescription).")
      return
    }
    DispatchQueue.main.sync {
      self.updatePreviewOverlayView()
      self.removeDetectionAnnotations()

      // Blocks.
      for block in recognizedText.blocks {
        let points = self.convertedPoints(from: block.cornerPoints, width: width, height: height)
        UIUtilities.addShape(
          withPoints: points,
          to: self.annotationOverlayView,
          color: UIColor.purple
        )

        // Lines.
        for line in block.lines {
          let points = self.convertedPoints(from: line.cornerPoints, width: width, height: height)
          UIUtilities.addShape(
            withPoints: points,
            to: self.annotationOverlayView,
            color: UIColor.orange
          )

          // Elements.
          for element in line.elements {
            let normalizedRect = CGRect(
              x: element.frame.origin.x / width,
              y: element.frame.origin.y / height,
              width: element.frame.size.width / width,
              height: element.frame.size.height / height
            )
            let convertedRect = self.previewLayer.layerRectConverted(
              fromMetadataOutputRect: normalizedRect
            )
            UIUtilities.addRectangle(
              convertedRect,
              to: self.annotationOverlayView,
              color: UIColor.green
            )
            let label = UILabel(frame: convertedRect)
            label.text = element.text
            label.adjustsFontSizeToFitWidth = true
            self.annotationOverlayView.addSubview(label)
          }
        }
      }
    }
  }

  private func detectObjectsOnDevice(
    in image: VisionImage,
    width: CGFloat,
    height: CGFloat,
    options: CommonObjectDetectorOptions
  ) {
    let detector = ObjectDetector.objectDetector(options: options)
    var objects: [Object]
    do {
      objects = try detector.results(in: image)
    } catch let error {
      print("Failed to detect objects with error: \(error.localizedDescription).")
      return
    }
    DispatchQueue.main.sync {
      self.updatePreviewOverlayView()
      self.removeDetectionAnnotations()
    }
    guard !objects.isEmpty else {
      print("On-Device object detector returned no results.")
      return
    }

    DispatchQueue.main.sync {
      for object in objects {
        let normalizedRect = CGRect(
          x: object.frame.origin.x / width,
          y: object.frame.origin.y / height,
          width: object.frame.size.width / width,
          height: object.frame.size.height / height
        )
        let standardizedRect = self.previewLayer.layerRectConverted(
          fromMetadataOutputRect: normalizedRect
        ).standardized
        UIUtilities.addRectangle(
          standardizedRect,
          to: self.annotationOverlayView,
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
        self.annotationOverlayView.addSubview(label)
      }
    }
  }

  // MARK: - Private

  private func setUpCaptureSessionOutput() {
    sessionQueue.async {
      self.captureSession.beginConfiguration()
      // When performing latency tests to determine ideal capture settings,
      // run the app in 'release' mode to get accurate performance metrics
      self.captureSession.sessionPreset = AVCaptureSession.Preset.medium

      let output = AVCaptureVideoDataOutput()
      output.videoSettings = [
        (kCVPixelBufferPixelFormatTypeKey as String): kCVPixelFormatType_32BGRA,
      ]
      output.alwaysDiscardsLateVideoFrames = true
      let outputQueue = DispatchQueue(label: Constant.videoDataOutputQueueLabel)
      output.setSampleBufferDelegate(self, queue: outputQueue)
      guard self.captureSession.canAddOutput(output) else {
        print("Failed to add capture session output.")
        return
      }
      self.captureSession.addOutput(output)
      self.captureSession.commitConfiguration()
    }
  }

  private func setUpCaptureSessionInput() {
    sessionQueue.async {
      let cameraPosition: AVCaptureDevice.Position = self.isUsingFrontCamera ? .front : .back
      guard let device = self.captureDevice(forPosition: cameraPosition) else {
        print("Failed to get capture device for camera position: \(cameraPosition)")
        return
      }
      do {
        self.captureSession.beginConfiguration()
        let currentInputs = self.captureSession.inputs
        for input in currentInputs {
          self.captureSession.removeInput(input)
        }

        let input = try AVCaptureDeviceInput(device: device)
        guard self.captureSession.canAddInput(input) else {
          print("Failed to add capture session input.")
          return
        }
        self.captureSession.addInput(input)
        self.captureSession.commitConfiguration()
      } catch {
        print("Failed to create capture device input: \(error.localizedDescription)")
      }
    }
  }

  private func startSession() {
    sessionQueue.async {
      self.captureSession.startRunning()
    }
  }

  private func stopSession() {
    sessionQueue.async {
      self.captureSession.stopRunning()
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
      title: Constant.alertControllerTitle,
      message: Constant.alertControllerMessage,
      preferredStyle: .alert
    )
    detectors.forEach { detectorType in
      let action = UIAlertAction(title: detectorType.rawValue, style: .default) {
        [unowned self] (action) in
        guard let value = action.title else { return }
        guard let detector = Detector(rawValue: value) else { return }
        self.currentDetector = detector
        self.removeDetectionAnnotations()

        // Reset the pose detector to `nil` when a new detector row is chosen. The detector will be
        // re-initialized via its getter when it is needed for detection again.
        self.poseDetector = nil
      }
      if detectorType.rawValue == currentDetector.rawValue { action.isEnabled = false }
      alertController.addAction(action)
    }
    alertController.addAction(UIAlertAction(title: Constant.cancelActionTitleText, style: .cancel))
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

  private func convertedPoints(
    from points: [NSValue]?,
    width: CGFloat,
    height: CGFloat
  ) -> [NSValue]? {
    return points?.map {
      let cgPointValue = $0.cgPointValue
      let normalizedPoint = CGPoint(x: cgPointValue.x / width, y: cgPointValue.y / height)
      let cgPoint = previewLayer.layerPointConverted(fromCaptureDevicePoint: normalizedPoint)
      let value = NSValue(cgPoint: cgPoint)
      return value
    }
  }

  private func normalizedPoint(
    fromVisionPoint point: VisionPoint,
    width: CGFloat,
    height: CGFloat
  ) -> CGPoint {
    let cgPoint = CGPoint(x: point.x, y: point.y)
    var normalizedPoint = CGPoint(x: cgPoint.x / width, y: cgPoint.y / height)
    normalizedPoint = previewLayer.layerPointConverted(fromCaptureDevicePoint: normalizedPoint)
    return normalizedPoint
  }

  private func addContours(for face: Face, width: CGFloat, height: CGFloat) {
    // Face
    if let faceContour = face.contour(ofType: .face) {
      for point in faceContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.blue,
          radius: Constant.smallDotRadius
        )
      }
    }

    // Eyebrows
    if let topLeftEyebrowContour = face.contour(ofType: .leftEyebrowTop) {
      for point in topLeftEyebrowContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.orange,
          radius: Constant.smallDotRadius
        )
      }
    }
    if let bottomLeftEyebrowContour = face.contour(ofType: .leftEyebrowBottom) {
      for point in bottomLeftEyebrowContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.orange,
          radius: Constant.smallDotRadius
        )
      }
    }
    if let topRightEyebrowContour = face.contour(ofType: .rightEyebrowTop) {
      for point in topRightEyebrowContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.orange,
          radius: Constant.smallDotRadius
        )
      }
    }
    if let bottomRightEyebrowContour = face.contour(ofType: .rightEyebrowBottom) {
      for point in bottomRightEyebrowContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.orange,
          radius: Constant.smallDotRadius
        )
      }
    }

    // Eyes
    if let leftEyeContour = face.contour(ofType: .leftEye) {
      for point in leftEyeContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.cyan,
          radius: Constant.smallDotRadius
        )
      }
    }
    if let rightEyeContour = face.contour(ofType: .rightEye) {
      for point in rightEyeContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.cyan,
          radius: Constant.smallDotRadius
        )
      }
    }

    // Lips
    if let topUpperLipContour = face.contour(ofType: .upperLipTop) {
      for point in topUpperLipContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.red,
          radius: Constant.smallDotRadius
        )
      }
    }
    if let bottomUpperLipContour = face.contour(ofType: .upperLipBottom) {
      for point in bottomUpperLipContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.red,
          radius: Constant.smallDotRadius
        )
      }
    }
    if let topLowerLipContour = face.contour(ofType: .lowerLipTop) {
      for point in topLowerLipContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.red,
          radius: Constant.smallDotRadius
        )
      }
    }
    if let bottomLowerLipContour = face.contour(ofType: .lowerLipBottom) {
      for point in bottomLowerLipContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.red,
          radius: Constant.smallDotRadius
        )
      }
    }

    // Nose
    if let noseBridgeContour = face.contour(ofType: .noseBridge) {
      for point in noseBridgeContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constant.smallDotRadius
        )
      }
    }
    if let noseBottomContour = face.contour(ofType: .noseBottom) {
      for point in noseBottomContour.points {
        let cgPoint = normalizedPoint(fromVisionPoint: point, width: width, height: height)
        UIUtilities.addCircle(
          atPoint: cgPoint,
          to: annotationOverlayView,
          color: UIColor.yellow,
          radius: Constant.smallDotRadius
        )
      }
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
    var shouldEnableClassification = false
    var shouldEnableMultipleObjects = false
    switch currentDetector {
    case .onDeviceObjectProminentWithClassifier, .onDeviceObjectMultipleWithClassifier,
      .onDeviceObjectCustomProminentWithClassifier, .onDeviceObjectCustomMultipleWithClassifier:
      shouldEnableClassification = true
    default:
      break
    }
    switch currentDetector {
    case .onDeviceObjectMultipleNoClassifier, .onDeviceObjectMultipleWithClassifier,
      .onDeviceObjectCustomMultipleNoClassifier, .onDeviceObjectCustomMultipleWithClassifier:
      shouldEnableMultipleObjects = true
    default:
      break
    }

    switch currentDetector {
    case .onDeviceBarcode:
      scanBarcodesOnDevice(in: visionImage, width: imageWidth, height: imageHeight)
    case .onDeviceFace:
      detectFacesOnDevice(in: visionImage, width: imageWidth, height: imageHeight)
    case .onDeviceText:
      recognizeTextOnDevice(in: visionImage, width: imageWidth, height: imageHeight)
    case .onDeviceObjectProminentNoClassifier, .onDeviceObjectProminentWithClassifier,
      .onDeviceObjectMultipleNoClassifier, .onDeviceObjectMultipleWithClassifier:
      let options = ObjectDetectorOptions()
      options.shouldEnableClassification = shouldEnableClassification
      options.shouldEnableMultipleObjects = shouldEnableMultipleObjects
      options.detectorMode = .stream
      detectObjectsOnDevice(
        in: visionImage,
        width: imageWidth,
        height: imageHeight,
        options: options)
    case .onDeviceObjectCustomProminentNoClassifier, .onDeviceObjectCustomProminentWithClassifier,
      .onDeviceObjectCustomMultipleNoClassifier, .onDeviceObjectCustomMultipleWithClassifier:
      guard
        let localModelFilePath = Bundle.main.path(
          forResource: Constant.localModelFile.name,
          ofType: Constant.localModelFile.type
        )
      else {
        print("Failed to find custom local model file.")
        return
      }
      let localModel = LocalModel(path: localModelFilePath)
      let options = CustomObjectDetectorOptions(localModel: localModel)
      options.shouldEnableClassification = shouldEnableClassification
      options.shouldEnableMultipleObjects = shouldEnableMultipleObjects
      options.detectorMode = .stream
      detectObjectsOnDevice(
        in: visionImage,
        width: imageWidth,
        height: imageHeight,
        options: options)

    case .pose, .poseAccurate:
      detectPose(in: visionImage, width: imageWidth, height: imageHeight)
    }
  }
}

// MARK: - Constants

public enum Detector: String {
  case onDeviceBarcode = "Barcode Scanning"
  case onDeviceFace = "Face Detection"
  case onDeviceText = "Text Recognition"
  case onDeviceObjectProminentNoClassifier = "ODT, single, no labeling"
  case onDeviceObjectProminentWithClassifier = "ODT, single, labeling"
  case onDeviceObjectMultipleNoClassifier = "ODT, multiple, no labeling"
  case onDeviceObjectMultipleWithClassifier = "ODT, multiple, labeling"
  case onDeviceObjectCustomProminentNoClassifier = "ODT, custom, single, no labeling"
  case onDeviceObjectCustomProminentWithClassifier = "ODT, custom, single, labeling"
  case onDeviceObjectCustomMultipleNoClassifier = "ODT, custom, multiple, no labeling"
  case onDeviceObjectCustomMultipleWithClassifier = "ODT, custom, multiple, labeling"
  case pose = "Pose Detection"
  case poseAccurate = "Pose Detection, accurate"
}

private enum Constant {
  static let alertControllerTitle = "Vision Detectors"
  static let alertControllerMessage = "Select a detector"
  static let cancelActionTitleText = "Cancel"
  static let videoDataOutputQueueLabel = "com.google.mlkit.visiondetector.VideoDataOutputQueue"
  static let sessionQueueLabel = "com.google.mlkit.visiondetector.SessionQueue"
  static let noResultsMessage = "No Results"
  static let localModelFile = (name: "bird", type: "tflite")
  static let labelConfidenceThreshold: Float = 0.75
  static let smallDotRadius: CGFloat = 4.0
  static let lineWidth: CGFloat = 3.0
  static let originalScale: CGFloat = 1.0
  static let padding: CGFloat = 10.0
  static let resultsLabelHeight: CGFloat = 200.0
  static let resultsLabelLines = 5
}
