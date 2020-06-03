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


import Foundation
import AVFoundation
import MLKitVision

class ImagePreparation {
    func createImage(uiImage: UIImage) -> VisionImage {
        // [START create_image_with_uiimage]
        let image = VisionImage(image: uiImage)
        // [END create_image_with_uiimage]
        return image
    }

    // [START image_orientation_from_device_orientation]
    func imageOrientation(
        deviceOrientation: UIDeviceOrientation,
        cameraPosition: AVCaptureDevice.Position
        ) -> VisionImageOrientation {
        switch deviceOrientation {
        case .portrait:
            return cameraPosition == .front ? .leftTop : .rightTop
        case .landscapeLeft:
            return cameraPosition == .front ? .bottomLeft : .topLeft
        case .portraitUpsideDown:
            return cameraPosition == .front ? .rightBottom : .leftBottom
        case .landscapeRight:
            return cameraPosition == .front ? .topRight : .bottomRight
        case .faceDown, .faceUp, .unknown:
            return .leftTop
        }
    }
    // [END image_orientation_from_device_orientation]

    func createImage(sampleBuffer: CMSampleBuffer) -> VisionImage {
        // [START create_image_metadata]
        let cameraPosition = AVCaptureDevice.Position.back  // Set to the capture device you used.
        let metadata = VisionImageMetadata()
        metadata.orientation = imageOrientation(
            deviceOrientation: UIDevice.current.orientation,
            cameraPosition: cameraPosition
        )
        // [END create_image_metadata]

        // [START create_image_with_buffer]
        let image = VisionImage(buffer: sampleBuffer)
        image.metadata = metadata
        // [END create_image_with_buffer]

        return image
    }
}
