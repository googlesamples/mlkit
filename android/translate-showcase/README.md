# ML Kit Translate Showcase App with Material Design

The ML Kit Translate Showcase app demonstrates how to build an end-to-end user experience with [Google ML Kit APIs](https://developers.google.com/ml-kit/guides) and following the new [Material for ML design guidelines](https://material.io/design/machine-learning/).

The goal of this app is to showcase an ideal ML Kit driven end to end solution for various ML Kit Natural Language Processing APIs and use cases. The following use cases are covered:

* **Real-time translation using on-device Text Recognition, Language ID, Translate APIs** - An end-to-end solution from text recognition to translate via the live camera.

![live_translate](./demo.gif)


## Steps to run the app

1. Clone this repo locally
5. Build and run it on a physical device (the simulator isn't recommended, as the app needs to use the camera on the device).

## How to use the app

This app demonstrates live text translate using the camera:
* Open the app and point the bounding box of the camera at any text of interest. The recognized text and it's detected language will show up on the upper part of the bottom sheet.
* As you recognize text within the bounding box, you'll see the translated version of this text appear on the bottom in real-time using the ML Kit on-device Translate API.
* You can also switch the language you’d like to translate to using the drop down menu located in the center of the bottom sheet.

## License
© Google, 2020. Licensed under an [Apache-2](./LICENSE) license.

