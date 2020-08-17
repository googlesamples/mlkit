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
    guard !rectangle.isNaN() else { return }
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

  /// Returns the minimum subset of all connected pose landmarks. Each key represents a start
  /// landmark, and each value in the key's value array represents an end landmark which is
  /// connected to the start landmark. These connections may be used for visualizing the landmark
  /// positions on a pose object.
  public static func poseConnections() -> [PoseLandmarkType: [PoseLandmarkType]] {
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

  // MARK: - Private

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
}

// MARK: - Extension

extension CGRect {
  /// Returns a `Bool` indicating whether the rectangle has any value that is `NaN`.
  func isNaN()  -> Bool {
    return origin.x.isNaN || origin.y.isNaN || width.isNaN || height.isNaN
  }
}
