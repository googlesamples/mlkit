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

import AVFoundation
import CoreVideo
import MLKit
import MaterialComponents

private let kBoxCornerRadius: CGFloat = 12.0
private let kBoxBorderWidth: CGFloat = 2.0
private let kBoxBackgroundAlpha: CGFloat = 0.12
private let boxWidth: CGFloat = 340.0
private let boxHeight: CGFloat = 100.0
private let boxWidthHalf = boxWidth / 2
private let boxHeightHalf = boxHeight / 2
private let hdWidth: CGFloat = 720  // AVCaptureSession.Preset.hd1280x720
private let hdHeight: CGFloat = 1280  // AVCaptureSession.Preset.hd1280x720
private let hdWidthHalf = hdWidth / 2
private let hdHeightHalf = hdHeight / 2
private let defaultMargin: CGFloat = 16
private let chipHeight: CGFloat = 32
private let chipHeightHalf = chipHeight / 2
private let customSelectedColor = UIColor(red: 0.10, green: 0.45, blue: 0.91, alpha: 1.0)
private let backgroundColor = UIColor(red: 0.91, green: 0.94, blue: 0.99, alpha: 1.0)

@objc(CameraViewController)
class CameraViewController: UIViewController {
  var detectCounts = [String: Int]()
  var detectQueue = [String]()
  var detectedText = ""
  var recentOutputLanguages = [TranslateLanguage.english, TranslateLanguage.spanish]
  let sizingChip = MDCChipView()
  var selectedItem = 0
  var cropX = 0
  var cropWidth = 0
  var cropY = 0
  var cropHeight = 0

  @IBOutlet var chipCollectionView: UICollectionView!

  // We keep track of the pending work item as a property
  private var pendingRequestWorkItem: DispatchWorkItem?

  private lazy var shapeGenerator: MDCRectangleShapeGenerator = {
    let gen = MDCRectangleShapeGenerator()
    gen.setCorners(MDCCornerTreatment.corner(withRadius: 4))
    return gen
  }()

  private var previewLayer: AVCaptureVideoPreviewLayer!
  private var cameraOverlayView: CameraOverlayView!
  private lazy var captureSession = AVCaptureSession()
  private lazy var sessionQueue = DispatchQueue(label: Constant.sessionQueueLabel)
  private lazy var languageId = LanguageIdentification.languageIdentification()

  var translator: Translator!

  @IBOutlet var resultsView: UIView!
  @IBOutlet var detectedTextLabel: UILabel!
  @IBOutlet var translateLanguageLabel: UILabel!
  @IBOutlet var detectedLanguageLabel: UILabel!
  @IBOutlet var translatedLabel: UILabel!
  let containerScheme = MDCContainerScheme()
  var detectedLanguage = TranslateLanguage.english

  private lazy var annotationOverlayView: UIView = {
    precondition(isViewLoaded)
    let annotationOverlayView = UIView(frame: .zero)
    annotationOverlayView.translatesAutoresizingMaskIntoConstraints = false
    return annotationOverlayView
  }()

  // MARK: - IBOutlets

  @IBOutlet private weak var previewView: UIView!

  // MARK: - UIViewController

  override func viewDidLoad() {
    super.viewDidLoad()
    //Translator.setMaxLoadedTranslators(8);
    setUpCameraPreviewLayer()
    sizingChip.mdc_adjustsFontForContentSizeCategory = true
    setUpAnnotationOverlayView()
    chipCollectionView.register(
      MDCChipCollectionViewCell.self, forCellWithReuseIdentifier: "identifier")
    chipCollectionView.dataSource = self
    chipCollectionView.delegate = self
    chipCollectionView.isScrollEnabled = false

    let ratio = hdWidth / previewView.bounds.width
    cropX = Int(hdHeightHalf - (ratio * boxHeightHalf))
    cropWidth = Int(boxHeight * ratio)
    cropY = Int(hdWidthHalf - (ratio * boxWidthHalf))
    cropHeight = Int(boxWidth * ratio)

    setUpCaptureSessionOutput()
    setUpCaptureSessionInput()

    MDCCornerTreatment.corner(withRadius: 4)
  }

  private func setUpCameraPreviewLayer() {
    previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
    previewLayer.backgroundColor = UIColor.black.cgColor
    previewLayer.videoGravity = .resizeAspectFill
    let rootLayer = previewView.layer
    rootLayer.masksToBounds = true
    previewLayer.frame = rootLayer.bounds
    rootLayer.addSublayer(previewLayer)
  }

  private func setUpCameraOverlayView() {
    cameraOverlayView = CameraOverlayView(frame: previewView.bounds)
    let rect = CGRect(
      x: previewView.bounds.midX - boxWidthHalf,
      y: previewView.bounds.midY - boxHeightHalf,
      width: boxWidth,
      height: boxHeight)
    cameraOverlayView.showBox(in: rect)
    previewView.addSubview(cameraOverlayView)
    let chipY = previewView.bounds.midY + boxHeightHalf + chipHeightHalf + defaultMargin
    cameraOverlayView.showMessage(
      "Center text in box and hold for a while",
      in: CGPoint(x: previewView.bounds.midX, y: chipY))
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    if cameraOverlayView == nil {
      setUpCameraOverlayView()
    }
    startSession()
    chipCollectionView.reloadData()
  }

  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    navigationController?.setNavigationBarHidden(true, animated: false)
  }

  override func viewDidDisappear(_ animated: Bool) {
    super.viewDidDisappear(animated)
    stopSession()
  }

  override func viewDidLayoutSubviews() {
    super.viewDidLayoutSubviews()
    previewLayer.frame = previewView.bounds
  }

  func numberOfComponents(in pickerView: UIPickerView) -> Int {
    return 1
  }

  private func configureChip(_ chip: MDCChipView?) {
    chip?.setTitleColor(customSelectedColor, for: .selected)
    chip?.setBorderColor(customSelectedColor, for: UIControl.State.selected)
    chip?.setBorderWidth(0, for: UIControl.State.selected)
    chip?.setBackgroundColor(backgroundColor, for: .selected)
    chip?.shapeGenerator = shapeGenerator
    chip?.setInkColor(backgroundColor, for: .normal)
  }

  private func recognizeTextOnDevice(in image: VisionImage) {
    let textRecognizer = TextRecognizer.textRecognizer()
    let group = DispatchGroup()
    group.enter()
    textRecognizer.process(image) { text, error in
      group.leave()
      self.removeDetectionAnnotations()

      guard error == nil, let text = text else {
        print(
          "On-Device text recognizer error: "
            + "\(error?.localizedDescription ?? Constant.noResultsMessage)")
        return
      }
      // Blocks.
      guard let block = text.blocks.first else { return }
      let detection = block.text
      if detection == self.detectedText {
        return
      }

      self.detectedText = detection
      DispatchQueue.main.async {
        self.detectedTextLabel.text = detection
      }

      self.identifyLanguage(for: detection)
    }
    group.wait()
  }

  private func identifyLanguage(for text: String) {
    self.pendingRequestWorkItem?.cancel()

    // Wrap our request in a work item
    let requestWorkItem = DispatchWorkItem { [weak self] in
      self?.languageId.identifyLanguage(for: text) { languageTag, error in
        if let error = error {
          print("Failed with error: \(error)")
          return
        }
        guard let languageTag = languageTag else {
          print("No language was identified.")
          return
        }
        let detectedLanguage = TranslateLanguage(rawValue: languageTag)
        guard TranslateLanguage.allLanguages().contains(detectedLanguage) else {
          return
        }
        if detectedLanguage != self?.detectedLanguage {
          self?.detectedLanguage = detectedLanguage
          DispatchQueue.main.async {
            self?.detectedLanguageLabel.text = detectedLanguage.localizedName()
          }
        }
        self?.translate(text)
      }
    }

    // Save the new work item and execute it after 50 ms
    self.pendingRequestWorkItem = requestWorkItem
    DispatchQueue.main.asyncAfter(
      deadline: .now() + .milliseconds(50),
      execute: requestWorkItem)
  }

  func translate(_ inputText: String) {
    let options = TranslatorOptions(
      sourceLanguage: detectedLanguage,
      targetLanguage: recentOutputLanguages[selectedItem])
    translator = Translator.translator(options: options)

    let translatorForDownloading = self.translator!
    translatorForDownloading.downloadModelIfNeeded { error in
      guard error == nil else {
        self.startSession()
        print("Failed to ensure model downloaded with error \(error!)")
        return
      }
      if translatorForDownloading == self.translator {
        translatorForDownloading.translate(inputText) { result, error in
          self.startSession()
          guard error == nil else {
            print("Failed with error \(error!)")
            return
          }
          if translatorForDownloading == self.translator {
            DispatchQueue.main.async {
              self.translatedLabel.text = result
            }
          }
        }
      }
    }
  }

  private func removeDetectionAnnotations() {
    for annotationView in annotationOverlayView.subviews {
      annotationView.removeFromSuperview()
    }
  }

  // MARK: - Private

  private func setUpCaptureSessionOutput() {
    sessionQueue.async {
      self.captureSession.beginConfiguration()
      // When performing latency tests to determine ideal capture settings,
      // run the app in 'release' mode to get accurate performance metrics
      self.captureSession.sessionPreset = AVCaptureSession.Preset.hd1280x720

      let output = AVCaptureVideoDataOutput()
      output.videoSettings =
        [(kCVPixelBufferPixelFormatTypeKey as String): kCVPixelFormatType_32BGRA]
      let outputQueue = DispatchQueue(label: Constant.videoDataOutputQueueLabel)
      output.alwaysDiscardsLateVideoFrames = true
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
      let cameraPosition: AVCaptureDevice.Position = .back
      guard let device = self.captureDevice(forPosition: cameraPosition) else {
        print("Failed to get capture device for back camera position")
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

  private func setUpAnnotationOverlayView() {
    previewView.addSubview(annotationOverlayView)
    NSLayoutConstraint.activate([
      annotationOverlayView.topAnchor.constraint(equalTo: previewView.topAnchor),
      annotationOverlayView.leadingAnchor.constraint(equalTo: previewView.leadingAnchor),
      annotationOverlayView.trailingAnchor.constraint(equalTo: previewView.trailingAnchor),
      annotationOverlayView.bottomAnchor.constraint(equalTo: previewView.bottomAnchor),
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
}

extension CameraViewController: UICollectionViewDelegate, UICollectionViewDataSource,
  UICollectionViewDelegateFlowLayout
{
  func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int)
    -> Int
  {
    return 3
  }

  func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath)
    -> UICollectionViewCell
  {
    let cell =
      collectionView.dequeueReusableCell(withReuseIdentifier: "identifier", for: indexPath)
      as! MDCChipCollectionViewCell
    let chip = cell.chipView
    chip.applyOutlinedTheme(withScheme: containerScheme)
    configureChip(chip)
    if indexPath.item == 2 {
      chip.titleLabel.text = "More"
    } else {
      chip.isSelected = indexPath.item == selectedItem
      chip.titleLabel.text = recentOutputLanguages[indexPath.item].localizedName()

      chip.selectedImageView.image = #imageLiteral(resourceName: "baseline_check_black_24pt")
      cell.alwaysAnimateResize = true
    }
    return cell
  }

  func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
    if indexPath.item == 2 {
      collectionView.deselectItem(at: IndexPath(item: 2, section: 0), animated: false)
      performSegue(withIdentifier: "search", sender: nil)
      return
    }
    selectedItem = indexPath.item
    collectionView.performBatchUpdates(nil, completion: nil)
    translate(self.detectedText)
  }

  func collectionView(
    _ collectionView: UICollectionView, layout collectionViewLayout: UICollectionViewLayout,
    sizeForItemAt indexPath: IndexPath
  ) -> CGSize {
    sizingChip.titleLabel.text =
      indexPath.item == 2 ? "More" : recentOutputLanguages[indexPath.item].localizedName()
    sizingChip.applyOutlinedTheme(withScheme: containerScheme)
    sizingChip.selectedImageView.image = #imageLiteral(resourceName: "baseline_check_black_24pt")
    sizingChip.isSelected = indexPath.item == selectedItem
    configureChip(sizingChip)
    return sizingChip.sizeThatFits(collectionView.bounds.size)
  }
}

// MARK: AVCaptureVideoDataOutputSampleBufferDelegate

extension CameraViewController: AVCaptureVideoDataOutputSampleBufferDelegate {

  func captureOutput(
    _ output: AVCaptureOutput,
    didOutput sampleBuffer: CMSampleBuffer,
    from connection: AVCaptureConnection
  ) {

    guard let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
    let newBuffer = resizePixelBuffer(
      imageBuffer, cropX: cropX, cropY: cropY, cropWidth: cropWidth, cropHeight: cropHeight)

    var sampleTime = CMSampleTimingInfo()
    sampleTime.duration = CMSampleBufferGetDuration(sampleBuffer)
    sampleTime.presentationTimeStamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
    sampleTime.decodeTimeStamp = CMSampleBufferGetDecodeTimeStamp(sampleBuffer)
    var videoInfo: CMVideoFormatDescription?
    CMVideoFormatDescriptionCreateForImageBuffer(kCFAllocatorDefault, newBuffer!, &videoInfo)

    // Creates `CMSampleBufferRef`.
    var resultBuffer: CMSampleBuffer? = nil
    CMSampleBufferCreateForImageBuffer(
      kCFAllocatorDefault, newBuffer!, true, nil, nil, videoInfo!, &sampleTime, &resultBuffer)

    let visionImage = VisionImage.init(buffer: resultBuffer!)
    let orientation = UIUtilities.imageOrientation(
      fromDevicePosition: .back
    )

    visionImage.orientation = orientation

    self.recognizeTextOnDevice(in: visionImage)

  }

}

// MARK: - Constants

private enum Constant {
  static let videoDataOutputQueueLabel = "com.google.mlkit.visiondetector.VideoDataOutputQueue"
  static let sessionQueueLabel = "com.google.mlkit.visiondetector.SessionQueue"
  static let noResultsMessage = "No Results"
  static let smallDotRadius: CGFloat = 4.0
  static let originalScale: CGFloat = 1.0
  static let padding: CGFloat = 10.0
  static let resultsLabelHeight: CGFloat = 200.0
  static let resultsLabelLines = 5
}
