/**
 * Copyright 2020 Google ML Kit team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#import "MLKStartPageViewController.h"

#import "MLKLiveObjectDetectionViewController.h"
#import "MLKStartPageHeaderView.h"
#import "MLKStartPageCell.h"

@import MaterialComponents;

NS_ASSUME_NONNULL_BEGIN

static NSString *const kStartPageCellReuseIdentifier = @"ShowcaseItemCell";

@interface MLKStartPageViewController ()<MLKLiveObjectDetectionViewControllerDelegate>

/** Cell that is used to calculate the height of each row. */
@property(nonatomic) MLKStartPageCell *measureCell;

@end

@implementation MLKStartPageViewController

#pragma mark - Public

- (instancetype)init {
  UICollectionViewFlowLayout *layout = [[UICollectionViewFlowLayout alloc] init];
  self = [super initWithCollectionViewLayout:layout];
  if (self != nil) {
    _measureCell = [[MLKStartPageCell alloc] init];
  }
  return self;
}

- (void)updateMinMaxHeightForHeaderView {
  MDCFlexibleHeaderView *flexibleHeaderView = self.headerViewController.headerView;
  flexibleHeaderView.maximumHeight =
      [self.headerView maxHeaderHeightForWidth:self.view.bounds.size.width];
  flexibleHeaderView.minimumHeight =
      [self.headerView minHeaderHeightForWidth:self.view.bounds.size.width];
}

#pragma mark - UIViewController

- (void)viewDidLoad {
  [super viewDidLoad];

  self.collectionView.backgroundColor = UIColor.blackColor;
  // Register cell classes
  [self.collectionView registerClass:MLKStartPageCell.class
          forCellWithReuseIdentifier:kStartPageCellReuseIdentifier];

  [self addFlexibleHeader];
}

#pragma mark - UICollectionViewDataSource

- (NSInteger)numberOfSectionsInCollectionView:(UICollectionView *)collectionView {
  return 1;
}

- (NSInteger)collectionView:(UICollectionView *)collectionView
     numberOfItemsInSection:(NSInteger)section {
  return MLKDetectorTypeCount;
}

- (UICollectionViewCell *)collectionView:(UICollectionView *)collectionView
                  cellForItemAtIndexPath:(NSIndexPath *)indexPath {
  MLKStartPageCell *cell =
      [collectionView dequeueReusableCellWithReuseIdentifier:kStartPageCellReuseIdentifier
                                                forIndexPath:indexPath];
  [cell isCellPopulatedWithName:[self nameForRowIndex:indexPath.row]
                        details:[self detailsForRowIndex:indexPath.row]];
  [cell setNeedsLayout];
  return cell;
}

- (CGSize)collectionView:(UICollectionView *)collectionView
                    layout:(nonnull UICollectionViewLayout *)collectionViewLayout
    sizeForItemAtIndexPath:(nonnull NSIndexPath *)indexPath {
  [self.measureCell isCellPopulatedWithName:[self nameForRowIndex:indexPath.row]
                                    details:[self detailsForRowIndex:indexPath.row]];
  CGFloat contentWidth = self.view.frame.size.width - self.collectionView.contentInset.left -
                         self.collectionView.contentInset.right;
  return CGSizeMake(contentWidth,
                    [self.measureCell sizeThatFits:CGSizeMake(contentWidth, CGFLOAT_MAX)].height);
}

  - (void)collectionView:(UICollectionView *)collectionView
didSelectItemAtIndexPath:(NSIndexPath *)indexPath {
    MLKLiveObjectDetectionViewController *liveViewController = [[MLKLiveObjectDetectionViewController alloc] initWithDetectorType:indexPath.row];
    liveViewController.modalPresentationStyle = UIModalPresentationFullScreen;
    [self presentViewController:liveViewController animated:YES completion:nil];
    liveViewController.delegate = self;
  }

#pragma mark - UIScrollViewDelegate

- (void)scrollViewDidScroll:(UIScrollView *)scrollView {
  if (scrollView == self.headerViewController.headerView.trackingScrollView) {
    [self.headerViewController.headerView trackingScrollViewDidScroll];
  }
}

- (void)scrollViewDidEndDecelerating:(UIScrollView *)scrollView {
  if (scrollView == self.headerViewController.headerView.trackingScrollView) {
    [self.headerViewController.headerView trackingScrollViewDidEndDecelerating];
  }
}

- (void)scrollViewDidEndDragging:(UIScrollView *)scrollView willDecelerate:(BOOL)decelerate {
  if (scrollView == self.headerViewController.headerView.trackingScrollView) {
    [self.headerViewController.headerView
        trackingScrollViewDidEndDraggingWillDecelerate:decelerate];
  }
}

- (void)scrollViewWillEndDragging:(UIScrollView *)scrollView
                     withVelocity:(CGPoint)velocity
              targetContentOffset:(inout CGPoint *)targetContentOffset {
  if (scrollView == self.headerViewController.headerView.trackingScrollView) {
    [self.headerViewController.headerView
        trackingScrollViewWillEndDraggingWithVelocity:velocity
                                  targetContentOffset:targetContentOffset];
  }
}

#pragma mark - Private

- (void)addFlexibleHeader {
  if (self.headerViewController == nil) {
    self.headerViewController = [[MDCFlexibleHeaderViewController alloc] init];
  }
  if (self.headerView == nil) {
    self.headerView = [[MLKStartPageHeaderView alloc] init];
  }
  NSString *headerText = @"MLKit Showcase";
  self.headerView.titleLabel.text = headerText;
  [self updateMinMaxHeightForHeaderView];

  [self.headerViewController willMoveToParentViewController:self];
  [self addChildViewController:self.headerViewController];

  self.headerView.autoresizingMask =
      (UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight);

  MDCFlexibleHeaderView *flexibleHeaderView = self.headerViewController.headerView;
  flexibleHeaderView.canOverExtend = NO;
  flexibleHeaderView.trackingScrollView = self.collectionView;

  [flexibleHeaderView addSubview:self.headerView];
  [self.view addSubview:flexibleHeaderView];

  self.headerView.frame = flexibleHeaderView.bounds;
  flexibleHeaderView.frame = self.view.bounds;

  [self.headerViewController didMoveToParentViewController:self];
}

/** Returns the name of the item for given index. */
- (nullable NSString *)nameForRowIndex:(NSInteger)index {
  static dispatch_once_t onceToken;
  static NSDictionary<NSNumber *, NSString *>* gShowcaseItemTypeToName;
  dispatch_once(&onceToken, ^{
     gShowcaseItemTypeToName = @{
       @(MLKDetectorTypeODTDefaultModel): @"Object Detection",
       @(MLKDetectorTypeODTBirdModel): @"Object Detection: Bird",
     };
  });
  return gShowcaseItemTypeToName[@(index)];
}

/** Returns the details of the item for given index. */
- (nullable NSString *)detailsForRowIndex:(NSInteger)index {
  static dispatch_once_t onceToken;
  static NSDictionary<NSNumber *, NSString *>* gShowcaseItemTypeToDetails;
  dispatch_once(&onceToken, ^{
    gShowcaseItemTypeToDetails = @{
      @(MLKDetectorTypeODTDefaultModel): @"Detect objects in the live camera view.",
      @(MLKDetectorTypeODTBirdModel): @"Detect bird in the camera view.",
    };
  });
  return gShowcaseItemTypeToDetails[@(index)];
}

#pragma mark - MLKLiveObjectDetectionViewControllerDelegate

- (void)didTapBackButton {
  [self dismissViewControllerAnimated:YES completion:nil];
}

@end

NS_ASSUME_NONNULL_END
