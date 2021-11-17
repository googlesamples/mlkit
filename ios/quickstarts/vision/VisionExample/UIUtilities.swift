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

/// Defines UI-related utilitiy methods for vision detection.
public class UIUtilities {

  // MARK: - Public

  public static func addCircle(
    atPoint point: CGPoint,
    to view: UIView,
    color: UIColor,
    radius: CGFloat
  ) {
    let divisor: CGFloat = 2.0
    let xCoord = point.x - radius / divisor
    let yCoord = point.y - radius / divisor
    let circleRect = CGRect(x: xCoord, y: yCoord, width: radius, height: radius)
    guard circleRect.isValid() else { return }
    let circleView = UIView(frame: circleRect)
    circleView.layer.cornerRadius = radius / divisor
    circleView.alpha = Constants.circleViewAlpha
    circleView.backgroundColor = color
    view.addSubview(circleView)
  }

  public static func addLineSegment(
    fromPoint: CGPoint, toPoint: CGPoint, inView: UIView, color: UIColor, width: CGFloat
  ) {
    let path = UIBezierPath()
    path.move(to: fromPoint)
    path.addLine(to: toPoint)
    let lineLayer = CAShapeLayer()
    lineLayer.path = path.cgPath
    lineLayer.strokeColor = color.cgColor
    lineLayer.fillColor = nil
    lineLayer.opacity = 1.0
    lineLayer.lineWidth = width
    let lineView = UIView()
    lineView.layer.addSublayer(lineLayer)
    inView.addSubview(lineView)
  }

  public static func addRectangle(_ rectangle: CGRect, to view: UIView, color: UIColor) {
    guard rectangle.isValid() else { return }
    let rectangleView = UIView(frame: rectangle)
    rectangleView.layer.cornerRadius = Constants.rectangleViewCornerRadius
    rectangleView.alpha = Constants.rectangleViewAlpha
    rectangleView.backgroundColor = color
    view.addSubview(rectangleView)
  }

  public static func addShape(withPoints points: [NSValue]?, to view: UIView, color: UIColor) {
    guard let points = points else { return }
    let path = UIBezierPath()
    for (index, value) in points.enumerated() {
      let point = value.cgPointValue
      if index == 0 {
        path.move(to: point)
      } else {
        path.addLine(to: point)
      }
      if index == points.count - 1 {
        path.close()
      }
    }
    let shapeLayer = CAShapeLayer()
    shapeLayer.path = path.cgPath
    shapeLayer.fillColor = color.cgColor
    let rect = CGRect(x: 0, y: 0, width: view.frame.size.width, height: view.frame.size.height)
    let shapeView = UIView(frame: rect)
    shapeView.alpha = Constants.shapeViewAlpha
    shapeView.layer.addSublayer(shapeLayer)
    view.addSubview(shapeView)
  }

  public static func imageOrientation(
    fromDevicePosition devicePosition: AVCaptureDevice.Position = .back
  ) -> UIImage.Orientation {
    var deviceOrientation = UIDevice.current.orientation
    if deviceOrientation == .faceDown || deviceOrientation == .faceUp
      || deviceOrientation
        == .unknown
    {
      deviceOrientation = currentUIOrientation()
    }
    switch deviceOrientation {
    case .portrait:
      return devicePosition == .front ? .leftMirrored : .right
    case .landscapeLeft:
      return devicePosition == .front ? .downMirrored : .up
    case .portraitUpsideDown:
      return devicePosition == .front ? .rightMirrored : .left
    case .landscapeRight:
      return devicePosition == .front ? .upMirrored : .down
    case .faceDown, .faceUp, .unknown:
      return .up
    @unknown default:
      fatalError()
    }
  }

  /// Applies a segmentation mask to an image buffer by replacing colors in the segmented regions.
  ///
  /// @param The mask output from a segmentation operation.
  /// @param imageBuffer The image buffer on which segmentation was performed. Must have pixel
  ///     format type `kCVPixelFormatType_32BGRA`.
  /// @param backgroundColor Optional color to render into the background region (i.e. outside of
  ///    the segmented region of interest).
  /// @param foregroundColor Optional color to render into the foreground region (i.e. inside the
  ///     segmented region of interest).
  public static func applySegmentationMask(
    mask: SegmentationMask, to imageBuffer: CVImageBuffer,
    backgroundColor: UIColor?, foregroundColor: UIColor?
  ) {
    assert(
      CVPixelBufferGetPixelFormatType(imageBuffer) == kCVPixelFormatType_32BGRA,
      "Image buffer must have 32BGRA pixel format type")

    let width = CVPixelBufferGetWidth(mask.buffer)
    let height = CVPixelBufferGetHeight(mask.buffer)
    assert(CVPixelBufferGetWidth(imageBuffer) == width, "Width must match")
    assert(CVPixelBufferGetHeight(imageBuffer) == height, "Height must match")

    if backgroundColor == nil && foregroundColor == nil {
      return
    }

    let writeFlags = CVPixelBufferLockFlags(rawValue: 0)
    CVPixelBufferLockBaseAddress(imageBuffer, writeFlags)
    CVPixelBufferLockBaseAddress(mask.buffer, CVPixelBufferLockFlags.readOnly)

    let maskBytesPerRow = CVPixelBufferGetBytesPerRow(mask.buffer)
    var maskAddress =
      CVPixelBufferGetBaseAddress(mask.buffer)!.bindMemory(
        to: Float32.self, capacity: maskBytesPerRow * height)

    let imageBytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer)
    var imageAddress = CVPixelBufferGetBaseAddress(imageBuffer)!.bindMemory(
      to: UInt8.self, capacity: imageBytesPerRow * height)

    var redFG: CGFloat = 0.0
    var greenFG: CGFloat = 0.0
    var blueFG: CGFloat = 0.0
    var alphaFG: CGFloat = 0.0
    var redBG: CGFloat = 0.0
    var greenBG: CGFloat = 0.0
    var blueBG: CGFloat = 0.0
    var alphaBG: CGFloat = 0.0

    let backgroundColor = backgroundColor != nil ? backgroundColor : .clear
    let foregroundColor = foregroundColor != nil ? foregroundColor : .clear
    backgroundColor!.getRed(&redBG, green: &greenBG, blue: &blueBG, alpha: &alphaBG)
    foregroundColor!.getRed(&redFG, green: &greenFG, blue: &blueFG, alpha: &alphaFG)

    for _ in 0...(height - 1) {
      for col in 0...(width - 1) {
        let pixelOffset = col * Constants.bgraBytesPerPixel
        let blueOffset = pixelOffset
        let greenOffset = pixelOffset + 1
        let redOffset = pixelOffset + 2
        let alphaOffset = pixelOffset + 3

        let maskValue: CGFloat = CGFloat(maskAddress[col])
        let backgroundRegionRatio: CGFloat = 1.0 - maskValue
        let foregroundRegionRatio = maskValue

        let originalPixelRed: CGFloat =
          CGFloat(imageAddress[redOffset]) / Constants.maxColorComponentValue
        let originalPixelGreen: CGFloat =
          CGFloat(imageAddress[greenOffset]) / Constants.maxColorComponentValue
        let originalPixelBlue: CGFloat =
          CGFloat(imageAddress[blueOffset]) / Constants.maxColorComponentValue
        let originalPixelAlpha: CGFloat =
          CGFloat(imageAddress[alphaOffset]) / Constants.maxColorComponentValue

        let redOverlay = redBG * backgroundRegionRatio + redFG * foregroundRegionRatio
        let greenOverlay = greenBG * backgroundRegionRatio + greenFG * foregroundRegionRatio
        let blueOverlay = blueBG * backgroundRegionRatio + blueFG * foregroundRegionRatio
        let alphaOverlay = alphaBG * backgroundRegionRatio + alphaFG * foregroundRegionRatio

        // Calculate composite color component values.
        // Derived from https://en.wikipedia.org/wiki/Alpha_compositing#Alpha_blending
        let compositeAlpha: CGFloat = ((1.0 - alphaOverlay) * originalPixelAlpha) + alphaOverlay
        var compositeRed: CGFloat = 0.0
        var compositeGreen: CGFloat = 0.0
        var compositeBlue: CGFloat = 0.0
        // Only perform rgb blending calculations if the output alpha is > 0. A zero-value alpha
        // means none of the color channels actually matter, and would introduce division by 0.
        if abs(compositeAlpha) > CGFloat(Float.ulpOfOne) {
          compositeRed =
            (((1.0 - alphaOverlay) * originalPixelAlpha * originalPixelRed)
              + (alphaOverlay * redOverlay)) / compositeAlpha
          compositeGreen =
            (((1.0 - alphaOverlay) * originalPixelAlpha * originalPixelGreen)
              + (alphaOverlay * greenOverlay)) / compositeAlpha
          compositeBlue =
            (((1.0 - alphaOverlay) * originalPixelAlpha * originalPixelBlue)
              + (alphaOverlay * blueOverlay)) / compositeAlpha
        }

        imageAddress[redOffset] = UInt8(compositeRed * Constants.maxColorComponentValue)
        imageAddress[greenOffset] = UInt8(compositeGreen * Constants.maxColorComponentValue)
        imageAddress[blueOffset] = UInt8(compositeBlue * Constants.maxColorComponentValue)
      }

      imageAddress += imageBytesPerRow / MemoryLayout<UInt8>.size
      maskAddress += maskBytesPerRow / MemoryLayout<Float32>.size
    }

    CVPixelBufferUnlockBaseAddress(imageBuffer, writeFlags)
    CVPixelBufferUnlockBaseAddress(mask.buffer, CVPixelBufferLockFlags.readOnly)
  }

  /// Converts an image buffer to a `UIImage`.
  ///
  /// @param imageBuffer The image buffer which should be converted.
  /// @param orientation The orientation already applied to the image.
  /// @return A new `UIImage` instance.
  public static func createUIImage(
    from imageBuffer: CVImageBuffer,
    orientation: UIImage.Orientation
  ) -> UIImage? {
    let ciImage = CIImage(cvPixelBuffer: imageBuffer)
    let context = CIContext(options: nil)
    guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else { return nil }
    return UIImage(cgImage: cgImage, scale: Constants.originalScale, orientation: orientation)
  }

  /// Converts a `UIImage` to an image buffer.
  ///
  /// @param image The `UIImage` which should be converted.
  /// @return The image buffer. Callers own the returned buffer and are responsible for releasing it
  ///     when it is no longer needed. Additionally, the image orientation will not be accounted for
  ///     in the returned buffer, so callers must keep track of the orientation separately.
  public static func createImageBuffer(from image: UIImage) -> CVImageBuffer? {
    guard let cgImage = image.cgImage else { return nil }
    let width = cgImage.width
    let height = cgImage.height

    var buffer: CVPixelBuffer? = nil
    CVPixelBufferCreate(
      kCFAllocatorDefault, width, height, kCVPixelFormatType_32BGRA, nil,
      &buffer)
    guard let imageBuffer = buffer else { return nil }

    let flags = CVPixelBufferLockFlags(rawValue: 0)
    CVPixelBufferLockBaseAddress(imageBuffer, flags)
    let baseAddress = CVPixelBufferGetBaseAddress(imageBuffer)
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let bytesPerRow = CVPixelBufferGetBytesPerRow(imageBuffer)
    let context = CGContext(
      data: baseAddress, width: width, height: height, bitsPerComponent: 8,
      bytesPerRow: bytesPerRow, space: colorSpace,
      bitmapInfo: (CGImageAlphaInfo.premultipliedFirst.rawValue
        | CGBitmapInfo.byteOrder32Little.rawValue))

    if let context = context {
      let rect = CGRect.init(x: 0, y: 0, width: width, height: height)
      context.draw(cgImage, in: rect)
      CVPixelBufferUnlockBaseAddress(imageBuffer, flags)
      return imageBuffer
    } else {
      CVPixelBufferUnlockBaseAddress(imageBuffer, flags)
      return nil
    }
  }

  /// Creates a pose overlay view for visualizing a given `pose`.
  ///
  /// - Parameters:
  ///   - pose: The pose which will be visualized.
  ///   - bounds: The bounds of the view to which this overlay will be added. The overlay view's
  ///         bounds will match this value.
  ///   - lineWidth: The width of the lines connecting the landmark dots.
  ///   - dotRadius: The radius of the landmark dots.
  ///   - positionTransformationClosure: Closure which transforms a landmark `position` to the
  ///         `UIView` `CGPoint` coordinate where it should be shown on-screen.
  /// - Returns: The pose overlay view.
  public static func createPoseOverlayView(
    forPose pose: Pose, inViewWithBounds bounds: CGRect, lineWidth: CGFloat, dotRadius: CGFloat,
    positionTransformationClosure: (VisionPoint) -> CGPoint
  ) -> UIView {
    let overlayView = UIView(frame: bounds)

    let lowerBodyHeight: CGFloat =
      UIUtilities.distance(
        fromPoint: pose.landmark(ofType: PoseLandmarkType.leftAnkle).position,
        toPoint: pose.landmark(ofType: PoseLandmarkType.leftKnee).position)
      + UIUtilities.distance(
        fromPoint: pose.landmark(ofType: PoseLandmarkType.leftKnee).position,
        toPoint: pose.landmark(ofType: PoseLandmarkType.leftHip).position)

    // Pick arbitrary z extents to form a range of z values mapped to our colors. Red = close, blue
    // = far. Assume that the z values will roughly follow physical extents of the human body, but
    // apply an adjustment ratio to increase this color-coded z-range because this is not always the
    // case.
    let adjustmentRatio: CGFloat = 1.2
    let nearZExtent: CGFloat = -lowerBodyHeight * adjustmentRatio
    let farZExtent: CGFloat = lowerBodyHeight * adjustmentRatio
    let zColorRange: CGFloat = farZExtent - nearZExtent
    let nearZColor = UIColor.red
    let farZColor = UIColor.blue

    for (startLandmarkType, endLandmarkTypesArray) in UIUtilities.poseConnections() {
      let startLandmark = pose.landmark(ofType: startLandmarkType)
      for endLandmarkType in endLandmarkTypesArray {
        let endLandmark = pose.landmark(ofType: endLandmarkType)
        let startLandmarkPoint = positionTransformationClosure(startLandmark.position)
        let endLandmarkPoint = positionTransformationClosure(endLandmark.position)

        let landmarkZRatio = (startLandmark.position.z - nearZExtent) / zColorRange
        let connectedLandmarkZRatio = (endLandmark.position.z - nearZExtent) / zColorRange

        let startColor = UIUtilities.interpolatedColor(
          fromColor: nearZColor, toColor: farZColor, ratio: landmarkZRatio)
        let endColor = UIUtilities.interpolatedColor(
          fromColor: nearZColor, toColor: farZColor, ratio: connectedLandmarkZRatio)

        UIUtilities.addLineSegment(
          fromPoint: startLandmarkPoint,
          toPoint: endLandmarkPoint,
          inView: overlayView,
          colors: [startColor, endColor],
          width: lineWidth)
      }
    }
    for landmark in pose.landmarks {
      let landmarkPoint = positionTransformationClosure(landmark.position)
      UIUtilities.addCircle(
        atPoint: landmarkPoint,
        to: overlayView,
        color: UIColor.blue,
        radius: dotRadius
      )
    }
    return overlayView
  }

  /// Adds a gradient-colored line segment subview in a given `view`.
  ///
  /// - Parameters:
  ///   - fromPoint: The starting point of the line, in the view's coordinate space.
  ///   - toPoint: The end point of the line, in the view's coordinate space.
  ///   - inView: The view to which the line should be added as a subview.
  ///   - colors: The colors that the gradient should traverse over. Must be non-empty.
  ///   - width: The width of the line segment.
  private static func addLineSegment(
    fromPoint: CGPoint, toPoint: CGPoint, inView: UIView, colors: [UIColor], width: CGFloat
  ) {
    let viewWidth = inView.bounds.width
    let viewHeight = inView.bounds.height
    if viewWidth == 0.0 || viewHeight == 0.0 {
      return
    }
    let path = UIBezierPath()
    path.move(to: fromPoint)
    path.addLine(to: toPoint)
    let lineMaskLayer = CAShapeLayer()
    lineMaskLayer.path = path.cgPath
    lineMaskLayer.strokeColor = UIColor.black.cgColor
    lineMaskLayer.fillColor = nil
    lineMaskLayer.opacity = 1.0
    lineMaskLayer.lineWidth = width

    let gradientLayer = CAGradientLayer()
    gradientLayer.startPoint = CGPoint(x: fromPoint.x / viewWidth, y: fromPoint.y / viewHeight)
    gradientLayer.endPoint = CGPoint(x: toPoint.x / viewWidth, y: toPoint.y / viewHeight)
    gradientLayer.frame = inView.bounds
    var CGColors = [CGColor]()
    for color in colors {
      CGColors.append(color.cgColor)
    }
    if CGColors.count == 1 {
      // Single-colored lines must still supply a start and end color for the gradient layer to
      // render anything. Just add the single color to the colors list again to fulfill this
      // requirement.
      CGColors.append(colors[0].cgColor)
    }
    gradientLayer.colors = CGColors
    gradientLayer.mask = lineMaskLayer

    let lineView = UIView(frame: inView.bounds)
    lineView.layer.addSublayer(gradientLayer)
    inView.addSubview(lineView)
  }

  /// Returns a color interpolated between to other colors.
  ///
  /// - Parameters:
  ///   - fromColor: The start color of the interpolation.
  ///   - toColor: The end color of the interpolation.
  ///   - ratio: The ratio in range [0, 1] by which the colors should be interpolated. Passing 0
  ///         results in `fromColor` and passing 1 results in `toColor`, whereas passing 0.5 results
  ///         in a color that is half-way between `fromColor` and `startColor`. Values are clamped
  ///         between 0 and 1.
  /// - Returns: The interpolated color.
  private static func interpolatedColor(
    fromColor: UIColor, toColor: UIColor, ratio: CGFloat
  ) -> UIColor {
    var fromR: CGFloat = 0
    var fromG: CGFloat = 0
    var fromB: CGFloat = 0
    var fromA: CGFloat = 0
    fromColor.getRed(&fromR, green: &fromG, blue: &fromB, alpha: &fromA)

    var toR: CGFloat = 0
    var toG: CGFloat = 0
    var toB: CGFloat = 0
    var toA: CGFloat = 0
    toColor.getRed(&toR, green: &toG, blue: &toB, alpha: &toA)

    let clampedRatio = max(0.0, min(ratio, 1.0))

    let interpolatedR = fromR + (toR - fromR) * clampedRatio
    let interpolatedG = fromG + (toG - fromG) * clampedRatio
    let interpolatedB = fromB + (toB - fromB) * clampedRatio
    let interpolatedA = fromA + (toA - fromA) * clampedRatio

    return UIColor(
      red: interpolatedR, green: interpolatedG, blue: interpolatedB, alpha: interpolatedA)
  }

  /// Returns the distance between two 3D points.
  ///
  /// - Parameters:
  ///   - fromPoint: The starting point.
  ///   - endPoint: The end point.
  /// - Returns: The distance.
  private static func distance(fromPoint: Vision3DPoint, toPoint: Vision3DPoint) -> CGFloat {
    let xDiff = fromPoint.x - toPoint.x
    let yDiff = fromPoint.y - toPoint.y
    let zDiff = fromPoint.z - toPoint.z
    return CGFloat(sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff))
  }

  // MARK: - Private

  /// Returns the minimum subset of all connected pose landmarks. Each key represents a start
  /// landmark, and each value in the key's value array represents an end landmark which is
  /// connected to the start landmark. These connections may be used for visualizing the landmark
  /// positions on a pose object.
  private static func poseConnections() -> [PoseLandmarkType: [PoseLandmarkType]] {
    struct PoseConnectionsHolder {
      static var connections: [PoseLandmarkType: [PoseLandmarkType]] = [
        PoseLandmarkType.leftEar: [PoseLandmarkType.leftEyeOuter],
        PoseLandmarkType.leftEyeOuter: [PoseLandmarkType.leftEye],
        PoseLandmarkType.leftEye: [PoseLandmarkType.leftEyeInner],
        PoseLandmarkType.leftEyeInner: [PoseLandmarkType.nose],
        PoseLandmarkType.nose: [PoseLandmarkType.rightEyeInner],
        PoseLandmarkType.rightEyeInner: [PoseLandmarkType.rightEye],
        PoseLandmarkType.rightEye: [PoseLandmarkType.rightEyeOuter],
        PoseLandmarkType.rightEyeOuter: [PoseLandmarkType.rightEar],
        PoseLandmarkType.mouthLeft: [PoseLandmarkType.mouthRight],
        PoseLandmarkType.leftShoulder: [
          PoseLandmarkType.rightShoulder,
          PoseLandmarkType.leftHip,
        ],
        PoseLandmarkType.rightShoulder: [
          PoseLandmarkType.rightHip,
          PoseLandmarkType.rightElbow,
        ],
        PoseLandmarkType.rightWrist: [
          PoseLandmarkType.rightElbow,
          PoseLandmarkType.rightThumb,
          PoseLandmarkType.rightIndexFinger,
          PoseLandmarkType.rightPinkyFinger,
        ],
        PoseLandmarkType.leftHip: [PoseLandmarkType.rightHip, PoseLandmarkType.leftKnee],
        PoseLandmarkType.rightHip: [PoseLandmarkType.rightKnee],
        PoseLandmarkType.rightKnee: [PoseLandmarkType.rightAnkle],
        PoseLandmarkType.leftKnee: [PoseLandmarkType.leftAnkle],
        PoseLandmarkType.leftElbow: [PoseLandmarkType.leftShoulder],
        PoseLandmarkType.leftWrist: [
          PoseLandmarkType.leftElbow, PoseLandmarkType.leftThumb,
          PoseLandmarkType.leftIndexFinger,
          PoseLandmarkType.leftPinkyFinger,
        ],
        PoseLandmarkType.leftAnkle: [PoseLandmarkType.leftHeel, PoseLandmarkType.leftToe],
        PoseLandmarkType.rightAnkle: [PoseLandmarkType.rightHeel, PoseLandmarkType.rightToe],
        PoseLandmarkType.rightHeel: [PoseLandmarkType.rightToe],
        PoseLandmarkType.leftHeel: [PoseLandmarkType.leftToe],
        PoseLandmarkType.rightIndexFinger: [PoseLandmarkType.rightPinkyFinger],
        PoseLandmarkType.leftIndexFinger: [PoseLandmarkType.leftPinkyFinger],
      ]
    }
    return PoseConnectionsHolder.connections
  }

  private static func currentUIOrientation() -> UIDeviceOrientation {
    let deviceOrientation = { () -> UIDeviceOrientation in
      switch UIApplication.shared.statusBarOrientation {
      case .landscapeLeft:
        return .landscapeRight
      case .landscapeRight:
        return .landscapeLeft
      case .portraitUpsideDown:
        return .portraitUpsideDown
      case .portrait, .unknown:
        return .portrait
      @unknown default:
        fatalError()
      }
    }
    guard Thread.isMainThread else {
      var currentOrientation: UIDeviceOrientation = .portrait
      DispatchQueue.main.sync {
        currentOrientation = deviceOrientation()
      }
      return currentOrientation
    }
    return deviceOrientation()
  }
}

// MARK: - Constants

private enum Constants {
  static let circleViewAlpha: CGFloat = 0.7
  static let rectangleViewAlpha: CGFloat = 0.3
  static let shapeViewAlpha: CGFloat = 0.3
  static let rectangleViewCornerRadius: CGFloat = 10.0
  static let maxColorComponentValue: CGFloat = 255.0
  static let originalScale: CGFloat = 1.0
  static let bgraBytesPerPixel = 4
}

// MARK: - Extension

extension CGRect {
  /// Returns a `Bool` indicating whether the rectangle's values are valid`.
  func isValid() -> Bool {
    return
      !(origin.x.isNaN || origin.y.isNaN || width.isNaN || height.isNaN || width < 0 || height < 0)
  }
}
