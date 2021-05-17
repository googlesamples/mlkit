# ML Kit Showcase Apps with Material Design

These apps demonstrate how to build an end-to-end user experience with [Google ML Kit APIs](https://developers.google.com/ml-kit) and following the new [Material for ML design guidelines](https://material.io/design/machine-learning/).

The goal is to make it as easy as possible to integrate ML Kit into your app with an experience that has been user tested.

## Apps

You can open each of the following apps as an Xcode project, and run
them on a mobile device or a simulator. Simply install the pods and open
the .xcworkspace file to see the project in Xcode.

```
$ pod install --repo-update
$ open your-project.xcworkspace
```

- Object Detection and Tracking:
  Search using the Object Detection & Tracking API - a complete workflow from object detection to product search in live camera.
![live_odt](screenshots/live_odt.gif)

- Object detection and Tracking with custom TensorFlow Lite model:
  Search using a custom TensorFlow Lite model (Example used: Bird recognition model) to detect and recognize objects with a live camera view. This shows how to implement a visual search flow with a custom TensorFlow Lite model.

## License
© Google, 2020. Licensed under an [Apache-2](./LICENSE) license.
