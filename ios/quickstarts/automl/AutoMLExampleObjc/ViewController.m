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
static NSString *const MLKAutoMLImageLabelerLocalModelManifestFilename = @"automl_labeler_manifest";

/** File type of AutoML local model manifest in the main resource bundle. */
static NSString *const MLKAutoMLManifestFileType = @"json";

static float const labelConfidenceThreshold = 0.75;
static CGColorRef lineColor;
static CGColorRef fillColor;

static int const rowsCount = 1;
static int const componentsCount = 1;

/**
 * @enum DetectorPickerRow
 * Defines the MLKit detector types.
 */
typedef NS_ENUM(NSInteger, DetectorPickerRow) {
  /** On-Device vision AutoML image label detector. */
  DetectorPickerRowDetectImageLabelsAutoML,
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
      return @"AutoML Image Classification";
  }
}

- (void)viewDidLoad {
  [super viewDidLoad];

  images = @[
    @"daisy.jpeg",
    @"dandelion.jpg",
    @"roses.jpeg",
    @"sunflower.jpg",
    @"tulips.jpeg",
  ];
  lineColor = UIColor.yellowColor.CGColor;
  fillColor = UIColor.clearColor.CGColor;

  _modelManager = [MLKModelManager modelManager];
  MLKAutoMLImageLabelerRemoteModel *remoteModel =
      [[MLKAutoMLImageLabelerRemoteModel alloc] initWithName:MLKRemoteAutoMLModelName];
  NSString *buttonImage =
      [self.modelManager isModelDownloaded:remoteModel] ? @"delete" : @"cloud_download";
  self.downloadOrDeleteModelButton.image = [UIImage imageNamed:buttonImage];

  self.imagePicker = [UIImagePickerController new];
  self.resultsText = [NSMutableString new];
  _currentImage = 0;
  _imageView.image = [UIImage imageNamed:images[_currentImage]];
  _annotationOverlayView = [[UIView alloc] initWithFrame:CGRectZero];
  _annotationOverlayView.translatesAutoresizingMaskIntoConstraints = NO;
  [_imageView addSubview:_annotationOverlayView];
  [NSLayoutConstraint activateConstraints:@[
    [_annotationOverlayView.topAnchor constraintEqualToAnchor:_imageView.topAnchor],
    [_annotationOverlayView.leadingAnchor constraintEqualToAnchor:_imageView.leadingAnchor],
    [_annotationOverlayView.trailingAnchor constraintEqualToAnchor:_imageView.trailingAnchor],
    [_annotationOverlayView.bottomAnchor constraintEqualToAnchor:_imageView.bottomAnchor]
  ]];
  _imagePicker.delegate = self;
  _imagePicker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;

  _detectorPicker.delegate = self;
  _detectorPicker.dataSource = self;

  BOOL isCameraAvailable =
      [UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceFront] ||
      [UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceRear];
  if (isCameraAvailable) {
    // `CameraViewController` uses `AVCaptureDeviceDiscoverySession` which is only supported for
    // iOS 10 or newer.
    if (@available(iOS 10, *)) {
      [_videoCameraButton setEnabled:YES];
    }
  } else {
    [_photoCameraButton setEnabled:NO];
  }
  [_detectorPicker selectRow:0 inComponent:0 animated:NO];
}

- (void)viewWillAppear:(BOOL)animated {
  [super viewWillAppear:animated];
  [self.navigationController.navigationBar setHidden:YES];
}

- (void)viewWillDisappear:(BOOL)animated {
  [super viewWillDisappear:animated];
  [self.navigationController.navigationBar setHidden:NO];
}

- (IBAction)detect:(id)sender {
  [self clearResults];
  [self detectImageLabelsInImage:_imageView.image];
}

- (IBAction)openPhotoLibrary:(id)sender {
  _imagePicker.sourceType = UIImagePickerControllerSourceTypePhotoLibrary;
  [self presentViewController:_imagePicker animated:YES completion:nil];
}

- (IBAction)openCamera:(id)sender {
  if (![UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceFront] &&
      ![UIImagePickerController isCameraDeviceAvailable:UIImagePickerControllerCameraDeviceRear]) {
    return;
  }
  _imagePicker.sourceType = UIImagePickerControllerSourceTypeCamera;
  [self presentViewController:_imagePicker animated:YES completion:nil];
}

- (IBAction)changeImage:(id)sender {
  [self clearResults];
  self.currentImage = (_currentImage + 1) % images.count;
  _imageView.image = [UIImage imageNamed:images[_currentImage]];
}

- (IBAction)downloadOrDeleteModel:(id)sender {
  [self clearResults];
  MLKAutoMLImageLabelerRemoteModel *remoteModel =
      [[MLKAutoMLImageLabelerRemoteModel alloc] initWithName:MLKRemoteAutoMLModelName];
  if ([self.modelManager isModelDownloaded:remoteModel]) {
    [self.modelManager
        deleteDownloadedModel:remoteModel
                   completion:^(NSError *_Nullable error) {
                     if (error) {
                       NSLog(@"Failed to delete the AutoML model.");
                       return;
                     }
                     NSLog(@"The downloaded remote model has been successfully deleted.");
                     self.downloadOrDeleteModelButton.image =
                         [UIImage imageNamed:@"cloud_download"];
                   }];
  }
}

/// Removes the detection annotations from the annotation overlay view.
- (void)removeDetectionAnnotations {
  for (UIView *annotationView in _annotationOverlayView.subviews) {
    [annotationView removeFromSuperview];
  }
}

/// Clears the results text view and removes any frames that are visible.
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
  resultsAlertController.message = _resultsText;
  resultsAlertController.popoverPresentationController.barButtonItem = _detectButton;
  resultsAlertController.popoverPresentationController.sourceView = self.view;
  [self presentViewController:resultsAlertController animated:YES completion:nil];
  NSLog(@"%@", _resultsText);
}

/// Updates the image view with a scaled version of the given image.
- (void)updateImageViewWithImage:(UIImage *)image {
  CGFloat scaledImageWidth = 0.0;
  CGFloat scaledImageHeight = 0.0;
  switch (UIApplication.sharedApplication.statusBarOrientation) {
    case UIInterfaceOrientationPortrait:
    case UIInterfaceOrientationPortraitUpsideDown:
    case UIInterfaceOrientationUnknown:
      scaledImageWidth = _imageView.bounds.size.width;
      scaledImageHeight = image.size.height * scaledImageWidth / image.size.width;
      break;
    case UIInterfaceOrientationLandscapeLeft:
    case UIInterfaceOrientationLandscapeRight:
      scaledImageWidth = image.size.width * scaledImageHeight / image.size.height;
      scaledImageHeight = _imageView.bounds.size.height;
      break;
  }

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
      self->_imageView.image = scaledImage;
    });
  });
}

- (CGAffineTransform)transformMatrix {
  UIImage *image = _imageView.image;
  if (!image) {
    return CGAffineTransformMake(0, 0, 0, 0, 0, 0);
  }
  CGFloat imageViewWidth = _imageView.frame.size.width;
  CGFloat imageViewHeight = _imageView.frame.size.height;
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
  self.downloadOrDeleteModelButton.enabled = row == DetectorPickerRowDetectImageLabelsAutoML;
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

#pragma mark - Vision On-Device Detection

/// Detects labels on the specified image using AutoML On-Device label API.
///
/// - Parameter image: The image.
- (void)detectImageLabelsInImage:(UIImage *)image {
  if (!image) {
    return;
  }
  [self requestAutoMLRemoteModelIfNeeded];

  // [START config_automl_label]
  MLKAutoMLImageLabelerOptions *options;
  MLKAutoMLImageLabelerRemoteModel *remoteModel =
      [[MLKAutoMLImageLabelerRemoteModel alloc] initWithName:MLKRemoteAutoMLModelName];
  if ([self.modelManager isModelDownloaded:remoteModel]) {
    NSLog(@"Use AutoML remote model.");
    options = [[MLKAutoMLImageLabelerOptions alloc] initWithRemoteModel:remoteModel];
  } else {
    NSLog(@"Use AutoML local model.");
    NSString *localModelFilePath =
        [[NSBundle mainBundle] pathForResource:MLKAutoMLImageLabelerLocalModelManifestFilename
                                        ofType:MLKAutoMLManifestFileType];
    if (localModelFilePath == nil) {
      self.resultsText =
          [NSMutableString stringWithFormat:@"Failed to find AutoML local model manifest file: %@",
                                            MLKAutoMLImageLabelerLocalModelManifestFilename];
      [self showResults];
      return;
    }
    MLKAutoMLImageLabelerLocalModel *localModel =
        [[MLKAutoMLImageLabelerLocalModel alloc] initWithManifestPath:localModelFilePath];
    options = [[MLKAutoMLImageLabelerOptions alloc] initWithLocalModel:localModel];
  }
  options.confidenceThreshold = @(labelConfidenceThreshold);
  // [END config_automl_label]

  // [START init_automl_label]
  MLKImageLabeler *autoMLImageLabeler = [MLKImageLabeler imageLabelerWithOptions: options];
  // [END init_automl_label]

  // Initialize a VisionImage object with the given UIImage.
  MLKVisionImage *visionImage = [[MLKVisionImage alloc] initWithImage:image];
  visionImage.orientation = image.imageOrientation;

  // [START detect_automl_label]
  [autoMLImageLabeler
      processImage:visionImage
        completion:^(NSArray<MLKImageLabel *> *_Nullable labels, NSError *_Nullable error) {
          if (!labels || labels.count == 0) {
            // [START_EXCLUDE]
            NSString *errorString = error ? error.localizedDescription : detectionNoResultsMessage;
            [self.resultsText
                appendFormat:@"AutoML On-Device label detection failed with error: %@",
                             errorString];
            [self showResults];
            // [END_EXCLUDE]
            return;
          }

          // [START_EXCLUDE]
          [self.resultsText setString:@""];
          for (MLKImageLabel *label in labels) {
            [self.resultsText
                appendFormat:@"Label: %@, Confidence: %f\n", label.text, label.confidence];
          }
          [self showResults];
          // [END_EXCLUDE]
        }];
  // [END detect_automl_label]
}

- (void)requestAutoMLRemoteModelIfNeeded {
  MLKAutoMLImageLabelerRemoteModel *remoteModel =
      [[MLKAutoMLImageLabelerRemoteModel alloc] initWithName:MLKRemoteAutoMLModelName];
  if ([self.modelManager isModelDownloaded:remoteModel]) {
    return;
  }
  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(remoteModelDownloadDidSucceed:)
                                             name:MLKModelDownloadDidSucceedNotification
                                           object:nil];
  [NSNotificationCenter.defaultCenter addObserver:self
                                         selector:@selector(remoteModelDownloadDidFail:)
                                             name:MLKModelDownloadDidFailNotification
                                           object:nil];

  dispatch_async(dispatch_get_main_queue(), ^{
    self.downloadProgressView.hidden = NO;
    MLKModelDownloadConditions *conditions =
        [[MLKModelDownloadConditions alloc] initWithAllowsCellularAccess:YES
                                             allowsBackgroundDownloading:YES];
    self.downloadProgressView.observedProgress = [self.modelManager downloadModel:remoteModel
                                                                       conditions:conditions];
    NSLog(@"Start downloading AutoML remote model.");
  });
}

#pragma mark - Notifications

- (void)remoteModelDownloadDidSucceed:(NSNotification *)notification {
  dispatch_async(dispatch_get_main_queue(), ^{
    self.downloadProgressView.hidden = YES;
    self.downloadOrDeleteModelButton.image = [UIImage imageNamed:@"delete"];
    MLKAutoMLImageLabelerRemoteModel *remotemodel =
        notification.userInfo[MLKModelDownloadUserInfoKeyRemoteModel];
    if (remotemodel == nil) {
      [self.resultsText appendString:@"MLKitModelDownloadDidSucceed notification posted "
                                     @"without a RemoteModel instance."];
      return;
    }
    [self.resultsText appendFormat:@"Successfully downloaded the remote model with name: %@. The "
                                   @"model is ready for detection.",
                                   remotemodel.name];
    NSLog(@"Successfully downloaded AutoML remote model.");
  });
}

- (void)remoteModelDownloadDidFail:(NSNotification *)notification {
  dispatch_async(dispatch_get_main_queue(), ^{
    self.downloadProgressView.hidden = YES;
    MLKAutoMLImageLabelerRemoteModel *remoteModel =
        notification.userInfo[MLKModelDownloadUserInfoKeyRemoteModel];
    NSError *error = notification.userInfo[MLKModelDownloadUserInfoKeyError];
    if (error == nil) {
      [self.resultsText appendString:@"MLKitModelDownloadDidFail notification posted without "
                                     @"a RemoteModel instance or error."];
      return;
    }
    [self.resultsText appendFormat:@"Failed to download the remote model with name: %@, error: %@.",
                                   remoteModel, error.localizedDescription];
    NSLog(@"Failed to download AutoML remote model.");
  });
}

@end

NS_ASSUME_NONNULL_END
