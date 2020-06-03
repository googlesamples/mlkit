#include <XCTest/XCTest.h>

NS_ASSUME_NONNULL_BEGIN

@interface SmokeTests : XCTestCase

@end

@implementation SmokeTests

- (void)testAppLaunches {
  XCUIApplication *app = [[XCUIApplication alloc] init];
  [app launch];
}

@end

NS_ASSUME_NONNULL_END
