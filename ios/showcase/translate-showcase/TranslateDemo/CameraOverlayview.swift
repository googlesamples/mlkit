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

import MaterialComponents

private let kBoxBorderWidth: CGFloat = 2.0
private let kLightBoxBorderAlpha: CGFloat = 0.5
private let kBoxCornerRadius: CGFloat = 12.0
private let kChipBackgroundAlpha: CGFloat = 0.6
private let kChipCornerRadius: CGFloat = 8.0
private let kChipFadeInDuration: CGFloat = 0.075
private let kChipScaleDuration: CGFloat = 0.15
private let kChipScaleFromRatio: CGFloat = 0.8
private let kChipScaleToRatio: CGFloat = 1.0
private let kChipBottomPadding: CGFloat = 36.0
private let kBoxBackgroundAlpha: CGFloat = 0.40

class CameraOverlayView: UIView {
  private var boxLayer: CAShapeLayer!
  private var boxMaskLayer: CAShapeLayer!
  private var messageChip = MDCChipView()

  override init(frame: CGRect) {
    super.init(frame: frame)

    boxMaskLayer = CAShapeLayer()
    layer.addSublayer(boxMaskLayer)

    boxLayer = CAShapeLayer()
    boxLayer.cornerRadius = kBoxCornerRadius
    layer.addSublayer(boxLayer)

    messageChip.setBackgroundColor(
      UIColor.black.withAlphaComponent(kChipBackgroundAlpha), for: .normal)
    messageChip.clipsToBounds = true
    messageChip.titleLabel.textColor = UIColor.white
    messageChip.layer.cornerRadius = kChipCornerRadius
    addSubview(messageChip)
  }

  required init?(coder aDecoder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  func showBox(in rect: CGRect) {
    let maskPath = UIBezierPath(rect: self.bounds)
    let boxPath = UIBezierPath(roundedRect: rect, cornerRadius: kBoxCornerRadius).reversing()
    maskPath.append(boxPath)
    boxMaskLayer.frame = self.frame
    boxMaskLayer.path = maskPath.cgPath
    boxMaskLayer.strokeStart = 0.0
    boxMaskLayer.strokeEnd = 1.0
    self.layer.backgroundColor = UIColor.black.withAlphaComponent(kBoxBackgroundAlpha).cgColor

    self.layer.mask = boxMaskLayer
    boxLayer.path = UIBezierPath(roundedRect: rect, cornerRadius: kBoxCornerRadius).cgPath
    boxLayer.lineWidth = kBoxBorderWidth
    boxLayer.strokeStart = 0.0
    boxLayer.strokeEnd = 1.0
    boxLayer.strokeColor = UIColor.white.cgColor
    boxLayer.fillColor = nil
  }

  func showMessage(_ message: String?, in center: CGPoint) {
    if messageChip.titleLabel.text?.isEqual(message) ?? false {
      return
    }
    messageChip.titleLabel.text = message
    messageChip.sizeToFit()
    self.messageChip.center = center

  }

  func clear() {
    boxLayer?.isHidden = true
    boxLayer?.removeFromSuperlayer()
  }

}
