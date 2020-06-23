# ML Kit Translate Showcase App with Material Design

This app demonstrates how to build an end-to-end user experience with [Google ML Kit APIs](https://developers.google.com/ml-kit/guides) and following the new [Material for ML design guidelines](https://material.io/collections/machine-learning/).

The goal of this app is to showcase an ideal ML Kit driven end to end solution for various ML Kit Natural Language Processing APIs and use cases. The following use cases are covered:
* Real-time translation using on-device Text Recognition, Language ID, Translate APIs - An end-to-end solution from text recognition to translate via the live camera.

![live_translate](./translate.gif)


## How to setup this app

1. Clone this repo locally
  ```
  git clone https://github.com/googlecodelabs/mlkit-ios
  ```
2. Find a `Podfile` in the folder and install all the dependency pods by running the following command:
  ```
  cd mlkit-ios
  cd translate
  pod cache clean --all
  pod install --repo-update
  ```
3. Open the generated `TranslateDemo.xcworkspace` file.
4. [Create a Firebase project in the Firebase console](https://firebase.google.com/docs/ios/setup),if you don't already have one.
5. Add a new iOS app into your Firebase project with a bundle ID like ***com.google.firebase.ml.md***.
6. Download `GoogleService-Info.plist` from the newly added app and add it to the
  ShowcaseApp project in Xcode. Remember to check `Copy items if needed` and
  select `Create folder references`.
7. Select the project in Xcode and uncheck `Automatically manage signing` option in
  `General` tab, and choose your own provisioning file.
8. Build and run it on a physical device (the simulator isn't recommended, as the app needs to use the camera on the device).

## How to use the app

This app demonstrates live text translate using the camera:
* Open the app and point the bounding box of the camera at any text of interest. The recognized text and it's detected language will show up on the upper part of the bottom sheet.
* As text is recognized within the bounding box, you'll see the translated version of this text appear on the bottom in real-time using the ML Kit on-device Translate API.
* You can also switch the language you’d like to translate to using the chips below, or clicking the "More" chip to search & select any of the 59 languages available.

## License
© Google, 2019. Licensed under an [Apache-2](./LICENSE) license.
