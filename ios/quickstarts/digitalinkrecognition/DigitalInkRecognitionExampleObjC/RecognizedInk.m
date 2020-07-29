#import "RecognizedInk.h"

#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

@import MLKit;

NS_ASSUME_NONNULL_BEGIN

@implementation RecognizedInk : NSObject

- (nullable instancetype)initWithInk:(MLKInk *)ink {
  self = [super init];
  if (self != nil) {
    _ink = ink;
  }
  return self;
}

@end

NS_ASSUME_NONNULL_END
