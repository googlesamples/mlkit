[![Build Status](https://travis-ci.org/firebase/mlkit-material-android.svg?branch=master)](https://travis-ci.org/firebase/mlkit-material-android)

# ML Kit Showcase App with Material Design

This app demonstrates how to build an end-to-end user experience with 
[Google ML Kit APIs](https://developers.google.com/ml-kit) and following the 
[new Material for ML design guidelines](https://material.io/collections/machine-learning/).

The goal is to make it as easy as possible to integrate ML Kit into your app with an experience 
that has been user tested for the specific use cases that are covered:

* Visual search using the Object Detection & Tracking API - a complete workflow from object
  detection to product search in live camera and static image
* Barcode detection using the Barcode API in live camera

<img src="screenshots/live_odt.gif" width="256"/> <img src="screenshots/static_odt.gif" width="256"/>
<img src="screenshots/live_barcode.gif" width="256"/>

## Steps to run the app

* Clone this repo locally
* Build and run it on an Android device

## How to use the app

This app supports two usage scenarios: Live Camera and Static Image.

### Live Camera scenario

It uses the camera preview as input and contains three workflow: object detection & visual search,
object detection & custom classification, and barcode detection. There's also a Settings page to
allow you to configure several options:
- Camera
  - Specify the preview size of rear camera manually (Default size is chose appropriately based on screen size)
- Object detection
  - Whether or not to enable multiple objects and coarse classification
- Product search
  - Whether or not to enable auto search: if enabled, search request will be fired automatically 
    once object is detected and confirmed, otherwise a search button will appear to trigger search manually
  - Required time that the auto-detected object needs to be focused for being regarded as user-confirmed
- Barcode detection
  - Barcode aiming frame size
  - Barcode size check: will prompt "Move closer" if the current detected barcode size is not big enough
  - Delay loading result: to simulate the case where the detected barcode requires further 
    processing before displaying result.

### Static Image scenario

It'll prompt to select an image from the Image Picker, detect objects in the picked image, 
and then perform visual search on them. There're well designed UI components (overlay dots, 
card carousel etc.) to indicate the detected objects and search results.

**Note** that the visual search functionality here is mock since no real search backend has set up 
for this repository, but it should be easy to hook up with your own search service 
(e.g. [Product Search](https://cloud.google.com/vision/product-search/docs)) by only replacing the 
[SearchEngine](./app/src/main/java/com/google/mlkit/md/productsearch/SearchEngine.kt) class implementation.

## License
© Google, 2020. Licensed under an [Apache-2](./LICENSE) license.
