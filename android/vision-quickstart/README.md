# ML Kit Vision Quickstart Sample App

## Introduction

This ML Kit Quickstart app demonstrates how to use and integrate various vision based ML Kit features into your app.

## Feature List

Features that are included in this Quickstart app:
* [Object Detection](https://developers.google.com/ml-kit/vision/object-detection/android) - Detect, track, and classify objects in real time and static images.
* [Face Detection](https://developers.google.com/ml-kit/vision/face-detection/android) - Detect faces in real time and static images.
* [Face Mesh Detection](https://developers.google.com/ml-kit/vision/face-mesh-detection/android) - Detect face mesh in real time and static images.
* [Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition/android) - Recognize text in real time and static images.
* [Barcode Scanning](https://developers.google.com/ml-kit/vision/barcode-scanning/android)  - Scan barcodes in real time and static images.
* [Image Labeling](https://developers.google.com/ml-kit/vision/image-labeling/android) - Label images in real time and static images.
* [Custom Image Labeling - Birds](https://developers.google.com/ml-kit/vision/image-labeling/custom-models/android) - Label images of birds with a custom TensorFlow Lite model.
* [Pose Detection](https://developers.google.com/ml-kit/vision/pose-detection/android) - Detect the position of the human body in real time.
* [Selfie Segmentation](https://developers.google.com/ml-kit/vision/selfie-segmentation/android) - Segment people from the background in real time.
* [Subject Segmentation](https://developers.google.com/ml-kit/vision/subject-segmentation/android) - Segment multiple subjects from the background in static images.

<img src="../screenshots/quickstart-picker.png" width="220"/> <img src="../screenshots/quickstart-image-labeling.png" width="220"/> <img src="../screenshots/quickstart-object-detection.png" width="220"/> <img src="../screenshots/quickstart-pose-detection.png" width="220"/>

## Getting Started

* Run the sample code on your Android device or emulator
* Try extending the code to add new features and functionality

## How to use the app

This app supports three usage scenarios: Live Camera, Static Image, and CameraX enabled live camera.

### Live Camera scenario
It uses the camera preview as input and contains these API workflows: Object detection & tracking, Face Detection, Face Mesh Detection, Text Recognition, Barcode Scanning, Image Labeling, and Pose Detection. There's also a settings page that allows you to configure several options:
* Camera
    * Preview size - Specify the preview size of rear/front camera manually (Default size is chosen appropriately based on screen size)
    * Enable live viewport - Toggle between blocking camera preview by API processing and result rendering or not
* Object detection / Custom Object Detection
    * Enable multiple objects -- Enable multiple objects to be detected at once
    * Enable classification -- Enable classification for each detected object
* Face Detection
    * Landmark mode -- Toggle between showing no or all facial landmarks
    * Contour mode -- Toggle between showing no or all contours
    * Classification mode -- Toggle between showing no or all classifications (smiling, eyes open/closed)
    * Performance mode -- Toggle between two operating modes (Fast or Accurate)
    * Face tracking -- Enable or disable face tracking
    * Minimum face size -- Choose the proportion of the head width to the image width
* Face Mesh Detection
    * Use Case -- Selects from `Bounding Box only` and `Face Mesh`
* Pose Detection
    * Performance mode -- Allows you to switch between "Fast" and "Accurate" operation mode
    * Show in-frame likelihood -- Displays InFrameLikelihood score for each landmark
    * Visualize z value -- Uses different colors to indicate z difference (red: smaller z, blue: larger z)
    * Rescale z value for visualization -- Maps the smallest z value to the most red and the largest z value to the most blue. This makes z difference more obvious
    * Run classification -- Classify squat and pushup poses. Count reps in streaming mode.
* Selfie Segmentation
    * Enable raw size mask -- Asks the segmenter to return the raw size mask which matches the model output size.

### Static Image scenario
The static image scenario is identical to the live camera scenario, but instead relies on images fed into the app through the gallery.

### CameraX Live Preview scenario
The CameraX live preview scenario is very similar to the native live camera scenario, but instead relies on CameraX live preview. Note: CameraX is only supported on API level 21+.

## Support

* [Documentation](https://developers.google.com/ml-kit/guides)
* [API Reference](https://developers.google.com/ml-kit/reference/android)
* [Stack Overflow](https://stackoverflow.com/questions/tagged/google-mlkit)

## License

Copyright 2020 Google, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
