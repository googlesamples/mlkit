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

#import "ViewController.h"
#import "UIImage+VisionDetection.h"
#import "UIUtilities.h"

@import MLKit;

NS_ASSUME_NONNULL_BEGIN

static NSArray *images;
static NSString *const ModelExtension = @"tflite";
static NSString *const quantizedModelFilename = @"mobilenet_quant_v1_224";

static NSString *const detectionNoResultsMessage = @"No results returned.";

/** Name of the local AutoML model. */
static NSString *const MLKLocalAutoMLModelName = @"local_automl_model";

/** Name of the remote AutoML model. */
static NSString *const MLKRemoteAutoMLModelName = @"remote_automl_model";

/** Filename of AutoML local model manifest in the main resource bundle. */
static NSString *const MLKAutoMLLocalModelManifestFilename = @"automl_labeler_manifest";

/** File type of AutoML local model manifest in the main resource bundle. */
static NSString *const MLKAutoMLManifestFileType = @"json";

static float const labelConfidenceThreshold = 0.75f;
static CGColorRef lineColor;
static CGColorRef fillColor;

static int const rowsCount = 5;
static int const componentsCount = 1;

/**
 * @enum DetectorPickerRow
 * Defines the MLKit detector types.
 */
typedef NS_ENUM(NSInteger, DetectorPickerRow) {
  /** AutoML image label detector. */
  DetectorPickerRowDetectImageLabelsAutoML,
  /** AutoML object detector, single, only tracking. */
  DetectorPickerRowDetectObjectsAutoMLSingleNoClassifier,
  /** AutoML object detector, single, with classification. */
  DetectorPickerRowDetectObjectsAutoMLSingleWithClassifier,
  /** AutoML object detector, multiple, only tracking. */
  DetectorPickerRowDetectObjectsAutoMLMultipleNoClassifier,
  /** AutoML object detector, multiple, with classification. */
  DetectorPickerRowDetectObjectsAutoMLMultipleWithClassifier,
};

@interface ViewController () <UINavigationControllerDelegate,
                              UIPickerViewDelegate,
                              UIPickerViewDataSource,
                              UIImagePickerControllerDelegate>

@property(nonatomic) MLKModelManager *modelManager;

/** A string holding current results from detection. */
@property(nonatomic) NSMutableString *resultsText;

/** An overlay view that displays detection annotations. */
@property(nonatomic) UIView *annotationOverlayView;

/** An image picker for accessing the photo library or camera. */
@property(nonatomic) UIImagePickerController *imagePicker;
@property(weak, nonatomic) IBOutlet UIBarButtonItem *detectButton;
@property(strong, nonatomic) IBOutlet UIProgressView *downloadProgressView;

// Image counter.
@property(nonatomic) NSUInteger currentImage;

@property(weak, nonatomic) IBOutlet UIPickerView *detectorPicker;
@property(weak, nonatomic) IBOutlet UIImageView *imageView;
@property(weak, nonatomic) IBOutlet UIBarButtonItem *photoCameraButton;
@property(weak, nonatomic) IBOutlet UIBarButtonItem *videoCameraButton;
@property(weak, nonatomic) IBOutlet UIBarButtonItem *downloadOrDeleteModelButton;

@end

@implementation ViewController

- (NSString *)stringForDetectorPickerRow:(DetectorPickerRow)detectorPickerRow {
  switch (detectorPickerRow) {
    case DetectorPickerRowDetectImageLabelsAutoML:
      return @"AutoML Image Labeling";
    case DetectorPickerRowDetectObjectsAutoMLSingleNoClassifier:
      return @"AutoML ODT, single, no labeling";
    case DetectorPickerRowDetectObjectsAutoMLSingleWithClassifier:
      return @"AutoML ODT, single, labeling";
    case DetectorPickerRowDetectObjectsAutoMLMultipleNoClassifier:
      return @"AutoML ODT, multiple, no labeling";
    case DetectorPickerRowDetectObjectsAutoMLMultipleWithClassifier:
      return @"AutoML ODT, multiple, labeling";
  }
}

- (void)viewDidLoad {
  [super viewDidLoad];

  images = @[
    @"dandelion.jpg",
    @"sunflower.jpg",
    @"tulips.jpeg",
    @"daisy.jpeg",
    @"roses.jpeg",
  ];
  lineColor = UIColor.yellowColor.CGColor;
  fillColor = UIColor.clearColor.CGColor;

  self.modelManager = [MLKModelManager modelManager];
  MLKRemoteModel *remoteModel = [self remoteModel];
  NSString *buttonImage =
      [self.modelManager isModelDownloaded:remoteModel] ? @"delete" : @"cloud_download";
  self.downloadOrDeleteModelButton.image = [UIImage imageNamed:buttonImage];

  self.imagePicker = [UIImagePickerController new];
  self.resultsText = [NSMutableString new];
  self.currentImage = 0;
  self.imageView.image = [UIImage imageNamed:images[self.currentImage]];
  self.annotationOverlayView = [[UIView alloc] initWithFrame:CGRectZero];
  self.annotationOverlayView.translatesAutoresizingMaskIntoConstraints = NO;
  [self.imageView addSubview:self.annotationOverlayView];
  [NSLayoutConstraint activateConstraints:@[
    [self.annotationOverlayView.topAnchor constraintEqualToAnchor:self.imageView.topAnchor],
    [self.annotationOverlayView.leadingAnchor constraintEqualToAnchor:self.imageView.leadingAnchor],
    [self.annotationOverlayView.trailingAnchor
        constraintEqualToAnchor:self.imageView.trailingAnchor],
    [self.annotationOverlayView.bottomAnchor constraintEqualToAnchor:self.imageView.bottomAnchor]
  ]];
  self.imagePicker.delegate = self;
  self.imagePicker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;

  self.detectorPicker.delegate = self;
  self.detectorPicker.dataSource = self;

  BOOL isCameraAvailable =
      [UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceFront] ||
      [UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceRear];
  if (isCameraAvailable) {
    // `CameraViewController` uses `AVCaptureDeviceDiscoverySession` which is only supported for
    // iOS 10 or newer.
    if (@available(iOS 10, *)) {
      [self.videoCameraButton setEnabled:YES];
    }
  } else {
    [self.photoCameraButton setEnabled:NO];
  }
  [self.detectorPicker selectRow:0 inComponent:0 animated:NO];
}

- (void)viewWillAppear:(BOOL)animated {
  [super viewWillAppear:animated];
  [self.navigationController.navigationBar setHidden:YES];
  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(remoteModelDownloadDidSucceed:)
                                             name:MLKModelDownloadDidSucceedNotification
                                           object:nil];
  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(remoteModelDownloadDidFail:)
                                             name:MLKModelDownloadDidFailNotification
                                           object:nil];
}

- (void)viewWillDisappear:(BOOL)animated {
  [super viewWillDisappear:animated];
  [self.navigationController.navigationBar setHidden:NO];
  // We wouldn't have needed to remove the observers if iOS 9.0+ had cleaned up the observer "the
  // next time it would have posted to it" as documented here:
  // https://developer.apple.com/documentation/foundation/nsnotificationcenter/1413994-removeobserver
  [NSNotificationCenter.defaultCenter removeObserver:self
                                                name:MLKModelDownloadDidSucceedNotification
                                              object:nil];
  [NSNotificationCenter.defaultCenter removeObserver:self
                                                name:MLKModelDownloadDidFailNotification
                                              object:nil];
}

- (IBAction)detect:(id)sender {
  [self clearResults];
  NSInteger rowIndex = [self.detectorPicker selectedRowInComponent:0];
  BOOL shouldEnableClassification =
      rowIndex == DetectorPickerRowDetectObjectsAutoMLSingleWithClassifier ||
      rowIndex == DetectorPickerRowDetectObjectsAutoMLMultipleWithClassifier;
  BOOL shouldEnableMultipleObjects =
      rowIndex == DetectorPickerRowDetectObjectsAutoMLMultipleNoClassifier ||
      rowIndex == DetectorPickerRowDetectObjectsAutoMLMultipleWithClassifier;
  switch (rowIndex) {
    case DetectorPickerRowDetectImageLabelsAutoML:
      [self detectImageLabelsInImage:self.imageView.image];
      break;
    case DetectorPickerRowDetectObjectsAutoMLSingleNoClassifier:
    case DetectorPickerRowDetectObjectsAutoMLSingleWithClassifier:
    case DetectorPickerRowDetectObjectsAutoMLMultipleNoClassifier:
    case DetectorPickerRowDetectObjectsAutoMLMultipleWithClassifier:
      [self detectObjectsInImage:self.imageView.image
           shouldEnableClassification:shouldEnableClassification
          shouldEnableMultipleObjects:shouldEnableMultipleObjects];
      break;
  }
}

- (IBAction)openPhotoLibrary:(id)sender {
  self.imagePicker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
  [self presentViewController:self.imagePicker animated:YES completion:nil];
}

- (IBAction)openCamera:(id)sender {
  if (![UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceFront] &&
      ![UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceRear]) {
    return;
  }
  self.imagePicker.sourceType = UIImagePickerControllerSourceTypeCamera;
  [self presentViewController:self.imagePicker animated:YES completion:nil];
}

- (IBAction)changeImage:(id)sender {
  [self clearResults];
  self.currentImage = (self.currentImage + 1) % images.count;
  self.imageView.image = [UIImage imageNamed:images[self.currentImage]];
}

- (IBAction)downloadOrDeleteModel:(id)sender {
  [self clearResults];
  MLKRemoteModel *remoteModel = [self remoteModel];
  if ([self.modelManager isModelDownloaded:remoteModel]) {
    __weak typeof(self) weakSelf = self;
    [self.modelManager
        deleteDownloadedModel:remoteModel
                   completion:^(NSError *_Nullable error) {
                     if (error) {
                       NSLog(@"Failed to delete the AutoML model.");
                       return;
                     }
                     __strong typeof(weakSelf) strongSelf = weakSelf;
                     NSLog(@"The downloaded remote model has been successfully deleted.");
                     strongSelf.downloadOrDeleteModelButton.image =
                         [UIImage imageNamed:@"cloud_download"];
                   }];
  } else {
    [self requestAutoMLRemoteModelIfNeeded];
  }
}

#pragma mark - Private

- (MLKRemoteModel *)remoteModel {
  MLKFirebaseModelSource *firebaseModelSource =
      [[MLKFirebaseModelSource alloc] initWithName:MLKRemoteAutoMLModelName];
  return [[MLKCustomRemoteModel alloc] initWithRemoteModelSource:firebaseModelSource];
}

/** Removes the detection annotations from the annotation overlay view. */
- (void)removeDetectionAnnotations {
  for (UIView *annotationView in self.annotationOverlayView.subviews) {
    [annotationView removeFromSuperview];
  }
}

/** Clears the results text view and removes any frames that are visible. */
- (void)clearResults {
  [self removeDetectionAnnotations];
  self.resultsText = [NSMutableString new];
}

- (void)showResults {
  UIAlertController *resultsAlertController =
      [UIAlertController alertControllerWithTitle:@"Detection Results"
                                          message:nil
                                   preferredStyle:UIAlertControllerStyleActionSheet];
  [resultsAlertController
      addAction:[UIAlertAction actionWithTitle:@"OK"
                                         style:UIAlertActionStyleDestructive
                                       handler:^(UIAlertAction *_Nonnull action) {
                                         [resultsAlertController dismissViewControllerAnimated:YES
                                                                                    completion:nil];
                                       }]];
  resultsAlertController.message = self.resultsText;
  resultsAlertController.popoverPresentationController.barButtonItem = self.detectButton;
  resultsAlertController.popoverPresentationController.sourceView = self.view;
  [self presentViewController:resultsAlertController animated:YES completion:nil];
  NSLog(@"%@", self.resultsText);
}

/**
 * Updates the image view with a scaled version of the given image.
 *
 * @param image The image to scale and use for updating the image view.
 */
- (void)updateImageViewWithImage:(UIImage *)image {
  CGFloat scaledImageWidth = 0.0;
  CGFloat scaledImageHeight = 0.0;
  switch (UIApplication.sharedApplication.statusBarOrientation) {
    case UIInterfaceOrientationPortrait:
    case UIInterfaceOrientationPortraitUpsideDown:
    case UIInterfaceOrientationUnknown:
      scaledImageWidth = self.imageView.bounds.size.width;
      scaledImageHeight = image.size.height * scaledImageWidth / image.size.width;
      break;
    case UIInterfaceOrientationLandscapeLeft:
    case UIInterfaceOrientationLandscapeRight:
      scaledImageWidth = image.size.width * scaledImageHeight / image.size.height;
      scaledImageHeight = self.imageView.bounds.size.height;
      break;
  }

  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED, 0), ^{
    // Scale image while maintaining aspect ratio so it displays better in the UIImageView.
    UIImage *scaledImage =
        [image scaledImageWithSize:CGSizeMake(scaledImageWidth, scaledImageHeight)];
    if (!scaledImage) {
      scaledImage = image;
    }
    if (!scaledImage) {
      return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
      __strong typeof(weakSelf) strongSelf = weakSelf;
      strongSelf.imageView.image = scaledImage;
    });
  });
}

#pragma mark - UIPickerViewDataSource

- (NSInteger)numberOfComponentsInPickerView:(nonnull UIPickerView *)pickerView {
  return componentsCount;
}

- (NSInteger)pickerView:(nonnull UIPickerView *)pickerView
    numberOfRowsInComponent:(NSInteger)component {
  return rowsCount;
}

#pragma mark - UIPickerViewDelegate

- (nullable NSString *)pickerView:(UIPickerView *)pickerView
                      titleForRow:(NSInteger)row
                     forComponent:(NSInteger)component {
  return [self stringForDetectorPickerRow:row];
}

- (void)pickerView:(UIPickerView *)pickerView
      didSelectRow:(NSInteger)row
       inComponent:(NSInteger)component {
  [self clearResults];
}

#pragma mark - UIImagePickerControllerDelegate

- (void)imagePickerController:(UIImagePickerController *)picker
    didFinishPickingMediaWithInfo:(NSDictionary<NSString *, id> *)info {
  [self clearResults];
  UIImage *pickedImage = info[UIImagePickerControllerOriginalImage];
  if (pickedImage) {
    [self updateImageViewWithImage:pickedImage];
  }
  [self dismissViewControllerAnimated:YES completion:nil];
}

#pragma mark - AutoML Detections

/**
 * Detects labels on the specified image using image classification models trained by AutoML via
 * Custom Image Labeling API.
 *
 * @param image The input image.
 */
- (void)detectImageLabelsInImage:(UIImage *)image {
  if (!image) {
    return;
  }
  [self requestAutoMLRemoteModelIfNeeded];

  // [START config_automl_label]
  MLKCommonImageLabelerOptions *options;
  MLKCustomRemoteModel *remoteModel = (MLKCustomRemoteModel *)[self remoteModel];

  if ([self.modelManager isModelDownloaded:remoteModel]) {
    NSLog(@"Use AutoML remote model.");
    options = [[MLKCustomImageLabelerOptions alloc] initWithRemoteModel:remoteModel];
  } else {
    NSLog(@"Use AutoML local model.");
    NSString *localModelFilePath =
        [[NSBundle mainBundle] pathForResource:MLKAutoMLLocalModelManifestFilename
                                        ofType:MLKAutoMLManifestFileType];
    if (localModelFilePath == nil) {
      self.resultsText =
          [NSMutableString stringWithFormat:@"Failed to find AutoML local model manifest file: %@",
                                            MLKAutoMLLocalModelManifestFilename];
      [self showResults];
      return;
    }
    MLKLocalModel *localModel = [[MLKLocalModel alloc] initWithManifestPath:localModelFilePath];
    options = [[MLKCustomImageLabelerOptions alloc] initWithLocalModel:localModel];
  }
  options.confidenceThreshold = @(labelConfidenceThreshold);
  // [END config_automl_label]

  // [START init_automl_label]
  MLKImageLabeler *autoMLImageLabeler = [MLKImageLabeler imageLabelerWithOptions:options];
  // [END init_automl_label]

  // Initialize a VisionImage object with the given UIImage.
  MLKVisionImage *visionImage = [[MLKVisionImage alloc] initWithImage:image];
  visionImage.orientation = image.imageOrientation;

  // [START detect_automl_label]
  __weak typeof(self) weakSelf = self;
  [autoMLImageLabeler
      processImage:visionImage
        completion:^(NSArray<MLKImageLabel *> *_Nullable labels, NSError *_Nullable error) {
          __strong typeof(weakSelf) strongSelf = weakSelf;
          if (!labels || labels.count == 0) {
            // [START_EXCLUDE]
            NSString *errorString = error ? error.localizedDescription : detectionNoResultsMessage;
            [strongSelf.resultsText
                appendFormat:@"AutoML image labeling failed with error: %@", errorString];
            [strongSelf showResults];
            // [END_EXCLUDE]
            return;
          }

          // [START_EXCLUDE]
          [strongSelf.resultsText setString:@""];
          for (MLKImageLabel *label in labels) {
            [strongSelf.resultsText
                appendFormat:@"Label: %@, Confidence: %f\n", label.text, label.confidence];
          }
          [strongSelf showResults];
          // [END_EXCLUDE]
        }];
  // [END detect_automl_label]
}

/**
 * Detects objects on the specified image using image classification models trained by AutoML via
 * Custom Object Detection API.
 *
 * @param image The input image.
 * @param shouldEnableClassification Whether image classification should be enabled.
 * @param shouldEnableMultipleObjects Whether multi-object detection should be enabled.
 */
- (void)detectObjectsInImage:(UIImage *)image
     shouldEnableClassification:(BOOL)shouldEnableClassification
    shouldEnableMultipleObjects:(BOOL)shouldEnableMultipleObjects {
  if (!image) {
    return;
  }
  [self requestAutoMLRemoteModelIfNeeded];

  MLKCustomRemoteModel *remoteModel = (MLKCustomRemoteModel *)[self remoteModel];
  MLKCustomObjectDetectorOptions *options;

  if ([self.modelManager isModelDownloaded:remoteModel]) {
    NSLog(@"Use AutoML remote model.");
    options = [[MLKCustomObjectDetectorOptions alloc] initWithRemoteModel:remoteModel];
  } else {
    NSLog(@"Use AutoML local model.");
    NSString *localModelFilePath =
        [[NSBundle mainBundle] pathForResource:MLKAutoMLLocalModelManifestFilename
                                        ofType:MLKAutoMLManifestFileType];
    if (localModelFilePath == nil) {
      self.resultsText =
          [NSMutableString stringWithFormat:@"Failed to find AutoML local model manifest file: %@",
                                            MLKAutoMLLocalModelManifestFilename];
      [self showResults];
      return;
    }
    MLKLocalModel *localModel = [[MLKLocalModel alloc] initWithManifestPath:localModelFilePath];
    options = [[MLKCustomObjectDetectorOptions alloc] initWithLocalModel:localModel];
  }
  options.shouldEnableClassification = shouldEnableClassification;
  options.shouldEnableMultipleObjects = shouldEnableMultipleObjects;
  options.detectorMode = MLKObjectDetectorModeSingleImage;

  MLKObjectDetector *autoMLObjectDetector = [MLKObjectDetector objectDetectorWithOptions:options];

  // Initialize a VisionImage object with the given UIImage.
  MLKVisionImage *visionImage = [[MLKVisionImage alloc] initWithImage:image];
  visionImage.orientation = image.imageOrientation;

  __weak typeof(self) weakSelf = self;
  [autoMLObjectDetector
      processImage:visionImage
        completion:^(NSArray<MLKObject *> *_Nullable objects, NSError *_Nullable error) {
          __strong typeof(weakSelf) strongSelf = weakSelf;
          if (!objects || objects.count == 0) {
            NSString *errorString = error ? error.localizedDescription : detectionNoResultsMessage;
            [strongSelf.resultsText
                appendFormat:@"AutoML object detection failed with error: %@", errorString];
            [strongSelf showResults];
            return;
          }

          [strongSelf.resultsText setString:@""];
          for (MLKObject *object in objects) {
            CGAffineTransform transform = [self transformMatrix];
            CGRect transformedRect = CGRectApplyAffineTransform(object.frame, transform);
            [UIUtilities addRectangle:transformedRect
                               toView:self.annotationOverlayView
                                color:UIColor.greenColor];
            [strongSelf.resultsText appendFormat:@"Frame: %@\nObject ID: %@\nLabels:\n",
                                                 NSStringFromCGRect(object.frame),
                                                 object.trackingID];
            int i = 0;
            for (MLKObjectLabel *l in object.labels) {
              NSString *labelString =
                  [NSString stringWithFormat:@"Label %d: %@, %f, %lu\n", i++, l.text, l.confidence,
                                             (unsigned long)l.index];
              [strongSelf.resultsText appendString:labelString];
            }
          }
          [strongSelf showResults];
        }];
}

- (CGAffineTransform)transformMatrix {
  UIImage *image = self.imageView.image;
  if (!image) {
    return CGAffineTransformMake(0, 0, 0, 0, 0, 0);
  }
  CGFloat imageViewWidth = self.imageView.frame.size.width;
  CGFloat imageViewHeight = self.imageView.frame.size.height;
  CGFloat imageWidth = image.size.width;
  CGFloat imageHeight = image.size.height;

  CGFloat imageViewAspectRatio = imageViewWidth / imageViewHeight;
  CGFloat imageAspectRatio = imageWidth / imageHeight;
  CGFloat scale = (imageViewAspectRatio > imageAspectRatio) ? imageViewHeight / imageHeight
                                                            : imageViewWidth / imageWidth;

  // Image view's `contentMode` is `scaleAspectFit`, which scales the image to fit the size of the
  // image view by maintaining the aspect ratio. Multiple by `scale` to get image's original size.
  CGFloat scaledImageWidth = imageWidth * scale;
  CGFloat scaledImageHeight = imageHeight * scale;
  CGFloat xValue = (imageViewWidth - scaledImageWidth) / 2.0;
  CGFloat yValue = (imageViewHeight - scaledImageHeight) / 2.0;

  CGAffineTransform transform =
      CGAffineTransformTranslate(CGAffineTransformIdentity, xValue, yValue);
  return CGAffineTransformScale(transform, scale, scale);
}

- (void)requestAutoMLRemoteModelIfNeeded {
  MLKRemoteModel *remoteModel = [self remoteModel];
  if ([self.modelManager isModelDownloaded:remoteModel]) {
    return;
  }
  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    strongSelf.downloadProgressView.hidden = NO;
    MLKModelDownloadConditions *conditions =
        [[MLKModelDownloadConditions alloc] initWithAllowsCellularAccess:YES
                                             allowsBackgroundDownloading:YES];
    strongSelf.downloadProgressView.observedProgress =
        [strongSelf.modelManager downloadModel:remoteModel conditions:conditions];
    NSLog(@"Start downloading AutoML remote model.");
  });
}

#pragma mark - Notifications

- (void)remoteModelDownloadDidSucceed:(NSNotification *)notification {
  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    strongSelf.downloadProgressView.hidden = YES;
    strongSelf.downloadOrDeleteModelButton.image = [UIImage imageNamed:@"delete"];
    MLKRemoteModel *remotemodel = notification.userInfo[MLKModelDownloadUserInfoKeyRemoteModel];
    if (remotemodel == nil) {
      [strongSelf.resultsText appendString:@"MLKitModelDownloadDidSucceed notification posted "
                                           @"without a RemoteModel instance."];
      return;
    }
    [strongSelf.resultsText
        appendFormat:@"Successfully downloaded the remote model with name: %@. The "
                     @"model is ready for detection.",
                     remotemodel.name];
    NSLog(@"Successfully downloaded AutoML remote model.");
  });
}

- (void)remoteModelDownloadDidFail:(NSNotification *)notification {
  __weak typeof(self) weakSelf = self;
  dispatch_async(dispatch_get_main_queue(), ^{
    __strong typeof(weakSelf) strongSelf = weakSelf;
    strongSelf.downloadProgressView.hidden = YES;
    MLKRemoteModel *remoteModel = notification.userInfo[MLKModelDownloadUserInfoKeyRemoteModel];
    NSError *error = notification.userInfo[MLKModelDownloadUserInfoKeyError];
    if (error == nil) {
      [strongSelf.resultsText appendString:@"MLKitModelDownloadDidFail notification posted without "
                                           @"a RemoteModel instance or error."];
      return;
    }
    [strongSelf.resultsText
        appendFormat:@"Failed to download the remote model with name: %@, error: %@.", remoteModel,
                     error.localizedDescription];
    NSLog(@"Failed to download AutoML remote model.");
  });
}

@end

NS_ASSUME_NONNULL_END
